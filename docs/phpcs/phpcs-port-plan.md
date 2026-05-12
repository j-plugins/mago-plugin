# PHP_CodeSniffer port to `:quality-tools-sdk` — plan and analysis

> Goal: take the existing JetBrains PHP_CodeSniffer (phpcs) plugin
> and write down exactly how it would be rebuilt on top of the new
> `:quality-tools-sdk`. We don't write code in this document — only
> the inventory, the mapping, the gaps, and the work plan.
> Implementation comes later, once the gaps are resolved.
>
> Why this exercise after PHPStan: phpcs exercises **two** features
> PHPStan does not: a second binary (`phpcbf`) acting as a formatter,
> and a rich "coding standard" picker that lists installed standards
> at runtime (PSR1/PSR2/PSR12/Symfony/PEAR/MySource/Zend + Composer-
> contributed ones like Doctrine/Drupal/WordPress/Yii2/CakePHP/etc).
> If the SDK can express that without growing a new "second binary"
> primitive, the runner/options/source model is solid.

---

## 0. Reference: source material

Reverse-engineered classes I analysed (CFR 0.152):

- `com.jetbrains.php.tools.quality.phpcs.*` (22 files, ~3,200 LOC)
  — bundled in the `phpcs` plugin (id
  `com.intellij.php.tools.quality.phpcs`).
- `com.jetbrains.php.remote.tools.quality.phpcs.*` (3 files, ~480
  LOC) — also inside `phpcs.jar`, registered via an optional config
  when `org.jetbrains.plugins.phpstorm-remote-interpreter` is
  present.
- `com.jetbrains.php.remote.tools.quality.*` (3 base classes, ~560
  LOC) — same shared remote-interpreter glue as in
  PHPStan §0; see "same as PHPStan §1.2".
- `com.jetbrains.php.tools.quality.QualityToolReformatFile*` (586
  LOC) — legacy SDK base for "external-formatter style" actions;
  PHPStan does not exercise this codepath, phpcs does.

Other artefacts in the legacy jar:

- `PhpBundle.properties` keys under `quality.tool.phpcs.*` and
  `quality.tool.phpcbf.*` — i18n.
- `inspection.html` description.
- `phpcs-remote-plugin.xml` — same pattern as PHPStan: optional
  config-file injecting `PhpCSRemoteConfigurationProvider`.

---

## 1. Inventory: what every class does today

### 1.1. Core "ports" of the legacy SDK

