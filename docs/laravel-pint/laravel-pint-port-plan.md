# Laravel Pint port to `:quality-tools-sdk` — plan and analysis

> Goal: take the existing JetBrains Laravel Pint plugin (which we
> reverse-engineered) and write down exactly how it would be rebuilt
> on top of our new `:quality-tools-sdk`. No code in this document —
> only the inventory, the mapping, the gaps, and the work plan.
>
> Why this exercise: Laravel Pint is the **smallest** of the bundled
> PHP quality tools — it's an opinionated PHP-CS-Fixer wrapper with
> Laravel-friendly defaults, a JSON config (`pint.json`) instead of a
> PHP one, and a much smaller options surface. Porting it after
> PHP-CS-Fixer is mostly a "shrinkage" exercise: we expect to inherit
> the CS-Fixer port's plumbing (reader, formatter adapter,
> check-in-handler integration) and add a thin Pint-specific layer on
> top. If Pint costs more than ~250 LOC on the new SDK, the SDK has
> regressed.

---

## 0. Reference: source material

Reverse-engineered classes I analysed (CFR 0.152):

- `com.jetbrains.php.tools.quality.laravelPint.*` (17 files,
  ~1,750 LOC including the decompiler's generated `$$$reportNull$$$0`
  null-check methods — real Java glue is closer to ~900 LOC) —
  bundled in the `laravelPint` plugin (id
  `com.intellij.php.tools.quality.laravelPint`).
- `com.jetbrains.php.remote.tools.quality.laravelPint.*` (2 files,
  ~390 LOC) — lives inside `php-remoteInterpreter.jar`'s
  `laravel-pint-remote-plugin.xml` optional config; only loaded when
  `org.jetbrains.plugins.phpstorm-remote-interpreter` is present.
- Shared base classes:
  - `com.jetbrains.php.tools.quality.Quality*` — same legacy SDK
    Pint shares with PHPStan / PHP-CS-Fixer / phpcs / phpmd /
    Psalm.
  - `com.jetbrains.php.tools.quality.phpCSFixer.PhpCSFixerMessageProcessor`
    — Pint's XML message processor **directly extends** this. The
    "applied_fixer" / diff-based message format is identical.
  - `com.jetbrains.php.tools.quality.PhpExternalFormatter*` —
    the radio-button picker in the commit-dialog "Reformat with
    one of {phpcsbf, php-cs-fixer, laravel pint, none}" — Pint
    is one of the three external formatters here.
- `com.jetbrains.php.remote.tools.quality.QualityToolByInterpreter*`
  — same generic remote-interpreter wizard already inventoried in
  the PHPStan plan §1.2.

Other artefacts in `laravelPint.jar`:

- `PhpBundle.properties` entries `quality.tool.laravel.pint.*` —
  about ~10 keys.
- `inspection.html` description.

---

## 1. Inventory: what every class does today

### 1.1. Core "ports" of the legacy SDK

