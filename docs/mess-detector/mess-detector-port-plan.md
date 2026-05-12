# PHP Mess Detector port to `:quality-tools-sdk` — plan and analysis

> Goal: take the bundled JetBrains PHPMD support and rebuild it on top
> of our `:quality-tools-sdk`. Same shape as the PHPStan plan —
> **analysis only**, no Kotlin code. Implementation lands once the
> gaps are resolved.
>
> Why this exercise: PHPMD is the second SDK adopter after PHPStan.
> Superficially similar (PHP-only linter, XML output, Composer
> auto-detect, remote interpreter) but introduces shapes PHPStan
> didn't exercise: **multi-ruleset selection** (closed-set toggles
> merged with arbitrary user XML files into one CSV CLI arg), the
> phpmd XML output schema (not checkstyle), and reactivity to PHP
> interpreter-set mutation.

---

## 0. Reference: source material

Reverse-engineered classes I analysed (CFR 0.152):

- `com.jetbrains.php.tools.quality.messDetector.*` — 20 files,
  ~2,490 LOC. Bundled in `php-impl.jar` (id `com.jetbrains.php`,
  not a separate downloadable plugin like `phpstan`).
- `com.jetbrains.php.remote.tools.quality.messDetector.*` —
  2 files, ~388 LOC. Lives in `php-remoteInterpreter.jar`,
  registered via optional `<depends>` from the host PHP plugin.
- `com.jetbrains.php.remote.tools.quality.*` (3 base classes,
  ~560 LOC) — shared by all remote-aware quality tools. Same code
  path PHPStan uses (see PHPStan §0).

Other artefacts inside the PHP plugin: PHPMD reuses
`messages/PhpBundle` rather than shipping its own (unlike PHPStan).
Keys of note: `code.size.rules`, `controversial.rules`,
`design.rules`, `naming.rules`, `unused.code.rules`,
`quality.tool.label.custom.rulesets`, `add.rule`, `remove.rule`.
No PHPDoc completion contributor (rulesets are declarative XML,
not annotations). No `OpenSettingsProvider` companion (PHPMD never
grew the Composer-notification deep-link surface PHPStan has) — the
generic SDK replacement (PHPStan gap 4.7) gives it to PHPMD for free.

---

## 1. Inventory: what every class does today

### 1.1. Core "ports" of the legacy SDK