| Class | LOC | Role |
| --- | --- | --- |
| `PhpCSQualityToolType` | 206 | EP entry point (`com.jetbrains.php.tools.quality.type`). Returns `displayName="PHP_CodeSniffer"`, `helpTopic="reference.settings.php.codesniffer"`, wires managers / blacklist / configurable. Same shape as PHPStan §1.1. |
| `PhpCSConfiguration` | 222 | The per-instance config. **Two binary paths**: `myPhpCodeSnifferPath` (phpcs) AND `myPhpCodeBeautifierPath` (phpcbf). Plus `myStandards` (a `;`-joined cache of installed standards), `myTimeoutMs=5000` (note: 5s, smaller than PHPStan's 30s), `myMaxMessagesPerFile=50`. |
| `PhpCSConfigurationManager` | 65 | Same shape as PHPStan §1.1. |
| `PhpCSConfigurationBaseManager` | 62 | Same shape as PHPStan §1.1. |
| `PhpCSConfigurationProvider` | 81 | Abstract — EP `com.jetbrains.php.tools.quality.PhpCS.PhpCSConfigurationProvider`. Same shape as PHPStan §1.1. |
| `PhpCSProjectConfiguration` | 56 | Same shape as PHPStan §1.1. |
| `PhpCSBlackList` | 23 | Same as PHPStan §1.1. `@State("PHPCodeSnifferBlackList")`. |
| `PhpCSValidationInspection` | 57 | `QualityToolValidationInspection` subclass. ShortName `PhpCSValidationInspection`. Stores **eight** public legacy fields (`IGNORE_WARNINGS`, `CODING_STANDARD="PSR2"`, `CUSTOM_RULESET_PATH`, `WARNING_HIGHLIGHT_LEVEL_NAME`, `SHOW_SNIFF_NAMES`, `USE_INSTALLED_PATHS`, `INSTALLED_PATHS`, `EXTENSIONS="php,js,css,inc"`). There is no `PhpCSGlobalInspection` separate from validation — phpcs is on-the-fly only. |
| `PhpCSOptionsConfiguration` | 153 | The modern project-level options store (`@State("PHPCodeSnifferOptionsConfiguration")`). Same eight fields, but now in `php.xml`. `getWarningLevel()` resolves the severity name to a `HighlightDisplayLevel`. |
| `PhpCSSettingsTransferStartupActivity` | 122 | One-shot migration copying the eight legacy fields off `PhpCSValidationInspection` (the profile-XML store) into `PhpCSOptionsConfiguration` (the project-state store). Same pattern as PHPStan §1.1, eight fields instead of five. |
| `PhpCSAnnotatorProxy` | 226 | `QualityToolAnnotator<PhpCSValidationInspection>`. Builds args: `<file>` (or `-` when `Registry.is("php.no.temp.file")`), `-n` (when `ignoreWarnings`), `--standard=<coding-standard>` (resolves "Custom" to the `customRuleset` path with remote mapping), `--runtime-set installed_paths <paths>` (when `useInstalledPaths`), `--encoding=utf-8`, `--report=xml`, `--extensions=<extensions>`, `--stdin-path=<file>` (when stdin mode). `isFileSuitable` filters by the configured extension list (default `php,js,css,inc`). `getWorkingDir` switches to the directory of the custom ruleset (so `<file>` includes are resolved relative to ruleset). |
| `PhpCSXmlMessageProcessor` | 239 | `QualityToolXmlMessageProcessor` (SAX). Parses checkstyle XML (similar to PHPStan §1.1). Severity from `<error>` / `<warning>` tags. Reads `source` (sniff name) and `fixable="1"` attributes. **Per-message quick-fix**: when `fixable=1` it surfaces a `REFORMAT_FILE_ACTION` (`PhpCSBeautifierReformatFileAction`). When `showSniffNames=true`, prepends `<source>:` to the message text. Severity for warnings collapses to the configured `myWarningsHighlightLevel` (or hidden when "ignoreWarnings" is on). |
| `PhpCSComposerConfig` | 435 | `QualityToolsComposerConfig`. Reads `composer.json` for `squizlabs/php_codesniffer`, picks `vendor/bin/phpcs`, plus four NEW behaviors phpcs-specific (see §2.1 features 5a–5d): `applyRulesetFromRoot` (looks for `phpcs.xml` / `phpcs.xml.dist`), `applyRulesetFromComposer` (parses `<rule ref=` from located ruleset), `applyRulesetExtended` (applies a `NonPSRStandard` default), `setupInstalledStandardPaths` (scans installed packages for an entry in the `NonPSRStandard` enum and configures `installed_paths`). `updateCustomSettings` ALSO auto-fills `myPhpCodeBeautifierPath` from `vendor/bin/phpcbf` next to phpcs. `checkComposerScriptsLeaves` parses `--standard=` out of `composer.json scripts.phpcs`. |
| `PhpCSComposerConfig$NonPSRStandard` | (inner) | Hand-coded enum mapping Composer package names to phpcs standard names. 11 entries: `doctrine/coding-standard → Doctrine`, `drupal/coder → Drupal`, `wp-coding-standards/wpcs → WordPress`, `phpcompatibility/php-compatibility → PHPCompatibility`, `joomla/coding-standards → Joomla`, `escapestudios/symfony2-coding-standard → Symfony`, `yiisoft/yii2-coding-standards → Yii2`, `wimg/php-compatibility → PHPCompatibility`, `magento-ecg/coding-standard → Ecg`, `mediawiki/mediawiki-codesniffer → MediaWiki`, `cakephp/cakephp-codesniffer → CakePHP`. Each entry knows its relative path inside the package (e.g. `/coder_sniffer` for Drupal) used to compute `installed_paths`. |
| `PhpCSConfigurable` | 101 | Settings configurable. Lives under `Settings/PHP/Quality Tools/PHP_CodeSniffer` (id `settings.php.quality.tools.php.code.sniffer`). Same shape as PHPStan §1.1. |
| `PhpCSConfigurableForm` | 165 | `QualityToolConfigurableForm` subclass. Validate button parses `PHP_CodeSniffer version (\d.\d.\d)`; also **enforces ≥ 1.5.0** (returns "required phpcs version" error otherwise — first tool to have a *minimum* version check). Overrides `getCustomConfigurable` to return `PhpCSCustomOptionsForm`. |
| `PhpCSCustomOptionsForm` | 269 | NEW vs PHPStan: a separate "Custom Options" sub-panel inside the per-profile configurable, hosting **the phpcbf path field** (with its own browse / remote SDK browse, filename filter `phpcbf` or `phpcbf.bat`, and an independent validate button). Wrapped in a `HideableDecorator` titled "PHPCBF settings". |
| `PhpCSOptionsPanel` | 506 | Swing-Designer panel for the "common options" section (right side of the configurable). Holds: "Show warnings" checkbox, **Coding-standard combobox** (populated dynamically by running `phpcs -i` against the selected profile and adding `"Custom"` as a sentinel), Custom-ruleset path field (shown only when standard == "Custom", with SDK-aware remote browse), "Use installed paths" checkbox + paths textfield, "Show sniff names" checkbox, "Highlight level" combobox (HighlightSeverity), extensions textfield. Calls `getInstalledStandards(project, config, comp, installedPaths)` synchronously to parse `phpcs -i` output ("The installed coding standards are X, Y and Z"). |
| `PhpCSCustomRulesetAnalyzer` | 28 | One static helper: `isCodingStandardFile(path)` — true iff extension is `.xml` or `.dist`. Used by `applyRulesetFromComposer` + `getWorkingDir`. |
| `PhpCSAddToIgnoredAction` | 33 | Subclass of `QualityToolAddToIgnoredAction`. Same as PHPStan §1.1. |
| `PhpCSInterpreterStateListener` | 29 | `PhpInterpretersStateListener` — invalidates phpcs configs whenever PHP interpreters change. Not present in PHPStan plugin; phpcs needs it because the coding-standard list depends on the running interpreter / `phpcs -i`. |
| `PhpCSBeautifierReformatFile` | 164 | NEW vs PHPStan: `QualityToolReformatFile` subclass. Builds args for `phpcbf <file>`: `--standard=<coding-standard>` (resolving "Custom" same way as the annotator), `--encoding=utf-8`, `--extensions=<extensions>`, file path(s). `getToolPath(settings)` returns `getPhpCodeBeautifierPath()` (NOT the phpcs path) — phpcbf is a different binary. `reportNonZeroCodes()` returns `false` because phpcbf intentionally exits non-zero when it actually fixes something. |
| `PhpCSBeautifierReformatFileAction` | 164 | `QualityToolReformatFileAction<PhpCSValidationInspection>` — surfaces phpcbf as an Intentions (Alt-Enter) action **per `fixable=1` checkstyle message** AND as a "Reformat with PHPCBF" line in the editor right-click menu. `getInspection()` looks the inspection tool up by short name from the current profile (so the action is gated on the inspection being enabled). |

**Total bundled: ~3,200 LOC of Java/Kotlin glue.**

### 1.2. Remote interpreter glue

In `phpcs.jar` (only loaded if remote-interpreter plugin is on):

| Class | LOC | Role |
| --- | --- | --- |
| `PhpCSRemoteConfiguration` | 171 | Subclass of `PhpCSConfiguration` that adds `interpreterId`, `PhpSdkDependentConfiguration` impl. `<Tag("phpcs_by_interpreter")>` for XML. Same shape as PHPStan §1.2. |
| `PhpCSRemoteConfigurationProvider` | 216 | Registered on the `PhpCSConfigurationProvider` EP. `createNewInstance` opens the by-interpreter dialog (`QualityToolByInterpreterDialog`); `createConfigurationByInterpreter` builds a `PhpCSRemoteConfiguration`; `fillSettingsByDefaultValue` overrides the **per-profile timeout to 30 000 ms** (vs 5 000 ms for local; vs PHPStan's 60 000 ms remote default). |
| `PhpCSRemoteByInterpreterConfigurableForm` | 94 | NEW vs PHPStan: a phpcs-specific wrapper of `QualityToolByInterpreterConfigurableForm` that adds a **second tool-path field for phpcbf** in the per-interpreter editor. Otherwise the same delegation pattern. |

In `php-remoteInterpreter.jar` — same shared base classes as PHPStan:
`QualityToolByInterpreterDialog`, `QualityToolByInterpreterConfigurableForm`,
`QualityRemoteToolProcessHandler` — same as PHPStan §1.2, identical code.

### 1.3. Plugin metadata

`META-INF/plugin.xml`:

- `<depends>com.jetbrains.php</depends>`
- `<depends>com.intellij.modules.ultimate</depends>` (PhpStorm only)
- `<depends optional="true" config-file="phpcs-remote-plugin.xml">org.jetbrains.plugins.phpstorm-remote-interpreter</depends>`
- 5 services + 1 localInspection (`PhpCSValidationInspection`) + 1
  externalAnnotator + 1 postStartupActivity + 1 configurable + 1
  composerConfigClient + 1 type + 1 inner EP + 1 action
  (`PhpCSAddToIgnoredAction`) + 1 intention/reformat action
  (`PhpCSBeautifierReformatFileAction`, both as `<intentionAction>`
  and as `<action>` under the editor popup) + 1 interpreters listener
  (`PhpCSInterpreterStateListener`).

---

## 2. Functional surface (what the user sees)

### 2.1. User-facing features

1. **Settings page** at `PHP / Quality Tools / PHP_CodeSniffer`:
   - List of profiles (local + per-interpreter), add/remove/edit.
   - Per-profile: phpcs path, validate button (`phpcs --version`,
     min 1.5.0), timeout (default 5s local / 30s remote), max
     messages per file.
   - Per-profile sub-panel "PHPCBF settings": phpcbf path
     (browse-only allows `phpcbf` / `phpcbf.bat`), validate button.
   - Common options across profiles: "Show warnings" toggle,
     coding-standard combobox, custom-ruleset path (shown only
     when "Custom" is picked), "Use installed paths" + paths text,
     "Show sniff names" toggle, "Highlight level" combobox,
     extensions list.
2. **Coding-standard combobox** is populated dynamically: clicking
   the profile's "Refresh" (or selecting it) runs `phpcs -i` and
   parses "The installed coding standards are A, B, …, and Z" into
   the list. Always appends `"Custom"` at the end as a sentinel for
   "use the path field below".
3. **Inspection profile entry**: `PhpCSValidationInspection` (local).
   No separate global inspection — phpcs is on-the-fly only.
4. **On-the-fly analysis** while editing files whose extension is in
   the configured extension list (default `php,js,css,inc`).
5. **Composer auto-detect** — significantly richer than PHPStan:
   - 5a. `vendor/bin/phpcs` appears → set tool path. Also probe
     for `vendor/bin/phpcbf` next door → set phpcbf path.
   - 5b. `phpcs.xml` / `phpcs.xml.dist` at project root → set the
     standard to `"Custom"` and point `customRuleset` at it.
   - 5c. `composer.json scripts.phpcs` contains `--standard=X` →
     adopt `X` as the standard.
   - 5d. `composer.json` requires a non-PSR coding-standard package
     (one of the 11 in `NonPSRStandard`) → set the standard to its
     `defaultStandard` and `installed_paths` to the vendor location
     of the contributed sniffs.
6. **"Add to ignored" action** in the right-click menu on any
   phpcs error in the editor — appends to the per-tool blacklist.
   Same as PHPStan §2.1.6.
7. **"Reformat with PHP Code Beautifier" action**:
   - As an *intention action* (Alt-Enter) on every `fixable=1`
     message coming from phpcs.
   - As an *editor / file-menu action* — "Reformat with PHPCBF"
     against the current file or selected files.
   - Mechanism: invoke `phpcbf <file> --standard=… --encoding=utf-8
     --extensions=…` over the same execution channel as phpcs
     (local or remote SDK), `reportNonZeroCodes=false` so the
     intentional non-zero exit on a successful fix isn't surfaced
     as an error. On completion, refresh the VFS for the file so
     the editor picks up the modified bytes.
8. **Notifications** with a "Configure" link that opens the
   phpcs settings page directly. Same as PHPStan §2.1.8.
9. **Settings migration**: when upgrading from a PhpStorm version
   that stored the options as eight public fields on
   `PhpCSValidationInspection` (profile XML) to the one that stores
   them in `PhpCSOptionsConfiguration` — happens on first startup
   per project. Same pattern as PHPStan §2.1.9, eight fields.
10. **Remote PHP interpreter support** (Docker/SSH/WSL):
    - Picking an interpreter in the by-interpreter dialog auto-
      maps **two** local paths (phpcs and phpcbf) to remote ones.
    - Path mapping for `--standard=<custom ruleset path>` (when
      the standard is "Custom").
    - 30-second default timeout for remote (vs 5s local).
11. **Interpreter-state listener**: when the user adds / removes a
    PHP interpreter, the phpcs profile's cached `myStandards` list
    is invalidated (next combobox open will re-run `phpcs -i`).

### 2.2. Internal plumbing (collapsed by the new SDK)

Same list as PHPStan §2.2 — same legacy `*ConfigurationBaseManager`
pair, three "configuration" objects, manual SAX XML parsing,
custom by-interpreter dialog — plus phpcs-specific:

- A second "binary path" field that lives next to the main
  tool-path field and ships with **its own** validate button,
  browse filter, and remote-browse action.
- A custom Composer post-detect step that mutates **eight** fields
  on `PhpCSOptionsConfiguration` (standard, custom ruleset,
  installed paths, useInstalledPaths) from four orthogonal
  inputs (root xml, composer scripts, composer requires, vendor
  layout).
- A `phpcs -i` "list installed standards" subcall that runs against
  the same profile, parses an English-language sentence, and
  refreshes a combobox.

---

## 3. Mapping each feature to the new SDK

Below: for each user-visible feature, which `:quality-tools-sdk`
phase / artefact handles it.

| Legacy feature | New SDK home | Notes |
| --- | --- | --- |
| `PhpCSQualityToolType` (registration) | `QualityTool` (phase 01) — one Kotlin class | Same EP `dev.jplugins.qualityTools.tool`. `inspectionShortNames = setOf("PhpCSValidationInspection")` (phase 10a.1) to preserve existing inspection profiles. |
| `PhpCSConfiguration` (phpcs path, timeout, max messages) | `LocalBinarySource` (phase 02) + per-`ConfigProfile.timeoutMs` | Same as PHPStan §3. Default timeout 5_000 ms — see gap §4.4 (already covered for remote, not for local). |
| **phpcbf path on `PhpCSConfiguration`** | NEW: a secondary binary on the same `ConfigSource`. See gap §4.1 | This is the big new shape. Two options: (a) a second `BinaryDescriptor` on the source, (b) a second `BinarySource` tagged with `binaryRole="formatter"`. We recommend (a) — see §4.1. |
| `PhpCSRemoteConfiguration` (interpreter id) | `PhpInterpreterBinarySource` in `:php` (phase 02) | Same as PHPStan §3 — generic. The "two binaries" gap §4.1 must also work for the PHP-interpreter source (i.e. `phpcs` and `phpcbf` are *both* resolved through the same interpreter / Docker mount). |
| `PhpCSConfigurationManager` + `*BaseManager` | `QualityToolsProjectStorage` (phase 04) | Same as PHPStan §3. |
| `PhpCSProjectConfiguration.selectedConfigurationId` | `QualityToolsProjectStorage.activeProfileId("phpcs","analyze")` | Same as PHPStan §3. |
| `PhpCSConfigurationProvider` EP | `ConfigSourceType` EP (phase 02) | Same as PHPStan §3. |
| `PhpCSRemoteConfigurationProvider` | `PhpInterpreterBinarySourceType` in `:php` | Same as PHPStan §3. |
| `PhpCSBlackList` | `GlobPathIgnorePolicy` (phase 06) | Same as PHPStan §3. |
| `PhpCSValidationInspection` (local) | Phase 10a.1 inspection-shortname preservation | The new annotator (phase 08) emits messages under `PhpCSValidationInspection` short-name so existing profiles keep severity / scope settings. |
| `PhpCSAnnotatorProxy` | `QualityToolsAnnotator` + `PhpCSTool.buildArgs` (phase 01/08) | ~180 LOC drops; we keep ≤ 40 LOC of arg-building (slightly larger than PHPStan because phpcs has more arg branches — `-n`, `--runtime-set`, `--stdin-path`, "Custom" resolution). |
| `PhpCSXmlMessageProcessor` (SAX checkstyle) | `CheckstyleXmlReader` (phase 06) — bundled | Mostly free. But three phpcs-specific knobs need expression: (a) read `source` attribute → `ToolMessage.ruleId`; (b) read `fixable="1"` → tag (`"fixable"`) on the message; (c) when `showSniffNames=true`, prepend the ruleId to title — see gap §4.2. |
| **`fixable="1"` → phpcbf quick-fix** | `ToolFix` of kind `RunFormatterFix(toolId="phpcs", mode="format")` (phase 06) + a `MessageEnricher` that attaches it when the message's tags contain `"fixable"`. | The mode `format` is a separate `ToolMode` on the same `QualityTool` (see "two-mode" mapping below). The fix's invocation goes through the standard SDK runner — no `QualityToolReformatFile` survives. See gap §4.3. |
| `PhpCSComposerConfig` (root xml + composer scripts + non-PSR + phpcbf-side-by-side) | `ComposerBinarySourceType` in `:php` (composer-from-vendor source) + a `PhpCSComposerOnDetectedHook` (per gap §4.1 + §4.5 from PHPStan plan) | The four sub-behaviors map cleanly: root-xml is a `RulesetProbe`, scripts is a `ComposerScriptArgParser`, non-PSR is a static table that's just data, the phpcbf side-by-side becomes "resolve the secondary binary". See §4.5 below. |
| `PhpCSComposerConfig$NonPSRStandard` (11-entry enum) | Pure-data table in `PhpCSComposerOnDetectedHook` — no SDK abstraction needed | Stays as Kotlin data class list inside the phpcs plugin. The SDK does not invent a "coding standard registry" — it would only be used by phpcs. |
| `PhpCSConfigurable` + `PhpCSConfigurableForm` + `PhpCSOptionsPanel` + `PhpCSCustomOptionsForm` | `PhpCSOptionsSchema` + `AutoToolSettingsPanel` (phase 04 + 07) + secondary-binary block (gap §4.1) | The "common options" become eight `OptionSpec`s. The per-profile phpcbf path block is auto-rendered from the secondary-binary descriptor. The coding-standard combobox needs a **dynamic** `ChoiceSpec` (see gap §4.4). |
| `PhpCSOptionsConfiguration` (project state) | `OptionsBag` in the unified storage (phase 04) | Same as PHPStan §3. |
| `PhpCSSettingsTransferStartupActivity` | Migration step inside the legacy-XML migrator (phase 10c-equivalent for phpcs) | Same as PHPStan §3, eight fields instead of five. |
| `PhpCSAddToIgnoredAction` | Built into the SDK once `GlobPathIgnorePolicy` is registered | Same as PHPStan §3 (gap 4.7 there). |
| **`PhpCSBeautifierReformatFile` (phpcbf runner)** | `ToolMode("format", executionStyle="format", outputFormat="diff-or-inplace", supportsFix=true)` on the same `PhpCSTool` | The formatter is a **mode**, not a separate tool. `buildArgs(mode=format)` returns the phpcbf args; `binaryRole` on the mode says "use the formatter binary, not the analyzer binary" — see gap §4.1. |
| **`PhpCSBeautifierReformatFileAction` (right-click + intention)** | Auto-registered by the SDK once a tool has any mode with `executionStyle=="format"` AND `supportsFix=true` | Phase 07 (UI layer) acceptance bullet: "for every tool that declares a `format` mode, contribute a `Reformat with <displayName>` action under Code menu + intention action". See gap §4.3. |
| `PhpCSCustomRulesetAnalyzer.isCodingStandardFile` | One-line predicate inside the Composer hook | Trivial; stays as Kotlin top-level fun in `PhpCSComposerOnDetectedHook.kt`. |
| `PhpCSInterpreterStateListener` (cache invalidation) | `ConfigSource.onResolvedChanged()` (phase 02) + cache-busting in the dynamic `ChoiceSpec` provider | The cached standards list lives in `ResolvedBinary.metadata["installedStandards"]`; resolution is rerun when the source signals "interpreters changed". See gap §4.4. |
| `PhpCSConfigurableForm.doValidation` (min version 1.5.0) | `BinaryValidator` SAM (same as PHPStan gap §4.1 — already on the SDK roadmap) | Phpcs is the first tool that gates on a **minimum** version. `BinaryValidator` returns `ok=false` when `detectedVersion < "1.5.0"`. Reinforces PHPStan §4.1 gap. |
| `PhpCSCustomOptionsForm.validate` (phpcbf version) | Same `BinaryValidator` SAM, applied per-binary | Each `BinaryDescriptor` carries its own `BinaryValidator?`. Phpcs's phpcs validator differs from phpcbf's (both check "PHP_CodeSniffer" in stdout but only the phpcs one enforces the version). |
| Per-mode default timeout (phpcs=5s local vs 30s remote vs phpcbf inherits) | `ConfigSourceType.defaultTimeoutMs` (PHPStan gap §4.4) — verified by phpcs's 5s/30s spread | Reinforces PHPStan §4.4. |
| `QualityToolByInterpreterDialog` + `QualityToolByInterpreterConfigurableForm` (in remote-interp jar) | Same as PHPStan §3 — `PhpInterpreterSourceWizard` in `:php` | Generic. |
| `QualityRemoteToolProcessHandler` | `IntellijProcessSpawner` in `:php` (phase 05) | Same as PHPStan §3. |
| `QualityToolReformatFile` base + local/remote spawn fallback | `ToolRunner` in phase 05 — same spawner for format and analyze | The legacy class is a complete second runner; the SDK collapses both into one runner with mode-aware dispatch. |

---

## 4. Gaps in the new SDK exposed by this exercise

Compared with the PHPStan plan, phpcs surfaces a fresh set of gaps
around "second binary as a fixer" and "dynamic option choices". The
PHPStan gaps (4.1 BinaryValidator, 4.2 per-mode reader id,
4.3 detected version, 4.4 defaultTimeoutMs, 4.5 onDetected hook,
4.7 auto-wired AddToIgnored, 4.9 batch-mode cache) all apply here
unchanged — see "same as PHPStan §X.Y" rather than restating.

### 4.1. Secondary binary on a `ConfigSource` ("the formatter binary")

**What it is:** phpcbf is a separate executable from phpcs.
Locally it's `vendor/bin/phpcbf` next to `vendor/bin/phpcs`. Through
a PHP interpreter source, it's a separate script the same
interpreter runs. The user picks both paths in the same per-profile
editor; both go through the same path-mapping when remote.

**Today:** A second `Attribute("beautifier_path")` field on the
`*Configuration` POJO and a hand-rolled "PHPCBF settings"
`HideableDecorator` in `PhpCSCustomOptionsForm`. Each consumer site
that runs phpcbf reads `getPhpCodeBeautifierPath()` instead of
`getToolPath()`.

**Recommendation (fix in SDK):** Extend the phase 02 `ConfigSource`
contract so a source can resolve **multiple binaries** keyed by a
role string:

```kotlin
public interface ConfigSource {
    public val typeId: String
    public fun resolveBinary(role: String = "default"): ResolvedBinary?
    // existing single-binary methods keep working via role="default"
}

public interface BinaryDescriptor {
    public val role: String       // "default", "formatter", "linter", …
    public val displayName: String
    public val validator: BinaryValidator?
    public val pathFilter: PathFilter
}
```

`QualityTool.binaryDescriptors: List<BinaryDescriptor>` — phpcs
declares two (`role="default"` for phpcs, `role="formatter"` for
phpcbf). A `ToolMode` carries `binaryRole: String = "default"`; the
runner (phase 05) picks the right binary at dispatch time.

`AutoToolSettingsPanel` reads the list and renders one path field +
validate-button per binary descriptor. The PHPCBF-as-hideable
section drops out for free.

**Phase 02 + 05 + 07 edit (medium).** This is the structural gap
that phpcs exposes most loudly. It is also forward-looking: any
tool with a separate fixer binary (php-cs-fixer is monolithic so
unaffected; some Ruby tools like `rubocop` + `standardrb` would
benefit). **Fix in SDK.**

### 4.2. Reader-level "rule ID" + "tag" plumbing for fixable messages

**What it is:** `PhpCSXmlMessageProcessor` reads two extra
attributes that `CheckstyleXmlReader` may not surface today:

- `source="Standard.Category.Rule"` → goes into `ToolMessage.ruleId`.
- `fixable="1"` → must signal "phpcbf can fix this" downstream.

Plus the "show sniff names" option *prepends* the ruleId to the
message title.

**Today:** Both are hardcoded inside the phpcs SAX subclass.

**Recommendation (fix in SDK, small):** The bundled
`CheckstyleXmlReader` should ALWAYS extract `source` into
`ruleId` (it's a checkstyle standard attribute) and a generic
`fixable` flag into `ToolMessage.tags = setOf("fixable")` when the
attribute is present. The "show sniff names" toggle is a phpcs-
specific `MessageEnricher` that does
`if (showSniffs) title = "${msg.ruleId}: ${msg.title}"`. **Phase 06
edit (small).** Stays in plugin: the enricher. Fix in SDK: the
reader extracts both attributes by default.

### 4.3. Formatter mode + auto-wired "Reformat with X" action

**What it is:** phpcbf is invoked in three places:
1. As a per-message intention action (Alt-Enter) on any `fixable=1`
   error / warning.
2. As an editor right-click / Code-menu "Reformat with PHP Code
   Beautifier" action.
3. Programmatically by other JetBrains code via the registered
   `<externalAnnotator>` extension (legacy
   `QualityToolReformatFile` system).

**Today:** Two phpcs-specific classes (`PhpCSBeautifierReformatFile`,
`PhpCSBeautifierReformatFileAction`) plus the 379-LOC
`QualityToolReformatFile` base class manage all three.

**Recommendation (fix in SDK, medium):** Express "tool is a fixer
in a particular mode" as a first-class concept already discussed in
phase 01:
- A `ToolMode` with `executionStyle="format"` and
  `formattingOutputMode="in_place"` declares the tool is a fixer.
- `binaryRole` (see gap §4.1) picks the right binary.
- `outputFormat="diff"` lets the runner apply the result as an
  edit; `"in_place"` re-reads the file from VFS after exit.

Phase 07 (UI layer) registers, **for every tool that has a `format`
mode**:
- A `<action>` "Reformat with `<displayName>`" wired to invoke
  `ToolRunner` with that mode + the current selection.
- A SAM provider that converts each `fixable`-tagged `ToolMessage`
  into a `RunFormatterFix(toolId, mode)` quick-fix attached to the
  inspection.

Concretely the phase 07 acceptance list grows two bullets; no new
API. **Phase 07 edit (small docs, medium implementation).**

This also covers psalter-style fixes (`psalm --alter`) and
laravel-pint, so it pays for itself across the suite.

### 4.4. Dynamic `ChoiceSpec` whose `values` come from a `ResolvedBinary`

**What it is:** The "Coding standard" combobox lists whatever
`phpcs -i` reports for the currently selected profile. Today this
is a synchronous subcall hidden inside `PhpCSOptionsPanel.init` and
re-triggered by `PhpCSInterpreterStateListener` whenever PHP
interpreters change.

**Today:** Hand-rolled in `PhpCSOptionsPanel.getInstalledStandards`,
including English-sentence parsing.

**Recommendation (fix in SDK, small):** Phase 04 already has
`ChoiceSpec(values: List<String>)`. We need a sibling whose values
come from a function of the resolved profile:

```kotlin
public interface DynamicChoiceSpec : OptionSpec<String> {
    public val staticValues: List<String>
    public fun valuesFor(
        ctx: ResolveContext,
        bag: OptionsBag,
        resolved: ResolvedBinary,
    ): List<String>
}
```

Phpcs declares the `staticValues = listOf("PSR1","PSR2","PSR12",
"PEAR","MySource","Squiz","Zend","Custom")` and overrides
`valuesFor` to run `phpcs -i` (the parser stays in the plugin —
the English-sentence parsing is phpcs-specific). The
"Custom" sentinel stays declared statically so the picker is usable
even when the binary fails to resolve.

`AutoToolSettingsPanel` renders this as a non-editable combobox
with a refresh icon; the refresh resolves the binary and calls
`valuesFor`. Cache lives on the resolved binary
(`ResolvedBinary.metadata["installed-standards"]`) so the listener
in `PhpCSInterpreterStateListener` becomes a `ConfigSource`
re-resolve trigger.

**Phase 04 + 07 edit (small).**

### 4.5. Composer hook — extends PHPStan gap §4.5

PHPStan's gap §4.5 added an `onDetected(bag)` callback. Phpcs needs
the same hook, but it writes to **eight** options and also wants to
mutate the **secondary binary descriptor** (the phpcbf path next
door). The current gap §4.5 signature

```kotlin
public interface OnDetectedHook {
    public fun onDetected(source: ConfigSource, project: ResolveContext, bag: OptionsBag)
}
```

needs `bag` to be writable AND the hook needs access to the source's
mutable binary set. Either widen the parameter list:

```kotlin
public fun onDetected(
    source: ConfigSource,
    project: ResolveContext,
    bag: OptionsBag,
    binaries: BinaryRegistry,   // can resolve & register secondaries
)
```

…or split it into two hooks: `onDetectedOptions(bag)` and
`onDetectedBinaries(BinaryRegistry)`. Recommendation: the wider
signature — phpcs is the worst case and the cost of an extra unused
parameter for PHPStan is nil. **Phase 02 edit (small).**

### 4.6. `BinaryValidator` enforces minimum version

PHPStan gap §4.1 introduced `BinaryValidator`. Phpcs is the first
tool that **rejects** an older binary (< 1.5.0). The current
proposed `ValidationResult` shape

```kotlin
public interface ValidationResult {
    public val ok: Boolean
    public val message: String
}
```

is sufficient; we just need to spell out in the phase-01 doc that
"ok=false" is a hard error (the validate button shows the message,
the binary is not usable). This is a documentation gap rather than
an API gap. **Phase 01 edit (docs only).**

### 4.7. "Reformat on save / format mode" surface

Adjacent to gap §4.3: the SDK currently has `executionStyle=
"on_save"` as a string. We'd benefit from a tiny clarification that
`"format"` mode + `"on_save"` execution style means "run the
formatter binary on save", and that this auto-registers an
"Action on Save" entry under `Settings/Tools/Actions on Save` —
mirroring the existing phpcs-fixer / laravel-pint behavior. Phpcs's
legacy plugin does **not** ship this auto-on-save (the user reaches
for it via Alt-Enter or the Code menu) but other tools do. **Phase
07 edit (docs only); call out the cross-reference.**

### 4.8. Inspection-shortname `PhpCSValidationInspection` preservation

Same as PHPStan §4.8 — phpcs has only ONE inspection short-name
(`PhpCSValidationInspection`), no separate "global". The migration
mechanism designed for Mago in phase 10 covers it; one more
`Migrator` impl, eight fields instead of five. **No SDK change.**

### 4.9. `inspectionStarted` batch-mode cache

Not exercised by phpcs — phpcs is on-the-fly only, no
`PhpCSGlobalInspection`. PHPStan gap §4.9 still applies for tools
that batch, but phpcs doesn't reinforce or contradict it.

### 4.10. "Two binary roles" interacts with remote SDK file transfer

When the source is a PHP interpreter (Docker / SSH), today
`PhpCSRemoteByInterpreterConfigurableForm` knows about both paths
and registers two remote-browse actions. After §4.1 lands, the
generic `PhpInterpreterBinarySourceType` wizard must iterate
`BinaryDescriptor`s and render one row per descriptor. **Phase 07
edit (small):** the wizard becomes a `for`-loop. Same code now
works for phpcs (phpcs + phpcbf) and the future "two binaries"
shape of any other tool. **Fix in SDK.**

### 4.11. Per-message tag-driven quick-fix attachment

Currently `ToolMessage.fixes` is set by the reader. Phpcs needs the
*reader* to set only `tags = setOf("fixable")`, then a *separate
hook* to convert that into a `RunFormatterFix`. This is the
`MessageEnricher` pattern from phase 06 — already supported. No SDK
change; phpcs ships
`PhpCSFixableMessageEnricher : MessageEnricher`. Stays in plugin.

### 4.12. Trusted-project gating for the formatter

The legacy `QualityToolReformatFile.getProcessHandler` refuses to
run if the project is untrusted. The SDK's `ToolRunner` should
inherit this — the formatter writes to the file. Already a phase 05
acceptance bullet for tools whose mode has `supportsFix=true`. No
change, but verify the bullet exists. **Phase 05 edit (docs)** if
not.

### 4.13. Process-output VFS refresh after format

After phpcbf exits, the file on disk has changed; the editor needs
to reload. `QualityToolReformatFile` does a `WriteAction.runAndWait
{ documentManager.saveDocument(document) }` before launch and a
VFS refresh in a `ProcessAdapter` after. The new SDK has this as
"if `outputFormat=in-place`, the runner queues a VFS refresh of
the target after the process exits". Verify this is in phase 05's
acceptance for `executionStyle="format"`. **Phase 05 edit (docs).**

### 4.14. Cross-EP intention actions

`PhpCSBeautifierReformatFileAction` is registered as both
`<intentionAction>` and `<action>`. The SDK should generate **both**
from the single declaration "this tool has a format mode" (gap
§4.3) rather than asking the plugin to register them. Already
covered by 4.3. **Fix in SDK.**

---

## 5. Generic-code overhead — what we'd write if we ported now

Following the gap list, with the SDK *as currently specified*, we'd
write some phpcs-specific code that should really be generic.
Marked them with ⚠ — these are signals the SDK is missing
something.

| Code we'd write in the phpcs port | Generic? | Action |
| --- | --- | --- |
| `PhpCSTool : QualityTool` | unique | keep |
| `PhpCSOptionsSchema : OptionsSchema` | unique | keep |
| `PhpCSComposerOnDetectedHook` | unique | keep |
| `PhpCSStandardsChoiceSpec : DynamicChoiceSpec` | unique | keep (needs SDK gap 4.4) |
| `PhpCSFixableMessageEnricher : MessageEnricher` | unique | keep |
| `PhpCSShowSniffsEnricher : MessageEnricher` | unique | keep |
| `PhpCSVersionValidator : BinaryValidator` (≥ 1.5.0) | unique | keep (needs PHPStan gap 4.1) |
| `PhpCBFVersionValidator : BinaryValidator` | unique | keep |
| ⚠ Secondary phpcbf path stored on `*Configuration` POJO | generic | resolve via gap 4.1 |
| ⚠ Two-binary wizard rows in remote SDK editor | generic | resolve via gap 4.10 |
| ⚠ Custom `QualityToolReformatFile` subclass for phpcbf | generic | resolve via gap 4.3 |
| ⚠ Custom intention-action wrapper for phpcbf | generic | resolve via gap 4.3 |
| ⚠ Right-click action registration for "Reformat with phpcbf" | generic | resolve via gap 4.3 |
| ⚠ Dynamic combobox-options Swing in `OptionsPanel` | generic | resolve via gap 4.4 |
| ⚠ "Refresh standards" trigger from interpreter listener | generic | resolve via gap 4.4 + 4.10 |
| ⚠ Reader extracts `source` + `fixable` attributes | generic | resolve via gap 4.2 |
| ⚠ VFS refresh after format-mode process exits | generic | resolve via gap 4.13 |
| ⚠ Trusted-project gating for formatter | generic | resolve via gap 4.12 |
| ⚠ Per-tool right-click "Add to ignored" action | generic | resolve via PHPStan gap 4.7 |
| ⚠ Storage of "detected version" for ≥ 1.5.0 check | generic | resolve via PHPStan gap 4.3 |
| ⚠ "On detected, enrich options & secondary binary" hook wiring | generic | resolve via PHPStan gap 4.5 (widened) + gap 4.5 here |

Twelve ⚠ items vs PHPStan's six. Almost all the new ones are
clustered around the "secondary binary as a formatter" story (gaps
4.1, 4.3, 4.10) plus the "dynamic option choices" story (gap 4.4).
Spending the SDK budget on those two stories pays for: phpcs, php-
cs-fixer (formatter mode without secondary binary), laravel-pint,
Mago (already designed for one binary), and any future Rector-
style tool. **Fixing all the new gaps in the SDK first is the right
call** — same conclusion as PHPStan.

---

## 6. Concrete file list for the phpcs port (post-gap-fix)

Assuming the gaps in §4 (and the still-open PHPStan ones) are
merged into the SDK, the phpcs plugin shrinks to roughly:

**Required**:

- `PhpCSTool.kt` (~120 LOC) — id, **two modes** (`analyze` on-the-fly
  with reader=checkstyle-xml, `format` formatter with
  binaryRole=formatter and executionStyle=format), **two binary
  descriptors** (default + formatter), capabilities (`"lint"`,
  `"fix"`), options schema, buildArgs (the actual phpcs/phpcbf arg
  branches).
- `PhpCSOptionsSchema.kt` (~60 LOC) — eight specs (one is the
  dynamic standards spec); mode schemas inheriting from tool-level.
- `PhpCSStandardsChoiceSpec.kt` (~50 LOC) — `DynamicChoiceSpec`
  impl. Runs `phpcs -i` and parses the English sentence "The
  installed coding standards are A, B, …, and Z". Static fallback
  list of seven built-ins plus `"Custom"`.
- `PhpCSVersionValidator.kt` + `PhpCBFVersionValidator.kt`
  (~25 LOC each) — `--version` regex, ≥ 1.5.0 gate for phpcs.
- `PhpCSComposerOnDetectedHook.kt` (~120 LOC) — four sub-tasks:
  root `phpcs.xml`, composer scripts, non-PSR standard table,
  vendor side-by-side `phpcbf` registration. The `NonPSRStandard`
  data list lives here.
- `PhpCSFixableMessageEnricher.kt` (~15 LOC) — turns
  `tags.contains("fixable")` into a `RunFormatterFix(toolId="phpcs",
  mode="format")` attachment.
- `PhpCSShowSniffsEnricher.kt` (~10 LOC) — prepends `ruleId` to
  title when the `showSniffs` option is set.
- `PhpCSMigration.kt` (~80 LOC) — legacy XML (`PHPCodeSniffer`
  `*ConfigurationBaseManager` storage) + the eight legacy fields
  off `PhpCSValidationInspection` → new storage.
- `META-INF/plugin.xml` (~50 LOC) — registrations.

**Optional** (small features that stay phpcs-specific):

- `PhpBundle.properties` keys — i18n (carry-over).
- `inspection.html` — description for the inspection.

**Total: ~480 LOC + bundle/HTML**, vs. ~3,200 LOC today. **~6.5×
reduction.** (PHPStan was 7× — phpcs ports a hair worse because of
the two-binary + dynamic-choices code that survives as plugin-
specific.)

---

## 7. Order of work (when we get to coding)

Sequenced so each step is mergeable independently. Numbered against
the existing phase-doc plan, NOT replacing it.

1. **SDK gap fixes** that PHPStan also needs (already on the plan):
   PHPStan §4.1 BinaryValidator → phase 01; §4.2 per-mode reader id
   → phase 01; §4.3 ResolvedBinary.detectedVersion → phase 02; §4.4
   defaultTimeoutMs → phase 02; §4.5 onDetected hook → phase 02
   (will be widened by §4.5 here); §4.7 auto-AddToIgnored → phase
   07. Land these first.

2. **SDK gap fixes specific to phpcs**:
   - §4.1 secondary-binary descriptors on `ConfigSource` → phase 02
     + 05 + 07 (the big one — medium).
   - §4.2 reader extracts `source` + `fixable` → phase 06 (trivial).
   - §4.3 format-mode + auto-wired actions → phase 07 (small docs,
     medium implementation).
   - §4.4 DynamicChoiceSpec → phase 04 + 07 (small).
   - §4.5 widen `OnDetectedHook` signature → phase 02 (trivial,
     pairs with PHPStan §4.5).
   - §4.10 wizard iterates BinaryDescriptors → phase 07 (small).
   - §4.12 trusted-project gating for fix-mode → phase 05 docs.
   - §4.13 VFS refresh after format → phase 05 docs.
   Ship as one "phase patches" PR after PHPStan §4 patches land.

3. **PHPCS tool registration** — minimal port. `PhpCSTool` declares
   one `analyze` mode and one binary descriptor; ignores
   formatter. Result: phpcs visible in Settings, on-the-fly
   inspection works, no phpcbf, no Composer auto-detect.

4. **PHPCS version detection** — `PhpCSVersionValidator` wired into
   the validate button (PHPStan §4.1).

5. **PHPCS dynamic standards combobox** — `PhpCSStandardsChoiceSpec`
   wired into the schema (§4.4). At this point the combobox
   populates via `phpcs -i` and the user can pick a standard.

6. **PHPCS Composer auto-detect (no phpcbf yet)** — first three of
   the four sub-tasks (root xml, composer scripts, non-PSR table).
   Replaces three quarters of `PhpCSComposerConfig`.

7. **PHPCS phpcbf as second binary** — `PhpCSTool` grows the second
   `BinaryDescriptor` + `format` mode. `PhpCBFVersionValidator`
   wires into the secondary validate button (§4.1).
   `PhpCSFixableMessageEnricher` attaches the formatter fix
   (§4.3). The SDK's auto-wired "Reformat with phpcbf" action
   appears in the Code menu and as an intention. Composer hook
   adds the fourth sub-task (side-by-side phpcbf path).

8. **PHPCS remote** — `<depends optional="true">` on the new
   PHP-interpreter source type from `:php` (zero phpcs code, once
   §4.10 lands so the wizard renders both phpcs and phpcbf rows).
   Tests against the 30-s timeout from PHPStan §4.4.

9. **PHPCS migration** — `PhpCSMigration` ports legacy XML + the
   `PhpCSSettingsTransferStartupActivity` profile → options
   carry-over into the unified storage. Eight fields.

10. **PHPCS show-sniffs enricher** — small `MessageEnricher`
    (§4.11).

11. **PHPCS inspection-shortname preservation** — verify
    `PhpCSValidationInspection` is emitted by the SDK bridge
    (phase 10a.1).

12. **Cleanup**: delete the legacy plugin's classes once the new
    build is validated.

---

## 8. Risks / open questions

- **Bundled vs separate plugin**: same as PHPStan §8 — start as a
  separate plugin, upstream once the SDK is validated.
- **Inspection-profile schema mismatch**: same as PHPStan §8, but
  eight fields instead of five (`IGNORE_WARNINGS`, `CODING_STANDARD`,
  `CUSTOM_RULESET_PATH`, `WARNING_HIGHLIGHT_LEVEL_NAME`,
  `SHOW_SNIFF_NAMES`, `USE_INSTALLED_PATHS`, `INSTALLED_PATHS`,
  `EXTENSIONS`).
- **`phpcs -i` output is English-locale**: "The installed coding
  standards are A, B, and Z" — the parser splits on commas and
  drops the literal `"and"`. If a future phpcs ever localizes the
  output, the parser silently fails. Mitigation: fall back to the
  static list of seven built-ins so the user can still pick a
  standard. **Open question**: do we want to teach phpcs (upstream)
  to emit `--report=json` for `-i`? Not in scope here.
- **"Custom" sentinel value**: the standards combobox carries an
  artificial `"Custom"` entry that flips a sibling path field
  visible. We can preserve the same UX with `DynamicChoiceSpec` +
  one boolean conditional (the `customRuleset` path spec has
  `enabledWhen = { bag[standardSpec] == "Custom" }`). Phase 04 has
  hints of `enabledWhen` but it's not in the current contract —
  call out as a sub-gap of §4.4.
- **phpcbf exit-code semantics**: phpcbf exits 1 when it fixes
  something and 2 when there are uncorrectable errors. The SDK's
  `ToolRun.exitCode` is exposed; the runner must NOT treat exit≠0
  as "process failed" when `reportNonZeroCodes=false`. Mode-level
  `exitCodeMeansFailure: IntPredicate? = null` would be a clean
  knob — minor §4 addition. Today legacy uses a bool flag.
- **Two binaries from one Composer source**: §4.5 widens the hook
  signature. There's a real possibility a future user reports
  "phpcbf wasn't auto-detected" because the side-by-side probe
  raced with the main detect. We need the hook to run AFTER both
  the source has resolved the default binary AND we've also probed
  for siblings. Worth a "Hook ordering" subsection in phase 02.
- **`fixable=1` quick-fix vs full-file reformat**: the legacy
  plugin's intention runs **phpcbf over the whole file**, not just
  the offending range. We must keep that semantics (phpcbf does not
  accept a range). `RunFormatterFix.targetRange` should default to
  "whole file" when the mode has `formattingOutputMode="in_place"`.
- **Stdin vs temp file vs `--stdin-path`**: `Registry.is("php.no.
  temp.file")` toggles between writing a temp file and piping
  through stdin. The SDK's phase 05 `supportsStdin` + `stdin`
  bytes covers this, but the `--stdin-path=` arg that lets phpcs
  resolve relative `<include>` rules in the ruleset needs to be
  built by `buildArgs(ctx, mode, target)` when stdin is in use.
  Verify `ToolRunContext` carries the *originating* file path even
  when the actual input is stdin — small phase 05 doc bullet.
- **`extensions=php,js,css,inc` is project-wide**: a single comma-
  list controls which files the annotator fires on. The phase-04
  `OptionSpec` for it should drive a generic file-filter (phase 03
  `Scope`?), or just be read by `isFileSuitable` in the annotator
  bridge. Recommendation: the bridge reads it; no scope plumbing
  needed.
- **What if phpcs declares NO formatter mode?**: a user might
  point their phpcs profile at a phpcs but leave phpcbf empty. The
  SDK must hide the "Reformat with phpcbf" action when the
  formatter binary is unresolved (validate fails / path is empty).
  `ResolvedBinary == null` ⇒ action grayed out. Already a natural
  consequence of binary-role plumbing in §4.1; spell it out as a
  phase 07 acceptance bullet.

---

## 9. Summary

- The full phpcs integration is **~3,200 LOC of Java/Kotlin glue
  today** spread across 22 classes in the main jar plus 3 in the
  remote-interpreter sub-jar.
- Mapped onto the proposed SDK as-specified, ~85% of those classes
  disappear in favor of generic infrastructure. The remaining
  ~480 LOC are genuinely phpcs-specific (the `NonPSRStandard`
  table, the English-sentence `phpcs -i` parser, the eight-field
  options schema, the `fixable` → `RunFormatterFix` glue).
- **The phpcs port is what proves the SDK can carry a second
  binary as a fixer**, which neither Mago nor PHPStan exercise.
  Gaps §4.1, §4.3, §4.10 are the meaningful new SDK work; once
  they land they also pay for psalter-style fixers and laravel-
  pint formatter integration.
- **The phpcs port is also what proves dynamic options can be
  driven by the resolved binary** (gap §4.4 — `DynamicChoiceSpec`).
  Without it, the SDK can only express options whose value sets
  are known at compile time. With it, any tool that exposes a
  `--list-rules` / `-i` / `--show-checks` style subcommand can be
  modeled cleanly.
- Post-gap-fix the phpcs plugin lands at **~480 LOC** — a 6.5×
  reduction. The remaining gap-list (twelve ⚠) shrinks to zero;
  everything that stays in the plugin is genuinely phpcs-specific.
- The remote-interpreter integration becomes nearly free, modulo
  the wizard iterating `BinaryDescriptor`s (gap §4.10) so phpcs
  and phpcbf are both prompted-for in the same flow.
- Recommendation: merge the SDK patches for §4.1, §4.3, §4.4
  (plus the widened §4.5 from PHPStan) as a "phase 11 hardening"
  pass AFTER the Mago migration and PHPStan port both ship. Then
  port phpcs as the third adopter — the one that validates the
  "fixer + dynamic options + remote SDK" trifecta that no earlier
  adopter touches.
