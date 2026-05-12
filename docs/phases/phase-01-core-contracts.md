# Phase 01 — Core Contracts

## Goal

`:core` exposes the minimum vocabulary every consumer needs: the tool,
its modes, and the target a run is invoked against. No runtime
behavior yet — just data + interfaces.

## Feature

A plugin author can author a class `class MagoTool : QualityTool { … }`
and have the compiler enforce the right shape.

## Solution

Everything is a plain `interface`. There is no `abstract class
QualityToolBase`. There is no `sealed`. No PHP / IntelliJ imports.

Top-level interfaces in `dev.jplugins.qualitytools.core`. None of
these uses `sealed` or `abstract class`. Every method either has a
`default` body or is `v1`-required.

```kotlin
public interface QualityTool {
    public val id: String
    public val displayName: String
    public val supportedLanguageIds: Set<String>      // "PHP", "JavaScript", …
    public val modes: List<ToolMode>
    public val capabilities: Set<String>              // "lint", "analyze", "fix"
    public val acceptedSourceTypeIds: Set<String>     // "*" for any source
    public val resultReaderId: String

    /** Schema for this tool's options. Auto-rendered into Settings. */
    public val optionsSchema: OptionsSchema

    /**
     * Inspection short-names this tool surfaces. Used by the legacy
     * `QualityToolType` bridge and by the platform's "run inspection
     * by name" lookup. Default: one entry per mode.
     */
    public val inspectionShortNames: Set<String>
        get() = modes.map { "${id.replaceFirstChar { c -> c.uppercase() }}${it.id.replaceFirstChar { c -> c.uppercase() }}" }.toSet()

    /**
     * Whether this tool is visible in the Settings/Quality Tools UI.
     * `Hidden` is used by CI-only / programmatic tools (no panel).
     */
    public val ui: ToolUi
        get() = ToolUi.Default

    public fun buildArgs(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg>
}

public interface ToolUi {
    public companion object {
        public val Default: ToolUi = object : ToolUi {}
        public val Hidden: ToolUi = object : ToolUi {}
    }
}

/**
 * Sugar for the common case. Tool authors who don't need the full
 * interface can declare a tool in four lines:
 *
 *     val pest = qualityTool("pest") {
 *         displayName = "Pest"
 *         languages("PHP")
 *         mode("test") { verb = "test"; outputFormat = "junit-xml" }
 *     }
 */
public fun qualityTool(id: String, build: QualityToolBuilder.() -> Unit): QualityTool

public interface ToolMode {
    public val id: String
    public val displayName: String
    public val verb: String
    public val outputFormat: String                  // matches reader id or "inherit"
    public val executionStyle: String                // "on_the_fly" | "on_save" | "manual" | "batch" | "format"
    public val formattingOutputMode: String          // "stdout" | "in_place"; only meaningful when executionStyle == "format"
        get() = "stdout"
    public val defaultArgs: List<ToolArg>
    public val supportsStdin: Boolean
    public val supportsFix: Boolean
    public val pathArgKeys: Set<String>              // e.g.: "--config", "--workspace"
}

public interface ToolTarget {
    public val normalizedPath: String
    public fun toCliArg(scope: ResolvedScope): ToolArg
}
```

`ToolArg` is an open interface — consumers can implement custom
kinds. Helpers live as top-level functions in `ToolArgs.kt`.

```kotlin
public interface ToolArg {
    public val raw: String
    public val isPath: Boolean
    public val pathPrefix: String?                   // for "--config=/abs/path", pathPrefix = "--config="
}

// Built-ins (no sealed):
public fun plainArg(raw: String): ToolArg              // isPath=false
public fun pathArg(raw: String): ToolArg               // isPath=true, pathPrefix=null
public fun kvPathArg(key: String, value: String): ToolArg
                                                       // raw="--key=value", isPath=true, pathPrefix="--key="
```

`PathAwareArgRewriter` (phase 05) walks the args list and rewrites
each one where `arg.isPath && pathMapper.canProcess(value)`, taking
`pathPrefix` into account.

`ToolRunContext` is **finalized here** to unblock later phases:

```kotlin
public interface ToolRunContext {
    public val projectId: String
    public val basePath: String?
    public val displayPath: String?           // user-visible path; differs from actual when stdin-via-tempfile
    public val actualPath: String?            // path actually fed to tool (may be temp file)
    public val tool: QualityTool
    public val cancellation: CancellationToken
    public val logger: QtLogger
}
```

`CancellationToken` and `QtLogger` are pin-pointed cross-cutting
contracts (see README.md "Cross-cutting concerns"):

