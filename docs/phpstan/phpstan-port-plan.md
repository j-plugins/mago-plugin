# PHPStan port to `:quality-tools-sdk` — plan and analysis

> Goal: take the existing JetBrains PHPStan plugin (which we
> reverse-engineered earlier) and write down exactly how it would be
> rebuilt on top of our new `:quality-tools-sdk`. We don't write code
> in this document — only the inventory, the mapping, the gaps, and
> the work plan. Implementation comes later, once the gaps are
> resolved.
>
> Why this exercise: PHPStan is the **richest** existing integration
> on the legacy SDK — it exercises remote interpreters via the
> PHP-remote-interpreter plugin, Composer auto-detect, a non-trivial
> options surface (level / config / autoload / memory-limit /
> full-project), and project-wide batch inspection alongside
> on-the-fly. If we can port PHPStan cleanly, the SDK is real.

---

## 0. Reference: source material

Reverse-engineered classes I analysed (CFR 0.152):

- `com.jetbrains.php.tools.quality.phpstan.*` (21 files, ~2.6 k LOC)
  — bundled in the `phpstan` plugin (id
  `com.intellij.php.tools.quality.phpstan`).
- `com.jetbrains.php.tools.quality.phpstan.remote.*` (2 files, ~390
  LOC) — also inside `phpstan.jar`, registered via the optional
  config `phpstan-remote-plugin.xml` when
  `org.jetbrains.plugins.phpstorm-remote-interpreter` is present.
- `com.jetbrains.php.remote.tools.quality.*` (3 base classes,
  ~560 LOC) — shared by all remote-aware quality tools, lives
  inside the `PHP Remote Interpreter` plugin (id
  `org.jetbrains.plugins.phpstorm-remote-interpreter`).
- `com.jetbrains.php.phpstan.completion.PhpStanCompletionContributor`
  (36 LOC) — PHPDoc tag completion for `@phpstan-require-extends`.

Other artefacts in `phpstan.jar`:

- `PhpStanBundle.properties` — i18n (~125 LOC of i18n keys).
- `inspection.html` description.
- `phpstan-remote-plugin.xml` — extension that injects
  `PhpStanRemoteConfigurationProvider` when the remote-interpreter
  plugin is enabled.

---

## 1. Inventory: what every class does today

### 1.1. Core "ports" of the legacy SDK

