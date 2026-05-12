# PHP-CS-Fixer port to `:quality-tools-sdk` — plan and analysis

> Goal: take the existing JetBrains PHP-CS-Fixer plugin (which we
> reverse-engineered) and write down exactly how it would be rebuilt on
> top of our new `:quality-tools-sdk`. We don't write code in this
> document — only the inventory, the mapping, the gaps, and the work
> plan. Implementation comes later, once the gaps are resolved.
>
> Why this exercise: PHP-CS-Fixer is the **first fixer-shaped** adopter
> the SDK will see. Unlike PHPStan or Psalm, the tool's primary purpose
> is **mutating the file**, not emitting diagnostics. Its on-the-fly
> annotator is a side product — it parses the *diff* PHP-CS-Fixer
> produces in `--dry-run` mode and turns it into squiggles. The
> manual-fix path runs the same binary without `--dry-run` and rewrites
> the document. If we can port PHP-CS-Fixer cleanly, the SDK's
> `format`-execution-style story (introduced in cycle 3 of phase 07) is
> real and not a bolt-on.

---

## 0. Reference: source material

Reverse-engineered classes I analysed (CFR 0.152):

- `com.jetbrains.php.tools.quality.phpCSFixer.*` (20 files, ~2,750
  LOC) — bundled inside `php.jar` (id `com.jetbrains.php`). Unlike
  PHPStan/Psalm, PHP-CS-Fixer rides along with the PHP plugin.
- `com.jetbrains.php.remote.tools.quality.phpCSFixer.*` (2 files,
  ~385 LOC) — registered via `phpCSFixer-remote-plugin.xml` when the
  remote-interpreter plugin is present.
- `com.jetbrains.php.remote.tools.quality.*` (3 base classes,
  ~560 LOC) — same shared remote infra as PHPStan §0.
- `com.jetbrains.php.tools.quality.QualityToolReformatFile` (~380
  LOC) and `QualityToolExternalFormatter` (~375 LOC) — abstract
  base + `AsyncDocumentFormattingService` adapter shared with
  phpcbf and Laravel Pint.
- `com.jetbrains.php.tools.quality.PhpExternalFormatterCheckinHandler`
  (~422 LOC of Kotlin) — VCS commit hook that runs the active
  external formatter over changed files. Shared with phpcbf and
  Laravel Pint via the `QualityToolsExternalFormatterConfiguration.ExternalFormatter`
  enum.

i18n / HTML: `PhpBundle.properties` keys
`quality.tool.cs.fixer.*` + `external.formatter.*` +
`php.commit.checkin.*`, `inspection.html`, and
`phpCSFixer-remote-plugin.xml` for the optional remote registration.

---

## 1. Inventory: what every class does today

### 1.1. Core "ports" of the legacy SDK

| Class | LOC | Role |
| --- | --- | --- |
| `PhpCSFixerQualityToolType` | 206 | EP entry point (`com.jetbrains.php.tools.quality.type`). Wires the seven sibling services, sets help topic `reference.settings.php.phpcsfixer`, returns the `PhpCSFixerValidationInspection` (no `*Global` cousin — see §1.4). |
| `PhpCSFixerConfiguration` | 198 | Per-instance config: `myPhpCSFixerPath`, `myStandards` (semicolon-joined cached list), `myMaxMessagesPerFile=50`, `myTimeoutMs=5000`, `getId()="local"`. Note the **default timeout is 5 s** vs PHPStan's 30 s — fixer runs are expected to be fast. |
| `PhpCSFixerConfigurationManager` | 66 | Glue between project and app `PhpCSFixerConfigurationBaseManager`. Two `@State` services (`PHPCSFixer` in `php.xml`). |
| `PhpCSFixerConfigurationBaseManager` | 62 | `PersistentStateComponent<Element>` with one config tag (`PHPCSFixerSettings`). |
| `PhpCSFixerConfigurationProvider` | 78 | Abstract — `EP_NAME = "com.jetbrains.php.tools.quality.phpCSFixer.phpCSFixerConfigurationProvider"`. `getInstances()` returns the single registered provider (logs error if multiple). Carries `fillSettingsByDefaultValue` that copies the standards array from the local config. |
| `PhpCSFixerProjectConfiguration` | 56 | Project state with `selectedConfigurationId` (default `DEFAULT_INTERPRETER`). |
| `PhpCSFixerBlackList` | 23 | `QualityToolBlackList` subclass; `@State` stored in `$WORKSPACE_FILE$`. |
| `PhpCSFixerValidationInspection` | 131 | The **only** inspection. Stores three public fields used by legacy XML profiles: `CODING_STANDARD="PSR2"`, `CUSTOM_RULESET_PATH=""`, `ALLOW_RISKY_RULES=false`. `getCommandLineOptions` builds the `fix … --dry-run --diff --format=xml -vv` invocation. **Note: no separate `*Global` inspection like PHPStan — PHP-CS-Fixer is on-the-fly-only.** |
| `PhpCSFixerOptionsConfiguration` | 82 | Project `@State` service (`PHPCSFixerOptionsConfiguration` in `php.xml`) holding the **modern** version of the three fields. `setTransferred(boolean)` migration sentinel inherited from `QualityToolsOptionsConfiguration`. |
| `PhpCSFixerSettingsTransferStartupActivity` | 110 | One-shot migration: copies `CODING_STANDARD` / `ALLOW_RISKY_RULES` / `CUSTOM_RULESET_PATH` from the inspection profile XML into `PhpCSFixerOptionsConfiguration` on first PhpStorm 2024+ startup. Skips in unit-test / headless / non-PhpStorm. |
| `PhpCSFixerAnnotatorProxy` | 177 | `QualityToolAnnotator<PhpCSFixerValidationInspection>` singleton. Picks working dir = `dirname(rulesetPath)` in Custom mode else `project.basePath`. Sets env `PHP_CS_FIXER_ALLOW_RISKY=yes`. Toggles `runOnTempFiles()` via registry key `php.cs.fixer.temp.file`. |
| `PhpCSFixerMessageProcessor` | 275 | `QualityToolXmlMessageProcessor` (SAX). Two-phase parse: pulls `<applied_fixer name="…">` into a comma-joined message string, then parses the embedded udiff hunks (`@@ -X,Y +A,B @@` + `+`/`-`/` ` lines) into multi-line TextRanges. Severity always `WARNING`. `getQuickFix()` returns `REFORMAT_FILE_ACTION` on every message. Drops the "risky fixers" + "Loaded config" stderr noise. |
| `PhpCSFixerComposerConfig` | 248 | `QualityToolsComposerConfig` — package `friendsofphp/php-cs-fixer`, binary `vendor/bin/php-cs-fixer`. `applyRulesetFromRoot` scans for the four ruleset filenames; `applyRulesetFromComposer` parses `composer.json scripts.*` for `--config=` / `--rules=` substrings. `canBeEnabled` adds the "coding standard non-empty" constraint. |
| `PhpCSFixerRulesetAnalyzer` | 30 | The four filename constants + `isCodingStandardFile(path)` predicate. |
| `PhpCSFixerConfigurable` | 106 | Settings configurable at `Settings/PHP/Quality Tools/PHP CS Fixer` (id `settings.php.quality.tools.php.cs.fixer`). |
| `PhpCSFixerConfigurableForm` | 127 | `QualityToolConfigurableForm` subclass — version regex `PHP CS Fixer ([\d.]*)` with hard minimum 2.8.0. |
| `PhpCSFixerOptionsPanel` | 394 | Swing-Designer panel: dynamic standards combobox (via `list-sets --format=json`, sorted, `Custom` appended; fallback `PSR1/PSR2/Symfony/DoctrineAnnotation/PHP70Migration/PHP71Migration`), risky checkbox, custom-ruleset file picker. `"Custom"` shows the picker and **disables** the risky checkbox. |
| `PhpCSFixerAddToIgnoredAction` | 33 | `QualityToolAddToIgnoredAction` subclass. |
| `PhpCSFixerReformatFile` | 190 | `QualityToolReformatFile` subclass. Two-arg ctor: `dryRun=true` for the annotator, `dryRun=false` for the user-invoked action. Args: `fix <paths> [--config=<remote-mapped>]/[--rules=@<std>] [--dry-run] --diff --format=udiff --format=xml -vv [--allow-risky=yes/no]`. |
| `PhpCSFixerReformatFileAction` | 156 | `QualityToolReformatFileAction<…>` — the `IntentionAction` returned as quick-fix. `generatePreview` is `EMPTY` (opaque fix). |

