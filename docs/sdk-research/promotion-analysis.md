# Cross-cutting promotion analysis — what belongs in `:core` vs `:php` vs stays in plugin

> Reading list:
> - `docs/phases/*.md` — current SDK design
> - `docs/sdk-research/*.md` — the original research
> - `docs/{phpstan,psalm,phpcs,php-cs-fixer,laravel-pint,mess-detector}/*-port-plan.md` — six port plans
> - `docs/phpstan/cross-tool-synthesis.md` — the gap synthesis
>
> This document goes one level deeper than the synthesis: instead of
> "what SDK feature is missing" it asks "**which concrete classes are
> being duplicated across all 6 ports — should they live in `:core`
> or `:php`?**" For each candidate I give an argument for and against
> promotion, then a recommendation.

---

## 1. Recurring per-tool classes — the duplication ledger

These classes exist in **every** of the 6 legacy plugins. Their
shape barely differs across tools, but the legacy SDK still forces
each plugin author to subclass them.

| Class shape | PHPStan | Psalm | phpcs | CS-Fixer | Pint | phpmd | Total LOC |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `<Tool>BlackList` (one liner subclass of `QualityToolBlackList`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~150 |
| `<Tool>ConfigurationManager` (App+Project pair, both `@State` services) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~390 |
| `<Tool>ConfigurationBaseManager` (`PersistentStateComponent<Element>` adapter) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~370 |
| `<Tool>ConfigurableForm` (Settings panel — tool path + validate button + timeout) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~700 |
| `<Tool>AnnotatorProxy` (on-the-fly + batch invocation glue) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~1 100 |
| `<Tool>AddToIgnoredAction` (one-liner subclass of `QualityToolAddToIgnoredAction`) | ✓ | ✓ | ✓ | ✓ | (no — uses CS-Fixer's?) | ✓ | ~180 |
| `<Tool>SettingsTransferStartupActivity` (legacy XML → options service migrator) | ✓ | ✓ | ✓ | ✓ | (none) | ✓ | ~340 |
| `<Tool>ComposerConfig` (vendor/bin/<tool> detect + config-file parse) | 301 | 237 | 435 | 248 | 221 | 194 | **1 636** |
| `<Tool>MessageProcessor` (XML SAX parser; checkstyle for 3, custom for 3) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~1 100 |
| `<Tool>RulesetAnalyzer` (custom config file detection) | (none) | (none) | ✓ | ✓ | (none) | ✓ | ~80 |
| `<Tool>RemoteConfiguration` + `<Tool>RemoteConfigurationProvider` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~2 000 |
| `<Tool>OpenSettingsProvider` (composer-notification deep-link) | ✓ | ✓ | (none) | (none) | (none) | (none) | ~80 |

**Total recurring per-tool LOC: ~8 000** spread across 70 classes
in the legacy plugins. The synthesis covered "what *feature* gaps
to close in the SDK"; this document covers "**which of those 70
classes can disappear entirely** because the SDK provides the
implementation."

---

## 2. Promotion candidates — to `:core`

### 2.1. `LegacyInspectionFieldsMigrator`

**What it'd replace**: 5 of 6 `<Tool>SettingsTransferStartupActivity`
classes — each reads N inline fields from a legacy
`globalInspection` XML entry and copies them to the modern
`*OptionsConfiguration` service.

**Variation across tools**:
- PHPStan: 5 fields (`FULL_PROJECT`, `memoryLimit`, `level`,
  `config`, `autoload`).
- Psalm: 3–4 fields.
- phpcs: 7 fields.
- CS-Fixer: 3 fields.
- phpmd: 6 fields.

The shape is identical: bool `transferred` flag, copy mapping,
one-shot startup activity. The *fields* differ but they're already
captured in `OptionsSchema`.

**Pro**:
- 5 × ~70 LOC = 350 LOC duplication today, all conceptually
  identical.
- The legacy bridge in phase 10a already needs to read the
  inspection short-name; reading public fields is the same XML
  walk.
- Inspection-profile XML schema is owned by the platform — every
  plugin writes the same XPath / JDOM walk.

**Contra**:
- Field names per tool are tool-specific, so the migrator needs a
  per-tool mapping table anyway. That table lives somewhere.
- Reading from `globalInspection`'s public fields is brittle
  reflection — risky to generalize.

**Recommendation: `:core` — promote.** Ship as
`LegacyInspectionFieldsMigrator(toolId, mappings)` where
`mappings: Map<String, OptionSpec<*>>`. Per-tool migration becomes a
20-LOC class declaring the field-to-spec mapping. Plugin author
writes only the table.

---

### 2.2. `CheckstyleXmlReader` for tools emitting checkstyle (already bundled)

Already in `:core` per phase 06. Three of six tools (PHPStan, Psalm,
phpcs) emit checkstyle XML; their per-tool MessageProcessor classes
are ~700 LOC of duplicated SAX glue.

**Pro**:
- Already designed; nothing new.

**Contra**:
- phpcs's checkstyle has extra attributes (`source`, `fixable`)
  that mapped onto `ToolMessage.ruleId` and `ToolMessage.tags`.
  The reader needs to extract them — small extension, not a fork.

**Recommendation: already in plan**, no change. The reader stays in
`:core`; tag-extraction policy lives in the reader. Per-tool
`MessageEnricher` (also in `:core`) handles tag-to-fix wiring.

---

### 2.3. `UdiffReader` for fixer-style tools (CS-Fixer + Pint + future)

**What it'd replace**: `PhpCSFixerMessageProcessor` (~275 LOC) +
`LaravelPintXmlMessageProcessor` (smaller — Pint's is more JSON-ish)
+ any future fixer-style tool's reader.

**Pro**:
- Two existing consumers; biome, prettier, dprint would benefit
  too — broader value than checkstyle.
- The diff parser is non-trivial (~120 LOC of hunk-walking + range
  conversion), exactly the kind of "thing every plugin gets wrong
  once."

**Contra**:
- CS-Fixer wraps the udiff in its own `<report>/<applied_fixer>`
  XML envelope; Pint uses a slightly different envelope. The reader
  must take an envelope-extraction lambda or be composable.
- biome/prettier emit pure JSON, not XML-wrapped — so the
  "envelope" abstraction is mandatory.

**Recommendation: `:core` — promote**, but design as
`UdiffReader.parse(udiffText: String): List<ReplaceFix>` (low-level)
plus a higher-level `ReaderWithUdiffPayload(envelopeParser:
(String) -> List<DiffPayload>)` composable reader. CS-Fixer's
reader becomes 40 LOC; Pint's becomes 40 LOC; biome's would be
30 LOC.

---

### 2.4. Auto-wired right-click action group: "Add to ignored"

**What it'd replace**: 5 of 6 `<Tool>AddToIgnoredAction` one-liners
(each is 25–40 LOC, all identical except for which
`QualityToolType` they return).

**Pro**:
- The class literally returns one constant — `getQualityToolType()`
  is the only override.
- The SDK already knows every registered tool via `ToolRegistry`;
  the action group can enumerate them at runtime.

**Contra**:
- Right-click menu UX may need per-tool ordering or grouping that
  the SDK doesn't know about.
- Some tools (Pint) skip the action entirely. The mechanism must
  allow opt-out.

**Recommendation: `:ui` — promote** as `QualityToolsAddToIgnoredActionGroup`
that auto-enumerates tools. Opt-out via `QualityTool.contributesAddToIgnored
: Boolean = true`. Plan covers this as gap G5 — concrete
specification: action group reads `IgnorePolicyRegistry`, for each
tool whose active profile has a `GlobPathIgnorePolicy` it adds a
child action keyed on `(tool, ignorePolicy)`. Net deletion: ~180 LOC.

---

### 2.5. `BinaryValidator` (validate-button + min-version)

**What it'd replace**: per-tool inlined regex + Swing in
`<Tool>ConfigurableForm.validateMessage()`. 6 of 6 plugins have it.

**Pro**:
- The widget (Validate button + label) is identical across all
  6 plugins.
- All 6 tools have a "tool name + version regex" — the only
  variable parts are (a) the regex itself, (b) the optional
  min-version check, (c) the success label format.

**Contra**:
- phpcs validates BOTH binaries (phpcs and phpcbf). Need to
  validate per `BinaryRole`.

**Recommendation: `:core` interface + `:ui` widget**. `QualityTool.binaryValidator
: BinaryValidator?` (gap G1). Phpcs returns a composite validator;
all others return a one-line regex matcher. Net deletion: ~600 LOC
(6 plugins × 100 LOC of `<Tool>ConfigurableForm.validateMessage`).

---

### 2.6. `ComposerToolDetector` skeleton (the recurring 80% of `<Tool>ComposerConfig`)

This is the biggest single duplication: **1 636 LOC of nearly
identical composer-parsing code** across 6 plugins.

**Shape of every `<Tool>ComposerConfig`**:

1. Constant: `PACKAGE = "vendor/tool"` (e.g. `phpstan/phpstan`).
2. Constant: `RELATIVE_PATH = "bin/<binname>" + (Windows ? ".bat" : "")`.
3. `getQualityInspectionShortName()` (one return statement).
4. `applyRulesetFromComposer(project, configuration)`:
   - Reads `composer.json`.
   - Finds tool-specific config file in same dir (`phpstan.neon` /
     `psalm.xml` / `phpcs.xml` / `pint.json` / `phpmd.xml.dist`).
   - Writes it into options.
5. `applyInspectionSettingsFromComposer(...)`:
   - Parses `scripts.<tool>` text for tool-specific args
     (`--memory-limit=4G`, `--level=8`, etc.).
   - Writes those into options.
6. Helper `getMemoryLimit` / `getRulesetFromScripts` / regex.

**Pro**:
- 1 636 LOC of duplicated `composer.json` parsing across 6 tools.
- Composer integration is PHP-specific but tool-agnostic.
- All 6 plugins parse the same JSON paths (`require`,
  `require-dev`, `scripts.X`); only the tool-specific values
  differ.
- The "package name + bin name + config file name + args regex"
  set is tiny — perfect for a declarative descriptor.

**Contra**:
- Each tool has at least 1–2 quirks (PHPStan reads `memory-limit`,
  phpcs has `NonPSRStandard` enum, phpmd has 6 built-in rulesets).
  The generic helper must accept tool-specific extension points.
- Plugin authors may want full control over composer-parsing
  for niche tools.

**Recommendation: `:php` — promote** as `ComposerToolDescriptor`:

```kotlin
public interface ComposerToolDescriptor {
    public val packageName: String              // "phpstan/phpstan"
    public val binName: String                  // "phpstan"
    public val configFileNames: List<String>    // [phpstan.neon, phpstan.neon.dist]
    public val scriptKey: String                // "phpstan"
    public val scriptParsers: List<ScriptParser>
        get() = emptyList()
}

public interface ScriptParser {
    /** Extract option-key/value pairs from `composer.json` `scripts.<key>` text. */
    public fun parse(scriptLine: String, bag: OptionsBag)
}
```

Per-tool composer support drops to ~30 LOC (descriptor + 1–2 parsers).
Net deletion: ~1 300 LOC across 6 plugins. **Biggest single
duplication win in the entire analysis.**

---

### 2.7. Per-tool `<Tool>BlackList` (one-liner subclass)

**What it is**: Each plugin has a 24-line class that does
`extends QualityToolBlackList` and has a `getInstance(project)`
companion. Pure boilerplate.

**Pro**:
- 6 × 24 LOC = ~150 LOC of identical wrapper code.
- The SDK already unifies persistence via
  `QualityToolsProjectStorage`. The per-tool blacklist becomes one
  registered `GlobPathIgnorePolicy` profile keyed on the tool id.

**Contra**:
- None. The SDK's `IgnorePolicyRegistry` already supports this.

**Recommendation: `:core` — already absorbed**. Each plugin
registers a `GlobPathIgnorePolicy` in its `plugin.xml` via the EP;
zero per-tool subclass.

---

### 2.8. `<Tool>ConfigurationManager` + `<Tool>ConfigurationBaseManager` (App+Project pair)

**What it is**: 6 × ~130 LOC = ~780 LOC of identical
`PersistentStateComponent<Element>` + `@State` annotation pair.

**Pro**:
- Pure mechanical boilerplate; nothing in it is tool-specific
  except the `@State(name=...)` value (which is tool-id by string).
- The SDK already moves persistence into the unified
  `QualityToolsProjectStorage` (phase 04).

**Contra**:
- Migration: existing users have `php.xml` entries named per-tool;
  the storage migrator (also covered in phase 04 acceptance) must
  read them.

**Recommendation: `:core` — already absorbed**. Plan covers it
under "no `*Manager` services per tool" (rule 8 from README).

---

### 2.9. `<Tool>OpenSettingsProvider` (notification deep-link)

**What it is**: Each tool has a class extending
`ComposerLogMessageBuilder.Settings` whose only override is
`show(project) → ShowSettingsUtil.showSettingsDialog(project,
"<Tool> Settings")`.

**Pro**:
- Two of six plugins (PHPStan, Psalm) have it.
- It's purely "open my settings page" — the SDK knows every tool's
  settings page since it owns the auto-rendered panel.
- If 2 of 6 already do it, the other 4 *probably should* (composer
  notification UX gap).

**Contra**:
- Composer notification machinery (`ComposerLogMessageBuilder`)
  is owned by the platform; we'd need to bridge to it.

**Recommendation: `:php` — promote** as
`QualityToolSettingsLinkProvider` that auto-registers an entry per
tool in the composer-notification system. Net deletion: ~80 LOC.

---

### 2.10. `<Tool>ConfigurableForm` (Settings panel for one profile)

**What it is**: 6 × ~120 LOC = ~700 LOC. The form is "tool path
field + validate button + timeout spinner + custom-options slot."

**Pro**:
- Already covered by `AutoToolSettingsPanel` (phase 07). With
  `BinaryValidator` (gap G1) and the unified `OptionsSchema`, no
  per-tool form is needed.

**Contra**:
- Phpcs's two-binary case wants a second path field. The
  `AutoToolSettingsPanel` may need a "secondary binary" affordance
  (gap G10/G15).

**Recommendation: `:ui` — already absorbed by `AutoToolSettingsPanel`.**
Phpcs's case extended once (gap G10) covers it.

---

## 3. Promotion candidates — to `:php`

These are PHP-ecosystem-specific things that *don't* belong in
`:core` (which must stay language-agnostic) but *do* belong in a
shared `:php` module.

### 3.1. `PhpInterpreterBinarySource` and its wizard

Already in plan as `:php` deliverable. Every tool uses it via
`PhpStanRemoteConfiguration` / `PsalmRemoteConfiguration` / etc.
Note from each port plan: "remote support becomes free" — exactly
because the SDK does this once.

**Pro**:
- 6 × ~200 LOC = ~1 200 LOC of `<Tool>RemoteConfiguration` +
  `<Tool>RemoteConfigurationProvider` collapses to 0 LOC per tool.

**Contra**:
- None. This is the single biggest win the SDK delivers.

**Recommendation: `:php` — already planned.**

---

### 3.2. Stderr-filter for "The Xdebug PHP extension is active"

**What it is**: Identical regex match across PHPStan, Psalm,
CS-Fixer, Pint that filters out a noise warning.

**Pro**:
- Same exact string filtered by 4+ tools. Zero variability.
- It's a property of the *PHP runtime*, not of any specific tool —
  belongs in `:php`.

**Contra**:
- A user with valid Xdebug usage might want the warning.

**Recommendation: `:php` — promote** as `XdebugWarningSuppressor :
MessageEnricher`. Bundled, enabled by default. User can disable
via Settings → Quality Tools → Common → "Suppress Xdebug warnings".
Net deletion: 4 × ~15 LOC = 60 LOC.

---

### 3.3. `XdebugOffMutator` (env var injection)

Already planned. Every tool sets `XDEBUG_MODE=off`.

**Pro**:
- 6 of 6 tools want it. Already in `:php` plan.

**Contra**:
- None.

**Recommendation: `:php` — already planned.**

---

### 3.4. `PhpFileLanguageFilter` for `QualityToolsAnnotator`

**What it is**: Every tool's annotator filters
`psiFile.language.id == "PHP"` (some also reject blade templates,
some accept them). The base `QualityToolsAnnotator` in phase 08 is
generic; each language plugin provides a subclass.

**Pro**:
- 6 of 6 PHP tools use the same subclass shape.
- Generic-language SDK has no PHP knowledge; the subclass *must*
  live in `:php`.

**Contra**:
- Tools accepting blade templates would override.

**Recommendation: `:php` — promote** as `PhpQualityToolsAnnotator
: QualityToolsAnnotator("PHP")`. Tools just register
`<externalAnnotator language="PHP" implementationClass="...PhpQualityToolsAnnotator"/>`.
Net deletion: 6 × ~20 LOC = 120 LOC.

---

### 3.5. `PhpToolBlacklistDefaultPolicy`

**What it is**: Every PHP tool ends up blacklisting the same
patterns by default (`vendor/**`, `node_modules/**`). Today plugins
either (a) ignore the issue, (b) hard-code patterns in their
config.

**Pro**:
- Saves users from re-adding `vendor/**` to every tool.
- PHP-ecosystem-specific (not relevant for JS / Rust).

**Contra**:
- Some users *do* want to lint vendor packages. Defaults are
  always wrong for someone.

**Recommendation: `:php` — promote** as
`PhpStandardIgnoreContributor` that contributes default patterns
when a `GlobPathIgnorePolicy` is created. User can edit / clear.
Net new capability, ~20 LOC.

---

### 3.6. `PhpComposerWatcher` shared facility

Every plugin watches the same `vendor/bin/<X>` path with similar
VFS listener code. Today our SDK has a `ConfigSourceType.watch(...)`
hook (phase 02), but the *implementation* of "watch composer's
vendor/ for a binary appearing" is identical across PHP tools.

**Pro**:
- 6 × ~30 LOC of `composer.json + vendor/bin/<x>` VFS watching is
  duplicated.
- Centralizing means **one listener** watches `composer.json`
  changes and notifies all registered tools, instead of N parallel
  listeners.

**Contra**:
- The single-listener fan-out adds tiny coupling complexity.

**Recommendation: `:php` — promote** as `ComposerVendorWatcher`
service that fan-out's `vendor/bin/<binName>` events to registered
`ConfigSourceType`s. Net deletion: 6 × 30 = 180 LOC. Bonus:
removes 5 redundant VFS listeners → less indexing overhead.

---

### 3.7. `PhpToolVersionParser` helpers

**What it is**: Each plugin has its own regex:

- PHPStan: `"PHPStan.* ([\d.]*)"`
- Psalm: `"Psalm (.*)"`
- phpcs: `"version (.*)"` + min-version 1.5.0 check
- CS-Fixer: `"PHP CS Fixer .* (\d+.*)"`
- Pint: `"Laravel Pint v(\d+.*)"`
- phpmd: `"PHPMD (\d+.*)"`

**Pro**:
- All six match `<ToolName>.*?(\d+\.\d+(\.\d+)?(-\w+)?)`.
- The `Version` class comparison (`<` for min-version) is also
  identical.

**Contra**:
- The "tool name" string for each is plugin-specific.

**Recommendation: `:php` — promote** as `PhpToolVersionParser(
toolNamePattern: String, minVersion: Version? = null
): BinaryValidator`. Each plugin instantiates with one string + an
optional min-version. Net deletion: 6 × ~30 = 180 LOC.

---

## 4. Things that LOOK promotable but shouldn't be

These pattern-match across plans but have hidden per-tool semantics
that make centralization a trap.

### 4.1. `<Tool>AnnotatorProxy.getOptions(...)` (the arg-building)

**Why it looks promotable**: Six classes, all called
`<Tool>AnnotatorProxy`, all override `getOptions(filePath,
inspection, profile, project)`.

**Why it isn't**: Every implementation is wildly different in
behavior — what flags to pass, which paths to include, when to
include the project root, how to switch between on-the-fly vs
batch arg sets, how to substitute temp-file paths. That's the
*entire reason* the SDK exposes `QualityTool.buildArgs(...)` as a
user override.

**Recommendation: stays in plugin.** The SDK contract is right;
the duplication is illusory.

---

### 4.2. `<Tool>MessageProcessor`

**Why it looks promotable**: 6 classes, all parsing tool output.

**Why it isn't**: Each format is different at the schema level
(checkstyle vs phpmd-native XML vs JSON-with-udiff vs Psalm-JSON).
Three already share `CheckstyleXmlReader`; the other three need
their own readers anyway.

**Recommendation: stays in plugin** for tool-specific outputs;
`:core` ships the three common readers (checkstyle, JSON, udiff).

---

### 4.3. Inspection short-name (e.g. `PhpStanGlobal`, `PhpStanValidation`)

**Why it looks promotable**: Every plugin has two of these short
names hard-coded for legacy compatibility.

**Why it isn't**: The naming convention is **not regular**:

- PHPStan: `PhpStanGlobal` + `PhpStanValidation`.
- Psalm: `PsalmGlobal` + `PsalmValidation`.
- phpcs: `PhpCSGlobal` + `PhpCSValidation`.
- Pint: `Laravel_Pint_validation_tool` (snake_case, single name).
- phpmd: `MessDetectorValidation` (single name).

Generating these from a pattern would break Pint's snake_case
and phpmd's single-name case. Existing user profile XMLs reference
each one verbatim.

**Recommendation: stays in plugin**, declared as a `Set<String>`
on `QualityTool.inspectionShortNames` (phase 01). The SDK reads,
doesn't generate.

---

### 4.4. `<Tool>RulesetAnalyzer` (config-file content sniffing)

**Why it looks promotable**: 3 of 6 plugins have one.

**Why it isn't**: Each sniffer reads a tool-specific schema. phpcs
parses XML ruleset structure; CS-Fixer reads `.php-cs-fixer.php`
PHP file; phpmd reads ruleset XML.

**Recommendation: stays in plugin.** No common abstraction.

---

### 4.5. `<Tool>OptionsSchema` declarations

These are pure data declarations. Generic SDK code can't generate
them.

**Recommendation: stays in plugin.** They're the *interface* the
plugin exposes to the SDK; that's by design.

---

### 4.6. `<Tool>Tool : QualityTool` implementations

The whole point of the SDK is to let tools register themselves.
`PhpStanTool`, `PsalmTool` etc. must exist per plugin.

**Recommendation: stays in plugin.**

---

## 5. Summary table — what moves where

| Candidate | Today (LOC × 6) | Recommendation | New home | Per-plugin LOC after |
| --- | --- | --- | --- | --- |
| `<Tool>BlackList` | 150 | absorbed by `IgnorePolicyRegistry` | `:core` (already) | 0 |
| `<Tool>ConfigurationManager` × 2 | 780 | absorbed by `QualityToolsProjectStorage` | `:core` (already) | 0 |
| `<Tool>ConfigurableForm` | 700 | absorbed by `AutoToolSettingsPanel` | `:ui` (already) | 0 |
| `<Tool>AddToIgnoredAction` | 180 | auto-wired by SDK | `:ui` (gap G5) | 0 |
| `<Tool>SettingsTransferStartupActivity` | 340 | `LegacyInspectionFieldsMigrator` | `:core` (new) | 20 (mapping) |
| `<Tool>ComposerConfig` | **1 636** | `ComposerToolDescriptor` | `:php` (new) | 30 |
| `<Tool>RemoteConfiguration*` | 2 000 | `PhpInterpreterBinarySource` | `:php` (already) | 0 |
| `<Tool>OpenSettingsProvider` | 80 (2/6) | auto-registered | `:php` (new) | 0 |
| `<Tool>ConfigurableForm.validateMessage` | 600 | `PhpToolVersionParser` | `:php` (new) | 5 (regex + min-version) |
| Composer VFS listeners | 180 | `ComposerVendorWatcher` | `:php` (new) | 0 |
| `Xdebug active` filter | 60 (4/6) | `XdebugWarningSuppressor` | `:php` (new) | 0 |
| PHP language filter on annotator | 120 | `PhpQualityToolsAnnotator` | `:php` (new) | 0 |
| Default `vendor/**` ignore patterns | 0 today | `PhpStandardIgnoreContributor` | `:php` (new, capability) | 0 |
| `<Tool>AnnotatorProxy.getOptions` | 1 100 | **stays** | per-plugin | full |
| `<Tool>MessageProcessor` (custom output) | 700 (3 of 6) | **stays** | per-plugin | full |
| `<Tool>RulesetAnalyzer` | 80 (3 of 6) | **stays** | per-plugin | full |
| `<Tool>OptionsSchema` | 240 | **stays** (declarative data) | per-plugin | full |
| `<Tool>Tool : QualityTool` | 360 | **stays** | per-plugin | full |

**Aggregate**:

- **Today**: ~9 000 LOC of duplicated infrastructure across 6
  plugins.
- **Promoted to `:core`**: 4 categories = ~1 470 LOC → ~120 LOC
  (~92% reduction); SDK code ~200 LOC.
- **Promoted to `:php`**: 7 categories = ~4 380 LOC → ~70 LOC
  (~98% reduction); SDK code ~400 LOC.
- **Stays per-plugin**: 5 categories = ~2 480 LOC remain.
- **Generic SDK code added**: ~600 LOC total.
- **Net**: ~9 000 LOC of duplication amortized into ~600 LOC of
  shared SDK code, leaving ~190 LOC of mapping/glue per plugin.

The synthesis already counted the **feature-gap** impact (~1 340 LOC
SDK absorbs ~16 000 LOC legacy → 12× ratio). This document refines
that into **specific class-level promotion** which is a sharper
target for engineering work: not "add a feature" but "ship this
class so 6 plugins can delete their copies."

---

## 6. Sequencing recommendation

Promotions are independently mergeable. Suggested order:

1. **`PhpComposerVendorWatcher`** (`:php`) — biggest single win,
   unblocks `ComposerToolDescriptor` adoption.
2. **`ComposerToolDescriptor`** (`:php`) — collapses the
   1 636 LOC composer-parsing duplication.
3. **`PhpToolVersionParser`** (`:php`) — combined with `BinaryValidator`
   gap G1 → validate-button widget.
4. **`PhpQualityToolsAnnotator`** (`:php`) — saves 120 LOC per
   plugin's annotator class.
5. **`LegacyInspectionFieldsMigrator`** (`:core`) — only needed
   when porting tools that have legacy users, can wait until phpcs/
   PHPStan ports start.
6. **`XdebugWarningSuppressor`** (`:php`) — tiny, no urgency.
7. **`PhpStandardIgnoreContributor`** (`:php`) — opt-in capability.
8. **Auto-wired `Add to ignored` group** (`:ui`, gap G5) — small
   but visible UX win.

Total runway: ~2 engineer-weeks of `:php` work + ~1 engineer-week
of `:core` work + ~1 week of `:ui` work, on top of the Tier 1
patches from the synthesis. Pays for itself in the first port.

---

## 7. Risks of promotion (the contra side, summarized)

| Risk | Where | Mitigation |
| --- | --- | --- |
| Over-generalization — the SDK helper grows to cover edge cases | `ComposerToolDescriptor` could absorb every per-tool quirk | Keep the descriptor *declarative*; force tool quirks into explicit `ScriptParser` callbacks |
| Breaking-change risk if the helper signature evolves | every `:core` promotion | Apply the cycle-5 semver rules (default impls + `aliasFieldNames`) |
| Plugin author can't override the helper for an unusual case | `XdebugWarningSuppressor`, `PhpStandardIgnoreContributor` | All bundled `MessageEnricher`s and `IgnorePolicyContributor`s are EP-driven; consumer can register their own that overrides |
| Hidden coupling between tools sharing a watcher | `ComposerVendorWatcher` fan-out | Watcher emits events to a `Flow`; consumers subscribe; no shared state |
| `:php` module bloats with capabilities that PhpStorm-Core uses too | most `:php` candidates | Already separate from `:core`; PHP-only IDE users opt in anyway |
| User expects per-tool customization that the helper hides | `PhpToolVersionParser` regex | Helper is `fun newToolVersionParser(...)`; consumer can implement `BinaryValidator` directly |

None of these is a blocker. All are paper cuts manageable via the
SDK's existing extension-point mechanism.

---

## 8. Conclusion

The synthesis identified **34 feature gaps**; this analysis adds a
parallel axis — **18 specific class-shape promotion candidates**
totalling ~9 000 LOC of recurring infrastructure across 6 plugins.

Of those 18:

- **6** are already covered by the existing phase plan (BlackList,
  ConfigurationManager pair, ConfigurableForm, RemoteConfiguration,
  XdebugOffMutator, CheckstyleXmlReader).
- **7 promotions are recommended** to `:php` — composer descriptor,
  composer watcher, version parser, annotator subclass, Xdebug
  filter, settings-link provider, default ignore contributor.
- **2 promotions are recommended** to `:core` —
  `LegacyInspectionFieldsMigrator` and `UdiffReader`.
- **1 promotion is recommended** to `:ui` — auto-wired
  Add-to-ignored action group.
- **5 candidates explicitly should NOT be promoted** —
  AnnotatorProxy.getOptions, custom-format MessageProcessors,
  inspection short-names, RulesetAnalyzer, OptionsSchema /
  QualityTool implementations themselves.

Total SDK code to add (across `:core`, `:php`, `:ui`): ~600 LOC.
Aggregated deletion across 6 plugins: ~6 700 LOC. **Ratio: ~11×
amortization.**

Combined with the synthesis-level feature-gap fixes (~1 340 LOC
SDK, ~16 000 LOC absorbed → 12× ratio), the total SDK foundation
absorbs **~22 700 LOC of legacy PHP-quality-tool plumbing** through
**~1 940 LOC of generic infrastructure** — net ~11.7× across all
class-level + feature-level promotions.