| Class | LOC | Role |
| --- | --- | --- |
| `PhpStanQualityToolType` | 253 | EP entry point (`com.jetbrains.php.tools.quality.type`). Returns `inspectionId="PhpStanGlobal"`, wires up managers, blacklist, configurable. |
| `PhpStanConfiguration` | 169 | The per-instance config: `myPhpStanPath`, `myMaxMessagesPerFile=50`, `myTimeoutMs=30000`, `getId()="local"`, no interpreter id. |
| `PhpStanConfigurationManager` | 64 | Glue between project and app `PhpStanConfigurationBaseManager`s. Two `@State` services (`PhpStan` in `php.xml`). |
| `PhpStanConfigurationBaseManager` | 62 | `PersistentStateComponent<Element>` with one config tag (`PhpStanSettings`). |
| `PhpStanConfigurationProvider` | 33 | Abstract — `EP_NAME = "com.jetbrains.php.tools.quality.PhpStan.PhpStanConfigurationProvider"`. `getInstances()` returns the single registered provider (logs error if multiple). |
| `PhpStanProjectConfiguration` | 57 | Project state with `selectedConfigurationId` (default `DEFAULT_INTERPRETER`). |
| `PhpStanBlackList` | 24 | `QualityToolBlackList` subclass with one constant — list of absolute paths to skip. |
| `PhpStanValidationInspection` | 36 | Empty subclass of `QualityToolValidationInspection`. ShortName `PhpStanValidation`. |
| `PhpStanGlobalInspection` | 258 | The "real" inspection (registered globally with shortName `PhpStanGlobal`). Stores the option set as five public fields (`FULL_PROJECT`, `memoryLimit`, `level`, `config`, `autoload`). Re-uses them in `getCommandLineOptions`. |
| `PhpStanOptionsConfiguration` | 102 | DUPLICATE of the same five fields, but as `@State(... php.xml)` project service. Used by on-the-fly. |
| `PhpStanSettingsTransferStartupActivity` | 70 | One-shot migration: copies the five fields from `PhpStanGlobalInspection` (legacy storage on profile) into `PhpStanOptionsConfiguration` and sets `isTransferred=true`. |
| `PhpStanAnnotatorProxy` | 184 | `QualityToolAnnotator<PhpStanValidationInspection>`. Builds args from `PhpStanOptionsConfiguration` for on-the-fly, from `PhpStanGlobalInspection` for batch. Suppresses one specific Xdebug warning. |
| `PhpStanMessageProcessor` | 261 | `QualityToolXmlMessageProcessor` (SAX). Parses checkstyle XML between `<file>` tags. Map severity `error/warning`. Dedups by `(line, col, message)`. |
| `PhpStanComposerConfig` | 301 | `QualityToolsComposerConfig`. Reads `composer.json`, picks `vendor/bin/phpstan`, applies `phpstan.neon` ruleset, parses `--memory-limit` from `scripts.phpstan`. |
| `PhpStanConfigurable` | 102 | Settings configurable. Lives under `Settings/PHP/Quality Tools/PHPStan` (parent id `settings.php.quality.tools`). |
| `PhpStanConfigurableForm` | 120 | `QualityToolConfigurableForm` subclass — adds version parsing `PHPStan .* ([\d.]*)`. |
| `PhpStanOptionsPanel` | 259 | Swing-Designer panel: full-project checkbox, memory-limit textfield, level spinner (0..8), config path + autoload path with SDK-aware browse. |
| `PhpStanAddToIgnoredAction` | 37 | Subclass of `QualityToolAddToIgnoredAction`. Just returns the tool type. |
| `PhpStanOpenSettingsProvider` | 37 | Hook for `composerLogMessageBuilder` so notifications can deep-link to Settings. |
| `PhpStanQualityToolAnnotatorInfo` | 74 | Subclass of `QualityToolAnnotatorInfo` — no overrides; pure marker for `instanceof` checks. |
| `PhpStanCompletionContributor` | 36 | PHPDoc completion for `phpstan-require-extends` and `phpstan-require-implements`. |
| `PhpStanBundle` | 125 | i18n. |

**Total bundled: ~2,650 LOC of Java/Kotlin glue.**

### 1.2. Remote interpreter glue

In `phpstan.jar` (only loaded if remote-interpreter plugin is on):

| Class | LOC | Role |
| --- | --- | --- |
| `PhpStanRemoteConfiguration` | 174 | Subclass of `PhpStanConfiguration` that adds `interpreterId`, `PhpSdkDependentConfiguration` impl. `<Tag("phpstan_by_interpreter")>` for XML. |
| `PhpStanRemoteConfigurationProvider` | 214 | Registered on `com.jetbrains.php.tools.quality.PhpStan.PhpStanConfigurationProvider` EP. Provides `createNewInstance` (which opens the by-interpreter dialog), `createConfigurationByInterpreter`, and overrides timeout default to 60 seconds. |

In `php-remoteInterpreter.jar` (shared by phpcs / phpCSFixer / phpmd /
Laravel Pint / PHPStan / Psalm):

| Class | LOC | Role |
| --- | --- | --- |
| `QualityToolByInterpreterDialog` | 205 | Modal dialog: combobox of PHP interpreters + per-tool config, validation, "local" / "default interpreter" rows. Used by `PhpStanRemoteConfigurationProvider.createNewInstance`. |
| `QualityToolByInterpreterConfigurableForm` | 249 | Wrapper form for editing a remote-interpreter-backed config: tool-path + interpreter-name label. |
| `QualityRemoteToolProcessHandler` | 104 | `BaseRemoteProcessHandler<RemoteProcess>` with `QualityToolOutputProcessor` integration — buffers stdout/stderr and feeds the `QualityToolMessageProcessor`. |

### 1.3. Plugin metadata

`META-INF/plugin.xml`:

- `<depends>com.jetbrains.php</depends>`
- `<depends>com.intellij.modules.ultimate</depends>` (PhpStorm only)
- `<depends optional="true" config-file="phpstan-remote-plugin.xml">org.jetbrains.plugins.phpstorm-remote-interpreter</depends>`
- 5 services + 1 inspection (`globalInspection PhpStanGlobal`) + 1
  externalAnnotator + 1 postStartupActivity + 1 configurable + 1
  completion contributor + 1 composerConfigClient + 1 type + 1
  openSettingsProvider + 1 inner EP + 1 action.

---

## 2. Functional surface (what the user sees)

I separate "features the user touches" from "internal implementation
plumbing" since the new SDK will collapse the latter.

### 2.1. User-facing features

1. **Settings page** at `PHP / Quality Tools / PHPStan`:
   - List of profiles (local + per-interpreter), add/remove/edit.
   - Per-profile: tool path, validate button (parses `phpstan
     --version`), timeout in seconds, max messages per file.
   - Common options across all profiles: full-project mode,
     memory limit (e.g. `2G`), level (0..8 numeric), config path
     (`phpstan.neon`), autoload path.
2. **Inspection profile entries**:
   - `PhpStanGlobal` (global, batch). User can change severity,
     scope, enable/disable.
   - `PhpStanValidation` (local) — surfaces on-the-fly results.
3. **On-the-fly analysis** while editing a `.php` file (uses
   selected profile + options config).
4. **Project-wide analysis** via `Code → Inspect Code…`.
5. **Composer auto-detect**: when the user runs `composer install`
   and `vendor/bin/phpstan` appears, the IDE auto-configures the
   tool path, reads `phpstan.neon` for ruleset, parses
   `composer.json` `scripts.phpstan` for `--memory-limit`.
6. **"Add to ignored" action** in the right-click menu on any
   PHPStan error in the editor — appends to the per-tool blacklist.
7. **PHPDoc completion** for `@phpstan-require-extends`,
   `@phpstan-require-implements`.
8. **Notifications** with a "Configure" link that opens the
   PHPStan settings page directly.
9. **Settings migration**: when upgrading from a PhpStorm version
   that stored the options on the inspection (`PhpStanGlobalInspection`
   fields) to the version that stores them in
   `PhpStanOptionsConfiguration` — happens on first startup.
10. **Remote PHP interpreter support** (Docker/SSH/WSL):
    - Picking an interpreter in the "by-interpreter" dialog auto-
      maps the local `phpstan` path to a remote one.
    - Path mapping for `--config=` and `--autoload-file=` paths
      passed as CLI args.
    - 60-second default timeout for remote runs.

### 2.2. Internal plumbing (collapsed by the new SDK)

- App + project-level `QualityToolConfigurationBaseManager` pair.
- Three different "configuration" objects: `PhpStanConfiguration`,
  `PhpStanRemoteConfiguration`, `PhpStanOptionsConfiguration`.
- Two parallel pipelines (annotator for on-the-fly, global
  inspection for batch) sharing some helpers.
- Custom `QualityToolByInterpreterDialog` per tool.
- `QualityRemoteToolProcessHandler` to bridge remote SDK process
  output into the SDK message processor.
- Manual XML SAX parsing of checkstyle output.
- Manual dedup of duplicate messages within a file.

---

## 3. Mapping each feature to the new SDK

Below: for each user-visible feature, which `:quality-tools-sdk`
phase / artefact handles it.