**Total bundled: ~2,750 LOC of Java/Kotlin glue.**

### 1.2. Remote interpreter glue

In `php.jar`'s remote section (only loaded if the remote-interpreter
plugin is on):

| Class | LOC | Role |
| --- | --- | --- |
| `PhpCSFixerRemoteConfiguration` | 169 | Subclass of `PhpCSFixerConfiguration` that adds `interpreterId`, `PhpSdkDependentConfiguration` impl. `<Tag("phpcs_fixer_by_interpreter")>` for XML. |
| `PhpCSFixerRemoteConfigurationProvider` | 216 | Registered on `com.jetbrains.php.tools.quality.phpCSFixer.phpCSFixerConfigurationProvider` EP. Provides `createNewInstance` (which opens the by-interpreter dialog), `createConfigurationByInterpreter`, and overrides default timeout to 30 s for remote runs (vs the 5 s local default). |

Shared remote infra (already inventoried in PHPStan §1.2):

- `QualityToolByInterpreterDialog`
- `QualityToolByInterpreterConfigurableForm`
- `QualityRemoteToolProcessHandler`

### 1.3. Formatter / VCS glue (shared with phpcbf + Laravel Pint)

| Class | LOC | Role |
| --- | --- | --- |
| `QualityToolExternalFormatter` | 375 | `AsyncDocumentFormattingService` impl. `canFormat` true for `PhpFile`s when an external formatter is selected. Empty `getFeatures()` (full-file only). `runAfter()` = `CoreFormattingService.class` when advanced-setting `php.use.internal.formatter` is on. On terminate reloads the file. `getTimeoutActions` offers "Add to excluded" + "Turn off external formatter". |
| `QualityToolsExternalFormatterConfiguration` | 65 | Project `@State` (`PhpExternalFormatter` in `php.xml`). Enum `ExternalFormatter ∈ {PHP_CS_FIXER, PHP_CBF, LARAVEL_PINT, NO}` — **mutually exclusive**. |
| `PhpExternalFormatterCheckinHandler` | 422 | Kotlin `CheckinHandler` + `CommitCheck` (`ExecutionOrder.MODIFICATION`). Adds the "Reformat code with external formatter" commit checkbox, runs the active formatter over the commit's `VirtualFile[]`, skips files in the blacklist, honors the active profile's timeout, notifies on non-zero exit. |
| `PhpExternalFormatterCheckinHandlerFactory` (+Kt + state) | ~150 | Factory registration + project `@State` for "should reformat on commit" boolean. |
| `PhpExternalFormatterPanel` (+Kt) | ~200 | Combobox UI in the Quality Tools settings root; `validateConfiguration(project)` returns the active formatter's profile or null. |

Dispatch happens through the four-value enum in
`QualityToolExternalFormatter.getQualityToolReformatFileToBlackList`
— intentionally tool-agnostic.

### 1.4. PHP-CS-Fixer-specific quirks (the constraints that drive §4)

1. **Fixer, not linter.** No `PhpCSFixerGlobalInspection`. Style
   problems surface only on-the-fly; the only quick-fix is "reformat
   this file" (same binary, no `--dry-run`).
2. **Diff-derived diagnostics.** Messages are synthesized by
   `PhpCSFixerMessageProcessor` from udiff hunks plus the
   `<applied_fixer name="…">` list. Message text = comma-joined
   fixer names; range = the changed-line region.
3. **`format` execution-style is primary.** The user-invoked
   reformat action and the project-wide
   `QualityToolExternalFormatter` are the *real* use case; the
   on-the-fly annotator is a `--dry-run` derivative of the same
   invocation.
4. **`"Custom"` sentinel in the coding-standard combobox** toggles
   the file-picker visible and **disables** the risky-rules
   checkbox. The standards list is dynamic (`list-sets --format=json`,
   sorted, with `Custom` appended; hard-coded fallback when the
   binary fails).
5. **`AllowRiskyRules` boolean** becomes `--allow-risky=yes|no`,
   suppressed when standard is `"Custom"`, and is *also* exported as
   the env var `PHP_CS_FIXER_ALLOW_RISKY=yes` regardless.