| Class | LOC | Role |
| --- | --- | --- |
| `LaravelPintQualityToolType` | 203 | EP entry point. Inspection id derives from `QualityToolValidationInspection.getShortName()` (`"Laravel_Pint_validation_tool"`). Wires up managers, blacklist, configurable. |
| `LaravelPintConfiguration` | 108 | Per-instance config: `myLaravelPintPath`, `myTimeoutMs=5000` (note: **5s** vs PHPStan's 30s), `getId()="local"`. No interpreter id. `getMaxMessagesPerFile()=50`. |
| `LaravelPintConfigurationManager` | 62 | Glue between project and app `LaravelPintConfigurationBaseManager`s. Two `@State` services named `LaravelPint` in `php.xml` / `php-tools.xml`. |
| `LaravelPintConfigurationBaseManager` | 60 | `PersistentStateComponent<Element>` with root `<laravel_pint_settings>`, old-style tool-path attr `laravelPintPath`. |
| `LaravelPintConfigurationProvider` | 31 | Abstract — `EP_NAME = "com.jetbrains.php.tools.quality.laravelPint.laravelPintConfigurationProvider"`. Identical shape to `PhpStanConfigurationProvider`. |
| `LaravelPintProjectConfiguration` | 54 | Project state with `selectedConfigurationId`. |
| `LaravelPintBlackList` | 32 | `QualityToolBlackList` subclass — list of absolute paths to skip. |
| `LaravelPintValidationInspection` | 33 | Empty subclass of `QualityToolValidationInspection`. ShortName `Laravel_Pint_validation_tool` (note: NOT `LaravelPintValidation` — historical underscore form). |
| `LaravelPintAnnotatorProxy` | 156 | `QualityToolAnnotator<LaravelPintValidationInspection>`. Builds args from `LaravelPintOptionsConfiguration`. Always appends `--test --format=xml -vvv`. Has the `addPresetOption` predicate that skips `--preset=...` when the user picked `"defined in pint.json"`. |
| `LaravelPintXmlMessageProcessor` | 58 | **Extends** `PhpCSFixerMessageProcessor` (not `QualityToolXmlMessageProcessor` directly). Overrides `processStdErrMessages` to drop one specific Laravel benign message ("`[OK] Your system is ready to run the application.`") and routes quick-fix to `LaravelPintReformatFileAction`. |
| `LaravelPintComposerConfig` | 221 | `QualityToolsComposerConfig`. Reads `composer.json`, picks `vendor/bin/pint` (`bin/pint.bat` on Windows), applies `pint.json` ruleset, parses `scripts.pint` for `--preset=`/`--config=`/`--dirty` switches. |
| `LaravelPintConfigurable` | 104 | Settings configurable. Lives under `Settings/PHP/Quality Tools/Laravel Pint`. Help topic `reference.settings.php.laravel.pint`. |
| `LaravelPintConfigurableForm` | 106 | `QualityToolConfigurableForm` subclass — adds version parsing regex `Pint.* ([\d.]*)`. Tool nickname `"pint"`. |
| `LaravelPintOptionsPanel` | 247 | Kotlin UI-DSL panel. Three widgets only: (1) "Reformat only uncommitted files" checkbox, (2) `pint.json` path picker with `.json` extension validation, (3) **4-item** preset combobox (`laravel`, `symfony`, `psr12`, `defined in pint.json`). |
| `LaravelPintReformatFile` | 138 | `QualityToolReformatFile`. The reformat action's command builder: re-uses `LaravelPintAnnotatorProxy.getCommandLineOptions(project, paths)` then strips the trailing `--test --format=xml -vvv` (it iterates `options.size() - 1`). |
| `LaravelPintReformatFileAction` | 106 | `QualityToolReformatFileAction<LaravelPintValidationInspection>`. The "Reformat file with Laravel Pint" IDE action. |

**Total bundled: ~1,750 LOC of Java/Kotlin (most lines from the
decompiler's `reportNull` boilerplate; real glue ~900 LOC).**

### 1.2. Remote interpreter glue

In `laravelPint.jar` → `laravel-pint-remote-plugin.xml` optional
config:

| Class | LOC | Role |
| --- | --- | --- |
| `LaravelPintRemoteConfiguration` | 169 | Subclass of `LaravelPintConfiguration` that adds `interpreterId`, `PhpSdkDependentConfiguration`. `<Tag("laravel_pint_by_interpreter")>` for XML. `getId()="DEFAULT_INTERPRETER"` when default. |
| `LaravelPintRemoteConfigurationProvider` | 219 | Registered on `com.jetbrains.php.tools.quality.laravelPint.laravelPintConfigurationProvider` EP. Provides `createNewInstance` (opens the by-interpreter dialog), `createConfigurationByInterpreter`, and **bumps timeout to 30 s** in `fillSettingsByDefaultValue` (vs Pint's 5 s local default). |

Shared classes in `php-remoteInterpreter.jar` (already inventoried
under PHPStan §1.2 — `QualityToolByInterpreterDialog`,
`QualityToolByInterpreterConfigurableForm`,
`QualityRemoteToolProcessHandler`).

### 1.3. Plugin metadata

`META-INF/plugin.xml`:

- `<depends>com.jetbrains.php</depends>`
- `<depends>com.intellij.modules.ultimate</depends>` (PhpStorm-only).
- `<depends optional="true" config-file="laravel-pint-remote-plugin.xml">org.jetbrains.plugins.phpstorm-remote-interpreter</depends>`.
- 4 services + 1 inspection (`localInspection Laravel_Pint_validation_tool`)
  + 1 externalAnnotator + 1 configurable + 1 composerConfigClient + 1
  type + 1 quality-tool reformat action + (transitively, via the
  base PHP plugin) participation in `PhpExternalFormatterPanel` and
  `PhpExternalFormatterCheckinHandler`.

Note vs PHPStan: Pint has **no** `globalInspection`, **no**
`postStartupActivity` (no historical fields-on-inspection
migration), **no** `completion.contributor`, and **no**
`openSettingsProvider`. Pint runs only as a local annotator
(on-the-fly + reformat), never as a `Code → Inspect Code…` batch
inspection.

---

## 2. Functional surface (what the user sees)

### 2.1. User-facing features

1. **Settings page** at `PHP / Quality Tools / Laravel Pint`:
   - List of profiles (local + per-interpreter), add/remove/edit.
   - Per-profile: tool path, validate button (parses
     `pint --version` → `Pint 1.13.6`), timeout in seconds (default
     5s local, 30s remote), max messages per file.
   - Common options: `pint.json` path picker (validates `.json`
     suffix), preset combobox (`laravel` / `symfony` / `psr12` /
     `defined in pint.json`), reformat-only-uncommitted-files
     checkbox.
2. **Inspection profile entry**: `Laravel_Pint_validation_tool`
   (local only). User can change severity, scope, enable/disable.
3. **On-the-fly analysis** while editing a `.php` file: shows
   "Laravel Pint: <fixer-name>" warnings highlighted on the lines
   the fixer would touch. Quick-fix → "Reformat file with Laravel
   Pint".
4. **Reformat-file action** in editor right-click → `Reformat with
   → Laravel Pint`. Calls Pint without `--test` to mutate the file
   in place.
5. **Format-on-commit**: Pint is one of the three radio choices in
   `Settings → Version Control → Commit → Run external formatter`
   (radio group with PHP CS Beautifier / PHP CS Fixer / Laravel
   Pint / None). Same code path as PHP-CS-Fixer.
6. **Composer auto-detect**: when `vendor/bin/pint` appears, the
   IDE auto-configures the tool path, reads `pint.json` next to
   `composer.json` for `--config=`, and parses `composer.json`
   `scripts.pint` for `--preset=…` / `--config=…` / `--dirty`.
7. **"Add to ignored" action** in the right-click menu on any
   Pint warning — appends to `LaravelPintBlackList`.
8. **Notifications** when Pint fails (binary missing, JSON syntax
   error, etc.).
9. **Remote PHP interpreter support** (Docker/SSH/WSL):
    - Picking an interpreter in the by-interpreter dialog auto-maps
      `bin/pint` to the remote path.
    - Path mapping for `--config=<pint.json>` passed as a CLI arg.
    - 30-second default timeout for remote runs.

### 2.2. Internal plumbing (collapsed by the new SDK)

Same as PHPStan §2.2 — manager-pair, three configuration objects,
`QualityToolByInterpreterDialog`, `QualityRemoteToolProcessHandler`,
manual XML SAX parsing. Pint additionally drags in the
`PhpExternalFormatterPanel` radio-button entry and a separate
`Reformat with Pint` action that re-uses the on-the-fly arg builder
but strips `--test --format=xml -vvv`. Both collapse into the
unified `format`-mode story (phase 07 `AsyncFormattingServiceAdapter`
plus the `executionStyle = "format"` mode on `LaravelPintTool`).

---

## 3. Mapping each feature to the new SDK

Below: for each user-visible feature, which `:quality-tools-sdk`
phase / artefact handles it.

| Legacy feature | New SDK home | Notes |
| --- | --- | --- |
| `LaravelPintQualityToolType` (registration) | `QualityTool` (phase 01) — one Kotlin class. | Same EP `dev.jplugins.qualityTools.tool`. Preserve inspection short-name `Laravel_Pint_validation_tool` via phase 10a.1's `inspectionShortNames` set (literal value, NOT auto-derived — see gap §4.4). |
| `LaravelPintConfiguration` (tool path, 5s timeout) | `LocalBinarySource` (phase 02) + `ConfigProfile.timeoutMs` (phase 04). | Same as PHPStan §3, except default seeds from `LocalBinarySourceType.defaultTimeoutMs = 5_000` (gap §4 already opened in the PHPStan plan §4.4). |
| `LaravelPintRemoteConfiguration` (interpreter id) | `PhpInterpreterBinarySource` in `:php` (phase 02). | Generic — same class serves PHPStan / PHP-CS-Fixer / Pint. Per-source-type `defaultTimeoutMs = 30_000` (PHPStan §4.4). |
| `LaravelPintConfigurationManager` + `*BaseManager` (2 services) | Unified `QualityToolsProjectStorage` (phase 04). | Same as PHPStan §3. |
| `LaravelPintProjectConfiguration.selectedConfigurationId` | `QualityToolsProjectStorage.activeProfileId("laravel-pint","analyze")`. | Same as PHPStan §3. |
| `LaravelPintConfigurationProvider` EP | `ConfigSourceType` EP (phase 02). | Same as PHPStan §3. |
| `LaravelPintRemoteConfigurationProvider` | `PhpInterpreterBinarySourceType` in `:php`. | Same as PHPStan §3. |
| `LaravelPintBlackList` | `GlobPathIgnorePolicy` (phase 06). | Same as PHPStan §3. |
| `LaravelPintValidationInspection` (local only — no global) | Phase 10a.1 inspection-shortname preservation. | Single shortname `Laravel_Pint_validation_tool` registered as a `localInspection`. No batch counterpart — `LaravelPintTool.modes` contains exactly one `analyze`-style mode at `executionStyle = "on_the_fly"`, plus a separate `format` mode (see below). |
| `LaravelPintAnnotatorProxy` | `QualityToolsAnnotator` + `LaravelPintTool.buildArgs` (phase 01/08). | Same as PHPStan §3. ~150 LOC drops; we keep ≤ 25 LOC of arg-building. |
| `LaravelPintXmlMessageProcessor` (extends CS-Fixer's diff-XML reader) | `PhpCsFixerDiffXmlReader` from PHP-CS-Fixer plan (a bundled `ResultReader` in `:php`) + a `PintStderrFilter` `MessageEnricher` to drop the `[OK] Your system is ready to run the application.` line. | The CS-Fixer plan introduces a reusable reader for the `<report><file><applied_fixer .../>...</file></report>` + unified-diff payload. Pint emits the **same** XML format and reuses the reader unchanged. **See PHP-CS-Fixer plan §3 — to be filled when sibling plan lands** for the reader name. |
| `LaravelPintReformatFile` + `LaravelPintReformatFileAction` (the "Reformat with Pint" intention) | `executionStyle = "format"` mode on `LaravelPintTool` + the auto-registered `AsyncFormattingServiceAdapter` from phase 07. | `formattingOutputMode = "in_place"` because Pint rewrites the file on disk (no stdout output without `--test`). Quick-fix on each annotation routes through `PostFixHook` "format-after-fix" (phase 08) — same wiring CS-Fixer uses. |
| `LaravelPintComposerConfig` | `ComposerBinarySourceType` in `:php` (composer-from-vendor source) + a `LaravelPintComposerOnDetectedHook` (phase 02 `OnDetectedHook` — gap §4.5 from PHPStan plan). | Pint's hook is structurally identical to CS-Fixer's; the regex set differs (`--preset` allow-list of {laravel,symfony,psr12}; `--config`; `--dirty`). **Same as PHP-CS-Fixer plan §3** — and shares the gap fix. |
| `LaravelPintConfigurable` + `LaravelPintConfigurableForm` + `LaravelPintOptionsPanel` | `LaravelPintOptionsSchema` + `AutoToolSettingsPanel` (phase 04 + 07). | Three widgets → three `OptionSpec`s. Zero custom Swing code. |
| `LaravelPintOptionsConfiguration` (project state) | Subsumed by `OptionsBag` in the unified storage (phase 04). | No separate `@State` service. |
| `LaravelPintReformatFileAction` quick-fix factory in `LaravelPintXmlMessageProcessor.getQuickFix` | `PostFixHook` "FormatAfterAnyFix" + the SDK-bundled `ApplyMode` quick-fix wiring in `:ui`. | Quick-fix entry on each annotation runs the `format` mode of `LaravelPintTool` over the current file. Generic — same as CS-Fixer. |
| `PhpExternalFormatterPanel` Pint radio button + `PhpExternalFormatterCheckinHandler` Pint branch | A `:php`-side `CommitFormatterRegistry` populated by every tool whose `LaravelPintTool` declares `capabilities += "format"` and registers a `format` mode. | The check-in handler iterates registered format-capable tools and renders one radio per tool, with a "None" sentinel. **Same as PHP-CS-Fixer plan §3 — to be filled when sibling plan lands** for the registry name / EP id. |
| Validate-button (`pint --version` parsing → "Pint X.Y.Z") | `LaravelPintVersionValidator` via `BinaryValidator` SAM on `QualityTool` (PHPStan plan §4.1 — gap). | Same shape as PHPStan; regex `Pint.* ([\d.]*)`. |
| 30-second default timeout for remote profiles | `PhpInterpreterBinarySourceType.defaultTimeoutMs` (PHPStan §4.4). | Already covered by the PHPStan gap fix. |
| "Suppress one specific stderr line" (`[OK] Your system is ready…`) | `MessageEnricher` matching `category="laravel-pint.stderr_filter"` that drops the message — OR a `StderrFilter` per PHPStan §4.6. | (a) suffices; one `LaravelPintStderrFilterEnricher` (~15 LOC). |
| Strip `--test --format=xml -vvv` for reformat-vs-annotate | Mode-level `defaultArgs` on `LaravelPintTool` — `analyze` mode appends them, `format` mode doesn't. | No bespoke argv-truncation logic. |

---

## 4. Gaps in the new SDK exposed by this exercise

Pint piggybacks on the PHPStan plan's gap list and the (in-flight)
PHP-CS-Fixer plan's gap list. Almost every gap below is already
opened by one of the sibling plans; we only call out what's
*newly* visible from Pint.

### 4.1. `BinaryValidator` for "click to verify version"

**Same as PHPStan §4.1.** Pint regex is `Pint.* ([\d.]*)`; expected
output looks like `Pint 1.13.6 by Nuno Maduro and Laravel.`. No
incremental SDK work — `LaravelPintVersionValidator` is one of
several validators the SDK already accommodates.

### 4.2. Multiple readers per tool

**Same as PHPStan §4.2.** Pint always uses the CS-Fixer diff-XML
reader; we don't need per-mode reader selection here, but the field
move is still desirable for forward compatibility with `pint
--format=json` (added in Pint 1.14+).

### 4.3. Tool-version-aware command-line construction

**Same as PHPStan §4.3.** Pint added `--dirty` in v1.1, `--bail` in
v1.13, etc. The plugin currently doesn't gate by version; we don't
need to either to reach feature parity, but the SDK affordance from
PHPStan §4.3 is the right home for future work.

### 4.4. Inspection short-name with non-PascalCase format

**New Pint-specific signal.** Pint's inspection short-name is
`Laravel_Pint_validation_tool` (snake-case with capitalised `Laravel`
and `Pint`). The default derivation in phase 01
(`"${id.capitalize()}${mode.id.capitalize()}"`) would produce
`LaravelPintAnalyze` — wrong, would orphan every user's existing
inspection profile.

**Recommendation:** Phase 10a.1 already allows overriding
`inspectionShortNames`; spell out explicitly in the phase 01
deliverables that the override accepts **arbitrary** strings (not
just "Tool" + "Mode" combos). One acceptance bullet, no code change.
Already implied — but Pint is the proof-case.

### 4.5. `ConfigSourceType.defaultTimeoutMs` override

**Same as PHPStan §4.4.** Pint's local default is **5 s** (vs
PHPStan's 30 s and PHP-CS-Fixer's 30 s — Pint is genuinely faster
because it processes a smaller set of fixers). The gap fix permits
each `ConfigSourceType` (including the bundled `LocalBinarySource`
when sub-classed per tool) to override the seed timeout.

### 4.6. Composer auto-detect "enrich the options from pint.json /
       scripts.pint"

**Same as PHPStan §4.5.** The `OnDetectedHook` SAM proposed there
applies verbatim to Pint. Pint-specific contents:

1. Look for `pint.json` next to `composer.json` → set the
   `pintJsonPath` option.
2. Parse `composer.json` `scripts.pint` for `--preset=<one of
   {laravel,symfony,psr12}>` → set the `preset` option.
3. Parse `--config=<path>` from `scripts.pint` → set
   `pintJsonPath`.
4. Parse `--dirty` from `scripts.pint` → set
   `reformatOnlyUncommittedFiles`.

(All four are PHP-Pint-specific code living in
`LaravelPintComposerOnDetectedHook`, ~50 LOC. The SDK exposes only
the hook.)

### 4.7. Stderr filtering / muting specific tool warnings

**Same as PHPStan §4.6.** Pint emits
`[OK] Your system is ready to run the application.` on every successful
run (from the Symfony console banner). One `MessageEnricher` drops
it. **No SDK change.**

### 4.8. Per-tool default ignore-policy registration

**Same as PHPStan §4.7.** Pint inherits the auto-registered "Add to
ignored" right-click menu item from `GlobPathIgnorePolicy`.

### 4.9. `executionStyle = "format"` + `formattingOutputMode = "in_place"`

**Already in phase 01/07** — but Pint is the **proof** that
`in_place` works for tools that rewrite on disk and emit nothing
useful to stdout. The PHP-CS-Fixer plan exercises the same path.

`see PHP-CS-Fixer plan §X — to be filled when sibling plan lands`
for the test fixture / behaviour spec covering `in_place` formatters.

### 4.10. Commit-dialog "external formatter" picker

**New from Pint+CS-Fixer.** The legacy `PhpExternalFormatterPanel` is
a radio group hard-wired to `{phpcsbf, php-cs-fixer, laravel-pint,
none}`. Generalising:

A `:php`-side `CommitFormatterRegistry` reads every registered
`QualityTool` with a `format` mode and `"PHP" in
supportedLanguageIds`; the radio group renders one radio per tool
plus "None". Selection persists into
`QualityToolsProjectStorage.activeProfileId("<toolId>", "format")` —
i.e. exact same storage as everywhere else, just a different reader.

**See PHP-CS-Fixer plan §X — to be filled when sibling plan lands**
for the registry / EP id. Pint adds zero further requirements.

### 4.11. JSON config file (`pint.json`) vs PHP config file
        (`.php-cs-fixer.dist.php`)

The only **truly Pint-unique** wrinkle. PHP-CS-Fixer's config is a
PHP file that returns a `PhpCsFixer\Config` object — the legacy
plugin has a `PhpCSFixerRulesetAnalyzer` that *runs PHP* to extract
the rule names. Pint's config is plain JSON, so there's nothing to
"analyze" — just a path on disk that the plugin forwards as
`--config=<path>`.

**Implication for the SDK:** none. `PathSpec(role="config_file",
extensionsFilter="json")` covers it. The `pint.json` schema (the
keys allowed inside the JSON) is the tool's business, not the
SDK's. The validation in `LaravelPintOptionsPanel` ("file must end
in `.json`") becomes a `PathFilter.JsonOnly` predicate on the
`PathSpec` — no Pint-specific UI code.

The downstream consequence is the auto-detect hook (§4.6) is
**simpler** for Pint than for CS-Fixer: no need to invoke PHP to
read the config, just discover the file path. CS-Fixer's hook is
inherently heavier and will need the spawner; Pint's is pure
file-system discovery.

### 4.12. No `globalInspection`, no `postStartupActivity`

Pint never had a batch-mode counterpart and never had options
stored as inspection-profile fields. So there is **no settings-
transfer activity to migrate** (PHPStan §4.8 doesn't apply). The
phase 10a migration code for Pint reads only:

- `<application>/<component name="LaravelPint">` (legacy app-level
  config).
- `<project>/<component name="LaravelPint">` (legacy project-level
  config).
- `<project>/<component name="LaravelPintOptionsConfiguration">`
  (the ruleset + pint.json + dirty options).
- `<project>/<component name="LaravelPintProjectConfiguration">`
  (selectedConfigurationId).
- `<workspace>/<component name="LaravelPintBlackList">` (paths).
- `<application/project>/<component
  name="LaravelPintRemoteConfiguration">` if the remote plugin was
  enabled — XML tag `laravel_pint_by_interpreter`.

All flat data, no five-fields-on-inspection mess. Migration is one
`Migrator` impl, ~50 LOC.

### 4.13. Format-after-fix uses the same tool

Pint's quick-fix is *itself* a format-mode invocation of Pint over
the same file. Phase 08's `PostFixHook` already handles this — but
there's a circularity risk: the quick-fix triggers `format`, which
re-runs `analyze` (because the annotator restarts), which emits the
same quick-fix on un-fixed lines. The legacy plugin handles this
implicitly because the second annotator run finds no fixers to
apply (file already formatted).

**Recommendation:** Spell out in phase 08 acceptance criteria that
`PostFixHook` does NOT cause the `analyze` mode to re-emit
`format`-targeted fixes in an unbounded loop. The hook is
fire-and-forget; the annotator restarts on its own
(`DaemonCodeAnalyzer.restart()`) and re-evaluates against the new
content. **Acceptance bullet, no code change.**

---

## 5. Generic-code overhead — what we'd write if we ported now

Following the gap list, if we shipped the Pint port today with the
SDK *as currently specified in phases 00-10 plus the PHPStan and
CS-Fixer gap patches*, we'd write the following. Marked ⚠ are
items that would duplicate generic plumbing if the gap isn't fixed.

| Code we'd write in the Pint port | Generic? | Action |
| --- | --- | --- |
| `LaravelPintTool : QualityTool` | unique | keep |
| `LaravelPintOptionsSchema : OptionsSchema` | unique | keep |
| `LaravelPintComposerOnDetectedHook` | unique | keep (needs §4.6) |
| `LaravelPintStderrFilterEnricher` | unique | keep |
| `LaravelPintVersionValidator : BinaryValidator` | unique | keep (needs §4.1) |
| `LaravelPintMigration : Migrator` | unique | keep |
| ⚠ Custom Swing in version-validate button | generic | resolve via §4.1 |
| ⚠ Default-timeout-5s for local source | generic | resolve via §4.5 |
| ⚠ "On detected, enrich options" callback wiring | generic | resolve via §4.6 |
| ⚠ "External formatter picker" radio group | generic | resolve via §4.10 |
| ⚠ Per-tool right-click "Add to ignored" action class | generic | resolve via §4.8 |

Five ⚠ items, all already opened by the PHPStan / CS-Fixer plans.
**Pint introduces zero new SDK gaps** beyond what those plans already
demand. Confirmation that the SDK after the PHPStan + CS-Fixer
porting passes is "complete enough" for Pint.

---

## 6. Concrete file list for the Pint port (post-gap-fix)

Assuming gaps in §4 (mostly inherited from PHPStan / CS-Fixer plans)
are merged into the SDK, the Pint plugin shrinks to roughly:

**Required**:

- `LaravelPintTool.kt` (~70 LOC) — id `"laravel-pint"`, modes
  (`analyze` on-the-fly, `format` in-place), capabilities, options
  schema, `buildArgs` (just appends `--config=` / `--preset=` /
  `--dirty` and, for `analyze`, `--test --format=xml -vvv`).
- `LaravelPintOptionsSchema.kt` (~30 LOC) — three specs:
  `pintJsonPath: PathSpec(extensionsFilter="json")`,
  `preset: ChoiceSpec(values=["laravel","symfony","psr12","defined in pint.json"])`,
  `reformatOnlyUncommittedFiles: BoolSpec(default=false)`. Plus
  `inspectionShortNames = setOf("Laravel_Pint_validation_tool")`.
- `LaravelPintVersionValidator.kt` (~25 LOC) — runs `--version`,
  regex parses, returns ok/version.
- `LaravelPintComposerOnDetectedHook.kt` (~50 LOC) — `pint.json`
  discovery + `composer.json scripts.pint` preset/config/dirty
  parsing.
- `LaravelPintStderrFilterEnricher.kt` (~15 LOC) — drops the
  `[OK] Your system is ready to run the application.` line.
- `LaravelPintMigration.kt` (~50 LOC) — legacy XML → new storage
  (no startup-activity layer to migrate).
- `META-INF/plugin.xml` (~30 LOC) — registrations.

**Optional** (i18n / docs):

- `LaravelPintBundle.properties` — ~10 keys.
- `inspection.html` — description for the inspection.

**Total: ~270 LOC + bundle/HTML**, vs. ~1,750 LOC (decompiled, ~900
LOC of real Java) today. **~3–6× reduction** depending on whether
you count the decompiler's `reportNull` boilerplate.

(The ratio is smaller than PHPStan's because Pint was already simple
to begin with — it never had the global-inspection or options-on-
profile machinery to drop.)

---

## 7. Order of work (when we get to coding)

Sequenced so each step is mergeable independently and **after** the
PHPStan and PHP-CS-Fixer ports have validated the SDK + opened the
gaps. Numbered against the existing phase-doc plan.

1. **Wait for PHPStan + PHP-CS-Fixer gap fixes** to land (phases 01,
   02, 07, 08 patches from PHPStan §4 and CS-Fixer §X — see PHP-CS-
   Fixer plan §7, to be filled when sibling plan lands). No Pint
   work happens before this — Pint is meant to validate that the
   gap list is complete, not to discover more.

2. **Pint tool registration** — minimal port: `LaravelPintTool`
   (analyze mode only) + `LaravelPintOptionsSchema` + reuse the
   `PhpCsFixerDiffXmlReader` from CS-Fixer's port. Result: Pint
   visible in Settings, on-the-fly works, but no validate button,
   no Composer auto-detect, no remote, no formatter mode.

3. **Pint format mode** — add the `format` mode with
   `formattingOutputMode = "in_place"` and verify
   `AsyncFormattingServiceAdapter` invokes Pint without `--test`.
   This is the "reformat with Pint" intention restoration.

4. **Pint version detection** — `LaravelPintVersionValidator` wired
   into the validate button (PHPStan §4.1).

5. **Pint Composer auto-detect** —
   `LaravelPintComposerOnDetectedHook` wired into the generic
   `ComposerBinarySourceType` (PHPStan §4.5 / Pint §4.6). Replaces
   `LaravelPintComposerConfig` entirely.

6. **Pint remote** — `<depends optional="true">` on the new PHP-
   interpreter source type from `:php`. Zero Pint code. Verify
   `defaultTimeoutMs = 30_000` is applied (PHPStan §4.4 fix).

7. **Pint migration** — `LaravelPintMigration` reads the seven
   legacy XML containers listed in §4.12 into the unified storage.

8. **Pint stderr filter** — small `MessageEnricher` per §4.7.

9. **Commit-dialog formatter picker** — verify Pint shows up in the
   new `CommitFormatterRegistry` alongside CS-Fixer (PHP-CS-Fixer
   plan §X — to be filled when sibling plan lands). Pint
   contributes zero code; the registry just sees Pint because Pint
   has a `format` mode.

10. **Pint inspection-shortname preservation** — verify
    `Laravel_Pint_validation_tool` is emitted by the SDK bridge
    (phase 10a.1) and existing user profiles keep their settings.

11. **Cleanup**: delete the legacy plugin's 17+2 classes once the
    new build is validated (mirror of phase 10c for Pint).

---

## 8. Risks / open questions

- **Bundled vs separate plugin**: same as PHPStan §8 — start as a
  separate community plugin, upstream once Mago + PHPStan have
  validated the SDK end-to-end.

- **Inspection short-name with underscore casing**:
  `Laravel_Pint_validation_tool` is the historical short-name. The
  SDK MUST allow arbitrary strings here (§4.4); the default
  `${id.capitalize()}${mode.id.capitalize()}` derivation would
  break user profiles. Pint is the canonical test case for this.

- **"defined in pint.json" sentinel value**: the legacy
  `addPresetOption` predicate skips `--preset=` if and only if the
  combobox value is literally `"defined in pint.json"`. We must
  preserve this string verbatim in the migrated `OptionsBag` and in
  `buildArgs`. Recommend a constant in `LaravelPintOptionsSchema`
  (`val PRESET_DEFINED_IN_PINT_JSON = "defined in pint.json"`)
  rather than scattering the magic string. Migration MUST map old
  storage values 1:1.

- **`--test --format=xml -vvv` is mode-locked**: these three args
  are appended *only* in `analyze` mode, not `format` mode. The
  legacy code in `LaravelPintReformatFile.fillArguments` strips
  them by indexing `options.size() - 1` — fragile. The SDK's
  per-mode `defaultArgs` is cleaner; flag this in the port test
  (run Pint in `format` mode, assert argv contains neither
  `--test` nor `--format=xml`).

- **Annotation ranges from unified diff**: `LaravelPintXmlMessageProcessor`
  inherits from `PhpCSFixerMessageProcessor` and reuses its
  `MultiLineXMLMessageHandler` which parses unified diff hunks
  (`@@ -X,Y +A,B @@`). Confirm the SDK's
  `PhpCsFixerDiffXmlReader` (CS-Fixer plan §X — to be filled when
  sibling plan lands) handles both:
    1. `<applied_fixer name="...">` aggregation (Pint emits one or
       more fixer names per `<file>`).
    2. The byte→char offset conversion noted in phase 06 (Pint
       output is UTF-8 byte offsets in the diff).

- **`pint.json` schema validation**: out of scope. The SDK takes
  the path, forwards it as `--config=`, and lets Pint complain on
  stderr if the JSON is malformed. (Phase 06's `parse_error`
  message category handles surfacing the complaint.)

- **No project-wide / batch mode**: unlike PHPStan, Pint has no
  `Code → Inspect Code` integration today. We *could* add one
  (Pint supports `pint --test path/to/folder`), but it's a new
  feature, not a port. Out of scope.

- **No fix-emitter quick-fixes (other than "reformat file")**:
  Pint's only quick-fix is "reformat the whole file with Pint" —
  there is no per-fixer "apply just this rule" affordance because
  Pint can't be told to apply a single fixer. We won't exercise
  per-message `ReplaceFix` or `PatchFix` round-trips on this port;
  Mago and CS-Fixer carry that load.

- **Always-on Laravel preset**: when the user picks "defined in
  pint.json" and the `pint.json` file is missing/empty, Pint
  silently falls back to its built-in Laravel preset. The plugin
  shouldn't add `--preset=laravel` in that case (it doesn't today),
  but the message-processor should NOT treat the Laravel fixers
  applied in that scenario as "stale preset" — they're authentic.
  Recommendation: just don't second-guess what Pint applied; let
  the `applied_fixer` names through verbatim. Already correct in
  the legacy code.

---

## 9. Summary

- The full Laravel Pint integration is **~1,750 LOC decompiled
  (~900 LOC real glue)** across 19 classes in two jars.
- Mapped onto the SDK as-specified plus the PHPStan §4 and
  PHP-CS-Fixer §X (to be filled when sibling plan lands) gap
  patches, ~85% of those classes disappear in favour of generic
  infrastructure.
- **Pint exposes zero new SDK gaps** beyond what PHPStan and
  PHP-CS-Fixer already opened. It surfaces one acceptance bullet
  (§4.4: arbitrary inspection short-names accepted) and one
  pedagogical example (§4.13: PostFixHook doesn't cause restart
  storms).
- The remote-interpreter integration becomes free (zero Pint
  code), same as PHPStan §9.
- The `pint.json` JSON-config wrinkle (vs CS-Fixer's PHP-returning-
  Config-object) actually makes the Composer auto-detect hook
  *simpler* for Pint, not harder — `PathSpec` plus a JSON
  extension filter is the whole story.
- The "Pint as an option in the commit-dialog external formatter
  picker" feature is solved by the same `CommitFormatterRegistry`
  the PHP-CS-Fixer port introduces; Pint contributes nothing
  beyond having a `format` mode.
- Post-gap-fix the Pint plugin lands at **~270 LOC** — a 3–6×
  reduction depending on how you count decompiled boilerplate.
- **Recommendation**: port Pint as the **third** adopter (after
  Mago and PHPStan, in parallel with or just after PHP-CS-Fixer).
  Its job is to *validate* that the SDK is complete enough for
  the cheap and obvious case. If Pint costs more than ~270 LOC,
  the SDK has regressed somewhere and we re-open the gap list.