| Legacy feature | New SDK home | Notes |
| --- | --- | --- |
| `PhpStanQualityToolType` (registration) | `QualityTool` (phase 01) — one Kotlin class | Same EP `dev.jplugins.qualityTools.tool`. Pre-existing `MagoGlobal` / `MagoValidation` inspection short-name preservation (phase 10a.1) applies → `PhpStan.inspectionShortNames = setOf("PhpStanGlobal", "PhpStanValidation")`. |
| `PhpStanConfiguration` (tool path, timeout) | `LocalBinarySource` (phase 02) + per-`ConfigProfile.timeoutMs` (phase 04) | Tool path moves into the source; timeout is a profile property. |
| `PhpStanRemoteConfiguration` (interpreter id) | `PhpInterpreterBinarySource` in `:php` (phase 02) | New typeId `phpstan.php-interpreter` or generic `php.interpreter` reused. |
| `PhpStanConfigurationManager` + `*BaseManager` (2 services) | Unified `QualityToolsProjectStorage` (phase 04) | Zero PHPStan-specific persistence code. |
| `PhpStanProjectConfiguration.selectedConfigurationId` | `QualityToolsProjectStorage.activeProfileId("phpstan","analyze")` | Native to phase 04. |
| `PhpStanConfigurationProvider` EP | `ConfigSourceType` EP (phase 02) | Each source type is a separate EP impl. No tool-private provider EP needed; we get the same multi-source story for every tool. |
| `PhpStanRemoteConfigurationProvider` | `PhpInterpreterBinarySourceType` in `:php` | Generic — same class serves Mago, PHPStan, Psalm, anyone. |
| `PhpStanBlackList` | `GlobPathIgnorePolicy` (phase 06) | Direct port; persisted via the unified storage. |
| `PhpStanValidationInspection` (local) + `PhpStanGlobalInspection` (global) | Phase 10a.1 inspection-shortname preservation | The new annotator (phase 08) surfaces both under the same shortnames so existing inspection profiles keep their settings. |
| `PhpStanAnnotatorProxy` | `QualityToolsAnnotator` + `PhpStanTool.buildArgs` (phase 01/08) | ~150 LOC drops; we keep ≤ 30 LOC of arg-building. |
| `PhpStanMessageProcessor` (SAX checkstyle) | `CheckstyleXmlReader` (phase 06) — bundled | Zero PHPStan-specific reader code. Dedup is built into the reader contract. |
| `PhpStanComposerConfig` | `ComposerBinarySourceType` in `:php` (composer-from-vendor source) + a small `PhpStanComposerRulesetEnricher` (phase 06 `MessageEnricher`? no — see §4 gap below) | Composer source covers the binary; `phpstan.neon` discovery + `composer.json scripts.phpstan` memory-limit parsing needs a hook (see §4.5). |
| `PhpStanConfigurable` + `PhpStanConfigurableForm` + `PhpStanOptionsPanel` | `PhpStanOptionsSchema` + `AutoToolSettingsPanel` (phase 04 + 07) | Five textfields + checkbox + spinner → six `OptionSpec`s. Zero custom Swing code. |
| `PhpStanOptionsConfiguration` (project state) | Subsumed by `OptionsBag` in the unified storage (phase 04) | No separate `@State` service. |
| `PhpStanSettingsTransferStartupActivity` | Migration step inside the legacy-XML migrator (phase 10c-equivalent for PHPStan) | Generic mechanism, same as Mago migration. |
| `PhpStanAddToIgnoredAction` | Built into the SDK once an `IgnorePolicy` is registered (phase 06/07) | Right-click action wired automatically for every tool with `GlobPathIgnorePolicy`. |
| `PhpStanOpenSettingsProvider` (composer-notification deep-link) | `InternalErrorActionProvider` (phase 07) — registers a "Configure PHPStan" action keyed on `category="phpstan.*"` | Generic for any tool. |
| `PhpStanCompletionContributor` (PHPDoc tags) | Stays as a plain `<completion.contributor>` in the PHPStan plugin's own `plugin.xml` | Not a quality-tool concern. Out of SDK scope. |
| `QualityToolByInterpreterDialog` + `QualityToolByInterpreterConfigurableForm` (in remote-interp jar) | `ConfigSourceWizard` for `PhpInterpreterBinarySourceType` in `:php` (phase 02 + 07) | One generic wizard for all tools — Mago/PHPStan/Psalm/phpcs all share it. |
| `QualityRemoteToolProcessHandler` | `IntellijProcessSpawner` in `:php` (phase 05 spec) | Already designed to handle remote SDK plus the stdin-base64 Docker fallback. |
| Validate-button (`phpstan --version` parsing) | `BinaryValidator` SAM on `QualityTool` — TBD: not yet in phase 01 spec, see gap §4.7 | Small gap. |
| 60-second default timeout for remote profiles | `ConfigSourceType.defaultTimeoutMs` override (phase 02) — needs adding, see gap §4.4 | Small gap. |
| "Suppress one specific stderr line" (Xdebug warning) | `MessageEnricher` matching `category="phpstan.parse_error"` that drops the message — OR — a `StderrFilter` on the runner (phase 05 has none yet) | Small gap §4.6. |

