# Phase 04 — Options Schema & Persistence

## Goal

A tool declares its options with a small DSL (`OptionsSchema`). The
SDK stores them in one place (`QualityToolsProjectStorage`) and rounds
trips through XML. UI rendering is automatic by default.

## Feature

Mago drops 100% of its `MagoConfigurable` Swing/UI-DSL boilerplate.
PHPStan removes `PhpStanOptionsConfiguration`. Adding a checkbox is
adding one line to the schema.

## Solution

DSL builder lives in `:core`; UI rendering lives in `:ui`.

```kotlin
public interface OptionSpec<T : Any> {
    public val key: String
    public val displayName: String
    public val default: T
    public val help: String?
    public val role: String                 // free-form: "scope_root", "config_file", …
    public fun encode(value: T): String
    public fun decode(text: String): T?
}
```

Concrete specs (all in `:core`, all open interfaces extending
`OptionSpec<T>`, NOT a sealed hierarchy):

- `BoolSpec`, `IntSpec`, `StringSpec`, `PathSpec`, `ChoiceSpec`
  (`values: List<String>` — renamed from `EnumLikeSpec`),
  `StringListSpec`, `ListSpec` (compound).

Each spec exposes `isPath: Boolean` (true for `PathSpec`, propagates
to generated `ToolArg`s via `path-aware` consumers — see phase 05
`PathAwareArgRewriter`).

Factories (lowercase top-level functions in `OptionSpecs.kt`):

```kotlin
public fun bool(key: String, default: Boolean = false, help: String? = null): BoolSpec
public fun int(key: String, default: Int = 0, range: IntRange? = null, …): IntSpec
public fun string(key: String, default: String = "", …): StringSpec
public fun path(key: String, fileFilter: PathFilter = PathFilter.Any, …): PathSpec
public fun enumLike(key: String, values: List<String>, default: String, …): EnumLikeSpec
public fun stringList(key: String, default: List<String> = emptyList()): StringListSpec
public fun <E : Any> list(key: String, element: () -> CompoundSpec<E>): ListSpec<E>
```

Schema (interface, not abstract class — cycle 1 ruling):

```kotlin
public interface OptionsSchema {
    public val toolId: String
    public val specs: List<OptionSpec<*>>
    public val modeSchemas: Map<String, ModeSchema>
        get() = emptyMap()
}

public interface ModeSchema {
    public val specs: List<OptionSpec<*>>
}

public fun modeSchema(build: ModeSchemaBuilder.() -> Unit): ModeSchema =
    ModeSchemaBuilder().apply(build).build()
```

`ModeSchema` and `OptionsSchema` are separate interfaces — mode-level
schemas don't pretend to be full schemas (no `toolId`, no nested
modes), avoiding the abstract-class crutch.

`OptionsBag` — runtime read/write store:

```kotlin
public interface OptionsBag {
    public operator fun <T : Any> get(spec: OptionSpec<T>): T
    public operator fun <T : Any> set(spec: OptionSpec<T>, value: T)
    public fun snapshot(): Map<String, String>
    public fun mode(modeId: String): OptionsBag

    /**
     * Atomically publishes buffered writes to the backing
     * [QualityToolsProjectStorage]. Without commit(), set() is
     * visible within the same OptionsBag instance only.
     */
    public fun commit()
}
```

Persistence — `QualityToolsProjectStorage` interface in `:core`,
implementation `PersistentQualityToolsProjectStorage` in `:ui` (final
decision; see README cross-cutting table):

```kotlin
public interface QualityToolsProjectStorage {
    public fun profilesByTool(toolId: String): List<ConfigProfile>
    public fun saveProfile(profile: ConfigProfile)
    public fun deleteProfile(profileId: String)
    public fun activeProfileId(toolId: String, modeId: String): String?
    public fun setActiveProfileId(toolId: String, modeId: String, profileId: String?)
}
```

Implementation in `:php` (or `:ui` — TBD in cycle reviews):
`QualityToolsProjectStoragePersistent` — `@Service(Project) +
PersistentStateComponent`.

XML representation (one `<storageState>` with nested `<profile>`
elements). Versioned envelope `version="1"`.

## Deliverables

`:core/options/`:

- `OptionSpec.kt`, `OptionSpecs.kt` (factories)
- `BoolSpec.kt`, `IntSpec.kt`, `StringSpec.kt`, `PathSpec.kt`,
  `ChoiceSpec.kt`, `StringListSpec.kt`, `ListSpec.kt`, `CompoundSpec.kt`
- `PathFilter.kt`
- `OptionsSchema.kt` (interface)
- `ModeSchema.kt` (interface) + `ModeSchemaBuilder.kt`
- `OptionsBag.kt`
- `QualityToolsProjectStorage.kt` (interface only)

(`MapOptionsBag` and `InMemoryQualityToolsProjectStorage` ship in
`:testing` only — phase 10. They are NOT deliverables of `:core`.)

Persistence implementation (`PersistentQualityToolsProjectStorage`)
lives in `:ui` because XML serialization is JDOM-coupled. This is
documented in README.md "Cross-cutting concerns".

Tests:

- `OptionsBagTest.kt` — read/write/default.
- `OptionsSchemaRoundTripTest.kt` — encode/decode for every spec kind.
- `ModeSchemaTest.kt`.

## Acceptance criteria

- [ ] No spec is `sealed`.
- [ ] `OptionsSchema` and `ModeSchema` are plain interfaces. **No
      abstract classes in `:core`.**
- [ ] Every spec round-trips through `encode/decode`.
- [ ] `OptionsBag.mode("analyze")` returns an overlay that falls back
      to top-level when a key isn't set.
- [ ] `OptionsBag.set` mutation semantics documented: writes go to a
      buffered snapshot, `commit()` atomically publishes to
      `QualityToolsProjectStorage`; reads from concurrent threads
      see last-committed.
- [ ] `QualityToolsProjectStorage` interface has no IntelliJ imports.
- [ ] No `MapOptionsBag` in `:core` (lives in `:testing`).
- [ ] `PathSpec.isPath == true` is wired so generated `ToolArg`s
      carry `isPath=true`.
- [ ] Adding a new spec type does not require touching any other file.

## Out of scope

- Auto-rendering UI (phase 07).
- Migration of existing `php.xml` records (phase 09).

## Depends on

`phase-03`.
