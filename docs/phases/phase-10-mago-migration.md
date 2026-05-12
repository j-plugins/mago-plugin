# Phase 10 — Mago Migration

## Goal

The Mago plugin uses `:quality-tools-sdk` for everything. All legacy
`com.jetbrains.php.tools.quality.*` extension is gone. State of
existing users is migrated transparently.

## Feature

Mago code is smaller, simpler, doesn't depend on the brittle JetBrains
private SDK. New tool authors copy the Mago migration as a reference.

## Solution

Three sub-steps, each shippable independently:

### 10a. Adapter to legacy `QualityToolType`

`:ui` adds an optional bridge class (`LegacyQualityToolBridge`) that
implements `com.jetbrains.php.tools.quality.QualityToolType` over a
new `QualityTool`. This is a one-way bridge: PhpStorm's Settings UI
still shows the tool in `Settings/PHP/Quality Tools`, but storage,
options, and runner are all SDK.

The adapter has its own EP registration `legacyQualityToolBridge`;
plugins that don't care skip it.

#### 10a.1. Inspection-id preservation

User inspection profiles reference `MagoGlobal` (batch) and
`MagoValidation` (local) by short name. After migration:

- The new SDK registers TWO `localInspection` / `globalInspection`
  shortNames `MagoGlobal` + `MagoValidation` whose implementation
  delegates to the new annotator pipeline.
- `QualityTool.inspectionShortNames: Set<String>` (added to phase 01
  as `inspectionShortNames` returning `{ "MagoGlobal", "MagoValidation" }`
  for Mago) — the legacy bridge reads this set.

#### 10a.2. Legacy `QualityToolConfiguration` synthesis

`LegacyQualityToolBridge` synthesises one `QualityToolConfiguration`
per new `ConfigProfile` so the legacy
`QualityToolConfigurationComboBox` and `QualityToolConfigurableList`
master/detail page keep functioning. Writes from the legacy panel
delegate to the new `QualityToolsProjectStorage`.

### 10b. Mago `:core`-level port

In `mago-plugin/src/main/kotlin/com/github/xepozz/mago/v2/`:

- `MagoTool` (implements `QualityTool`) — replaces `MagoQualityToolType
  + MagoConfiguration + MagoConfigurationManager*`.
- `MagoOptionsSchema` — replaces `MagoCustomOptionsForm + 60% of
  MagoProjectConfiguration`.
- `MagoJsonLinesReader` — moves the JSON parsing logic out of
  `MagoExternalAnnotator`.
- Source types in `mago.sources`:
  - `ComposerMagoSourceType` (typeId = `mago.composer`)
  - `PhpInterpreterMagoSourceType` (typeId = `mago.php-interpreter`,
    only registered when remote-interpreter plugin present)
- `MagoMigration` — one-shot startup activity reading old
  `MagoSettings` / `MagoProjectConfiguration` from `php.xml` /
  `workspace.xml` and emitting `ConfigProfile`s in the new storage.

### 10c. Delete

After migration is **confirmed stable** (defined: 2 minor releases
shipped, <0.1% migration error rate from telemetry, zero open P0/P1
tagged `mago-v2`), the following files/classes are deleted in a
follow-up PR (NOT in this phase — it ships behind a project-level
flag `MagoV2State.useV2 = true`, see §10d):

- `MagoQualityToolType.kt`
- `MagoConfiguration.kt`
- `MagoConfigurationManager.kt`
- `MagoConfigurationBaseManager.kt`
- `MagoConfigurationProvider.kt`
- `MagoRemoteConfigurationProvider.kt`
- `MagoRemoteConfiguration.kt`
- `MagoCustomOptionsForm.kt`
- `MagoAnnotatorProxy.kt`
- `MagoConfigurableForm.kt`
- `MagoBlackList.kt`
- `MagoValidationInspection.kt`
- `MagoGlobalInspection.kt`
- 80% of `MagoConfigurable.kt`
- `MagoCliOptions.kt` (subsumed by `MagoTool.buildArgs`)
- 50% of `MagoExternalAnnotator.kt`

### 10d. Rollout & reversibility

- **Flag scope**: project-level `MagoV2State` `@Service(Project)`
  with `useV2: Boolean`. Default `true` on fresh install; existing
  users opt-in via Settings or Registry override
  `mago.use.v2.default`.