---

## 4. Gaps in the new SDK exposed by this exercise

These are the items that the SDK doesn't currently cover, ranked by
how much "generic code" we'd otherwise be tempted to write. Each gap
gets a recommendation: fix in the SDK (small, generic) vs. leave to
the plugin (PHPStan-specific only).

### 4.1. `BinaryValidator` for "click to verify version"

**What it is:** Each tool's settings form has a "Validate" button
that runs `<binary> --version`, parses the output to confirm the
right tool (and on PHPCS, that it is ≥ 1.5.0), then shows
"OK, PHPStan 1.10.50" or an error.

**Today:** Hand-coded in every `*ConfigurableForm` with a tool-
specific regex.

**Recommendation:** Add to `QualityTool` (phase 01) a small SAM:

```kotlin
public interface BinaryValidator {
    public suspend fun validate(run: ToolRun): ValidationResult
}
public interface ValidationResult {
    public val ok: Boolean
    public val message: String
}
```

`QualityTool.binaryValidator: BinaryValidator?` is optional;
`AutoToolSettingsPanel` shows the button only when non-null. Drives
zero plugin-specific UI code. **Phase 01 edit (small).**

### 4.2. Multiple readers per tool (config-driven format selection)

**What it is:** PHPStan can output checkstyle, JSON, raw, or
its own GitHub-actions format. Today's plugin hard-codes
checkstyle. A user upgrading to PHPStan 2.x might want JSON.

**Today:** The reader id is fixed at the `QualityTool` level
(`resultReaderId: String`). To switch formats you'd ship a new
plugin.

**Recommendation:** Allow `ToolMode.resultReaderId` to override
the tool-level default. `QualityToolsAnnotator` already looks up by
mode. **Phase 01 edit (trivial: move the field from `QualityTool` to
`ToolMode`, fall back to tool-level).**

### 4.3. Tool-version-aware command-line construction

**What it is:** PHPStan ≥ 1.10 added `--memory-limit`, `--debug`
is gated by version, level 8 was added at some point, etc.

**Today:** Some plugins read `phpstan --version` once at tool path
configuration time, store the version in `*Configuration`, then
gate args.

**Recommendation:** Store `detectedVersion: String?` on
`ResolvedBinary` (phase 02). `buildArgs` can read
`ctx.resolvedBinary?.detectedVersion` and branch. **Phase 02 edit
(small; default `null`).**

This is the same field the validator (4.1) would populate.

### 4.4. `ConfigSourceType.defaultTimeoutMs` override

**What it is:** `PhpStanRemoteConfigurationProvider` overrides
timeout to 60s for remote runs.

**Today:** Hard-coded inside the per-tool provider.

**Recommendation:** Add `defaultTimeoutMs: Long` (default 30_000) on
`ConfigSourceType`. The `ConfigProfile` initial value seeds from it.
**Phase 02 edit (trivial).**

### 4.5. Composer auto-detect "enrich the options from
`phpstan.neon` / `scripts.phpstan`"

**What it is:** When Composer source detects `vendor/bin/phpstan`,
the legacy plugin **also**:

1. Reads `composer.json` for `scripts.phpstan` and parses
   `--memory-limit=4G` out of the string.
2. Looks for `phpstan.neon` next to `composer.json`, applies it as
   `--config=`.

Both of these mutate `PhpStanOptionsConfiguration` after detection.

**Today:** `QualityToolsComposerConfig.applyRulesetFromComposer` and
`applyInspectionSettingsFromComposer` — PHPStan-specific helpers in
the legacy SDK.

**Recommendation:** This is **tool-specific business logic**, not
generic SDK. We should add a SAM hook the source type can implement
when it detects a binary, but the regex and JSON parsing live in
PHPStan's own code. Concretely:

```kotlin
public interface OnDetectedHook {
    public fun onDetected(source: ConfigSource, project: ResolveContext, bag: OptionsBag)
}
```

