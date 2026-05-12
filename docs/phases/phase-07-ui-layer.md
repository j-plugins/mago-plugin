# Phase 07 — UI Layer

## Goal

`:ui` provides Kotlin UI DSL panels: tool settings page, profile list,
scope mapping table, option renderers. Auto-renders any
`OptionsSchema`. Custom renderers plug in via EP.

## Feature

Mago's 295-line `MagoConfigurable.kt` collapses to ~30 lines that pin
ordering. New tool authors write zero UI code.

## Solution

`:ui` module depends on `:core` and the IntelliJ platform (with
`intellij.platform.ide.impl` for Kotlin UI DSL).

```kotlin
public interface ToolSettingsPanel {
    public val toolId: String
    public fun build(panel: Panel, project: Project)
}
```

Default implementation `AutoToolSettingsPanel(schema)` walks
`OptionsSchema.specs` and renders each via the matched
`OptionRenderer`:

```kotlin
public interface OptionRenderer {
    public val order: Int get() = 0
    public fun supports(spec: OptionSpec<*>): Boolean
    public fun render(spec: OptionSpec<*>, bag: OptionsBag, row: Row): Cell<*>
}
```

`ConfigSourceRenderer` EP — for source types that need a custom
panel (Docker compose service combo + path-mapping table, etc.):

```kotlin
public interface ConfigSourceRenderer {
    public val typeId: String
    public fun render(panel: Panel, source: ConfigSource, project: Project, modified: () -> Unit)
}
```

EP: `dev.jplugins.qualityTools.configSourceRenderer`.

Built-in renderers in `:ui`:

- `BoolRenderer` (OnOff button)
- `IntRenderer` (`JBIntSpinner`)
- `StringRenderer` (textField)
- `PathRenderer` (`textFieldWithBrowseButton`)
- `EnumLikeRenderer` (`comboBox`)
- `StringListRenderer` (single-column table)
- `ListRenderer` (multi-column table — used by workspace mappings)

