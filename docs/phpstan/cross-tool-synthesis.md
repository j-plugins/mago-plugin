# Cross-tool SDK gap synthesis

> Six port plans (PHPStan, Psalm, phpcs, PHP-CS-Fixer, Laravel Pint,
> Mess Detector) each surfaced a list of gaps in the new
> `:quality-tools-sdk` that would force the plugin author to write
> "generic-shaped" code if they shipped the port today.
>
> This document **deduplicates** those gaps across the six plans
> and produces one consolidated patch list. Gaps that appear in 3+
> tools get priority; gaps unique to one tool are still
> worth doing but stay lower in the queue.
>
> Read alongside the individual plans in
> `docs/{phpstan,phpcs,php-cs-fixer,laravel-pint,mess-detector,psalm}/`.

---

## 1. Counting frequency

Each row is one logical gap; columns mark which port-plan exercised
it.

Legend: ✅ = plan opens this gap explicitly. ⊙ = plan mentions it
as latent / nice-to-have / cross-referenced from another plan.

| # | Gap | PHPStan | Psalm | phpcs | CS-Fixer | Pint | phpmd | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| G1 | `BinaryValidator` SAM (validate-button parses `--version`, optional min-version) | ✅ 4.1 | ⊙ 4.0 | ✅ 4.6 | ⊙ 4.0 | ✅ 4.1 | ⊙ | **6** |
| G2 | `ConfigSourceType.defaultTimeoutMs` override (60s for remote, 5s for some local) | ✅ 4.4 | ⊙ 4.0 | ⊙ | ⊙ | ✅ 4.5 | ⊙ | **6** |
| G3 | `OnDetectedHook` on `ConfigSourceType` (Composer detect → enrich options from `phpstan.neon` / `psalm.xml` / `phpcs.xml` / `pint.json` / `phpmd.xml.dist` / `composer.json scripts.X` memory-limit) | ✅ 4.5 | ⊙ 4.0 | ✅ 4.5 | ⊙ | ✅ 4.6 | ⊙ | **6** |
| G4 | Stderr filtering / muting tool-noise messages (Xdebug warning, "system is ready" balloon) | ✅ 4.6 (no SDK change — `MessageEnricher` suffices) | — | ⊙ | ⊙ | ✅ 4.7 | — | **3** |
| G5 | Auto-wired right-click "Add to ignored" action group | ✅ 4.7 | ⊙ | ⊙ | ⊙ 4.13 (unified ignore) | ✅ 4.8 | ⊙ | **6** |
| G6 | Inspection-shortname preservation + 5–7 inline fields → unified options migration | ✅ 4.8 | ⊙ 4.0 | ✅ 4.8 | ⊙ | ⊙ | ✅ 4.7 | **6** |
| G7 | Batch-mode "one project-wide run, cached per-file results" (`inspectionStarted` dance) | ✅ 4.9 | ⊙ | ✅ 4.9 | "not applicable" | "no globalInspection" | ✅ 4.8 | **4** |
| G8 | Tool-version-aware arg construction (`ResolvedBinary.detectedVersion`) | ✅ 4.3 | ⊙ | ✅ (used by min-version check) | ⊙ | ✅ 4.3 | ⊙ | **5** |
| G9 | Per-mode reader id override (multiple output formats per tool: checkstyle / JSON / GitHub) | ✅ 4.2 | ⊙ | ⊙ | ⊙ | ✅ 4.2 | — | **4** |
| G10 | **Secondary binary on a `ConfigSource`** (phpcs+phpcbf — two paths on one source) | — | — | ✅ 4.1 | — | — | — | **1** |
| G11 | **Per-message `ruleId` + `tags` for fixable/non-fixable** | — | — | ✅ 4.2 | ⊙ 4.14 | — | ⊙ | **3** |
| G12 | **Formatter `executionStyle = "format"` + `formattingOutputMode = "in_place"` + auto-action wiring** | — | — | ✅ 4.3, 4.7 | ✅ (whole point) | ✅ 4.9 | — | **3** |
| G13 | **`DynamicChoiceSpec` — option values come from a `ResolvedBinary` invocation** (e.g. `phpcs -i`) | — | — | ✅ 4.4 | ✅ 4.16 | — | ✅ 4.6 (ruleset list) | **3** |
| G14 | **`OptionVisibilityRule` — sentinel value (`"Custom"`) swaps another field's visibility** | — | — | ⊙ | ✅ 4.17 | ⊙ | ✅ 4.2 (custom ruleset) | **3** |
| G15 | **Two binary roles + remote SDK file transfer** (phpcs/phpcbf both need transfer mappings) | — | — | ✅ 4.10 | — | — | — | **1** (interaction with G10) |
| G16 | **Per-message tag-driven quick-fix attachment** (e.g. `fixable="1"` → wire phpcbf as `RunCli` fix) | — | — | ✅ 4.11 | ✅ 4.15 InvokeMode | — | — | **2** |
| G17 | **Trusted-project gating for `format` modes** (writes the file — needs `TrustedProjects.isTrusted`) | — | — | ✅ 4.12 | ⊙ | ⊙ | — | **2** |
| G18 | **Process-output VFS refresh after format** (`VfsUtil.markDirtyAndRefresh` post-write) | — | — | ✅ 4.13 | ⊙ | ⊙ | — | **2** |
| G19 | **Cross-EP intention actions** (a tool's `<intentionAction>` keyed on someone else's annotation/category) | — | — | ✅ 4.14 | ⊙ | — | — | **2** |
| G20 | **Unified `IgnorePolicy` evaluated by `format` modes too** (not just lint-style annotators) | — | — | — | ✅ 4.13 | ⊙ | — | **2** |
| G21 | **`UdiffReader`** — parses unified-diff hunks emitted by CS-Fixer / Pint / future formatters | — | — | — | ✅ 4.14 | ⊙ (extends CS-Fixer) | — | **2** |
| G22 | **"Quick-fix that invokes a different mode of the same tool"** (CS-Fixer `--dry-run` → re-run without it) | — | — | ⊙ 4.11 | ✅ 4.15 | ⊙ | — | **3** |
| G23 | **Project-level "active formatter" selection** (which tool runs on Ctrl+Alt+L for PHP files) | — | — | — | ✅ 4.18 | ✅ 4.10 | — | **2** |
| G24 | **Generic "format on commit" checkin handler** (today `PhpExternalFormatterCheckinHandler` is shared by CS-Fixer + Pint) | — | — | — | ✅ 4.19 | ✅ 4.10 | — | **2** |
| G25 | **Per-mode `WorkingDirResolver`** (CS-Fixer wants working dir at file's containing dir for relative `.php-cs-fixer.php` discovery; phpmd wants project root) | — | — | — | ✅ 4.20 | — | — | **1** |
| G26 | **phpmd-native XML reader (not checkstyle)** | — | — | — | — | — | ✅ 4.1 | **1** |
| G27 | **Multi-ruleset / `ChoiceListSpec`** (compound spec: list of choices, some from a known set, some custom files) | — | — | — | — | — | ✅ 4.2 | **1** |
| G28 | **Path-aware arg rewriting for CSV-of-mixed-tokens** (`--rulesets=cleancode,/abs/path/extra.xml,naming`) | — | — | — | — | — | ✅ 4.3 | **1** |
| G29 | **PHP interpreter-change listener under `:php`** (re-detect rulesets when interpreter changes) | — | — | ⊙ | — | — | ✅ 4.5 | **1** |
| G30 | **Ruleset file picker that filters by content (XML root tag)** | — | — | — | — | — | ✅ 4.6 | **1** |
| G31 | **Per-tool "Generate config" notification action** (Psalm's `psalm --init . 3` affordance) | — | ✅ 4.1 | — | — | — | — | **1** |
| G32 | **`onTimeoutActions` per `QualityTool`** (Psalm's "Recreate cache" button on timeout) | — | ✅ 4.2 | — | — | — | — | **1** |
| G33 | **Non-PascalCase inspection short-names** (Pint's `Laravel_Pint_validation_tool`) | — | — | — | — | ✅ 4.4 | — | **1** |
| G34 | **Legacy XML tag with cross-tool name collision** (Psalm's `psalm_fixer_by_interpreter` copy-paste typo from CS-Fixer; must round-trip) | — | ✅ 4.4 | — | — | — | — | **1** |

**Total unique gaps: 34**. **Common (≥3 tools): 14** (G1–G9, G11, G12, G13, G14, G16, G22).

---

## 2. Consolidated patch list (by priority)

### Tier 1 — open in ≥4 plans. Must ship before any port.

| # | Gap | Phase to patch | Estimated LOC |
| --- | --- | --- | --- |
| **G1** | `BinaryValidator` SAM on `QualityTool` (optional). `AutoToolSettingsPanel` shows a "Validate" button when present. | 01 + 07 | ~60 |
| **G2** | `ConfigSourceType.defaultTimeoutMs: Long` (default 30 000). `ConfigProfile.timeoutMs` seeds from it on creation. | 02 + 04 | ~10 |
| **G3** | `ConfigSourceType.onDetected(source, ctx, bag): Unit` callback fired when `watch(...)` produces a new source. Used to enrich `OptionsBag` from `phpstan.neon` / `psalm.xml` / `phpcs.xml` / `pint.json` / `phpmd.xml.dist` / `composer.json scripts.X`. | 02 + 04 | ~30 |
| **G5** | `:ui` auto-wires a right-click "Add to ignored" submenu enumerating every registered tool with a `GlobPathIgnorePolicy`. Generic `QualityToolsAddToIgnoredActionGroup`. | 07 | ~80 |
| **G6** | `LegacyInspectionFieldsMigrator` — generic helper that reads N public fields from a legacy `globalInspection` profile entry and writes them as `OptionSpec` values in the unified storage, gated on a `transferred` marker. Plus `QualityTool.inspectionShortNames: Set<String>` preservation (already in phase 10a.1). | 10 (migration) | ~120 |
| **G7** | Batch-mode "one project-wide run + per-file cache". `QualityToolsAnnotator` invokes `ToolRunner` once with `target = ToolTarget.Project`, caches `messagesByFile: Map<String, List<ToolMessage>>` per inspection run; `checkFile` reads from the cache. | 08 | ~80 |
| **G8** | `ResolvedBinary.detectedVersion: String?` (default null) populated by `BinaryValidator` when used. `buildArgs` can branch on it. | 02 | ~10 |
| **G9** | `ToolMode.resultReaderId: String?` overrides the tool-level default. `QualityToolsAnnotator` falls back through `mode.resultReaderId ?: tool.resultReaderId`. | 01 | ~10 |

**Tier 1 total: ~400 LOC of generic SDK code. Unlocks all 6 ports.**

### Tier 2 — open in 2–3 plans. Ship before format-tool ports.

| # | Gap | Phase | Est LOC |
| --- | --- | --- | --- |
| **G11** | `ToolMessage.ruleId: String?` (already in phase 06 cycle-3 fix) + `tags: Set<String>` (already there). Reader contract docs: "use `tags = setOf("fixable")` when the tool marks a message as auto-fixable." | 06 (doc only) | 0 |
| **G12** | `executionStyle = "format"` + `formattingOutputMode ∈ {"stdout","in_place"}` plus auto-registered `<intentionAction>` "Reformat with X" and `<action>` in Code menu, contributed by `:ui` for every tool with a format-mode. | 07 | ~120 |
| **G13** | `DynamicChoiceSpec` — `OptionSpec` variant whose `values: List<String>` are produced by a `ToolRunner` invocation (e.g. `phpcs -i`). Cached per-source-instance, invalidated by `OnAvailabilityChanged`. | 04 + 07 | ~70 |
| **G14** | `OptionVisibilityRule` — small SAM that, given the current `OptionsBag`, decides which other specs to hide/show. Sentinel-driven "Custom" → file-picker swap. | 04 + 07 | ~40 |
| **G16** | Generic per-tag `ToolFixHandler` registration — readers emit `ToolMessage.tags = setOf("fixable")` and the SDK's bundled handler wires `RunCli` fix from the tool's "fix" mode. | 06 + 07 | ~30 |
| **G22** | `InvokeModeFix : ToolFix` — built-in fix kind whose `apply()` re-invokes the same tool's other mode (CS-Fixer dry-run → fix). | 06 + 07 | ~30 |

**Tier 2 total: ~290 LOC.**

### Tier 3 — open in 2 plans (formatter-pair).

| # | Gap | Phase | Est LOC |
| --- | --- | --- | --- |
| **G17** | Trusted-project gate: `format` modes refuse to run when `!TrustedProjects.isTrusted(project)`. | 08 | ~10 |
| **G18** | Post-format VFS refresh: `AsyncFormattingServiceAdapter` calls `VfsUtil.markDirtyAndRefresh(...)` after `in_place` rewrites. | 07 | ~10 |
| **G19** | Cross-EP `<intentionAction>` — tools contribute intentions keyed on other tools' annotation categories (e.g. `phpcs.fixable` → "Apply with phpcbf"). New EP `dev.jplugins.qualityTools.crossToolIntention`. | 07 | ~80 |
| **G20** | Unified `IgnorePolicy` evaluated by `format` modes too — not just by the lint annotator. `AsyncFormattingServiceAdapter` runs the same chain. | 07 | ~30 |
| **G21** | `UdiffReader` bundled in `:core` — parses `<report>/<applied_fixer>` + unified-diff hunks into `ReplaceFix`/`PatchFix`. Reusable by CS-Fixer, Pint, prettier, biome. | 06 | ~120 |
| **G23** | Project-level "active formatter for language X" stored in `QualityToolsProjectStorage` instead of per-tool `*ExternalFormatterConfiguration`. Auto-detected combo-box in Settings. | 04 + 07 | ~80 |
| **G24** | Generic `FormatOnCommitCheckinHandler` — replaces today's tool-specific `PhpExternalFormatterCheckinHandler`. EP `dev.jplugins.qualityTools.commitFormatter`. | 07 | ~120 |

**Tier 3 total: ~450 LOC.**

### Tier 4 — open in exactly 1 plan. Ship together with their tool's port.

| # | Gap | Tool | Ship with |
| --- | --- | --- | --- |
| **G10** + **G15** | Secondary binary on a `ConfigSource` (phpcs+phpcbf) | phpcs | phpcs port |
| **G25** | Per-mode `WorkingDirResolver` | CS-Fixer | CS-Fixer port |
| **G26** | `PhpmdXmlReader` (phpmd-native XML, NOT checkstyle) | phpmd | phpmd port (stays in plugin) |
| ~~**G27**~~ | ~~`ChoiceListSpec`~~ — **revisited after Mess Detector plan finalized.** Multi-ruleset composes from 6 `BoolSpec`s (one per closed-set name) + one `ListSpec<CompoundSpec>` for custom XMLs. No new spec kind required. | phpmd | **dropped** |
| **G28** | `compositeKvPathArg(key, parts)` with per-part `isPath` flag — `--rulesets=codesize,/abs/foo.xml,naming` where some CSV parts are paths and some aren't. New helper in `ToolArgs.kt` (phase 01) + rewriter change (phase 05). | phpmd | ~30 LOC |
| **G29** | `ConfigSource.staleness(): Flow<StaleReason>` — an existing `ResolvedBinary` has gone stale (PHP interpreter changed under the source, container restarted). Phase 02's `watch` and `onAvailabilityChanged` don't cover this case. Replaces the originally-scoped `:php` interpreter listener. | phpmd | phase 02 addendum (~50 LOC) |
| **G30** | Ruleset file picker filtered by XML-root-tag content | phpmd | stays in phpmd plugin's wizard |
| **G31** | Per-tool "Generate config" notification action (`psalm --init`) | Psalm | `QualityTool.initConfigAction: () -> Unit?` — generic |
| **G32** | `onTimeoutActions` per `QualityTool` (Psalm "Recreate cache") | Psalm | generic — added to `QualityTool` |
| **G33** | Non-PascalCase inspection short-names (`Laravel_Pint_validation_tool`) | Pint | SDK acceptance — drop short-name validation regex |
| **G34** | Legacy XML tag with cross-tool name collision (`psalm_fixer_by_interpreter`) | Psalm | SDK doc — `SerializedField.aliases` covers it |

**Tier 4: 11 small SDK additions (~10–30 LOC each) + 4 plugin-specific carve-outs.**

---

## 3. Aggregate impact

### 3.1. SDK changes summary

| Tier | Count | Total SDK code |
| --- | --- | --- |
| 1 | 8 gaps | ~400 LOC |
| 2 | 6 gaps | ~290 LOC |
| 3 | 7 gaps | ~450 LOC |
| 4 | 11 gaps | ~200 LOC (tool-by-tool) |
| **Total** | **32 gaps** | **~1340 LOC of generic SDK work** |

— vs. the **~16 000 LOC** that today lives across the 6 legacy
plugins (PHPStan 2 650 + Psalm 2 460 + phpcs 3 200 + CS-Fixer 2 750
+ Pint 900 + phpmd 2 900 + shared bridge classes). Roughly
**1340 LOC SDK** absorbs the **~16 000 LOC** of duplication. **~12×
amortization.**

### 3.2. Post-port LOC per tool

Taken from each plan's §6:

| Tool | Today | Post-port | Reduction |
| --- | --- | --- | --- |
| PHPStan | 2 650 | ~360 | 7.4× |
| Psalm | 2 460 | ~330 | 7.5× |
| phpcs | 3 200 | ~480 | 6.7× |
| PHP-CS-Fixer | 2 750 | ~490 | 5.6× |
| Laravel Pint | 900 | ~270 | 3.3× |
| Mess Detector | 2 490 | ~480 | 5.2× |
| **Total** | **14 860** | **~2 350** | **6.3× average** |

(Pint's reduction is lower because it has the smallest baseline —
the SDK has less to absorb.)

### 3.3. Ordering recommendation

Sequenced so each step ships independently with a green build:

1. **Tier 1 SDK patches** (~400 LOC, 1–2 weeks).
2. **Mago migration** (already planned as phase 10) — validates
   Tier 1 against a real tool.
3. **PHPStan port** — validates Tier 1 against a JetBrains tool we
   didn't design with.
4. **Tier 2 SDK patches** (~290 LOC) + **Psalm port** — exercises
   format-style none, validates the per-mode-reader gap.
5. **phpcs port** — needs Tier 4 G10/G15 (secondary binary).
6. **Tier 3 SDK patches** (~450 LOC) + **CS-Fixer port** — first
   real formatter-style consumer.
7. **Pint port** — second formatter, validates CS-Fixer plumbing.
8. **Mess Detector port** + Tier 4 G27/G28/G29 — validates
   `ChoiceListSpec` and CSV-path-mix.

Total runway: roughly **6–10 engineer-weeks** from Tier 1 merge to
all 6 tools shipped on the new SDK. Compatible with the original
62-story-point estimate in phase 10 + this synthesis.

---

## 4. Open questions for stakeholder review

1. **Should the SDK bundle `PhpmdXmlReader`?** Only phpmd uses it
   today; but other tools may emit similar custom XML. Recommend:
   stays in phpmd plugin until 2nd consumer appears.
2. **`DynamicChoiceSpec` — when does its cache invalidate?** Today's
   plans say "on interpreter change" via G29. Alternative: refresh
   on every Settings dialog open. Pick a policy.
3. **`InvokeModeFix` UX**: does running it count as "user-initiated
   write" for `Undo` purposes? Probably yes; document in phase 06.
4. **`FormatOnCommitCheckinHandler` priority** vs. existing
   `<checkinHandlerFactory>` ordering — figure out how
   PhpExternalFormatterCheckinHandler is currently sequenced.
5. **`crossToolIntention` EP** — does a 2-use case justify a new EP,
   or should it be a plain `<intentionAction>` keyed on
   `category="phpcs.fixable"`? Recommend: plain `<intentionAction>`;
   no new EP.
6. **`OptionVisibilityRule` evaluation timing** — on every
   `OptionsBag.set` call, or on UI render only? Recommend:
   on render + on debounced `set` (200 ms).
7. **`onDetected` hook threading** — runs on background or EDT?
   For phpstan/psalm config-discovery it can be background; for
   showing a notification it must hop to EDT. Document.
8. **Should the legacy `Laravel_Pint_validation_tool` short-name
   collision be left as a wart**, or do we normalize all
   short-names to PascalCase during migration? Probably wart —
   user profiles depend on it. Confirm.

---

## 5. Conclusion

Six independent port plans, written by six independent agents over
the same SDK design, converged on roughly the same gap list with no
fundamental redesigns needed. The pattern is:

- 14 gaps are common (3+ tools).
- 11 small additions handle long-tail tool quirks generically.
- 0 gaps require revisiting phase 01/02 contracts — every patch is
  additive.
- Average per-tool reduction is **6.3×** (Pint 3.3× to Psalm 7.5×).
- Aggregate amortization is **~12×**: 1340 LOC of SDK replaces
  ~16 000 LOC of legacy duplication.

The SDK design holds up against the most-integrated tools in the
PhpStorm ecosystem. Recommended to merge Tier 1 patches in parallel
with the Mago migration in phase 10, then start the per-tool ports
in the order above.
