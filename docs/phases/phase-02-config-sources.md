# Phase 02 — Config Sources

## Goal

Plugin authors can declare "where the binary lives" as a `ConfigSource`
plus its `ConfigSourceType` (registry entry). Multiple types coexist;
adding a new one (Docker, mise, asdf, k8s exec) is one class in a
separate plugin.

## Feature

Mago, PHPStan, and a future Docker plugin can each register their own
source types. SDK has no hardcoded knowledge of which sources exist.

## Solution

Two interfaces in `dev.jplugins.qualitytools.core.source`:

```kotlin
public interface ConfigSource {
    public val instanceId: String          // uuid per profile entry
    public val typeId: String              // registry discriminator
    public val displayName: String
    public suspend fun resolve(ctx: ResolveContext): ResolvedBinary?
}

public interface ConfigSourceType {
    public val typeId: String
    public val aliasTypeIds: Set<String>
        get() = emptySet()
    public val displayName: String
    public val requiredPluginIds: Set<String>
        get() = emptySet()

    public fun isAvailable(ctx: AvailabilityContext): Boolean
    public fun createWizard(ctx: WizardContext): ConfigSourceWizard?
    public fun deserialize(element: SerializedSourceElement): ConfigSource
    public fun serialize(source: ConfigSource): SerializedSourceElement

    public fun watch(ctx: WatchContext, onDetected: (ConfigSource) -> Unit): AutoCloseable?
        = null
}
```

`ResolvedBinary`:

```kotlin
public interface ResolvedBinary {
    public val command: List<String>
    public val workingDir: String?
    public val env: Map<String, String>
    public val pathMapper: PathMapper        // identity by default
    public val supportsStdin: Boolean
}

public interface PathMapper {
    public fun toRemote(localPath: String): String = localPath
    public fun toLocal(remotePath: String): String = remotePath

    /**
     * Whether toRemote/toLocal would change [localPath].
     *
     * The Identity mapper returns FALSE; remote mappers return TRUE
     * for paths under their root. `PathAwareArgRewriter` rewrites
     * only when canProcess == true, so the Identity mapper is a
     * cheap no-op.
     */
    public fun canProcess(localPath: String): Boolean = false

    public companion object {
        public val Identity: PathMapper = object : PathMapper {}
    }
}
```

`SerializedSourceElement` is an opaque tree (string → string/list/tree)
that does not leak `org.jdom.Element` into `:core`. `:ui` provides the
JDOM adapter.

```kotlin
public interface SerializedSourceElement {
    public val name: String
    public val attributes: Map<String, String>
    public val children: List<SerializedSourceElement>
    public val text: String?

    public fun child(name: String): SerializedSourceElement?
    public fun children(name: String): List<SerializedSourceElement>
}

public interface SerializedSourceElementBuilder {
    public fun attr(name: String, value: String): SerializedSourceElementBuilder
    public fun text(value: String): SerializedSourceElementBuilder
    public fun child(name: String, build: SerializedSourceElementBuilder.() -> Unit = {}): SerializedSourceElementBuilder
    public fun build(): SerializedSourceElement
}
```

Consumers don't reimplement XML — `:core` ships a reflection codec:

```kotlin
public object SerializedSourceCodec {
    public fun <T : Any> encode(value: T): SerializedSourceElement
    public fun <T : Any> decode(element: SerializedSourceElement, klass: KClass<T>): T
}

@Target(AnnotationTarget.PROPERTY) public annotation class SerializedField(val name: String = "")
@Target(AnnotationTarget.PROPERTY) public annotation class SerializedAttribute(val name: String = "")
@Target(AnnotationTarget.PROPERTY) public annotation class SerializedListElement(val name: String)
```

Plugin authors write data-classes annotated with `@SerializedField` and
delegate `serialize`/`deserialize` to `SerializedSourceCodec`. Manual
implementation remains possible.

`:core` answers "who instantiates `ConfigSource` after XML load":
`QualityToolsProjectStorage` (phase 04) holds a `ConfigSourceRegistry`
reference and resolves `typeId` → `ConfigSourceType.deserialize(...)`
during state load. The registry implementation in `:ui` is **the
only one** allowed to inject EP-resolved types; tests use
`InMemoryConfigSourceRegistry` from `:testing`.

The registry:

```kotlin
public interface ConfigSourceRegistry {
    public fun findByTypeId(typeId: String): ConfigSourceType?
    public fun available(ctx: AvailabilityContext): List<ConfigSourceType>
    public fun all(): List<ConfigSourceType>
}
```

`ConfigSourceRegistry` is platform-bound — its implementation lives in
`:ui` and reads from an IntelliJ EP. `:core` only declares the
interface so consumers can mock it in tests. **No reflection of EPs in
`:core`.**

Bundled implementations (in `:core`):

- `LocalBinarySource` — `command = listOf(path)`, identity mapper.
- `LocalBinarySourceType` (typeId = `"local"`).

(`composer`, `docker`, `mise`, etc. live in `:php` or downstream
plugins.)

## Deliverables

`:core`:

- `source/ConfigSource.kt`
- `source/ConfigSourceType.kt`
- `source/ConfigSourceRegistry.kt` (interface only)
- `source/ResolvedBinary.kt`
- `source/PathMapper.kt`
- `source/SerializedSourceElement.kt`,
  `source/SerializedSourceElementBuilder.kt`
- `source/SerializedSourceCodec.kt` (+ `SerializedField`,
  `SerializedAttribute`, `SerializedListElement` annotations)
- `source/ResolveContext.kt`, `AvailabilityContext.kt`,
  `WizardContext.kt`, `WatchContext.kt` — context interfaces.
- `source/ConfigSourceWizard.kt` — UI-agnostic interface returning
  `Result<ConfigSource>`.
- `source/local/LocalBinarySource.kt`
- `source/local/LocalBinarySourceType.kt`

`:ui` (declared here so EP visibility is checkable in this phase):

- `META-INF/quality-tools-eps.xml` with `<extensionPoint
  qualifiedName="dev.jplugins.qualityTools.configSourceType"
  beanClass="…ConfigSourceTypeBean" dynamic="true"/>`
- `EpConfigSourceRegistry.kt` — registry impl reading the EP list.

`:core/src/test`:

- `LocalBinarySourceTest.kt`
- `SerializedSourceElementTest.kt`
- `PathMapperContractTest.kt`

## Acceptance criteria

- [ ] `ConfigSource` and `ConfigSourceType` are plain interfaces.
- [ ] `aliasTypeIds` exists for stable migration of `typeId`.
- [ ] `requiredPluginIds: Set<String>` — strings, not platform classes
      (kept testable without IntelliJ on classpath).
- [ ] `SerializedSourceElement` does not expose JDOM in `:core`.
- [ ] `PathMapper.Identity` is a singleton; consumers must not need to
      implement an empty mapper.
- [ ] `LocalBinarySource` round-trips through `serialize/deserialize`
      in a test.
- [ ] Registry implementation **not** present in `:core` — only the
      interface (verified by inspecting compiled classes).
- [ ] `watch` defaults to `null` (no autodetect by default).
- [ ] No `org.jdom.Element` import anywhere in `:core`.
- [ ] `SerializedSourceCodec.encode/decode` round-trips a sample
      `data class` with `@SerializedField`/`@SerializedAttribute`
      on every field type listed in `OptionSpec` family (phase 04).
- [ ] EP `dev.jplugins.qualityTools.configSourceType` is declared in
      `:ui/META-INF/quality-tools-eps.xml` with `dynamic="true"`.
- [ ] `EpConfigSourceRegistry` resolves the EP list lazily — does
      NOT instantiate every `ConfigSourceType` at startup.
- [ ] `EpConfigSourceRegistry` test asserts the `AutoCloseable`
      returned by `ConfigSourceType.watch(...)` is closed on
      project disposal AND on `isAvailable` flipping false (dynamic
      plugin unload).
- [ ] `ConfigSourceType.createWizard` is `@ThreadingPolicy("edt")`;
      `isAvailable` is `@ThreadingPolicy("any")`; `watch` is
      `@ThreadingPolicy("background")`; `deserialize`/`serialize`
      are `@ThreadingPolicy("any")`.
- [ ] `AvailabilityContext` exposes an `onAvailabilityChanged`
      hook so docker daemon start/stop and k8s context-switch
      refresh the registry list without project reload.

## Out of scope

- Scope.
- Profile.
- Wizard UI (only the interface here).
- Composer / Docker / Mise types (later phases or downstream).

## Depends on

`phase-01`.