| Class | LOC | Role |
| --- | --- | --- |
| `MessDetectorQualityToolType` | 206 | EP entry point (`com.jetbrains.php.tools.quality.type`). Returns `displayName="Mess Detector"`, helpTopic `reference.settings.php.messdetector`. Wires up managers, blacklist, configurable. Inspection short name derived from the validation-inspection class — same legacy convention as phpcs / Psalm. |
| `MessDetectorConfiguration` | 144 | Per-instance config: `myMessDetectorPath`, `myMaxMessagesPerFile=50`, `myTimeoutMs=5000` (note: **5 s default**, vs. PHPStan's 30 s). `getId()=PhpBundle.message("local")`, no interpreter id. |
| `MessDetectorConfigurationManager` | 64 | Glue between project and app `MessDetectorConfigurationBaseManager`s. Two `@State` services (`MessDetector` in `php.xml` for project, `php-tools.xml` for app). |
| `MessDetectorConfigurationBaseManager` | 62 | `PersistentStateComponent<Element>` mirror of `PhpStanConfigurationBaseManager`. |
| `MessDetectorConfigurationProvider` | 31 | Abstract — `EP_NAME = "com.jetbrains.php.tools.quality.messDetector.messDetectorConfigurationProvider"`. `getInstances()` returns the single registered provider. |
| `MessDetectorProjectConfiguration` | 56 | Project state with `selectedConfigurationId` (workspace-file storage). |
| `MessDetectorBlackList` | 23 | `QualityToolBlackList` subclass; `$WORKSPACE_FILE$` storage. |
| `MessDetectorValidationInspection` | 114 | The "real" inspection. Stores legacy options as five public fields (`CODESIZE`, `CONTROVERSIAL`, `DESIGN`, `UNUSEDCODE`, `NAMING`) plus a `List<RulesetDescriptor> customRulesets`. **Critical difference vs. PHPStan: NO separate `MessDetectorGlobalInspection`.** Everything runs through this one validation inspection. `getRuleSetsOption(...)` is the heart of arg-building — joins the enabled built-ins with the (path-mapped) custom paths into a single comma-separated string. |
| `MessDetectorOptionsConfiguration` | 106 | DUPLICATE fields as `@State(php.xml)` project service, used by on-the-fly. Stores `myCustomRulesets: List<RulesetDescriptor>`. |
| `MessDetectorSettingsTransferStartupActivity` | 113 | One-shot migration: copies the five booleans + ruleset list from the legacy inspection profile into `MessDetectorOptionsConfiguration`, stamps `isTransferred=true`. Same shape as PHPStan's equivalent. |
| `MessDetectorAnnotator` | 145 | `QualityToolAnnotator<MessDetectorValidationInspection>` — **NOT** the `AnnotatorProxy` pattern PHPStan / phpcs use. PHPMD subclasses the base annotator directly because there is only one inspection. Builds args: `[path, "xml", joinedRulesetOptions]`. Returns `null` (skip) when the ruleset string is empty. |
| `MessDetectorMessageProcessor` | 123 | `QualityToolXmlMessageProcessor` (SAX). **Not checkstyle** — phpmd's own schema (`<pmd>/<file>/<violation beginline="N" rule="…" priority="N">`). Severity always `ERROR`. `getMessagePrefix() = "phpmd"`. |
| `MessDetectorComposerConfig` | 194 | `QualityToolsComposerConfig`. Package `phpmd/phpmd`, binary `vendor/bin/phpmd`. `applyRulesetFromRoot` looks for `phpmd.xml` / `phpmd.xml.dist` next to `composer.json`; `applyRulesetFromComposer` parses `scripts.phpmd` for a CSV ruleset arg, routing each token to either a built-in toggle or `MessDetectorRulesetAnalyzer.readRulesetFile`. |
| `MessDetectorConfigurable` | 106 | Settings page (`settings.php.quality.tools.mess.detector`). |
| `MessDetectorConfigurableForm` | 106 | Validation regex: anything starting with `PHPMD` is OK. |
| `MessDetectorOptionsPanel` | 522 | Swing-Designer panel — biggest class. Five built-in checkboxes (`cleancode` is **absent** — see §2.1) + a `JBTable` with add/remove toolbar over `CustomRulesetTableModel`. Add-button opens a file chooser filtered to `xml`/`dist`, parses the ruleset XML for its name, appends a `RulesetDescriptor`. |
| `MessDetectorOptionsPanel.CustomRulesetTableModel` | inner | Two-column model (name, path). Path cell renders `<does not exist>` when the file is missing locally (skipped on remote interpreters). |
| `MessDetectorRulesetAnalyzer` | 92 | SAX parser reading just the `<ruleset name="…">` attribute → returns a `RulesetDescriptor` or `null`. |
| `RulesetDescriptor` | 49 | Value type — `name`, `originalPath`, plus `isValid(isRemote)` (short-circuits to true on remote). |
| `MessDetectorAddToIgnoredAction` | 33 | Trivial subclass; just returns the tool type. |
| `MessDetectorInterpreterStateListener` | 29 | `PhpInterpretersStateListener` — on PHP interpreter add/remove/edit, fires `MessDetectorConfigurationManager.onInterpretersUpdate()` to prune profiles referencing deleted interpreters and re-resolve the rest. PHPStan gets this from inheritance; PHPMD wires it explicitly. **This is the one quirk the SDK doesn't speak to today.** See §4.5. |

**Total bundled: ~2,490 LOC of Java glue.**

### 1.2. Remote interpreter glue

In `php-remoteInterpreter.jar`:

| Class | LOC | Role |
| --- | --- | --- |
| `MessDetectorRemoteConfiguration` | 172 | Subclass of `MessDetectorConfiguration` adding `interpreterId`; `PhpSdkDependentConfiguration` impl. `@Tag("phpmd_by_interpreter")` for XML. |
| `MessDetectorRemoteConfigurationProvider` | 216 | Registered on `messDetectorConfigurationProvider` EP. Provides `createNewInstance` (opens the by-interpreter dialog), `createConfigurationByInterpreter`, and overrides remote timeout to **30 s** (`settings.setTimeout(30000)`). Note: PHPStan's remote provider goes to 60 s; PHPMD only 30 s. The SDK gap (PHPStan §4.4) covers both. |

The three shared base classes from `com.jetbrains.php.remote.tools.quality.*`
(`QualityToolByInterpreterDialog`, `QualityToolByInterpreterConfigurableForm`,
`QualityRemoteToolProcessHandler`) are the same code the PHPStan plan
maps to `PhpInterpreterBinarySourceType` / `IntellijProcessSpawner` in
`:php`. See PHPStan §1.2; this port reuses the exact same wiring.

### 1.3. Plugin metadata

Unlike PHPStan, PHPMD support is **bundled directly inside the host
`com.jetbrains.php` plugin** (no separate downloadable plugin). The
remote subtree is loaded only when the remote-interpreter plugin is
on. Relevant entries from the host `plugin.xml`:

- `<localInspection>` `MessDetector` (or whatever short name the
  legacy `QualityToolValidationInspection` chooses — derived from the
  inspection class FQN by platform).
- `<externalAnnotator language="PHP"` for the `MessDetectorAnnotator`.
- 5 services + 1 inspection + 1 postStartupActivity + 1 configurable +
  1 composerConfigClient + 1 inner EP (`messDetectorConfigurationProvider`)
  + 1 action (`MessDetectorAddToIgnoredAction`) + 1 application
  listener (`MessDetectorInterpreterStateListener` on
  `PhpInterpretersStateListener.TOPIC`).

---

## 2. Functional surface (what the user sees)

### 2.1. User-facing features

1. **Settings page** at `PHP / Quality Tools / PHP Mess Detector`:
   profiles list (local + per-interpreter); per-profile tool path,
   validate button (anything starting with `PHPMD` is OK), timeout
   in seconds, max messages per file; shared multi-ruleset surface:
   - **Five** built-in toggles: `codesize`, `controversial`,
     `design`, `naming`, `unusedcode`.
   - **`cleancode` is NOT exposed by the legacy UI** despite being
     a first-class phpmd ruleset since 2.3.0. The brief lists six;
     the legacy plugin ships five. The port should add `cleancode`
     (free win) — see §4.4 / §6.
   - Custom-rulesets table — add/remove rows, each `(name, abs
     XML path)`. Built-ins and custom paths coexist in a single
     comma-separated CLI arg.
2. **Inspection profile**: **just one** inspection (a
   `QualityToolValidationInspection` subclass; short name
   `MessDetectorValidationInspection` preserved via phase 10a.1).
   No separate global/batch inspection — batch mode is the same
   inspection scheduled over a wider scope.
3. **On-the-fly analysis** while editing a `.php` file.
4. **Project-wide analysis** via `Code → Inspect Code…` (same
   inspection re-run; legacy plugin does NOT use PHPStan's
   "build-all-once" pattern — PHPStan §4.9 doesn't apply).
5. **Composer auto-detect**: when `vendor/bin/phpmd` appears,
   auto-configure the tool path AND (a) look for `phpmd.xml` /
   `phpmd.xml.dist` next to `composer.json`; (b) parse
   `composer.json` `scripts.phpmd` for the CSV ruleset arg, flipping
   built-in checkboxes and adding custom files as rows.
6. **"Add to ignored" action** in the right-click menu — appends
   to the per-tool workspace blacklist.
7. **Notifications**: no deep-link "Configure" in legacy (no
   `OpenSettingsProvider`); generic SDK replacement gives it for
   free.
8. **Settings migration**: pre-2024 inspection-profile-fields →
   `MessDetectorOptionsConfiguration`. Same shape as PHPStan, plus
   the nested list of `RulesetDescriptor`s.
9. **Remote PHP interpreter support** (Docker / SSH / WSL): the
   "by-interpreter" dialog maps the local `phpmd` path to a remote
   one. **Every custom-ruleset XML path is path-mapped before
   join** — `getRuleSetsOption` walks descriptors through
   `PhpPathMapper`. The argument is ONE CSV (`--rulesets=codesize
   ,/abs/local/foo.xml`), not N flags — see §4.3. 30-second
   default timeout (not 60 s like PHPStan).
10. **Interpreter-change reactivity**:
    `MessDetectorInterpreterStateListener` invalidates cached
    resolutions on PHP interpreter add/remove/edit, marking dead
    profiles in the dropdown without a project reopen.

### 2.2. Internal plumbing (collapsed by the new SDK)

Same shape as PHPStan §2.2, with two PHPMD-specific additions: hand-
written multi-row table model + add/remove actions in the options
panel, and one bespoke startup listener
(`MessDetectorInterpreterStateListener`) that reacts to PHP
interpreter-set changes. PHPMD also runs ONE annotator pipeline
(no on-the-fly vs. batch split).

---

## 3. Mapping each feature to the new SDK

| Legacy feature | New SDK home | Notes |
| --- | --- | --- |
| `MessDetectorQualityToolType` (registration) | `QualityTool` (phase 01) — one Kotlin class | Same EP `dev.jplugins.qualityTools.tool`. `PhpMessDetector.inspectionShortNames = setOf("MessDetectorValidationInspection")` for phase 10a.1 compatibility. |
| `MessDetectorConfiguration` (tool path, timeout) | `LocalBinarySource` (phase 02) + per-`ConfigProfile.timeoutMs` (phase 04) | Same as PHPStan §3. Default timeout 5 s vs. PHPStan's 30 s — stored per-profile, not in the source type. |
| `MessDetectorRemoteConfiguration` (interpreter id) | `PhpInterpreterBinarySource` in `:php` (phase 02) | Same generic source. typeId `mago.php-interpreter` (already declared for Mago — fully reused; see phase 10 §61–62). |
| `MessDetectorConfigurationManager` + `*BaseManager` (2 services) | Unified `QualityToolsProjectStorage` (phase 04) | Same as PHPStan §3. |
| `MessDetectorProjectConfiguration.selectedConfigurationId` | `QualityToolsProjectStorage.activeProfileId("phpmd","analyze")` | Same as PHPStan §3. |
| `MessDetectorConfigurationProvider` EP | `ConfigSourceType` EP (phase 02) | Same as PHPStan §3. |
| `MessDetectorRemoteConfigurationProvider` | `PhpInterpreterBinarySourceType` in `:php` | Same as PHPStan §3. |
| `MessDetectorBlackList` | `GlobPathIgnorePolicy` (phase 06) | Direct port; unified storage. |
| `MessDetectorValidationInspection` (the one inspection) | Phase 10a.1 short-name preservation + `QualityToolsAnnotator` (phase 08) | The single short name `MessDetectorValidationInspection` keeps existing user profile XML working. |
| `MessDetectorAnnotator` (sub-class, not proxy) | `QualityToolsAnnotator` + `PhpMessDetectorTool.buildArgs` (phase 01/08) | Same drop as PHPStan — annotator hand-coding disappears entirely. The proxy/non-proxy distinction stops mattering because phase 08's annotator is a single uniform class. |
| `MessDetectorMessageProcessor` (SAX, phpmd XML) | New **`PhpmdXmlReader`** in `:php` (NOT bundled in `:core`) | Phpmd's XML is NOT checkstyle — see §4.1 below. `CheckstyleXmlReader` cannot be reused. We add one tool-specific reader in `:php`. |
| `MessDetectorComposerConfig` | `ComposerBinarySourceType` in `:php` + a `PhpMessDetectorComposerOnDetectedHook` (gap 4.5 from PHPStan plan) | Same shape as PHPStan; the hook reads `phpmd.xml`/`phpmd.xml.dist` and `composer.json scripts.phpmd`. |
| `MessDetectorConfigurable` + `MessDetectorConfigurableForm` + `MessDetectorOptionsPanel` (522 LOC) | `PhpMessDetectorOptionsSchema` + `AutoToolSettingsPanel` (phase 04 + 07) | Five checkboxes + the multi-row ruleset table become **one** options schema. Custom-rulesets table is a `ListSpec<RulesetEntry>` (compound) — see §4.2. Zero Swing code in the port. |
| `MessDetectorOptionsConfiguration` (project state) | Subsumed by `OptionsBag` in unified storage (phase 04) | Same as PHPStan §3. |
| `MessDetectorSettingsTransferStartupActivity` | Migration step inside the legacy-XML migrator (phase 10c-equivalent for PHPMD) | Same generic mechanism. Reuses Mago's `Migrator` interface. |
| `MessDetectorAddToIgnoredAction` | Auto-wired in the SDK (PHPStan gap 4.7) | Same generic action group; PHPMD is one of many tools it covers. |
| `MessDetectorRulesetAnalyzer` (XML name-attribute reader) | Stays as a small `:php` helper used by the wizard and the on-detected hook | Trivial 40 LOC. Could in principle live anywhere — it's a pure SAX over a single attribute. Not an SDK concern. |
| `RulesetDescriptor` (value type) | `CompoundSpec<RulesetEntry>` data class (phase 04) | Same shape: `(name: String, path: String)`. The "valid on remote skip" check is replaced by `PathSpec`-aware validation in `AutoToolSettingsPanel`. |
| `MessDetectorInterpreterStateListener` (reacts to interpreter set changes) | **No direct equivalent today** — see §4.5 | Phase 02 `AvailabilityContext.onAvailabilityChanged` is the closest thing; we need to wire `PhpInterpreterBinarySourceType` to refresh availability on interpreter add/remove. Documented in §4.5. |
| `QualityToolByInterpreterDialog` + form (in remote-interp jar) | `PhpInterpreterSourceWizard` in `:php` (phase 02 + 07) | Identical reuse of PHPStan §3. |
| `QualityRemoteToolProcessHandler` | `IntellijProcessSpawner` in `:php` (phase 05) | Identical reuse. |
| Validate-button (`phpmd --version` parsing) | `BinaryValidator` SAM on `QualityTool` (gap 4.1 from PHPStan plan) | Same gap. The phpmd regex is even simpler (`startsWith("PHPMD")`). |
| 30-second default timeout for remote profiles | `ConfigSourceType.defaultTimeoutMs` override (gap 4.4 from PHPStan plan) | Same gap. PHPMD picks 30 s, PHPStan picks 60 s. |

---

## 4. Gaps in the new SDK exposed by this exercise

PHPMD reuses every gap PHPStan surfaced — `BinaryValidator` (4.1),
per-mode reader id (4.2), `ResolvedBinary.detectedVersion` (4.3),
`ConfigSourceType.defaultTimeoutMs` (4.4), `OnDetectedHook` (4.5),
auto-wired "Add to ignored" (4.7), batch-mode cache (4.9). They are
not repeated below — see PHPStan §4 for the recommendations.

PHPMD adds **three new gaps** plus one judgement call:

### 4.1. The phpmd XML output format (not checkstyle)

**What it is:** phpmd does not emit checkstyle. Schema:

```
<pmd version="2.15.0" timestamp="…">
  <file name="/abs/path/Foo.php">
    <violation beginline="12" endline="12" rule="UnusedFormalParameter"
               ruleset="Unused Code Rules" priority="3"
               externalInfoUrl="…">message text</violation>
  </file>
  <error filename="/abs/path/Bad.php" msg="Parse error: …" />
</pmd>
```

Differences vs. checkstyle: outer tag `<pmd>` not `<checkstyle>`;
file messages nested as `<violation>` not `<error>`; line attribute
is `beginline` (plus optional `endline`), no columns at all;
severity implicit (legacy hard-codes ERROR; phpmd's `priority="1..5"`
is ignored); a separate top-level `<error filename="…" msg="…">`
reports per-file parse errors (legacy drops these).

**Today:** `MessDetectorMessageProcessor` (123 LOC) extends
`QualityToolXmlMessageProcessor` and parses `<violation>` directly.

**Recommendation:** New reader **`PhpmdXmlReader`** in `:php` (NOT
`:core` — phpmd-specific) registered on
`dev.jplugins.qualityTools.resultReader`. The reader maps priority
1–2 → error, 3 → warning, 4–5 → weak_warning (free improvement
over legacy ERROR-everything); maps standalone `<error filename>`
to `ToolMessage(severityLevel = "internal_error", category =
"phpmd.parse_error")` per phase 06. **No SDK change** — clean fit
for the existing reader EP.

### 4.2. Multi-ruleset selection — new shape for `OptionsSchema`?

**What it is:** the user picks any subset of a known set of short
names (`cleancode`, `codesize`, `controversial`, `design`, `naming`,
`unusedcode`) AND adds arbitrary custom XML files. The CLI gets
ONE comma-separated value mixing both:

```
--rulesets=codesize,unusedcode,/abs/path/custom-team.xml,/abs/path/team-shared.xml
```

**Question (from the brief):** *can this be a `StringListSpec`, or
does it need a new spec kind?*

**Answer: not `StringListSpec`, but not a new kind either.** Three
reasons `StringListSpec` is wrong: (a) built-in names are a closed
localised set — checkboxes, not a text list; (b) custom entries
carry TWO fields (`name` parsed from `<ruleset name="…">` + `path`)
— `StringListSpec` only holds strings; (c) custom paths must
propagate `isPath=true` through `PathAwareArgRewriter`, which
`StringListSpec` doesn't do per-element.

Existing specs compose cleanly: six `BoolSpec`s (one per built-in
ruleset — better UX than a multi-select for six items) plus one
`ListSpec<RulesetEntry>` (phase 04 line 51) where `RulesetEntry` is
a `CompoundSpec` with fields `nameSpec: StringSpec` and `pathSpec:
PathSpec`. `AutoToolSettingsPanel` auto-renders the compound list
as a `JBTable` with add/remove toolbar. `buildArgs` walks both
sets, joins with `,`, emits a single composite arg (see §4.3).

**No new spec kind needed.** Add a phase-07 acceptance bullet:
"`ListSpec<CompoundSpec>` with a `PathSpec` member renders as a
two-column table with add/remove toolbar buttons" — PHPMD is the
first concrete consumer.

### 4.3. Path-aware arg rewriting for CSV-of-mixed-tokens

**What it is:** Phpmd's `--rulesets` is ONE CLI token whose value
mixes short-names and absolute paths
(`codesize,/abs/path1,/abs/path2`). `PathAwareArgRewriter` (phase
05) either remote-maps the full string (wrong — `codesize` is not
a path) or skips it (wrong — `/abs/path1` needs mapping).

**Today:** Legacy `MessDetectorValidationInspection.getRuleSetsOption`
walks the descriptor list, maps each path via `PhpPathMapper`, then
joins the CSV. The mapper lives in PHP-impl, not the SDK.

**Recommendation:** Add `compositeKvPathArg` to `ToolArgs.kt` (phase
01 addendum, ≤ 20 LOC):

```kotlin
public fun compositeKvPathArg(
    key: String,                       // e.g. "--rulesets="
    parts: List<CompositePart>,        // ordered tokens
    separator: String = ",",
): ToolArg
```

`CompositePart = data class(token: String, isPath: Boolean)`.
`PathAwareArgRewriter` is taught to walk the parts list, rewrite
only `isPath = true` tokens, and re-join. **Phase 05 edit (small)**
— the rewriter learns one new ToolArg sub-kind. This is the **one
truly new gap** PHPMD exposes that PHPStan did not.

Alternative: emit N separate `--rulesets` args. Phpmd's CLI does
accept that, but the legacy plugin chose CSV; preserving CSV for
argv-fingerprint stability is worth the tiny SDK addition.

### 4.4. The "no cleancode in legacy UI" cleanup

**What it is:** As noted in §2.1, the legacy panel ships five
built-in toggles but phpmd has six ruleset short names since 2.3.0
(`cleancode` is the missing one). Users who want `cleancode` have
to add it as a custom row pointing to phpmd's built-in
`rulesets/cleancode.xml` inside the vendor dir — gross.

**Recommendation:** Add `cleancode` as a sixth `BoolSpec` in the
port's options schema. Free win; no SDK change. The migration
(§4.7) doesn't need to special-case it because the legacy value
was never persisted.

### 4.5. Reacting to PHP interpreter set changes — does the SDK have an equivalent today?

**What it is:** `MessDetectorInterpreterStateListener` registers on
`PhpInterpretersStateListener.TOPIC`. On add / remove / modify of a
PHP interpreter, it fires
`MessDetectorConfigurationManager.onInterpretersUpdate()` which (a)
walks remote profiles, (b) marks profiles whose `interpreterId` no
longer resolves as dead, and (c) re-resolves binary paths (an
interpreter edit can change the remote `phpmd` path). PHPStan gets
this from inheritance; PHPMD registers explicitly.

**Does the SDK have an equivalent today?** I read every phase doc
(00–10). The closest hooks in **phase 02**:

- `ConfigSourceType.watch(ctx, onDetected)` — one-shot detection
  of NEW sources appearing (Composer install, mise add). Not a
  long-lived "existing profiles must re-resolve" channel.
- `AvailabilityContext.onAvailabilityChanged` (phase 02 line 209)
  — the registry refreshes on docker daemon start/stop or k8s
  context switch. Right shape, but it refreshes the list of
  source TYPES, not individual source INSTANCES.

**No SDK hook today** covers "a previously-resolved `ResolvedBinary`
has gone stale because the underlying remote backend changed in a
way the source instance must observe".

**Recommendation:** Add to `ConfigSource` (phase 02 addendum, NOT
on `ConfigSourceType`):

```kotlin
public interface ConfigSource {
    /**
     * Cold flow of "self has gone stale, callers should re-resolve".
     * Default emits nothing. `PhpInterpreterBinarySource` in `:php`
     * subscribes to `PhpInterpretersStateListener.TOPIC` and emits
     * when the referenced interpreter is mutated or removed.
     */
    public fun staleness(ctx: WatchContext): Flow<StaleReason>
        get() = emptyFlow()
}
public enum class StaleReason { BackendChanged, BackendRemoved, ResolvedPathChanged }
```

`QualityToolsProjectStorage` (phase 04) wires a project-scoped
collector that invalidates the resolved-binary cache on each
`StaleReason`, and (on `BackendRemoved`) marks the profile orphaned
in the dropdown. **Phase 02 edit (small)** — similar size to
PHPStan's `OnDetectedHook`. PHPStan and Psalm benefit too.

After this lands the explicit
`MessDetectorInterpreterStateListener` class disappears: the source
instance carries its own reactivity.

### 4.6. Ruleset name discovery during file-pick

**What it is:** When the user clicks "Add" in the custom rulesets
table, a file chooser opens. After selection,
`MessDetectorRulesetAnalyzer.readRulesetFile(file)` parses the XML
to extract the `<ruleset name="…">` attribute as the table's
"Name" column. The parser is tightly coupled to the panel.

**Recommendation:** The parser itself is tool-specific business
logic; the port keeps `MessDetectorRulesetAnalyzer` as a small
`:php`-local helper (~40 LOC) used by the compound-spec "add"
affordance and the Composer on-detected hook.

But the SDK needs one wiring point: does `ListSpec<CompoundSpec>`'s
auto-rendered "add" button support a custom post-pick callback to
populate fields from the picked file? Phase 07 today says no. Add
acceptance bullet: "`ListSpec<CompoundSpec>` supports an optional
`onItemAdded(addedItem, ctx) → CompoundValue` hook for tools that
need to enrich a new row from an external source." **Phase 07 edit
(trivial).** Without it, plugins either replace the whole panel
(regression vs. zero-Swing) or accept blank names until the user
types them.

### 4.7. Inspection-profile compatibility with seven-field history

**What it is:** Pre-2024 PhpStorm stored PHPMD options as five
booleans (`CODESIZE`, `CONTROVERSIAL`, `DESIGN`, `UNUSEDCODE`,
`NAMING`) + a `List<RulesetDescriptor>` on
`MessDetectorValidationInspection` (i.e. inside the inspection
profile XML). `MessDetectorSettingsTransferStartupActivity` copies
them into the modern `MessDetectorOptionsConfiguration` and stamps
`isTransferred=true`.

**Recommendation:** Same generic migrator pattern designed for
Mago (phase 10) and PHPStan (PHPStan §4.8). PHPMD's `Migrator`
reads the six legacy fields + the descriptor list, writes into the
options bag, sets the `isTransferred` flag on its own migration
state. **No SDK change** — falls under the same plan.

Note: the `List<RulesetDescriptor>` (a complex value, not a
scalar) is the first migration case that exercises the
`ListSpec<CompoundSpec>` round-trip — the legacy XML has nested
`<RulesetDescriptor name="…" originalPath="…"/>` entries
serialized by JDOM bean reflection. The new storage emits
`SerializedSourceElement` for the list. **Migrator acceptance
bullet (phase 10):** "Migrator can read a JDOM list and emit a
`SerializedSourceElement` list — covered by the PHPMD
ruleset-descriptor round-trip test."

### 4.8. The "no global inspection split" case

**What it is:** PHPStan has TWO inspections (`PhpStanGlobal`,
`PhpStanValidation`); PHPMD has ONE
(`MessDetectorValidationInspection`). When phase 10a.1's short-name
preservation is exercised, it must handle a tool whose
`inspectionShortNames` set has exactly one entry.

**Recommendation:** Should already work — `Set<String>` has no
arity assumption — but worth an explicit acceptance bullet in
phase 10a.1 so the regression is caught:

> "Tool with a single inspection short-name (PHPMD-style) is
> exercised in the bridge integration test alongside the dual
> short-name PHPStan-style case."

Total new SDK gaps from PHPMD: **two** (the staleness hook in
phase 02, and the composite path arg in phase 05). One judgement
note (the post-pick enrichment hook in phase 07) is borderline
SDK-vs-plugin; we propose adding it because the alternative is
"every tool that needs it overrides the whole panel" which defeats
phase 07's purpose.

---

## 5. Generic-code overhead — what we'd write if we ported now

| Code we'd write in the PHPMD port | Generic? | Action |
| --- | --- | --- |
| `PhpMessDetectorTool : QualityTool` | unique | keep |
| `PhpMessDetectorOptionsSchema : OptionsSchema` | unique | keep |
| `PhpmdXmlReader : ResultReader` (in `:php`) | unique | keep |
| `PhpMessDetectorComposerOnDetectedHook` | unique | keep |
| `PhpMessDetectorVersionValidator : BinaryValidator` | unique | keep (PHPStan gap 4.1) |
| `MessDetectorRulesetAnalyzer` (XML name reader, ~40 LOC) | unique | keep |
| `PhpMessDetectorMigration` | unique | keep |
| ⚠ Multi-token CSV path arg ("rulesets=…") wiring | generic | resolve via gap 4.3 |
| ⚠ Re-resolve profile when interpreter set changes | generic | resolve via gap 4.5 |
| ⚠ "Enrich newly-added compound-row from file pick" wiring | generic | resolve via gap 4.6 |
| ⚠ (everything PHPStan §5 lists) | generic | resolve via PHPStan §4 gaps |

Three new ⚠ items on top of PHPStan's six. Each adds 5–20 LOC of
generic-shaped code per tool. **Fixing all three in the SDK alongside
the PHPStan-uncovered ones is the right call** before we ship two
adopters worth of duplicated boilerplate.

---

## 6. Concrete file list for the PHPMD port (post-gap-fix)

Assuming all gaps in §4 and PHPStan §4 are merged, the PHPMD plugin
shrinks to roughly:

**Required:**

- `PhpMessDetectorTool.kt` (~80 LOC) — id `phpmd`, single mode
  `analyze`, capabilities `{"lint"}`, options schema, buildArgs
  emitting `[displayPath or stdin marker, "xml",
  compositeKvPathArg("--rulesets=", parts)]`.
- `PhpMessDetectorOptionsSchema.kt` (~50 LOC) — six `BoolSpec`s
  (`cleancode`, `codesize`, `controversial`, `design`, `naming`,
  `unusedcode`) + one `ListSpec<RulesetEntry>` where `RulesetEntry`
  is a `CompoundSpec` with `nameSpec: StringSpec` and `pathSpec:
  PathSpec`.
- `PhpMessDetectorVersionValidator.kt` (~20 LOC) — runs `--version`,
  checks `startsWith("PHPMD")`, returns ok/version.
- `PhpMessDetectorComposerOnDetectedHook.kt` (~80 LOC) — phpmd.xml
  / phpmd.xml.dist discovery + scripts.phpmd parsing. Reuses
  `MessDetectorRulesetAnalyzer` for the name extraction.
- `MessDetectorRulesetAnalyzer.kt` (~40 LOC) — straight port of the
  ~92-LOC Java class.
- `PhpmdXmlReader.kt` in `:php` (~100 LOC) — SAX, maps phpmd
  priorities to `SeverityLevels` and `<error>` to `internal_error`.
- `PhpMessDetectorMigration.kt` (~70 LOC) — legacy XML
  (six fields + descriptor list) → new storage.
- `META-INF/plugin.xml` (~40 LOC) — registrations.

**Optional:**

- `PhpMessDetectorBundle.properties` — i18n (or reuse PhpBundle
  keys for parity).
- `inspection.html` — description for the inspection.

**Total: ~480 LOC + bundle/HTML**, vs. ~2,490 LOC today.
**~5.2× reduction.** PHPStan came in at ~7×; PHPMD is slightly
worse because the multi-ruleset compound list and the dedicated
phpmd XML reader carry irreducible work the SDK can't subsume.

---

## 7. Order of work (when we get to coding)

Mergeable in sequence. Assumes the PHPStan SDK gaps (PHPStan §7
step 1) have already landed — PHPMD is the second adopter.

1. **PHPMD-specific SDK gap fixes** (≤ 50 LOC each, ship as
   "phase patches v2" after PHPStan v1):
   - 4.3 `compositeKvPathArg` + rewriter parts walk → phase 01
     + phase 05.
   - 4.5 `ConfigSource.staleness(...)` + storage collector →
     phase 02 + phase 04.
   - 4.6 `ListSpec<CompoundSpec>.onItemAdded` hook → phase 07.
   - 4.8 single-entry short-name acceptance bullet → phase 10a.1
     (doc only).
2. **PHPMD tool registration** — minimal port: `PhpMessDetectorTool`
   + `PhpMessDetectorOptionsSchema` + `PhpmdXmlReader`. Visible in
   Settings with checkboxes and an empty custom-rulesets table.
3. **Version validator** wired into the validate button (reuses
   PHPStan gap 4.1).
4. **Composer auto-detect** — `PhpMessDetectorComposerOnDetectedHook`
   on the generic `ComposerBinarySourceType` (reuses PHPStan gap
   4.5). Replaces `MessDetectorComposerConfig`.
5. **Remote** — optional `<depends>` on the `:php` interpreter
   source type. Tests against the 30 s default timeout from PHPStan
   gap 4.4.
6. **CSV path-aware rewriting** — once gap 4.3 lands, switch
   `buildArgs` to `compositeKvPathArg`. Fake-Docker test that path
   parts map but short-name parts don't.
7. **Interpreter-change reactivity** — once gap 4.5 lands, verify
   the explicit `MessDetectorInterpreterStateListener` has nothing
   to do and remove it.
8. **Migration** — `PhpMessDetectorMigration` ports the legacy
   options + descriptor-list transfer. Stress-tests
   `ListSpec<CompoundSpec>` round-trip.
9. **Short-name preservation** — verify
   `MessDetectorValidationInspection` is the sole short name
   emitted (phase 10a.1, single-name case).
10. **Cleanup**: delete legacy classes once validated.

---

## 8. Risks / open questions

- **Bundled vs separate plugin**: PHPMD support is bundled inside
  `com.jetbrains.php`, not a standalone download. The port ships
  as a separate community plugin first; if JetBrains will not
  remove their bundled implementation, the two must coexist.
  Same `inspectionShortNames` would conflict. Mitigation: default
  to a new short-name prefix (e.g.
  `MessDetectorValidationInspection2`) and only switch to the
  JetBrains-compatible name when the bundled feature is detected
  absent / disabled.
- **Inspection-profile schema mismatch**: user profile XML carries
  `<inspection_tool class="MessDetectorValidationInspection">` with
  scalar booleans AND a nested
  `<customRulesets><RulesetDescriptor …/></customRulesets>` list.
  The migrator handles both shapes — covered in §4.7.
- **Single mode**: PHPMD has one mode (analyze) with no on-the-fly
  vs. batch split. Same call as PHPStan §8: one mode `analyze`,
  scope drives target.
- **Phpmd does not emit columns**: `SourceRange.startColumn = 0`
  (unknown per phase 06 convention); annotator highlights the
  whole line.
- **Priority → severity mapping is a behavior change**: legacy
  flattens everything to ERROR; the port maps priority 1–2 → error,
  3 → warning, 4–5 → weak_warning. User-visible change. Consider
  a `respectPriority: BoolSpec` (default true) for opt-out;
  default-true is defensible because users could already adjust
  severity in the inspection profile and the port surfaces strictly
  more information.
- **The `<error filename="…">` parse-error path**: phpmd emits
  this when a file fails to parse. Legacy plugin drops it; phase 06
  says parse errors surface as `internal_error` ToolMessages. Port
  follows phase 06 — flag in release notes.
- **`cleancode` ruleset addition** (§4.4): a feature win but also
  an inspection-output change. Default `cleancode = false` to
  preserve legacy output; opt-in only.
- **No fix-emitter**: PHPMD does not emit fixes — so the port
  doesn't exercise the `ToolFix` hierarchy. PHPStan + PHPMD
  together cover sources (local + remote + composer), single-
  and multi-valued options, migration, CSV-path arg rewriting,
  and source-instance staleness. The remaining SDK surface after
  both ship is the rich `ToolFix` family + `SarifReader` +
  `JsonLinesReader` + `CommentAnnotationIgnorePolicy` — all
  exercised by Mago.

---

## 9. Summary

- The full PHPMD integration is **~2,490 LOC of Java glue** spread
  across 22 classes in two jars.
- Mapped onto the proposed SDK (PHPStan §4 gaps in place), ~95% of
  those classes disappear into generic infrastructure.
- **2 new SDK gaps** beyond what PHPStan surfaced:
  `compositeKvPathArg` (phase 05) and `ConfigSource.staleness`
  (phase 02), each ≤ 50 LOC. Plus one smaller phase-07 hook
  (`onItemAdded`) for file-derived compound-list rows.
- Multi-ruleset selection is **not a new spec kind** — it composes
  from six `BoolSpec`s + one `ListSpec<CompoundSpec>` with a
  `PathSpec` member. BoolSpec-fan-out beats multi-select UX for
  six closed items; the compound list covers the open set.
- Phpmd's XML format is **not checkstyle** — gets its own
  `PhpmdXmlReader` in `:php` (≈100 LOC) via the existing
  `ResultReader` EP; no SDK change.
- `MessDetectorInterpreterStateListener` exposes a real gap: SDK
  has hooks for new sources appearing (`watch`) and source-type
  availability changing (`onAvailabilityChanged`) but no hook for
  an existing source instance going stale. `ConfigSource.staleness`
  closes it.
- Post-gap-fix the PHPMD plugin lands at **~480 LOC** — a 5.2×
  reduction (vs. PHPStan's 7×; PHPMD's dedicated XML reader and
  compound-spec UI carry irreducible work).
- Recommendation: ship the 2 PHPMD-specific SDK patches as "phase
  11 hardening v2" after the PHPStan v1 patches land, then port
  PHPMD as the second adopter — validates compound-list options,
  mixed-token path-aware args, and source-instance staleness, none
  of which PHPStan alone catches.
