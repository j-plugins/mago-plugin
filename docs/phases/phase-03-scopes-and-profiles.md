# Phase 03 — Scopes & Profiles

## Goal

A user can have several profiles per tool, each scoped to a directory
/ glob / module / arbitrary predicate. `ProfileSelector` picks the
most specific profile for a given file, exactly the way
`MagoCliOptions.resolveForFile` already does it with longest-prefix
match.

## Feature

Per-monorepo / per-source-root tool settings; mode-level enabled
toggles; per-mode argument overrides. No global singleton config.

## Solution

```kotlin
public interface ConfigProfile {
    public val id: String
    public val displayName: String
    public val toolId: String
    public val source: ConfigSource
    public val scope: ConfigScope
    public val options: OptionsBag          // phase 04 declares
    public val modes: Map<String, ModeSettings>
}

public interface ModeSettings {
    public val enabled: Boolean
    public val additionalArgs: String       // ParametersList format
    public val customConfigFile: String?    // optional override
}

public interface ConfigScope {
    public val typeId: String
    public fun matches(target: ToolTarget, ctx: MatchContext): Boolean
    public fun specificity(target: ToolTarget, ctx: MatchContext): Int
}

public interface ConfigScopeType {
    public val typeId: String
    public val aliasTypeIds: Set<String> get() = emptySet()
    public val displayName: String
    public fun isAvailable(ctx: AvailabilityContext): Boolean = true
    public fun createWizard(ctx: WizardContext): ConfigScopeWizard?
    public fun deserialize(element: SerializedSourceElement): ConfigScope
    public fun serialize(scope: ConfigScope): SerializedSourceElement
}

public interface MatchContext {
    public val projectId: String
    public val basePath: String?
    public val vcsBranch: String?                 // null when no VCS / branch unknown
    public val moduleIdOf: (target: ToolTarget) -> String?

    /**
     * Free-form attribute bag for third-party scope types that need
     * extra environment data without `:core` learning about it.
     * Built-in attributes:
     *   "ui.theme" -> "dark"/"light"
     *   "ide.os" -> "linux"/"mac"/"windows"
     * Custom keys allowed; convention: vendor.namespace.key.
     */
    public fun attribute(key: String): String?
        = null
}
```

Bundled scope implementations in `:core`:

- `EntireProjectScope` (typeId = `"entire-project"`) — always matches,
  specificity = 0.
- `WorkspaceRootScope` (typeId = `"workspace-root"`) — longest-prefix
  match on path; specificity = `root.length`.
- `GlobScope` (typeId = `"glob"`) — `patterns: List<String>`,
  specificity = sum of unique prefix lengths.

Selector:

```kotlin
public class ProfileSelector(private val profiles: List<ConfigProfile>) {
    public fun selectFor(
        toolId: String,
        modeId: String,
        target: ToolTarget,
        ctx: MatchContext,
    ): ConfigProfile? = profiles.asSequence()
        .filter { it.toolId == toolId }
        .filter { it.modes[modeId]?.enabled != false }
        .filter { it.scope.matches(target, ctx) }
        .maxByOrNull { it.scope.specificity(target, ctx) }
}
```

`ConfigProfile.options` is just a forward reference to phase 04's
`OptionsBag`.

`ResolvedScope` (forward-decl'd in phase 01) is finalized here:

```kotlin
public interface ResolvedScope {
    public val workspaceDir: String
    public val configFile: String?
    public fun relativize(absolute: String): String
}
```

`ScopeResolver` in `:core`:

```kotlin
public class ScopeResolver(private val profile: ConfigProfile) {
    public fun resolve(target: ToolTarget, ctx: MatchContext): ResolvedScope
}
```

## Deliverables

`:core/dev/jplugins/qualitytools/core/scope/`:

- `ConfigScope.kt`, `ConfigScopeType.kt`, `ConfigScopeWizard.kt`
- `MatchContext.kt`, `ResolvedScope.kt`, `ScopeResolver.kt`
- `EntireProjectScope.kt` + `EntireProjectScopeType.kt`
- `WorkspaceRootScope.kt` + `WorkspaceRootScopeType.kt`
- `GlobScope.kt` + `GlobScopeType.kt` (use plain `PathMatcher` /
  in-house glob — no JDK 7 NIO that's missing on certain JBR builds).

`:core/dev/jplugins/qualitytools/core/profile/`:

- `ConfigProfile.kt`, `ModeSettings.kt`
- `ProfileSelector.kt`

Tests:

- `WorkspaceRootScopeTest.kt` — longest-prefix, no spurious matches.
- `GlobScopeTest.kt` — incl. `**`, `?`, `[abc]`.
- `ProfileSelectorTest.kt` — selection ties, disabled modes filter.

## Acceptance criteria

- [ ] All scope types are plain interfaces; no `sealed`.
- [ ] `ConfigScope.typeId` is mandatory (not nullable).
- [ ] `ProfileSelector` returns `null` on no match (does NOT throw).
- [ ] Longest-prefix match in `WorkspaceRootScopeTest` covers:
      mappings `/a` vs `/a/b/c`, target `/a/b/c/x.php` → `/a/b/c`.
- [ ] `MatchContext` is a plain interface; `:core` doesn't see
      `Project`.
- [ ] `ScopeResolver.resolve()` produces working-dir + config-file
      from the active scope without touching VFS.
- [ ] No platform imports leaked into `:core`.

## Out of scope

- Options (phase 04).
- Persistence (phase 04).
- UI panels (phase 07).

## Depends on

`phase-01`, `phase-02`.