- **Migration is idempotent**: `MagoMigration` writes a marker
  `mago.v2.migratedVersion: Int` into v2 storage; subsequent runs
  no-op when marker == current.
- **Migration chain**: `Migrator.migrate(from: Int, to: Int)` runs
  the diff. Renames in N+2 add `migrate(N+1, N+2)` step; never
  re-runs full chain.
- **Dual-write window** (one release cycle): when `useV2 = true`,
  v2 storage is canonical AND legacy XML is written too (write-only,
  best-effort) so flipping back to false loses nothing. Drop dual-
  write in the §10c cleanup PR.
- **App-level migration**: `MagoAppMigration : ApplicationActivity`
  with single-execution lock — runs once per IDE; not per project.
- Acceptance: per-project state, flag survives IDE restart,
  toggling back-and-forth keeps data, no duplicates.

### 10e. Migration field-map

| Legacy (php.xml / workspace.xml) | New (`MagoOptionsSchema` / `ConfigProfile`) |
| --- | --- |
| `MagoSettings.toolPath` | `LocalBinarySource.path` on local profile |
| `MagoSettings.timeoutMs` | `ConfigProfile.timeoutMs` |
| `MagoSettings.customParameters` | `mode("analyze").additionalArgs` |
| `MagoProjectConfiguration.enabled` | tool registered or not (drops if false) |
| `MagoProjectConfiguration.formatterEnabled` | `modeSchema("format").enabled` |
| `MagoProjectConfiguration.linterEnabled` | `modeSchema("lint").enabled` |
| `MagoProjectConfiguration.guardEnabled` | `modeSchema("guard").enabled` |
| `*AdditionalParameters` | `modeSchema(<mode>).additionalArgs` |
| `formatAfterFix` | options spec `formatAfterFix` |
| `configurationFile` | options spec `configurationFile` |
| `debug` | options spec `debug` |
| `workspaceMappings[*]` | One `ConfigProfile` per mapping with `WorkspaceRootScope(workspace)` + override `configFile` |
| `MagoBlackList.filePaths[*]` | `GlobPathIgnorePolicy.patterns` (verbatim absolute paths) |
| `MagoRemoteConfiguration.interpreterId` | One `ConfigProfile` with `PhpInterpreterMagoSourceType(interpreterId)` |

`PhpInterpreterMagoSourceType` is only registered when
`org.jetbrains.plugins.phpstorm-remote-interpreter` is present.

## Modes

Mago modes preserved 1:1. `format` uses the new `formattingService`
adapter from phase 07 (NOT the annotator).

| Mode | Verb | executionStyle | stdin | fix |
| --- | --- | --- | --- | --- |
| `analyze` | `analyze` | `on_the_fly` | yes | yes |
| `lint` | `lint` | `on_the_fly` | yes | yes |
| `format` | `fmt` | `format` | no | n/a |
| `guard` | `guard` | `on_save` | yes | no |

`format` is wired through `AsyncFormattingServiceAdapter`; the
annotator (phase 08) only handles `on_the_fly` / `on_save` /
`manual` / `batch`.

## Options (schema)

```kotlin
class MagoOptionsSchema : OptionsSchema {
    override val toolId = "mago"
    override val specs = listOf(
        path("configurationFile", fileFilter = PathFilter.ext("toml")),
        bool("debug"),
        list("workspaceMappings") {
            path("workspace", role = "scope_root")
            path("configFile", fileFilter = PathFilter.ext("toml"))
        },
    )
    override val modeSchemas = mapOf(
        "analyze" to modeSchema { enabled(true); additionalArgs() },
        "lint"    to modeSchema { enabled(false); additionalArgs() },
        "format"  to modeSchema {
            enabled(true); additionalArgs(); bool("formatAfterFix")
        },
        "guard"   to modeSchema { enabled(false); additionalArgs() },
    )
}
```

## Scopes

Workspace mappings become first-class `WorkspaceRootScope` per profile.
The migration emits one profile per existing `MagoWorkspaceMapping` and
one profile with `EntireProjectScope` for the default config.

## Deliverables

`mago-plugin` changes:

- `build.gradle.kts` adds `implementation(project(":quality-tools-sdk:core"))`,
  `implementation(project(":quality-tools-sdk:php"))`,
  `implementation(project(":quality-tools-sdk:ui"))`.
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoTool.kt`
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoOptionsSchema.kt`
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoJsonLinesReader.kt`
  (reads byte offsets, sets `SourceRange.isByteOffset = true`,
  populates `relatedRanges` from `secondaryAnnotations`)
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoIgnoreCommentRenderer.kt`
  — `IgnoreCommentRenderer` implementation. Handles existing-PHPDoc
  merge (`mergeCodesInTag`), single-line→multiline normalization,
  enclosing-scope dedup (`isSuppressedInEnclosingDeclarations`), and
  the `@mago-ignore` vs `@mago-expect` choice. Registered on
  `dev.jplugins.qualityTools.ignoreCommentRenderer` EP keyed by
  `toolId = "mago"`.
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoUnifiedDiffSynth.kt`
  — synthesises a unified diff from `MagoEdit.replacements` for
  cross-file `ExternalFileEditFix`. For same-file edits, emits
  `AggregateFix(children = list of ReplaceFix, grouping = "by-safety")`.
  Maps Mago safety: `SAFE_ONLY → "safe"`,
  `POTENTIALLY_UNSAFE → "risky"`, `UNSAFE → "experimental"`.
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoRangeNarrowingEnricher.kt`
  — `MessageEnricher` keyed on `(toolId, category, ruleId)` that
  ports the existing `resolveHighlightRange` / `resolveFromSecondary`
  per-rule range overrides (`missing-return-type` →
  `resolveFunctionNameRange`, `unused-pragma` → secondary range).
- `src/main/kotlin/com/github/xepozz/mago/v2/sources/ComposerMagoSourceType.kt`
  — `watch()` honors `vendor/bin/mago` appearances; `createWizard()`
  also performs the one-shot startup probe.
- `src/main/kotlin/com/github/xepozz/mago/v2/sources/PhpInterpreterMagoSourceType.kt`
- `src/main/kotlin/com/github/xepozz/mago/v2/fixes/MagoDeleteRedundantFileFix.kt`
  — `DeleteFileFix` impl for `lint:no-redundant-file`.
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoFormatAfterFixHook.kt`
  — `PostFixHook` reading `formatAfterFix` option.
- `src/main/kotlin/com/github/xepozz/mago/v2/MagoMigration.kt`
- `src/main/resources/META-INF/plugin.xml` — register new extensions.

Registry flag: `mago.use.v2` (default `true` in dev, behind staged
rollout in production).

## Acceptance criteria

- [ ] Mago v2 plugin starts with `mago.use.v2 = true`, opens
      Settings, sees the new tool panel.
- [ ] User with existing `MagoSettings` in `php.xml` sees their
      `toolPath`, `customParameters`, `workspaceMappings` etc.
      preserved after restart.
- [ ] On-the-fly analysis on a `.php` file produces the same
      `ToolMessage` set as the legacy annotator (golden test).
- [ ] `mago analyze --stdin-input` path is exercised; format mode
      uses real path.
- [ ] All 5 existing functional tests in `MagoAnnotationFunctionalTest`
      pass with `MagoV2State.useV2 = true`.
- [ ] Adding a workspace mapping via UI produces a new `ConfigProfile`
      with `WorkspaceRootScope`.
- [ ] Switching `useV2 = false` restores legacy behavior (dual-write
      window guarantees zero data loss).
- [ ] Inspection profile XML referencing `class="MagoGlobal"` or
      `class="MagoValidation"` continues to apply user's
      severity/enabled flags after upgrade (golden test against a
      hand-crafted `inspectionProfiles/*.xml`).
- [ ] `MagoBlackList.filePaths` are imported into `IgnorePolicy`
      after migration (golden test).
- [ ] Migration is idempotent (re-running on already-migrated
      project produces no duplicates).
- [ ] App-level `MagoSettings` (php.xml) migrates once per IDE,
      not per project.
- [ ] Composer-detected `toolPath` becomes
      `LocalBinarySource(instanceId="migrated-composer", path=…)`
      — pinned, NOT re-probed each startup.
- [ ] Migration golden fixtures: `legacy-xml-2025.1.5.golden`
      → `v2-storage.golden`, one per supported source release,
      stored in `src/test/resources/migration/`.

## Out of scope

- Deleting legacy classes (separate cleanup PR).
- Docker source — that's a separate plugin (or a later phase).

## Depends on

`phase-08`, `phase-09` (testing infra for migration tests).