```kotlin
public interface CancellationToken {
    public val isCanceled: Boolean
    public fun throwIfCanceled()
    /** Schedules cancellation; callers continue, the next throwIfCanceled() raises. */
    public fun cancel()
    /**
     * Registers a callback fired on cancellation; returned closeable
     * unregisters. Used to wire process destruction to the single
     * cancellation channel.
     */
    public fun onCancel(handler: () -> Unit): AutoCloseable
}

public fun interface QtLogger {
    public fun log(level: String, message: String, throwable: Throwable? = null)
}
```

`ToolRegistry` — looking up `QualityTool`s by id without coupling
`:core` to an extension-point implementation:

```kotlin
public interface ToolRegistry {
    public fun byId(id: String): QualityTool?
    public fun all(): List<QualityTool>
    public fun byLanguageId(languageId: String): List<QualityTool>
}
```

`@ThreadingPolicy` annotation (rule 14):

```kotlin
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class ThreadingPolicy(public val value: String)
// value ∈ {"any", "background", "edt"}
```

`ResolvedScope` is forward-declared here (concrete shape in phase 03).

## Deliverables

`:core` files:

- `dev/jplugins/qualitytools/core/QualityTool.kt`
- `dev/jplugins/qualitytools/core/ToolMode.kt`
- `dev/jplugins/qualitytools/core/ToolTarget.kt`
- `dev/jplugins/qualitytools/core/ToolArg.kt` + `ToolArgs.kt`
- `dev/jplugins/qualitytools/core/ToolRunContext.kt` (finalized)
- `dev/jplugins/qualitytools/core/CancellationToken.kt`
- `dev/jplugins/qualitytools/core/QtLogger.kt`
- `dev/jplugins/qualitytools/core/ToolRegistry.kt`
- `dev/jplugins/qualitytools/core/ToolUi.kt`
- `dev/jplugins/qualitytools/core/QualityToolBuilder.kt`
- `dev/jplugins/qualitytools/core/ExecutionStyles.kt` (string constants)
- `dev/jplugins/qualitytools/core/OutputFormats.kt`
- `dev/jplugins/qualitytools/core/Capabilities.kt`
- `dev/jplugins/qualitytools/core/FormattingOutputModes.kt`
- `dev/jplugins/qualitytools/core/ThreadingPolicy.kt`
- `dev/jplugins/qualitytools/core/Glob.kt` (glob helper)
- `dev/jplugins/qualitytools/core/ResolvedScope.kt` (forward-decl)
- `dev/jplugins/qualitytools/core/package-info.kt` (KDoc on package)

Tests:

- `core/src/test/kotlin/dev/jplugins/qualitytools/core/QualityToolContractTest.kt`
  — compile-only smoke that a no-op `QualityTool` implementation
  compiles when only abstract members are overridden.

## Acceptance criteria

- [ ] All interfaces compile under `explicitApi`.
- [ ] No `sealed` modifier appears in `:core`.
- [ ] No `abstract class` with `abstract` members appears in
      public API of `:core`.
- [ ] No imports of `com.intellij.*`, `com.jetbrains.php.*`, or any
      AWT/Swing class in `:core`.
- [ ] Smoke test that a 30-line `NoopTool : QualityTool` class compiles
      and can be instantiated.
- [ ] `pathArgKeys` is `Set<String>`, not enum.
- [ ] v1 interface methods are abstract; **future** additions in
      minor versions must have `default` bodies (enforced in CR).
- [ ] `ToolRunContext` exposes both `displayPath` and `actualPath`
      (covers stdin-via-tempfile rename scenario for Mago).
- [ ] `CancellationToken.throwIfCanceled` is the only path tools use
      to check cancellation — no `Thread.interrupted()` in `:core`.
- [ ] `QtLogger` is the only logging surface in `:core` — no
      `org.slf4j.Logger` or IntelliJ `Logger` imports.
- [ ] `ExecutionStyles`, `OutputFormats`, `Capabilities`,
      `FormattingOutputModes` const objects (in `:core`) expose
      every recognized string value with KDoc so authors can
      `import dev.jplugins.qualitytools.core.ExecutionStyles.ON_THE_FLY`
      instead of typing the raw string.
- [ ] `CancellationToken.onCancel` is the canonical way to register
      a cleanup; tests verify that `process.destroyForcibly()`
      fires when token cancels.
- [ ] `qualityTool { … }` DSL builder produces a working tool with
      ≤ 4 LOC for the smallest case (Pest example acceptance).
- [ ] `ToolUi.Hidden` removes the tool from `AutoToolSettingsPanel`
      enumeration (validated in phase 07 tests).

## Out of scope

- Runtime resolution.
- Persistence.
- Registry/EP — that's phase 02.

## Depends on

`phase-00`.