A `PhpStanComposerOnDetectedHook` reads `phpstan.neon` and
`composer.json` and writes to the new tool's `OptionsBag`. **Phase 02
edit (small): `ConfigSourceType.watch` callback already runs;
add an `onDetected(bag)` extension on the resolver.**

### 4.6. Stderr filtering / muting specific tool warnings

**What it is:** PHPStan emits a "The Xdebug PHP extension is
active" warning to stderr when XDebug is on; the legacy plugin
silently drops it from the user-facing notifier.

**Today:** Hardcoded in `PhpStanAnnotatorProxy.showMessage(message)`.

**Recommendation:** Either:

- (a) PHPStan plugin ships a `MessageEnricher` that filters
  out the matching `internal_error` ToolMessage by category
  before it reaches the notifier; OR
- (b) SDK provides a `StderrFilter` EP on the runner.

(a) is sufficient and PHPStan-specific; the SDK is fine as-is.
**No SDK change.**

### 4.7. Per-tool default ignore-policy registration

**What it is:** Today every tool registers its own
`<projectService>` for the blacklist + its own
`<AddToIgnoredAction>` action. The right-click "Add to ignored"
menu item is wired by inheriting `QualityToolAddToIgnoredAction`.

**Recommendation:** Phase 06 already has `IgnorePolicyType`;
`GlobPathIgnorePolicy` is bundled. The SDK should auto-register
the right-click action for **every** tool that has at least one
`GlobPathIgnorePolicy` profile. **Phase 07 edit:
`QualityToolsAddToIgnoredActionGroup` in `:ui` reads the registered
tools, contributes one menu item per tool. Currently the master EP
list in phase 07 mentions this implicitly; spell it out as an
explicit acceptance bullet.**

### 4.8. Inspection-profile compatibility with five-field history

**What it is:** Pre-2024 PhpStorm stored PHPStan options as five
public fields on `PhpStanGlobalInspection` (i.e. inside the
inspection profile XML). `PhpStanSettingsTransferStartupActivity`
copies them into the modern `PhpStanOptionsConfiguration`.

**Recommendation:** The migration mechanism designed for Mago in
phase 10 (idempotent + version stamped + dual-write window) is
exactly what's needed. The PHPStan migration is one more `Migrator`
implementation. **No SDK change** — it falls under the same plan.

### 4.9. `inspectionStarted` user-data dance for batch mode

**What it is:** When `Code → Inspect Code` runs, the legacy
`PhpStanGlobalInspection.inspectionStarted` runs the annotator
once over the whole project, stores results in
`project.putUserData(PHPSTAN_ANNOTATOR_INFO, ...)`, and
`checkFile` later picks them up.

This pattern works around the platform's per-file `checkFile`
contract for tools that prefer one project-wide invocation.

**Recommendation:** Phase 08 annotator already has a notion of
`executionStyle = "batch"`. We need to spell out that a
batch-style mode invokes `ToolRunner` once with
`target = ToolTarget.Project` and caches the results in a
per-run map keyed by `(toolId, profileId)`; `checkFile`
during the same inspection run reads from the cache. **Phase 08
edit (medium): document the batch-vs-per-file cache contract.**

### 4.10. Cross-EP completion contributor

**What it is:** `PhpStanCompletionContributor` adds two PHPDoc
tags to completion. Has nothing to do with quality-tool runs.

**Recommendation:** Stays as a plain
`<completion.contributor language="PHP">` in the PHPStan plugin's
own `plugin.xml`. **Not an SDK concern.** Documented in the
plugin-author guide §5 as "not every plugin file goes through the
SDK."

### 4.11. PhpStorm-only gating (`PlatformUtils.isPhpStorm()`)

**What it is:** Settings-transfer activity bails out if not
PhpStorm. Quality-tool plugin requires PhpStorm
(`<depends>com.intellij.modules.ultimate</depends>`).

**Recommendation:** Plugin-level concern, not SDK. The SDK runs
fine in any IntelliJ-platform IDE; whether you ship a plugin gated
to PhpStorm is up to you.

### 4.12. `QualityToolByInterpreterConfigurableForm` / Dialog

