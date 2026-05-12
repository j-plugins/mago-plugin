# Quality Tools SDK — Implementation Phases

Implementation of `:quality-tools-sdk` is split into 10 phases. Each
phase has its own file with:

- **Goal** — one-sentence statement of what we get.
- **Feature** — what users (= plugin authors) gain.
- **Solution** — design summary + reference to the relevant section of
  the research docs (`docs/sdk-research/*.md`).
- **Deliverables** — files to create, listed by path.
- **Acceptance criteria** — checklist. Each criterion is binary
  (yes / no), automatically verifiable (tests / compile / EP visible).
- **Out of scope** — what this phase explicitly does not do.
- **Depends on** — earlier phases that must be merged first.

Phases are ordered so that each one merges independently into
`claude/refactor-quality-tools-ZWyei` and the build stays green at every
step. None of the phases except phase 09 modify existing Mago code.

| Phase | Subject |
| --- | --- |
| `phase-00` | Bootstrap (already done — gradle skeleton). |
| `phase-01` | Core contracts: `QualityTool`, `ToolMode`, `ToolTarget`. |
| `phase-02` | Config sources: `ConfigSource`, `ConfigSourceType` + registry. |
| `phase-03` | Scopes & profiles: `ConfigScope`, `ConfigProfile`, `ProfileSelector`. |
| `phase-04` | Options schema & persistence: declarative options DSL. |
| `phase-05` | Runner pipeline: process, env mutators, path-aware args. |
| `phase-06` | Readers & messages: `ResultReader`, `ToolMessage`, `ToolFix`, `IgnorePolicy`. |
| `phase-07` | UI layer: settings panels + option renderers. |
| `phase-08` | Annotator bridge: ExternalAnnotator integration. |
| `phase-09` | Testing module: `FakeProcessRunner`, fixtures. |
| `phase-10` | Mago migration: port `MagoTool` onto the SDK. |

(Phases 9 and 10 swapped after cycle-1 architecture audit — testing
infra needs to land before migration tests can assert anything.)

The phases are designed to be reviewed independently. Phases 01–06
target `:core` and have no IntelliJ-platform dependency.

## Rules carried over from the research

Every phase MUST respect the following rules; reviewers reject PRs
that violate them.

1. **No `sealed` modifier in public API.** Everything that needs to
   be extended is an `interface` registered through an extension
   point.
2. **No `enum class` in data models a third-party plugin may want to
   extend a case of.** `severityLevel`, `safety`, `ignoreFix.scopeType`
   are `String`. Every consumer of these strings MUST define explicit
   fallback behavior for unknown values + log once. Closed enums for
   true single-plugin internal use (e.g. internal pool state) are
   allowed.
3. **No PHP / IntelliJ-platform imports in `:core`.** Only stdlib +
   kotlinx-coroutines + jetbrains-annotations.
4. **No abstract classes in public API.** Everything is `interface`,
   including `OptionsSchema` and `QualityToolsAnnotator`.
   `companion object` / top-level functions hold shared helpers.
5. **All extension points are `dynamic="true"`.** Each EP is
   declared in the phase that introduces it, in `:ui/META-INF/quality-
   tools-eps.xml` consumed via `<depends>` from host plugins.
6. **All `typeId` values are stable for the lifetime of the API.**
   New aliases via `aliasTypeIds`. Removal requires major bump + at
   least one minor version of deprecation warning.
7. **Interface methods added in minor versions must have `default`
   implementations.** v1 methods may be abstract; subsequent additions
   may not break consumers' implementations.
8. **No class encapsulates global mutable state behind a service**
   under the name `*Manager`. Persistent project state goes through
   `QualityToolsProjectStorage`; pure dispatch lives in companion
   objects.
9. **No `*Configuration` services per tool.** Each tool's options
   live in the unified storage.
10. **DumbAware by default** for everything that touches PSI. Phases
    that touch PSI must include a `DumbAware`-on-by-default acceptance
    bullet.

## Cross-cutting concerns (single owner per concern)

To avoid the "different idiom per layer" trap, each concern is owned
by one phase.