Profile list panel — master/details replacement (NOT
`MasterDetailsComponent` — that's deprecated). Use plain Kotlin UI DSL:

```kotlin
public class ProfileListPanel(
    private val project: Project,
    private val tool: QualityTool,
    private val storage: QualityToolsProjectStorage,
)
```

Wizard host: `:ui` provides `ConfigSourceWizardHost` that takes the
`ConfigSourceType.createWizard(...)` result and shows it as a dialog.

`ToolFixHandler` EP (registered in `:ui`):

```kotlin
public interface ToolFixHandler {
    public fun supports(fix: ToolFix): Boolean
    public fun toLocalQuickFix(fix: ToolFix, message: ToolMessage): LocalQuickFix
}
```

Bundled handlers convert `ReplaceFix`, `PatchFix`, `CliFix`,
`IgnoreFix`, `DeleteFileFix`, `ExternalFileEditFix`, `AggregateFix`
to `LocalQuickFix`. `AggregateFix` produces a submenu action group
with N children plus an "apply all" item.

`InternalErrorNotifier` — handles `ToolMessage` with
`severityLevel == "internal_error"`:

- Severity `internal_error` messages are NOT shown as inline
  annotations.
- Instead, `InternalErrorNotifier` emits one `Notification` per
  `(toolId, category)` per IDE session (de-duped).
- Notification carries actions resolved via
  `InternalErrorActionProvider` EP — e.g.: "Start Docker container"
  for `category = "docker.unavailable"`. Built-in actions:
  "Open Settings", "Disable Tool".

```kotlin
public interface InternalErrorActionProvider {
    public fun supports(message: ToolMessage, ctx: ToolRunContext): Boolean
    public fun actions(message: ToolMessage, ctx: ToolRunContext): List<NotificationAction>
}
```

`AsyncFormattingServiceAdapter` — wraps `QualityTool`+`format`-mode
into `com.intellij.formatting.service.AsyncDocumentFormattingService`.
Modes with `executionStyle = "format"` are exposed through this EP
instead of `externalAnnotator`.

Contract (resolves the cycle-3 ambiguity):

1. Adapter writes the editor document content to a temp file, then
   invokes the runner with `target = SingleFile(tempPath)` (or
   `Stdin(...)` if `mode.supportsStdin`).
2. After the run completes:
   - If the tool emits formatted content to **stdout** (typical for
     `prettier`, `phpcsbeautifier --stdin`): adapter reads `run.stdout`
     and applies as the new document text.
   - If the tool **rewrites the file in place** (Mago `fmt`,
     `phpcbf`): adapter re-reads the temp file from disk and applies
     its content as the new document text.

   Discriminated by `ToolMode.formattingOutputMode: String` —
   `"stdout"` or `"in_place"`. New field on `ToolMode` introduced in
   phase 01 (added retroactively in cycle 3).
3. Non-zero exit + non-empty stderr → adapter shows a balloon via
   `InternalErrorNotifier` and leaves the document untouched.
4. `PostFixHook`s fire when format mode is invoked as a post-fix
   (Mago `formatAfterFix`); regular Ctrl+Alt+L invocations do NOT
   trigger hooks.

Severity mapping:

```kotlin
public object SeverityMapping {
    public fun toHighlightDisplayLevel(severity: String, logger: QtLogger): HighlightDisplayLevel
}
```

Unknown severity → `HighlightDisplayLevel.WEAK_WARNING` + one-shot
`logger.log("warn", ...)`. Never raw `Logger.getInstance(...)` —
goes through the `IntellijLogger` SAM impl of `QtLogger`.

## Deliverables

`:ui/dev/jplugins/qualitytools/ui/`:

- `ToolSettingsPanel.kt`, `AutoToolSettingsPanel.kt`
- `ProfileListPanel.kt`
- `OptionRenderer.kt` + 7 built-in renderers
- `ConfigSourceRenderer.kt` (EP)
- `ConfigSourceWizardHost.kt`
- `ToolFixHandler.kt` + 7 built-in handlers (Replace, Patch, Cli,
  Ignore, DeleteFile, ExternalFileEdit, Aggregate)
- `SeverityMapping.kt`
- `WorkspaceMappingTable.kt` (specialized `ListRenderer` consumer)
- `PersistentQualityToolsProjectStorage.kt` — `@Service(Project)` +
  `PersistentStateComponent` impl of the storage interface from
  phase 04. Stores `quality-tools.xml`. Lives in `:ui` per cross-
  cutting decision in README.md.
- `XmlSerializedSourceElement.kt` — JDOM adapter for the abstract
  `SerializedSourceElement`.
- `InternalErrorNotifier.kt`, `InternalErrorActionProvider.kt` (EP)
- `AsyncFormattingServiceAdapter.kt` — wires `format`-style modes
  into IntelliJ's `formattingService` EP.
- `EpConfigSourceRegistry.kt` (declared in phase 02 deliverables but
  built here).
- `EpConfigScopeRegistry.kt`, `EpToolRegistry.kt`, `EpReaderRegistry.kt`,
  `EpIgnorePolicyRegistry.kt`, `EpFixHandlerRegistry.kt`,
  `EpEnvMutatorRegistry.kt`, `EpMessageEnricherRegistry.kt`.

`:ui/META-INF/quality-tools-eps.xml` (the master EP file consumed by
host plugins via `<depends>`):

- `<extensionPoints>` for **all** SDK EPs (master list — must match
  README cross-cutting table exactly):
  - `tool`, `configSourceType`, `configSourceRenderer`,
    `configScopeType`, `resultReader`, `ignorePolicyType`,
    `ignoreCommentRenderer`, `messageEnricher`, `envMutator`,
    `processSpawner`, `processPoolPolicy`, `optionRenderer`,
    `toolFixHandler`, `internalErrorActionProvider`,
    `postFixHook`, `toolRunListener`, `pathMapperContributor`,
    `legacyQualityToolBridge`.
  - Each `dynamic="true"`.
- `<applicationService>` for `IntellijLogger`, the master EP
  registries (Tool, ConfigSource, ConfigScope, ResultReader,
  IgnorePolicy, MessageEnricher, EnvMutator, OptionRenderer,
  FixHandler, ProcessSpawnerSelector, PostFixHook,
  InternalErrorActionProvider).

Tests:

- `AutoToolSettingsPanelTest.kt`
- `WorkspaceMappingTableTest.kt`
- `XmlRoundTripTest.kt`

## Acceptance criteria

- [ ] No `.form` (UI Designer) files. Kotlin UI DSL only.
- [ ] `AutoToolSettingsPanel` auto-renders every spec kind from
      phase 04 without code per tool.
- [ ] `QualityToolsProjectStorageImpl` migrates from legacy
      `php.xml` `<MagoSettings>` and `<PhpStanOptionsConfiguration>`
      on first load (one-shot migrator hook).
- [ ] `ToolFixHandler` chain produces working `LocalQuickFix` for
      `ReplaceFix` (test with `BasePlatformTestCase`).
- [ ] `SeverityMapping` covers `error/warning/weak_warning/info/hint/
      internal_error` and falls back deterministically.
- [ ] Extension points are `dynamic="true"`.
- [ ] All EP names are `dev.jplugins.qualityTools.*`.
- [ ] `EpMasterListTest` reflects every `Registry` interface in
      `:core` and asserts the corresponding `<extensionPoint>`
      appears in `quality-tools-eps.xml` with `dynamic="true"`.
- [ ] `AutoToolSettingsPanel` hides tools with `ui == ToolUi.Hidden`.
- [ ] `AsyncFormattingServiceAdapter` deletes its temp file in a
      `finally` block, even on cancellation/exception.
- [ ] `MatchContext.attribute(key)` carries `ui.theme` /
      `ui.colorScheme` so a third-party `ConfigScope` can read
      environment without leaking platform types into `:core`.

## Out of scope

- Annotator (phase 08).
- Adapter to legacy `QualityToolType` (phase 09).

## Depends on

`phase-06`.