**What it is:** 250 + 200 LOC of Swing that shows a combobox of PHP
interpreters and configures the tool path inside the chosen one.

**Recommendation:** This *is* generic across all PHP-aware tools.
Live in `:php` as `PhpInterpreterSourceWizard` (phase 02 wizard,
phase 07 host). **No SDK gap** — already covered.

---

## 5. Generic-code overhead — what we'd write if we ported now

Following the gap list, if we shipped the PHPStan port today with the
SDK *as currently specified in phases 00-10*, we'd be writing some
PHPStan-specific code that should really be generic. Marked them
with ⚠ — these are signals the SDK is missing something.

| Code we'd write in the PHPStan port | Generic? | Action |
| --- | --- | --- |
| `PhpStanTool : QualityTool` | unique | keep |
| `PhpStanOptionsSchema : OptionsSchema` | unique | keep |
| `PhpStanComposerOnDetectedHook` | unique | keep |
| `PhpStanXdebugWarningFilter : MessageEnricher` | unique | keep |
| `PhpStanVersionValidator : BinaryValidator` | unique | keep (needs SDK gap 4.1) |
| ⚠ Custom Swing in version-validate button | generic | resolve via gap 4.1 |
| ⚠ Storage of "detected version" in profile | generic | resolve via gap 4.3 |
| ⚠ Default-timeout-60s for remote source | generic | resolve via gap 4.4 |
| ⚠ "On detected, enrich options" callback wiring | generic | resolve via gap 4.5 |
| ⚠ "Batch mode = one project-wide run + cached results" plumbing | generic | resolve via gap 4.9 |
| ⚠ Per-tool right-click "Add to ignored" action class | generic | resolve via gap 4.7 |

Six ⚠ items. Each adds 5–30 LOC of generic-shaped code per tool. If
we have 10 tools eventually, that's 250 LOC of duplication. **Fixing
all six in the SDK first is the right call.**

---

## 6. Concrete file list for the PHPStan port (post-gap-fix)

Assuming gaps in §4 are merged into the SDK (small edits to phases
01, 02, 07, 08), the PHPStan plugin shrinks to roughly:

**Required**:

- `PhpStanTool.kt` (~80 LOC) — id, modes (`analyze` on-the-fly,
  `analyze-full` batch), capabilities, options schema, buildArgs.
- `PhpStanOptionsSchema.kt` (~40 LOC) — six specs + mode schemas.
- `PhpStanVersionValidator.kt` (~30 LOC) — runs `--version`, regex
  parses, returns ok/version.
- `PhpStanComposerOnDetectedHook.kt` (~60 LOC) — `phpstan.neon`
  discovery + `composer.json scripts.phpstan` memory-limit parsing.
- `PhpStanXdebugFilterEnricher.kt` (~15 LOC) — drops the single
  known-noise message.
- `PhpStanMigration.kt` (~60 LOC) — legacy XML → new storage.
- `META-INF/plugin.xml` (~40 LOC) — registrations.

**Optional** (the small features that stay PHPStan-specific):

- `PhpStanCompletionContributor.kt` — unchanged from today,
  35 LOC.
- `PhpStanBundle.properties` — i18n.
- `inspection.html` — description for the inspection.

**Total: ~360 LOC + bundle/HTML**, vs. ~2,650 LOC today. **~7×
reduction.**

---

## 7. Order of work (when we get to coding)

Sequenced so each step is mergeable independently. Numbered against
the existing phase-doc plan, NOT replacing it.

1. **SDK gap fixes** (phase doc edits + corresponding code):
   - 4.1 `BinaryValidator` → phase 01.
   - 4.2 per-mode reader id → phase 01.
   - 4.3 `ResolvedBinary.detectedVersion` → phase 02.
   - 4.4 `ConfigSourceType.defaultTimeoutMs` → phase 02.
   - 4.5 `onDetected(bag)` hook on source resolution → phase 02.
   - 4.7 auto-wired "Add to ignored" action → phase 07.
   - 4.9 batch-mode cache contract → phase 08.

   Each is small (≤ 50 LOC of code). Ship as one "phase patches"
   PR after Mago migration validates the SDK in production.