6. **Working-dir trick**: `dirname(rulesetPath)` when standard is
   `"Custom"` and a path is set; `project.basePath` otherwise. Makes
   relative paths inside the custom config file resolve correctly.
7. **Composer detect** (package `friendsofphp/php-cs-fixer`) scans
   project root and composer parent for the four legacy/modern
   filenames, plus parses `composer.json scripts.*` shell strings
   for `--config=` / `--rules=` substrings.
8. **`QualityToolsExternalFormatterConfiguration`** is a four-way
   exclusive enum (`PHP_CS_FIXER / PHP_CBF / LARAVEL_PINT / NO`)
   shared across all three fixer-style tools — porting requires
   surfacing the choice generically in the SDK.
9. **`PhpExternalFormatterCheckinHandler`** is the same shared
   Kotlin class for all three; it dispatches to whichever tool is
   active. Porting requires a generic "format on commit" hook keyed
   on `executionStyle = "format"`.

### 1.5. Plugin metadata

PHP-CS-Fixer is bundled inside `php.jar` itself — there is no
standalone `phpCSFixer.jar` to depend on. The relevant
`<extensions>` registrations live in `php-plugin.xml` (or its
equivalent), and the remote-interpreter optional config lives in
`phpCSFixer-remote-plugin.xml`. Key entries:

- 6 `@State` project services
- 1 `localInspection` (`PhpCSFixerValidation`) — NO globalInspection
- 1 `externalAnnotator` (`PhpCSFixerAnnotatorProxy`)
- 1 `postStartupActivity` (`PhpCSFixerSettingsTransferStartupActivity`)
- 1 `configurable`
- 1 `composerConfigClient` (`PhpCSFixerComposerConfig`)
- 1 `type` (`PhpCSFixerQualityToolType`)
- 1 inner EP (`phpCSFixerConfigurationProvider`)
- 1 `AddToIgnoredAction`
- 1 `formattingService` (`QualityToolExternalFormatter` — shared)
- 1 `checkinHandlerFactory` (`PhpExternalFormatterCheckinHandlerFactory`
  — shared)

---

## 2. Functional surface (what the user sees)

### 2.1. User-facing features

Same shape as PHPStan §2.1 items 1–2, 4–10 (settings page, inspection
entry, on-the-fly analysis, Composer auto-detect, "Add to ignored",
notifications, settings migration, remote PHP interpreter). Specific
PHP-CS-Fixer differences worth calling out:

1. **Settings page** at `PHP / Quality Tools / PHP CS Fixer`: same
   list-of-profiles UI as PHPStan, but with a min-version of 2.8.0
   enforced by the validate button and a **5 s default timeout**
   (vs PHPStan's 30 s).
2. **Only one inspection entry**: `PhpCSFixerValidation` (local —
   surfaces on-the-fly diff hunks as warnings). **No `Global`
   counterpart.**
3. **On-the-fly analysis**: runs `php-cs-fixer fix - --dry-run
   --diff --format=xml -vv` (or `… <tempfile>` if the
   `php.cs.fixer.temp.file` registry key is on), parses the udiff,
   paints multi-line warnings labeled with the comma-joined fixer
   names.
4. **"Reformat with PHP CS Fixer" quick-fix** on every annotator
   warning — invokes the same binary without `--dry-run`, rewriting
   the file in place. Same dispatcher as the manual right-click
   action.
5. **`Settings/PHP/Quality Tools/External Formatter` combobox**
   picks PHP-CS-Fixer as the active external formatter for the
   project; thereafter `Code → Reformat Code` (Ctrl-Alt-L) on a
   `.php` file routes through `QualityToolExternalFormatter`
   (`AsyncDocumentFormattingService`) and rewrites the file. The
   combobox is shared with phpcbf / Laravel Pint — only one tool can
   be active at a time.
6. **"Reformat code with external formatter" checkbox in the commit
   dialog** (`PhpExternalFormatterCheckinHandler`) — when ticked,
   runs the active formatter on every changed file before the commit
   proceeds. Non-zero exit emits a notification; commit still
   proceeds (`ExecutionOrder.MODIFICATION`, not `BLOCK`).
7. **Composer auto-detect** (`friendsofphp/php-cs-fixer`): when
   `vendor/bin/php-cs-fixer` appears, also scans the project root
   for `.php_cs` / `.php-cs-fixer.php` / `.php_cs.dist` /
   `.php-cs-fixer.dist.php` AND reads `composer.json scripts.*` for
   any string containing `php-cs-fixer`, pulling `--config=` /
   `--rules=` substrings out of it.
8. **Settings migration**: three-field carry-over
   (`CODING_STANDARD`, `ALLOW_RISKY_RULES`, `CUSTOM_RULESET_PATH`)
   from the inspection profile XML into
   `PhpCSFixerOptionsConfiguration` on first PhpStorm 2024+ startup.

### 2.2. Internal plumbing (collapsed by the new SDK)

Mirrors PHPStan §2.2: app + project-level manager pair, three
"configuration" objects, the by-interpreter dialog, and the
remote-process handler. PHP-CS-Fixer-specific extras: manual SAX of
the `<applied_fixer>` + udiff envelope, bespoke udiff-to-TextRange
mapper, stderr filtering for "risky fixers" / "Loaded config", env
var injection (`PHP_CS_FIXER_ALLOW_RISKY=yes`), the working-dir trick
for `Custom` standard, and the `QualityToolExternalFormatter` /
`PhpExternalFormatterCheckinHandler` plumbing shared with phpcbf and
Laravel Pint.

---

## 3. Mapping each feature to the new SDK

For each user-visible feature, which `:quality-tools-sdk` phase /
artefact handles it.

| Legacy feature | New SDK home | Notes |
| --- | --- | --- |
| `PhpCSFixerQualityToolType` (registration) | `QualityTool` (phase 01) | Same EP `dev.jplugins.qualityTools.tool`. `capabilities = setOf("fix", "format")`; `supportedLanguageIds = setOf("PHP")`. |
| `PhpCSFixerConfiguration` (tool path, timeout) | `LocalBinarySource` (phase 02) + `ConfigProfile.timeoutMs` (phase 04) | Same as PHPStan §3 — local source for the binary, profile-level timeout (default 5 000 ms). |
| `PhpCSFixerRemoteConfiguration` (interpreter id) | `PhpInterpreterBinarySource` in `:php` (phase 02) | Generic source — same class serves Mago/PHPStan/Psalm/phpcs/phpcbf/php-cs-fixer/Laravel Pint. |
| `PhpCSFixerConfigurationManager` + `*BaseManager` (2 services) | Unified `QualityToolsProjectStorage` (phase 04) | Zero PHP-CS-Fixer-specific persistence code. |
| `PhpCSFixerProjectConfiguration.selectedConfigurationId` | `QualityToolsProjectStorage.activeProfileId("php-cs-fixer","fix")` | Native to phase 04. |
| `PhpCSFixerConfigurationProvider` EP | `ConfigSourceType` EP (phase 02) | Same as PHPStan §3. |
| `PhpCSFixerRemoteConfigurationProvider` | `PhpInterpreterBinarySourceType` in `:php` | Same as PHPStan §3 — 30 s default timeout for remote profiles via gap 4.4 (re-used from PHPStan plan). |
| `PhpCSFixerBlackList` | `GlobPathIgnorePolicy` (phase 06) | Direct port; persisted via unified storage. **Honored by both annotator path and `QualityToolExternalFormatter` path** — see §4 gap 4.13. |
| `PhpCSFixerValidationInspection` (local) | Phase 10a.1 inspection-shortname preservation | `PhpCSFixer.inspectionShortNames = setOf("PhpCSFixerValidation")`. Only one entry — no global cousin. |
| `PhpCSFixerAnnotatorProxy` | `QualityToolsAnnotator` + `PhpCSFixerTool.buildArgs(mode="check")` (phase 01/08) | Annotator wires the `check` mode (executionStyle `on_the_fly`) and the udiff-XML reader. ≤ 25 LOC of arg-building. |
| `PhpCSFixerMessageProcessor` (SAX → udiff hunks → text ranges) | New SDK reader `PhpCsFixerUdiffXmlReader` (phase 06) | **NEW reader** — does not fit `CheckstyleXmlReader` or `JsonStreamReader`. Reads `<report>` envelope, extracts `<applied_fixer name="…">` list per `<file>`, and parses the embedded udiff text into multi-line ranges. See §4 gap 4.14 — should it live in the SDK as a generic udiff reader, or in `:php-cs-fixer` only? |
| `PhpCSFixerReformatFile` (manual) | `PhpCSFixerTool.buildArgs(mode="fix")` + `AsyncFormattingServiceAdapter` (phase 07) | Mode with `executionStyle = "format"`, `formattingOutputMode = "in_place"`. The same `buildArgs` minus `--dry-run`. Zero adapter code in the plugin. |
| `PhpCSFixerReformatFileAction` (quick-fix on annotator) | Built-in `FormatAfterAnyFix` PostFixHook (phase 08) — generalised | Phase 08 already has a `PostFixHook` for Mago's `formatAfterFix`. Here we want the *inverse*: a fix that doesn't replace text but instead **invokes the `format` mode** of the same tool over the same file. See §4 gap 4.15. |
| `PhpCSFixerComposerConfig` | `ComposerBinarySourceType` in `:php` (composer-from-vendor) + `PhpCsFixerComposerOnDetectedHook` (PHPStan gap 4.5 mechanism, re-used) | Same shape as PHPStan §3 line. The hook reads ruleset files + `composer.json scripts.*` and writes to the new tool's `OptionsBag`. |
| `PhpCSFixerRulesetAnalyzer` (filename detection) | `OnDetectedHook` (gap 4.5) | Just a constant list — folded into `PhpCsFixerComposerOnDetectedHook`. |
| `PhpCSFixerConfigurable` + `PhpCSFixerConfigurableForm` + `PhpCSFixerOptionsPanel` | `PhpCSFixerOptionsSchema` + `AutoToolSettingsPanel` (phase 04 + 07) + custom renderer for the dynamic combobox (phase 04 `OptionRenderer` EP) | Three `OptionSpec`s: `codingStandard: EnumWithSentinel`, `allowRiskyRules: Boolean`, `rulesetPath: FilePath`. The combobox **must** be dynamic — see §4 gap 4.16. The "Custom" sentinel that toggles the file picker visible/hides risky checkbox needs schema-level support — see §4 gap 4.17. |
| `PhpCSFixerOptionsConfiguration` (project state) | Subsumed by `OptionsBag` in unified storage (phase 04) | Same as PHPStan §3. |
| `PhpCSFixerSettingsTransferStartupActivity` | Migration step inside the legacy-XML migrator (phase 10c-equivalent for PHP-CS-Fixer) | Same shape as PHPStan migration. |
| `PhpCSFixerAddToIgnoredAction` | Built into the SDK (PHPStan gap 4.7 mechanism, re-used) | Right-click action wired automatically once a `GlobPathIgnorePolicy` exists. |
| `QualityToolByInterpreterDialog` + `QualityToolByInterpreterConfigurableForm` | `ConfigSourceWizard` for `PhpInterpreterBinarySourceType` in `:php` (phase 02 + 07) | Same as PHPStan §3 line. |
| `QualityRemoteToolProcessHandler` | `IntellijProcessSpawner` in `:php` (phase 05 spec) | Same as PHPStan §3 line. |
| Validate-button (`php-cs-fixer --version` + ≥ 2.8.0 check) | `BinaryValidator` SAM on `QualityTool` (PHPStan gap 4.1) | Re-uses the PHPStan gap-fix. Min-version constant `(2,8,0)` lives in the validator impl. |
| **`QualityToolExternalFormatter` (AsyncDocumentFormattingService)** | `AsyncFormattingServiceAdapter` in `:ui` (phase 07) | Phase 07 already specifies this adapter. It walks all registered tools, finds modes with `executionStyle = "format"`, and registers them with `formattingService`. The tri-state `ExternalFormatter` enum collapses into "which `format`-mode tool is active for the project" — see §4 gap 4.18. |
| **`PhpExternalFormatterCheckinHandler` (format-on-commit)** | New SDK component `FormatOnCommitCheckinHandler` (cross-cutting addition — gap 4.19) | Generic across tools: any `format`-execution-style mode can opt in. Single shared `<checkinHandlerFactory>` in `:ui`. |
| **`QualityToolsExternalFormatterConfiguration` (which tool formats this project?)** | `QualityToolsProjectStorage.activeFormatterToolId` (phase 04 — gap 4.18) | New project-level setting; serialized in the same `quality-tools.xml`. |
| `PHP_CS_FIXER_ALLOW_RISKY` env var injection | `EnvMutator` EP (phase 05) | Already exists. Plugin ships a small `PhpCsFixerEnvMutator` that sets the var when the mode is `fix` or `check`. |
| "Working dir = dirname(rulesetPath) when Custom" | `WorkingDirResolver` on `ToolMode` — gap 4.20 | Currently phase 05 sets working dir to `scope.rootPath` unconditionally. Needs a per-mode hook reading from the `OptionsBag`. |
| Stderr filtering ("risky fixers" / "Loaded config" lines) | `MessageEnricher` matching `category="php-cs-fixer.tool_warning"` that drops the message (PHPStan gap 4.6 mechanism, re-used) | Plugin-specific enricher; ~20 LOC. |

---

## 4. Gaps in the new SDK exposed by this exercise

These are the items the SDK doesn't currently cover, ranked by how
much "generic code" we'd otherwise be tempted to write per tool. The
first eleven items (4.1–4.11) **are the same gaps PHPStan surfaced**
and the recommendations from that plan apply unchanged — only items
4.12–4.20 are new to this port.

### 4.1 – 4.11. Same as PHPStan §4.1 – §4.11

In particular:

- **4.1** `BinaryValidator` — PHP-CS-Fixer needs a min-version check
  (≥ 2.8.0). Same SAM, different regex + constant.
- **4.2** Per-mode `resultReaderId` override — PHP-CS-Fixer's
  `check` and `fix` modes both want the udiff-XML reader (or none,
  in `fix` mode); not a per-tool default.
- **4.3** `ResolvedBinary.detectedVersion` — PHP-CS-Fixer 2.x and
  3.x have different config-resolution semantics; future-proofing.
- **4.4** `ConfigSourceType.defaultTimeoutMs` — local 5 s, remote
  30 s. Both diverge from the SDK's default 30 s.
- **4.5** `onDetected(bag)` Composer hook — re-used for the
  ruleset-from-`composer.json scripts.*` parsing.
- **4.6** Stderr filtering / muting — re-used for "risky fixers" /
  "Loaded config" noise.
- **4.7** Auto-wired "Add to ignored" action — re-used; the
  PHP-CS-Fixer blacklist is honored by both annotator and format
  paths (see also gap 4.13).
- **4.8** Inspection-profile compatibility with three-field history
  (`CODING_STANDARD`, `ALLOW_RISKY_RULES`, `CUSTOM_RULESET_PATH`).
- **4.9** *Not applicable* — PHP-CS-Fixer has no batch mode.
- **4.10** *Not applicable* — no PHPDoc completion contributor for
  PHP-CS-Fixer.
- **4.11** PhpStorm-only gating — same plugin-level concern.

### 4.12. *Not applicable*

(Reserved for parity with PHPStan §4.12 — `QualityToolByInterpreterConfigurableForm`
is already covered there.)

### 4.13. Ignore-policy honored by `format` modes, not just by
annotator

**What it is:** The PHP-CS-Fixer blacklist is checked in **three**
places: the annotator (don't show squiggles), the
`AsyncDocumentFormattingService` (don't reformat on Ctrl-Alt-L), and
the commit hook (don't reformat on commit). Each call site does its
own `blackList.containsFile(...)` check today.

**Today:** Three separate call sites, all hand-coded.

**Recommendation:** Phase 07's `AsyncFormattingServiceAdapter` and the
new `FormatOnCommitCheckinHandler` (see gap 4.19) should both consult
the same `IgnorePolicyType` registry that the annotator uses (phase 06).
Add a single `IgnorePolicyEvaluator.shouldSkip(toolId, file): Boolean`
helper to phase 06, called from all three sites. **Phase 06 edit
(small).**

### 4.14. Udiff reader for fixer-style tools

**What it is:** PHP-CS-Fixer's "diagnostics" are derived from the
embedded udiff in its `--format=xml` output. The current
`PhpCSFixerMessageProcessor` does **two** things in one class:
streams `<applied_fixer>` tags out of the XML *and* parses
`@@ -X,Y +A,B @@` headers + `+`/`-` lines from the diff body.

**Today:** Bespoke in `PhpCSFixerMessageProcessor`.

**Recommendation:** This shape is shared with **Laravel Pint** (same
binary family, same output) and a future **Rector** integration could
benefit. Add a generic `UdiffReader` to `:readers` (phase 06):

```kotlin
public interface UdiffReader : ResultReader {
    // Walks `+`/`-` hunks, emits one ToolMessage per contiguous
    // changed-line region, with `range = ...` set from the diff
    // header and `message` supplied by the caller.
}
```

The XML wrapper that carries the diff (the `<applied_fixer>` tags)
stays in a small plugin-side `PhpCsFixerXmlEnvelopeReader` that
delegates the diff body to the shared `UdiffReader`. **Phase 06 edit
(medium; ~80 LOC of generic reader + ~30 LOC plugin envelope).**

### 4.15. "Quick-fix that invokes a different mode of the same tool"

**What it is:** `PhpCSFixerReformatFileAction` is presented to the
user as a quick-fix on every annotator message. Clicking it doesn't
patch text directly — it spawns `php-cs-fixer fix` (the `fix` mode,
not the `check` mode that produced the message) over the whole file.

**Today:** Bespoke `IntentionAction` per tool.

**Recommendation:** Add a built-in `ToolFix` subtype
`InvokeMode(toolId, modeId, target)` resolved by a new
`InvokeModeFixHandler` in phase 07. Tools mark messages with this
fix via the reader (or via a `MessageEnricher` that appends it to
every message). **Phase 07 edit (small): one more `ToolFixHandler`
in the built-in seven.**

For PHP-CS-Fixer specifically: `PhpCsFixerXmlEnvelopeReader` (gap
4.14) sets `message.fixes = listOf(InvokeMode("php-cs-fixer", "fix",
target = SingleFile(file)))` on every emitted message. No plugin-
side `IntentionAction` code needed.

### 4.16. Dynamic enum values for option specs

**What it is:** The coding-standard combobox is populated at runtime
by calling `php-cs-fixer list-sets --format=json` and caching the
result. Fallback constants
(`PSR1/PSR2/Symfony/DoctrineAnnotation/PHP70Migration/PHP71Migration`)
bake in when the call fails.

**Today:** Custom Swing in `PhpCSFixerOptionsPanel`.

**Recommendation:** Add `OptionSpec.valuesProvider: ValuesProvider?`
in phase 04 — a SAM with `suspend fun values(ctx): List<String>` plus
a `fallback: List<String>`. `AutoToolSettingsPanel` renders a combobox
with a refresh button. Cached values live in the transient bag
section. PHP-CS-Fixer ships one `PhpCsFixerListSetsValuesProvider`
(~40 LOC). **Phase 04 + 07 edit (medium).**

### 4.17. Sentinel values that swap UI mode

**What it is:** The `"Custom"` value in the coding-standard combobox
isn't a real coding standard — it's a sentinel that:

1. Hides the combobox's normal "selected" state.
2. Reveals a sibling file-picker control.
3. Disables the (otherwise unrelated) "Allow risky rules" checkbox.

**Today:** ad-hoc `ItemListener` on the combobox in
`PhpCSFixerOptionsPanel`.

**Recommendation:** Generalise via an `OptionSpec.sentinelValue`
field on `enum`-typed specs, plus a declarative "when-then" edit
on the schema:

```kotlin
public interface OptionVisibilityRule {
    public fun visible(bag: OptionsBag): Boolean
    public fun enabled(bag: OptionsBag): Boolean
}
```

Each `OptionSpec.visibilityRule: OptionVisibilityRule? = null`.
For PHP-CS-Fixer, the `rulesetPath` spec has rule
`visible = bag["codingStandard"] == "Custom"`, and the
`allowRiskyRules` spec has rule
`enabled = bag["codingStandard"] != "Custom"`. **Phase 04 + 07 edit
(small).**

Without this gap fix, the plugin must ship a custom panel or a
custom renderer — which defeats the SDK's auto-rendering promise.

### 4.18. Project-level "active formatter" selection

**What it is:** `QualityToolsExternalFormatterConfiguration.ExternalFormatter`
is a four-way mutually-exclusive enum that gates: (1) whether the
`AsyncDocumentFormattingService` runs, (2) which tool's
`QualityToolReformatFile` impl is invoked, (3) which blacklist is
consulted, (4) which tool the commit-hook reformats with.

**Today:** Hard-coded `switch` in the legacy SDK.

**Recommendation:** Add `activeFormatterToolId: String?` to phase 04's
project storage (null = no external formatter).
`AsyncFormattingServiceAdapter` reads it and dispatches to that tool's
`format`-mode. UI auto-renders as a combobox of every registered tool
with a `format`-execution-style mode. **Phase 04 + 07 edit (medium).**

This is the single biggest non-trivial SDK gap from this port, but
also the most rewarding — it unblocks every fixer-shaped tool
(Laravel Pint, phpcbf, Rector, prettier-php) for free.

### 4.19. Generic "format on commit" checkin handler

**What it is:** `PhpExternalFormatterCheckinHandler` (422 LOC) adds a
checkbox to the commit dialog and runs the active external formatter
over each changed file in a `withProgressText` block.

**Today:** One Kotlin class, hard-coded against the three-tool enum.

**Recommendation:** Add a generic `FormatOnCommitCheckinHandler` to
`:ui` (phase 07), keyed on `activeFormatterToolId` (gap 4.18). Skips
files via the unified ignore-policy evaluator (gap 4.13), honors
profile `timeoutMs`, notifies on non-zero exit, reads its toggle
boolean from `quality-tools.xml`. **Phase 07 edit (medium; ~150 LOC
of new code, removes the 422 LOC from the legacy plugin).**

### 4.20. Per-mode `WorkingDirResolver`

**What it is:** PHP-CS-Fixer's working dir is normally
`project.basePath` but is `dirname(optionsBag.rulesetPath)` when the
coding standard is `Custom` *and* a path is set. This affects
PHP-CS-Fixer's own resolution of relative paths inside the custom
config file (`$finder->in('./src')` etc.).

**Today:** Hardcoded override in
`PhpCSFixerAnnotatorProxy.getWorkingDir` AND
`PhpCSFixerReformatFile.getWorkingDir` (yes, two copies).

**Recommendation:** Phase 05's `ToolRunner` currently builds the
process working dir from `scope.rootPath`. Add a SAM:

```kotlin
public interface WorkingDirResolver {
    public fun resolve(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget): Path
}
```

`ToolMode.workingDirResolver: WorkingDirResolver?` (default null,
falls back to current behavior). PHP-CS-Fixer's tool ships one
`PhpCsFixerCustomConfigWorkingDirResolver` (~10 LOC) that reads
the options bag and branches. **Phase 05 + 01 edit (small).**

This also benefits any future tool whose working dir depends on
options (rare but real — e.g. tools that respect a `--cwd` flag).

---

## 5. Generic-code overhead — what we'd write if we ported now

Same accounting as PHPStan §5. Marked with ⚠ the items that should
become SDK gap fixes rather than per-plugin code.

| Code we'd write in the PHP-CS-Fixer port | Generic? | Action |
| --- | --- | --- |
| `PhpCsFixerTool : QualityTool` | unique | keep |
| `PhpCsFixerOptionsSchema : OptionsSchema` | unique | keep |
| `PhpCsFixerComposerOnDetectedHook` | unique | keep |
| `PhpCsFixerStderrFilterEnricher` | unique | keep |
| `PhpCsFixerVersionValidator : BinaryValidator` | unique | keep (needs SDK gap 4.1) |
| `PhpCsFixerEnvMutator` (`PHP_CS_FIXER_ALLOW_RISKY=yes`) | unique | keep |
| `PhpCsFixerListSetsValuesProvider` | unique | keep (needs SDK gap 4.16) |
| `PhpCsFixerCustomConfigWorkingDirResolver` | unique | keep (needs SDK gap 4.20) |
| `PhpCsFixerXmlEnvelopeReader` (delegates body to `UdiffReader`) | unique | keep (needs SDK gap 4.14) |
| `PhpCsFixerMigration` (legacy three-field → bag) | unique | keep |
| ⚠ Custom Swing for the dynamic combobox | generic | resolve via gap 4.16 |
| ⚠ Custom Swing for the "Custom" sentinel → file-picker swap | generic | resolve via gap 4.17 |
| ⚠ Bespoke udiff parser per fixer-shaped tool | generic | resolve via gap 4.14 |
| ⚠ Bespoke `IntentionAction` for "reformat this file" | generic | resolve via gap 4.15 |
| ⚠ Per-tool blacklist checks at three call sites | generic | resolve via gap 4.13 |
| ⚠ Project-level "active formatter" enum | generic | resolve via gap 4.18 |
| ⚠ Format-on-commit checkin handler | generic | resolve via gap 4.19 |
| ⚠ Per-mode working-dir override | generic | resolve via gap 4.20 |

Eight ⚠ items new to this port (on top of the six surfaced by
PHPStan). Each adds 10–60 LOC of generic-shaped code per tool. If
PHP-CS-Fixer, Laravel Pint, phpcbf, and a future Rector port all
ship without these fixes, that's ~1,000 LOC of duplication. **Fixing
them in the SDK first is decisively the right call** — the
format-on-commit and active-formatter mechanisms in particular are
the dominant value-add of this port.

---

## 6. Concrete file list for the PHP-CS-Fixer port (post-gap-fix)

Assuming gaps in §4 are merged into the SDK (edits to phases 01, 04,
05, 06, 07, 08), the PHP-CS-Fixer plugin shrinks to roughly:

**Required**:

- `PhpCsFixerTool.kt` (~90 LOC) — id `"php-cs-fixer"`, two modes
  (`check` with executionStyle `on_the_fly`, `fix` with
  executionStyle `format` + `formattingOutputMode = "in_place"`),
  capabilities `{"fix","format"}`, options schema, buildArgs.
- `PhpCsFixerOptionsSchema.kt` (~50 LOC) — three specs:
  `codingStandard` (enum w/ `valuesProvider` + sentinel `"Custom"`),
  `allowRiskyRules` (Boolean w/ visibility rule), `rulesetPath`
  (FilePath w/ visibility rule).
- `PhpCsFixerVersionValidator.kt` (~30 LOC) — regex + min-version
  `(2,8,0)`.
- `PhpCsFixerListSetsValuesProvider.kt` (~50 LOC) — runs `list-sets
  --format=json`, parses, sorts, appends `"Custom"`; fallback list.
- `PhpCsFixerXmlEnvelopeReader.kt` (~30 LOC) — pulls
  `<applied_fixer>` names, delegates diff body to bundled
  `UdiffReader`, sets one `InvokeMode("php-cs-fixer","fix",…)` fix
  per message.
- `PhpCsFixerComposerOnDetectedHook.kt` (~80 LOC) — ruleset file
  discovery (`.php_cs`, `.php-cs-fixer.php`, `.php_cs.dist`,
  `.php-cs-fixer.dist.php`) + `composer.json scripts.*` parsing.
- `PhpCsFixerEnvMutator.kt` (~10 LOC) — sets
  `PHP_CS_FIXER_ALLOW_RISKY=yes` for `check` and `fix` modes.
- `PhpCsFixerCustomConfigWorkingDirResolver.kt` (~15 LOC) — reads
  bag, returns `dirname(rulesetPath)` or `scope.rootPath`.
- `PhpCsFixerStderrFilterEnricher.kt` (~20 LOC) — drops the two
  known-noise stderr lines.
- `PhpCsFixerMigration.kt` (~70 LOC) — legacy three-field XML +
  the inspection-profile carry-over.
- `META-INF/plugin.xml` (~45 LOC) — registrations.

**Optional** (small features that stay PHP-CS-Fixer-specific):

- `PhpCsFixerBundle.properties` — i18n.
- `inspection.html` — inspection description.

**Total: ~490 LOC + bundle/HTML**, vs. ~2,750 LOC today (or ~3,150
LOC if you count the per-plugin share of the shared
`QualityToolExternalFormatter` + `PhpExternalFormatterCheckinHandler`
plumbing). **~6× reduction.**

Compared to PHPStan's 7× reduction we eat a little extra plugin code
for the unique pieces (the udiff envelope, the list-sets provider,
the env mutator, the custom working dir). All of it is genuinely
PHP-CS-Fixer-shaped — none of it is generic plumbing in disguise.

---

## 7. Order of work (when we get to coding)

Sequenced so each step is mergeable independently. Numbered against
the existing phase-doc plan, NOT replacing it.

1. **SDK gap fixes** (phase doc edits + corresponding code):
   - Re-use PHPStan §7 step 1 (gaps 4.1, 4.2, 4.3, 4.4, 4.5, 4.6,
     4.7) — already on the docket.
   - Add: 4.13 unified ignore-policy evaluator → phase 06.
   - Add: 4.14 `UdiffReader` → phase 06.
   - Add: 4.15 `InvokeMode` ToolFix + handler → phase 07.
   - Add: 4.16 `OptionSpec.valuesProvider` → phase 04 + 07.
   - Add: 4.17 `OptionVisibilityRule` → phase 04 + 07.
   - Add: 4.18 `activeFormatterToolId` storage field → phase 04 + 07.
   - Add: 4.19 generic `FormatOnCommitCheckinHandler` → phase 07.
   - Add: 4.20 `WorkingDirResolver` per-mode → phase 01 + 05.

   Each is small-to-medium (≤ 150 LOC each). Ship as one "phase 12
   hardening" PR after the PHPStan port has validated the first
   round of gap fixes.

2. **PHP-CS-Fixer tool registration** — phase 01-style minimal
   port: `PhpCsFixerTool` + `PhpCsFixerOptionsSchema` + the new
   `PhpCsFixerXmlEnvelopeReader`. Result: PHP-CS-Fixer visible in
   Settings, on-the-fly squiggles work, no Composer detect, no
   format mode, no commit hook.

3. **PHP-CS-Fixer version detection** — `PhpCsFixerVersionValidator`
   wired into the validate button (gap 4.1).

4. **PHP-CS-Fixer Composer auto-detect** —
   `PhpCsFixerComposerOnDetectedHook` wired into the generic
   `ComposerBinarySourceType` (gap 4.5). Replaces
   `PhpCSFixerComposerConfig` entirely.

5. **PHP-CS-Fixer remote** — `<depends optional="true">` on the
   PHP-interpreter source type from `:php` (zero PHP-CS-Fixer code).
   Test the 30-s remote timeout from gap 4.4.

6. **PHP-CS-Fixer dynamic standards combobox** —
   `PhpCsFixerListSetsValuesProvider` wired to the schema (gap 4.16).

7. **PHP-CS-Fixer sentinel UI** — wire `OptionVisibilityRule` so
   `"Custom"` toggles file-picker visibility + risky checkbox enable
   state (gap 4.17).

8. **PHP-CS-Fixer `fix` mode** — declare the second `ToolMode` with
   `executionStyle = "format"`, `formattingOutputMode = "in_place"`.
   `AsyncFormattingServiceAdapter` picks it up automatically. Test
   via Ctrl-Alt-L after the user sets `activeFormatterToolId =
   "php-cs-fixer"` (gap 4.18).

9. **PHP-CS-Fixer quick-fix on annotator** — set
   `message.fixes = listOf(InvokeMode("php-cs-fixer","fix",…))`
   inside the envelope reader; handler from gap 4.15 takes care of
   the rest.

10. **PHP-CS-Fixer format-on-commit** — register the generic
    `FormatOnCommitCheckinHandler` (gap 4.19); plugin-side code is
    zero LOC.

11. **PHP-CS-Fixer migration** — `PhpCsFixerMigration` ports legacy
    `PHPCSFixerSettings`-style XML + the inspection-profile carry-
    over into the unified storage.

12. **PHP-CS-Fixer stderr filter** — small `MessageEnricher` per
    PHPStan gap 4.6 mechanism.

13. **PHP-CS-Fixer inspection-shortname preservation** — verify
    `PhpCSFixerValidation` is emitted by the SDK bridge
    (phase 10a.1).

14. **PHP-CS-Fixer env + workingDir** — register
    `PhpCsFixerEnvMutator` and the working-dir resolver from gap 4.20.

15. **Cleanup**: delete the legacy plugin's classes (~2,750 LOC) once
    the new build is validated (mirror of phase 10c for PHP-CS-Fixer).

---

## 8. Risks / open questions

- **Bundled inside `php.jar`.** PHP-CS-Fixer is not a separately
  versioned plugin (same question as PHPStan §8 — prototype as a
  separate plugin first, upstream later).
- **Mutual exclusion of external formatters is a UX choice, not a
  technical one.** Today only one of PHP-CS-Fixer / phpcbf /
  Laravel Pint can be active. `activeFormatterToolId` (gap 4.18)
  preserves this. A "pipeline of formatters" mode is out of scope.
- **Udiff parser is fragile.** PHP-CS-Fixer's diff is influenced by
  `-vv` verbosity and the precise `--format=udiff` vs
  `--diff-format=udiff` flag across versions. The new `UdiffReader`
  should be at least as tolerant as the legacy parser — emit one
  `internal_error` ToolMessage of `category =
  "php-cs-fixer.diff_parse_error"` rather than failing the run.
- **`runOnTempFiles()` registry-key escape hatch.** Legacy plugin
  defaults to stdin (`fix -`); the `php.cs.fixer.temp.file`
  registry key flips it to a temp file. Today's SDK has
  `ToolMode.supportsStdin`; the registry escape can become a
  per-profile advanced option (or be dropped if all targeted
  PHP-CS-Fixer versions handle stdin correctly).
- **Multiple `--format` flags in the manual reformat invocation**
  (`--format=udiff --format=xml`) work because PHP-CS-Fixer honors
  the last one. The new `buildArgs(mode="fix")` should emit no
  format flag at all (in-place rewrite needs no report). Behavior
  test before shipping.
- **No granular fix-emitter.** Unlike Mago, PHP-CS-Fixer doesn't
  tell us which specific ranges to patch — only which fixers ran.
  The user's only choice is "apply the whole reformat" or
  "ignore"; we don't exercise `Replace` / `Patch` / `DeleteFile` —
  just `InvokeMode` (gap 4.15) and built-in `Ignore`.
- **Coexistence with the platform's PSR-12 formatter.**
  `QualityToolExternalFormatter.runAfter()` returns
  `CoreFormattingService.class` only when advanced-setting
  `php.use.internal.formatter` is on. The new adapter must
  preserve this so users running both get both passes.
- **No batch-mode plumbing exercised.** PHPStan needed phase 8's
  batch cache (gap 4.9); PHP-CS-Fixer does not. Useful negative
  test: confirm the SDK handles a tool with only `on_the_fly` +
  `format` modes and no batch mode.

---

## 9. Summary

- The full PHP-CS-Fixer integration is **~2,750 LOC of Java/Kotlin
  glue today** across 22 classes in two locations (`php.jar` +
  `php-remoteInterpreter.jar`), plus a per-plugin share of the
  ~800 LOC of shared external-formatter / commit-hook plumbing.
- Mapped onto the proposed SDK as-specified, ~80% of those classes
  disappear in favor of generic infrastructure.
- **The first six gaps are shared with PHPStan** and assumed merged
  by the time this port starts. **Eight new gaps surface from this
  exercise** (4.13 – 4.20), all but two of which are direct
  consequences of PHP-CS-Fixer being a fixer rather than a linter:
  - 4.13 unified ignore-policy evaluator,
  - 4.14 generic udiff reader,
  - 4.15 `InvokeMode` ToolFix,
  - 4.16 dynamic enum values,
  - 4.17 sentinel-based visibility rules,
  - 4.18 project-level active-formatter selection,
  - 4.19 generic format-on-commit checkin handler,
  - 4.20 per-mode working-dir resolver.
- Post-gap-fix the PHP-CS-Fixer plugin lands at **~490 LOC** — a
  ~6× reduction.
- The remote-interpreter integration, ignored-files action,
  add-to-blacklist menu, and Composer auto-detect mechanism are
  **all free** (zero plugin code) — every PHP-aware quality tool
  inherits them.
- Recommendation: bundle gaps 4.13 – 4.20 into a "phase 12 hardening"
  PR after the PHPStan port has validated the first six gaps. Port
  PHP-CS-Fixer as the third adopter (after Mago and PHPStan) to
  validate the SDK against a fundamentally different tool shape —
  the fixer-first model that Laravel Pint, phpcbf, and any future
  Rector port will all share.