| Concern | Owner phase | Vocabulary |
| --- | --- | --- |
| Cancellation | phase 05 | `CancellationToken` interface in `:core`; bridged in `:ui` to the `ProgressIndicator` passed to `doAnnotate`. `ToolRunRequest.cancellation` is the **single** channel; coroutine `Job` cancellation is an implementation detail. |
| Logging | phase 01 | `QtLogger` SAM interface in `:core`; `Slf4jLogger` in `:testing`; `IntellijLogger` in `:ui`. **No `Logger.getInstance(...)` calls anywhere in `:core`/`:php`/`:ui` outside of `IntellijLogger.kt`.** |
| Error reporting | bridge between phase 05 → 06 | `RunnerToMessageBridge` in `:core/runner` translates `ToolRun(exitCode<0)` / `ToolRun.canceled` / missing-binary / timeout into a `ToolMessage(severityLevel="internal_error", category="<typeId>.unavailable" / "<toolId>.timeout" / "<toolId>.cancelled")`. Readers never see these failure modes; they receive a normal stdout. |
| `Project` boundary | phase 02 | `Project` lives in `:ui`; `:core` sees `ToolRunContext`, `MatchContext`, `ResolveContext`, `AvailabilityContext`, `WizardContext`, `WatchContext`. |
| EP registration | phase that introduces the EP | All EPs declared in `:ui/META-INF/quality-tools-eps.xml`. **Master list (phase 07 owns the file)**: `tool`, `configSourceType`, `configSourceRenderer`, `configScopeType`, `resultReader`, `ignorePolicyType`, `ignoreCommentRenderer`, `messageEnricher`, `envMutator`, `processSpawner`, `processPoolPolicy`, `optionRenderer`, `toolFixHandler`, `internalErrorActionProvider`, `postFixHook`, `toolRunListener`, `pathMapperContributor`, `legacyQualityToolBridge`. |
| Registries | phase that introduces the EP | Every EP gets a `Registry` **interface** in `:core` and an `Ep*Registry` impl in `:ui`. List: `ToolRegistry`, `ConfigSourceRegistry`, `ConfigScopeRegistry`, `ResultReaderRegistry`, `IgnorePolicyRegistry`, `IgnoreCommentRendererRegistry`, `MessageEnricherRegistry`, `EnvMutatorRegistry`, `ProcessSpawnerSelector`, `ProcessPoolPolicyRegistry`, `FixHandlerRegistry`, `PostFixHookRegistry`, `ToolRunListenerRegistry`, `PathMapperContributorRegistry`, `InternalErrorActionProviderRegistry`. Every registry interface exposes `all(): List<T>` plus a typed lookup; tests inject `:testing` in-memory impls. |
| XML adapter | phase 02 | `SerializedSourceElement` in `:core`; JDOM adapter in `:ui`; reflection codec in `:core` (`@SerializedField` + `SerializedSourceCodec`). |
| Disposal | phase that introduces a watcher | `AutoCloseable` returned by `ConfigSourceType.watch(...)` is closed when (a) the project closes, (b) the host plugin unloads, or (c) the `ConfigSourceType` becomes `!isAvailable`. `EpConfigSourceRegistry` owns the lifecycle. |

## Contract rules (binding, enforced in CR)

11. **Equality**: `ConfigProfile.equals` is defined on `id`. `ConfigSource.equals` on `(typeId, instanceId)`. `ConfigScope.equals` on `(typeId, serialized form)`. Implementations override `equals`/`hashCode`.
12. **Mutability**: `:core` public interfaces declare `val` only. Implementations may back with `var` privately. State changes go through `OptionsBag.commit()` / `QualityToolsProjectStorage.saveProfile`.
13. **Null vs empty**: collections never null — default `emptyList/Map/Set`. `String?` only for "genuinely absent" (e.g. `description`).
14. **Threading**: every public method's KDoc declares `@ThreadingPolicy(...)`: `"any"` / `"background"` / `"edt"`. Annotation defined in `:core`.
15. **Protocol order**: `ConfigSourceType.createWizard` precondition is `isAvailable(ctx) == true`. `OptionsBag.set` requires `commit()` for persistence. `ToolRunner.run` rejects `mode.id !in tool.modes`.
16. **Serialization compat**: `SerializedSourceCodec.decode` returns spec default for missing fields; renames via `@SerializedField(name = "newName", aliases = ["oldName"])`.
17. **JVM defaults**: every Kotlin module compiles with `-Xjvm-default=all` so Java consumers see real interface defaults (not `DefaultImpls`).
18. **`@ApiStatus.Experimental`** on every new EP for one minor version after introduction.
19. **Interface evolution**: Adding a member requires a `default`
    body — applies to methods AND properties. Renaming a member
    requires `@Deprecated(level = WARNING)` for 1 minor, then
    `ERROR` for the next, then removal at major. Removing a member
    requires `@ApiStatus.ScheduledForRemoval(inVersion = ...)` 1
    minor before major.
20. **EP-absence handling**: every `Ep*Registry` calls
    `ExtensionPointName.findExtensionPointOrNull(...)` and degrades
    to an empty list when the EP is absent (host plugin pinned to
    older `:ui`). Failures (`LinkageError`, `AbstractMethodError`)
    from stale 3rd-party impls are caught and logged once.
21. **String-value aliasing**: free-form string fields
    (`executionStyle`, `severityLevel`, `safety`, `scopeType`,
    `outputFormat`) support value aliases via the central
    `StringValueAliases` table in `:core`. Renaming `"on_save"` →
    `"onSave"` is a one-line registration; old user XML keeps
    working without per-reader code.