2. **PHPStan tool registration** — phase 01-style minimal port:
   `PhpStanTool` + `PhpStanOptionsSchema` + reuse the bundled
   `CheckstyleXmlReader`. Result: PHPStan visible in Settings, but
   no validate button, no Composer auto-detect, no remote support.

3. **PHPStan version detection** — `PhpStanVersionValidator` wired
   into the validate button (gap 4.1).

4. **PHPStan Composer auto-detect** — `PhpStanComposerOnDetectedHook`
   wired into the generic `ComposerBinarySourceType` (gap 4.5).
   Replaces `PhpStanComposerConfig` entirely.

5. **PHPStan remote** — `<depends optional="true">` on the new
   PHP-interpreter source type from `:php` (zero PHPStan code).
   Tests against the 60-s timeout from gap 4.4.

6. **PHPStan migration** — `PhpStanMigration` ports legacy
   `MagoSettings`-style XML + the
   `PhpStanSettingsTransferStartupActivity` profile→options carry-
   over into the unified storage.

7. **PHPStan stderr filter** — small `MessageEnricher` per gap 4.6.

8. **PHPStan inspection-shortname preservation** — verify
   `PhpStanGlobal` / `PhpStanValidation` are emitted by the SDK
   bridge (phase 10a.1).

9. **Cleanup**: delete the legacy plugin's classes once the new
   build is validated (mirror of phase 10c for PHPStan).

---

## 8. Risks / open questions

- **Bundled vs separate plugin**: Should the PHPStan port live in
  the existing JetBrains plugin (requires their PR) or a separate
  community plugin first? Recommendation: start as a separate plugin
  to de-risk; upstream once Mago has validated the SDK.
- **Inspection-profile schema mismatch**: A user's profile XML
  references `<inspection_tool class="PhpStanGlobal" enabled="true"
  level="WEAK_WARNING" ...>` with five custom fields stored inline.
  If the SDK bridge surfaces the inspection short-name but loses the
  inline fields, the migration must read them once during transfer
  (same as `PhpStanSettingsTransferStartupActivity`).
- **Multiple "modes" don't map cleanly to PHPStan**: PHPStan has one
  mode (analyze) with a flag (`--analyze` defaults; full-project
  toggles `target`). Should `full-project` be its own mode, or an
  option of the `analyze` mode? Recommendation: one mode `analyze`,
  the `fullProject` option flips `ToolTarget` between
  `SingleFile`/`Project`. Cleaner semantically.
- **PHPStan baselines** (`phpstan-baseline.neon`) — the user can
  carry a baseline file that suppresses N known errors. Not in the
  legacy plugin (handled externally as a third-party
  `de.shyim.ideaphpstantoolbox`). Out of scope here; could be added
  later via `IgnorePolicyType` with `typeId = "phpstan.baseline"`.
- **PHPStan ignoreErrors via `phpstan.neon`** — same as baselines,
  out of scope.
- **No fix-emitter**: PHPStan does not emit fixes. So we won't
  exercise the rich `ToolFix` hierarchy. Mago covers that side; this
  port exercises sources, remote interpreter, batch-vs-on-the-fly,
  options schema, Composer auto-detect, and migration. Together they
  cover the bulk of the SDK contract.

---

## 9. Summary

- The full PHPStan integration is **~2,650 LOC of Java/Kotlin glue
  today** spread across 22 classes in two jars.
- Mapped onto the proposed SDK as-specified, ~95% of those classes
  disappear in favor of generic infrastructure.
- **6 small SDK gaps** surface from the exercise; all are local
  patches to phases 01/02/07/08 (≤ 50 LOC each). None require a
  fundamental redesign.
- Post-gap-fix the PHPStan plugin lands at **~360 LOC** — a 7×
  reduction.
- The remote-interpreter integration becomes free (zero PHPStan
  code) because the generic `PhpInterpreterBinarySourceType` in
  `:php` handles every PHP-based quality tool the same way.
- Recommendation: merge the 6 SDK patches as a "phase 11" hardening
  pass *after* the Mago migration in phase 10 ships, then port
  PHPStan as the second adopter to validate the SDK against a
  tool we didn't design with.
