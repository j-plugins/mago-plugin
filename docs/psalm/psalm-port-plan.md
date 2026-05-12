# Psalm port to `:quality-tools-sdk` — plan and analysis

> Goal: take the existing JetBrains Psalm plugin (which we
> reverse-engineered after the PHPStan exercise) and write down how it
> would be rebuilt on top of our new `:quality-tools-sdk`. No code in
> this document — only inventory, mapping, gaps, and the work plan.
>
> Why bother after PHPStan: Psalm is the **structural twin** of
> PHPStan inside JetBrains' codebase — same author style, same parallel
> class hierarchy, same `<depends optional>` story for the remote
> interpreter. Porting Psalm right after PHPStan is the cleanest way to
> validate that the SDK's per-tool surface scales beyond a single
> reference adopter, and to surface any *additional* gaps that PHPStan
> alone didn't exercise. We expect ~90% of this document to read
> "same as PHPStan §X" — and that is the point.

---

## 0. Reference: source material

Reverse-engineered classes I analysed (CFR 0.152):

- `com.jetbrains.php.tools.quality.psalm.*` (20 files, ~2.5 k LOC)
  — bundled in the `psalm` plugin (id
  `com.intellij.php.tools.quality.psalm`).
- `com.jetbrains.php.tools.quality.psalm.remote.*` (2 files, ~390
  LOC) — also inside `psalm.jar`, registered via the optional
  config `psalm-remote-plugin.xml` when
  `org.jetbrains.plugins.phpstorm-remote-interpreter` is present.
- Shared remote-interpreter glue (`com.jetbrains.php.remote.tools.quality.*`,
  3 base classes ~560 LOC) lives in the PHP Remote Interpreter
  plugin — same jar shared with PHPStan, phpcs, phpmd, Pint,
  Laravel Pint. Already inventoried in the PHPStan plan §1.2.

Other artefacts in `psalm.jar`:

- `PsalmBundle.properties` — i18n.
- `inspection.html` description.
- `psalm-remote-plugin.xml` — injects
  `PsalmRemoteConfigurationProvider` when the remote-interpreter
  plugin is enabled.

**No `psalm.completion.*` package**: unlike PHPStan, the JetBrains
Psalm plugin does **not** ship a PHPDoc completion contributor for
Psalm-specific tags (`@psalm-suppress`, `@psalm-var`, etc). Those
tags are recognised by core PhpStorm's PHPDoc lexer separately. So
Psalm's port surface is **slightly smaller** than PHPStan's.

---

## 1. Inventory: what every class does today

### 1.1. Core "ports" of the legacy SDK

| Class | LOC | Role |
| --- | --- | --- |
| `PsalmQualityToolType` | 253 | EP entry point (`com.jetbrains.php.tools.quality.type`). Returns `inspectionId="PsalmGlobal"`, wires up managers, blacklist, configurable. Same shape as `PhpStanQualityToolType`. |
| `PsalmConfiguration` | 166 | Per-instance config: `myPsalmPath`, `myMaxMessagesPerFile=50`, `myTimeoutMs=30000`, `getId()="local"`. **Identical field set to `PhpStanConfiguration`** — only the field name differs (`myPsalmPath` vs. `myPhpStanPath`). |
| `PsalmConfigurationManager` | 64 | Same pattern as PHPStan §1.1: app+project `@State` services (`Psalm` in `php.xml`). |
| `PsalmConfigurationBaseManager` | 64 | `PersistentStateComponent<Element>` with config tag `PsalmSettings`. |
| `PsalmConfigurationProvider` | 35 | Abstract — EP name `com.jetbrains.php.tools.quality.Psalm.PsalmConfigurationProvider`. Single registered provider. |
| `PsalmProjectConfiguration` | 57 | Project state with `selectedConfigurationId` (default `DEFAULT_INTERPRETER`). |
| `PsalmBlackList` | 24 | `QualityToolBlackList` subclass, workspace-file-stored. |
| `PsalmValidationInspection` | 36 | Empty subclass of `QualityToolValidationInspection`. ShortName `PsalmValidation`. |
| `PsalmGlobalInspection` | 374 | The "real" inspection (`shortName=PsalmGlobal`). Stores the option set as **four** public fields (`config`, `showInfo`, `findUnusedCode`, `findUnusedSuppress`) — note Psalm has **no `level` field, no `memoryLimit` field, no `autoload` field, no `FULL_PROJECT` flag**. Implements `notifyAboutMissingConfig` with two notification actions ("Open settings" + **"Generate psalm.xml"** — invokes `psalm --init . 3`). |
| `PsalmOptionsConfiguration` | 89 | DUPLICATE of the same four fields, as `@State(name="PsalmOptionsConfiguration", storage="php.xml")` project service. Used by on-the-fly. |
| `PsalmSettingsTransferStartupActivity` | 63 | One-shot migration (Kotlin): copies the four fields from `PsalmGlobalInspection` (legacy storage on the inspection profile) into `PsalmOptionsConfiguration` and sets `isTransferred=true`. Skips on default project / unit-test / headless / non-PhpStorm. |
| `PsalmAnnotatorProxy` | 229 | `QualityToolAnnotator<PsalmValidationInspection>`. Reuses `PsalmGlobalInspection.getCommandLineOptions` for both on-the-fly **and** batch (unlike PHPStan, which special-cases). Has a custom `getAdditionalTimeoutActions()` returning a **"Recreate Psalm cache"** action — runs the tool once with no file arg as a warm-up. |
| `PsalmMessageProcessor` | 174 | `QualityToolXmlMessageProcessor`. Parses **checkstyle XML** (same as PHPStan), between `<file name=` and `</file>`. Severity `error`/`warning`. Pushes results into `project.putUserData(PSALM_ANNOTATOR_INFO, …)` when `myFile == null` (batch mode). |
| `PsalmComposerConfig` | 237 | `QualityToolsComposerConfig`. Reads `composer.json`, picks `vendor/bin/psalm`, applies `psalm.xml` *or* `psalm.xml.dist` ruleset, parses `--config=`/`-c` from `scripts.psalm`. |
| `PsalmConfigurable` | 102 | Settings configurable under `Settings/PHP/Quality Tools/Psalm`. Parent id `settings.php.quality.tools`. |
| `PsalmConfigurableForm` | 124 | `QualityToolConfigurableForm` subclass — version regex `Psalm.* v?([\d.]*).*`, plus a special-case for `dev-master` builds. `validateWithNoAnsi()=false` (Psalm strips colours via `--monochrome` instead). |
| `PsalmOptionsPanel` | 222 | Swing-Designer panel. **Three checkboxes** (showInfo / findUnusedCode / findUnusedSuppress) + **one config-path field** (with SDK-aware browse). No level spinner, no memory-limit textfield, no autoload field. Simpler than PHPStan's panel. |
| `PsalmAddToIgnoredAction` | 34 | Subclass of `QualityToolAddToIgnoredAction`. Just returns the tool type. |
| `PsalmOpenSettingsProvider` | 37 | Hook for `composerLogMessageBuilder` so notifications deep-link to the Psalm settings page. |
| `PsalmBundle` | 125 | i18n. |

**Total bundled: ~2,460 LOC of Java/Kotlin glue.**

Notable Psalm-specific class absent from PHPStan: none — Psalm is a
*strict subset* of PHPStan's class list (no `*QualityToolAnnotatorInfo`
marker subclass, no `*CompletionContributor`).

### 1.2. Remote interpreter glue

In `psalm.jar` (only loaded if remote-interpreter plugin is on):

| Class | LOC | Role |
| --- | --- | --- |
| `PsalmRemoteConfiguration` | 170 | Subclass of `PsalmConfiguration` that adds `interpreterId`, `PhpSdkDependentConfiguration` impl. **`@Tag("psalm_fixer_by_interpreter")`** for XML — note the `_fixer_` infix is a verbatim copy-paste from PHPCSFixer's tag; it is **not** semantically meaningful for Psalm but **must be preserved** for backward-compat load. |
| `PsalmRemoteConfigurationProvider` | 219 | Registered on `com.jetbrains.php.tools.quality.Psalm.PsalmConfigurationProvider` EP. Overrides `createNewInstance` (opens the by-interpreter dialog) and bumps default timeout to **60 000 ms**. Constant `PSALM_BY_INTERPRETER = "psalm_fixer_by_interpreter"`. |

In `php-remoteInterpreter.jar` — shared with PHPStan, phpcs,
phpCSFixer, phpmd, Laravel Pint. Same three classes
(`QualityToolByInterpreterDialog`, `QualityToolByInterpreterConfigurableForm`,
`QualityRemoteToolProcessHandler`) as in the PHPStan plan §1.2.
**No Psalm-specific additions.**

### 1.3. Plugin metadata

`META-INF/plugin.xml`:

- `<depends>com.jetbrains.php</depends>`
- `<depends>com.intellij.modules.ultimate</depends>` (PhpStorm only)
- `<depends optional="true" config-file="psalm-remote-plugin.xml">org.jetbrains.plugins.phpstorm-remote-interpreter</depends>`
- 5 services + 1 inspection (`globalInspection PsalmGlobal`) + 1
  externalAnnotator + 1 postStartupActivity + 1 configurable + 1
  composerConfigClient + 1 type + 1 openSettingsProvider + 1 inner
  EP + 1 action.

**No `<completion.contributor>` element** (cf. PHPStan plan §1.3).

---

## 2. Functional surface (what the user sees)

### 2.1. User-facing features

1. **Settings page** at `PHP / Quality Tools / Psalm` — same shape
   as PHPStan §2.1.1 except the per-profile common options are:
   - config path (`psalm.xml` or `psalm.xml.dist`),
   - "Show info" toggle (`--show-info=true`),
   - "Find unused code" toggle (`--find-unused-code`),
   - "Find unused suppress" toggle (`--find-unused-psalm-suppress`).
   No level spinner. No memory-limit field. No autoload field. No
   full-project checkbox.
2. **Inspection profile entries**:
   - `PsalmGlobal` (global, batch). Severity / scope / enable
     toggle. *(same as PHPStan §2.1.2)*
   - `PsalmValidation` (local) — surfaces on-the-fly results.
3. **On-the-fly analysis** while editing a `.php` file. *(same)*
4. **Project-wide analysis** via `Code → Inspect Code…`. *(same)*
5. **Composer auto-detect**: when `vendor/bin/psalm` appears, auto-
   configure the tool path; read **`psalm.xml`** then fall back to
   **`psalm.xml.dist`**; parse `composer.json scripts.psalm` for
   `--config=`/`-c` flag. *(same shape as PHPStan §2.1.5 but the
   data being parsed is config path, not memory-limit.)*
6. **"Add to ignored" action** in the right-click menu on any
   Psalm error in the editor — appends to `PsalmBlackList`. *(same
   as PHPStan §2.1.6)*
7. **Notifications** with deep-links to Psalm settings. *(same)*
8. **"Missing psalm.xml" notification with `--init` action**:
   when batch starts and there is no `psalm.xml` and no `-c` flag,
   the global inspection notifies the user and offers a **"Generate
   psalm.xml in project root"** action that runs `psalm --init . 3`
   in the project root and stores the generated path on
   `PsalmOptionsConfiguration`. **This is Psalm-only** — PHPStan
   has no equivalent generate-config action.
9. **"Recreate Psalm cache" action** on the on-the-fly timeout
   notification: Psalm caches across runs in `~/.psalm` and a slow
   first run is normal; this action invokes Psalm once with no file
   arg as a warm-up. **Psalm-only** — PHPStan has no equivalent.
10. **Settings migration**: same shape as PHPStan §2.1.9 — copies
    four fields from `PsalmGlobalInspection` into
    `PsalmOptionsConfiguration`.
11. **Remote PHP interpreter support** (Docker/SSH/WSL) — same as
    PHPStan §2.1.10. 60-second default timeout. Path mapping for
    `-c` config arg. Local↔remote conversion via
    `QualityToolAnnotator.updateIfRemoteMappingExists`.

### 2.2. Internal plumbing (collapsed by the new SDK)

Same shape as PHPStan §2.2: app+project base managers, three
configuration objects, two parallel pipelines, custom by-interpreter
dialog, manual SAX parsing of checkstyle output, manual dedup.

---

## 3. Mapping each feature to the new SDK

| Legacy feature | New SDK home | Notes |
| --- | --- | --- |
| `PsalmQualityToolType` | `QualityTool` (phase 01) — one Kotlin class | Same EP `dev.jplugins.qualityTools.tool`. Inspection-shortname preservation (phase 10a.1): `Psalm.inspectionShortNames = setOf("PsalmGlobal", "PsalmValidation")`. |
| `PsalmConfiguration` / `PsalmRemoteConfiguration` / `PsalmProjectConfiguration` | `LocalBinarySource` (phase 02), `PhpInterpreterBinarySource` from `:php` (phase 02), unified `QualityToolsProjectStorage` (phase 04). | **Same as PHPStan §3.** Zero Psalm-specific persistence. |
| `PsalmConfigurationManager` + `*BaseManager` | Unified storage (phase 04). | Same as PHPStan §3. |
| `PsalmConfigurationProvider` EP | `ConfigSourceType` EP (phase 02). | Same as PHPStan §3. |
| `PsalmRemoteConfigurationProvider` | `PhpInterpreterBinarySourceType` in `:php`. | Same generic implementation serves Psalm and PHPStan; the only customisation is the typeId discriminator and the legacy XML tag (see §4.1 below for the `_fixer_` quirk). |
| `PsalmBlackList` | `GlobPathIgnorePolicy` (phase 06). | **Same as PHPStan §3.** |
| `PsalmValidationInspection` + `PsalmGlobalInspection` (shortnames only) | Phase 10a.1 inspection-shortname preservation. | `PsalmGlobal` / `PsalmValidation` carried through the SDK bridge. |
| `PsalmAnnotatorProxy` | `QualityToolsAnnotator` + `PsalmTool.buildArgs` (phase 01/08). | The Psalm `buildArgs` is **trivial** — fixed `--output-format=checkstyle --monochrome`, plus four conditionals (config, showInfo, findUnused, findUnusedSuppress). ~25 LOC. |
| `PsalmMessageProcessor` | `CheckstyleXmlReader` (phase 06) — bundled. | Psalm emits the **exact same checkstyle dialect as PHPStan** (line/column/severity/message attrs on `<error>` inside `<file>`); zero Psalm-specific reader code. **Confirmed by reading `PsalmMessageProcessor.java`** — see §3a below. |
| `PsalmComposerConfig` | `ComposerBinarySourceType` in `:php` + a small `PsalmComposerOnDetectedHook` (phase 02 — needs the gap from PHPStan plan §4.5). | Composer source covers the binary; `psalm.xml`/`psalm.xml.dist` discovery + `scripts.psalm --config=`/`-c` parsing needs the same `OnDetectedHook` SAM that PHPStan needs. |
| `PsalmConfigurable` + `PsalmConfigurableForm` + `PsalmOptionsPanel` | `PsalmOptionsSchema` + `AutoToolSettingsPanel` (phase 04 + 07). | One text-field + three checkboxes → four `OptionSpec`s. **Smaller schema than PHPStan's six.** |
| `PsalmOptionsConfiguration` | `OptionsBag` in the unified storage (phase 04). | Same as PHPStan §3. |
| `PsalmSettingsTransferStartupActivity` | `Migrator` impl. | Generic mechanism, same shape as Mago/PHPStan migration. |
| `PsalmAddToIgnoredAction` | Auto-wired by the SDK from the registered `GlobPathIgnorePolicy` (PHPStan plan §4.7). | Same gap. |
| `PsalmOpenSettingsProvider` | `InternalErrorActionProvider` (phase 07) — keyed on `category="psalm.*"`. | Same generic mechanism as PHPStan §3. |
| `--init` "Generate psalm.xml" notification action | New SDK gap — see §4.1 below | Psalm-specific custom command; not exercised by PHPStan. |
| "Recreate Psalm cache" timeout action | New SDK gap — see §4.2 below | Psalm-specific custom command; not exercised by PHPStan. |
| Version-validate button (`psalm --version`) | `BinaryValidator` (PHPStan plan gap §4.1). | Same gap; Psalm's regex differs (`Psalm.* v?([\d.]*).*` plus `dev-master` carve-out) but the SAM is shared. |
| 60 s default timeout for remote profiles | `ConfigSourceType.defaultTimeoutMs` (PHPStan plan gap §4.4). | Same gap. |

### 3a. Output-format cross-check (the question)

Psalm's CLI supports many formats — `console`, `compact`, `text`,
`emacs`, `json`, `pylint`, `xml`, `checkstyle`, `junit`, `sonarqube`,
`github`, `phpstorm`, `codeclimate`. The legacy plugin hard-codes
**`--output-format=checkstyle`** in `PsalmGlobalInspection.getCommandLineOptions`
and the message processor parses the checkstyle XML envelope.

That is **the same format PHPStan's plugin uses**. The two readers
read effectively the same SAX events: `<file name=…>` then nested
`<error line=… column=… severity=… message=…/>`. The only practical
delta is:

- Psalm always emits a `column` attribute; PHPStan emits one
  inconsistently. `PsalmMessageProcessor.getColumn()` returns
  `myColumn - 1` (convert 1-based to 0-based) — the same convention
  is already in PHPStan's `QualityToolXmlMessageProcessor` parent.
- Psalm does **not** emit `<error source="…">`. PHPStan does, under
  `--error-format=raw` (unused here). Not relevant.

**Conclusion: the bundled `CheckstyleXmlReader` from phase 06 covers
Psalm with no per-tool work.**

### 3b. `psalm.xml` cross-check (the question)

Yes — Psalm has its own project-level config file, **`psalm.xml`**
(or its committed-to-VCS twin **`psalm.xml.dist`**). Functionally
analogous to PHPStan's `phpstan.neon`. The legacy plugin treats it
identically to `phpstan.neon` from the SDK's point of view: a
config-file path the user can set, the Composer auto-detect tries to
find one next to `composer.json`, and the resolver replaces local↔
remote paths via `updateIfRemoteMappingExists`.

The only Psalm-specific bit is the **two-name fallback**
(`psalm.xml` → `psalm.xml.dist`). PHPStan's plugin only looks for
the single `phpstan.neon` name. **This is a one-line difference in
the `OnDetectedHook` body**, not an SDK gap.

### 3c. Baseline support cross-check (the question)

Psalm has first-class baseline support via `--set-baseline=`,
`--use-baseline=`, `--update-baseline`, `--ignore-baseline`. The
JetBrains plugin **does not surface any of these** — the
`PsalmOptionsConfiguration` panel has four options and none of them
are baselines. Users who want baselines today must add the flag
manually outside the IDE (e.g. in `composer.json scripts.psalm`) or
rely on the Composer-script auto-detect to pass it through.

**Result: no baseline support in scope.** Same as PHPStan — both can
be added later via `IgnorePolicyType` (`typeId="psalm.baseline"`),
but neither exists today. Out of scope here.

---

## 4. Gaps in the new SDK exposed by this exercise

Most of the gaps PHPStan surfaced (PHPStan plan §4.1–4.9) are
re-exercised by Psalm verbatim. Below I list those for completeness
with a one-line confirmation, then list the **two new gaps Psalm
introduces** that PHPStan did not.

### 4.0. Gaps re-exercised from the PHPStan list (no SDK changes beyond what PHPStan already needs)

- **PHPStan §4.1 `BinaryValidator`** — yes, Psalm uses the same
  pattern (`PsalmConfigurableForm.validateMessage`). Its regex
  differs but the SAM is identical. **Same fix; nothing new.**
- **PHPStan §4.2 per-mode reader id** — not exercised (Psalm only
  ever uses checkstyle today, and the JetBrains plugin only ever
  reads checkstyle). Latent.
- **PHPStan §4.3 `ResolvedBinary.detectedVersion`** — Psalm
  exercises it too (the `validateMessage` carve-out for `dev-master`
  is exactly a version-aware branch). **Same fix.**
- **PHPStan §4.4 `defaultTimeoutMs`** — yes, Psalm's
  `PsalmRemoteConfigurationProvider.fillSettingsByDefaultValue`
  bumps timeout to 60 s identically. **Same fix.**
- **PHPStan §4.5 `OnDetectedHook` for Composer** — yes, Psalm's
  `PsalmComposerConfig` is the structural twin of PHPStan's.
  Differences are pure data (regex, file names). **Same fix.**
- **PHPStan §4.6 stderr filtering** — *not exercised by Psalm*.
  Psalm does not emit known-noise stderr lines that the plugin
  filters. `PsalmAnnotatorProxy` has no override of
  `showMessage`. Latent.
- **PHPStan §4.7 auto-wired "Add to ignored" action** — yes. Same
  pattern, same fix.
- **PHPStan §4.8 inspection-profile compatibility with field
  history** — yes. Psalm's four fields
  (`config`/`showInfo`/`findUnusedCode`/`findUnusedSuppress`) need
  the same migration mechanism. **Same fix.**
- **PHPStan §4.9 batch-mode user-data dance** — yes. Psalm uses
  `project.putUserData(PSALM_ANNOTATOR_INFO, …)` from
  `PsalmGlobalInspection.inspectionStarted` and reads it back in
  `PsalmMessageProcessor.processMessage`. **Same fix.**

So eight of nine PHPStan gaps re-exercise verbatim. None require
*additional* SDK changes beyond what PHPStan already drives.

### 4.1. **New gap**: tool-emitted "generate config" affordance

**What it is:** When Psalm's batch inspection starts and there is
no `psalm.xml` in the project root *and* no `-c` flag on the
command line, the plugin shows a notification with **two actions**:

1. "Open inspection settings" — generic, covered by gap §3
   above (`InternalErrorActionProvider`).
2. **"Generate psalm.xml in project root"** — runs
   `psalm --init . 3` (where `3` is the default Psalm level),
   refreshes VFS, and stores the new path on
   `PsalmOptionsConfiguration` via
   `InspectionProfileManager.modifyToolSettings`.

This second action is a *tool-specific custom CLI invocation* used
to generate a configuration file. PHPStan has nothing analogous —
`phpstan --init` exists but the plugin doesn't expose it.

**Today:** Hard-coded in
`PsalmGlobalInspection.notifyAboutMissingConfig`'s anonymous
`AnAction` subclass (~30 LOC).

**Recommendation:** Define a small extension on `QualityTool`:

- `public val initConfigAction: InitConfigAction?` — when non-null,
  the SDK's missing-config notification adds an extra action with
  the supplied label that, when clicked, runs the tool with the
  supplied argv, refreshes VFS, then writes the resulting path into
  the named `OptionSpec` (e.g. `config`).

This is generic enough to also serve future tools that gain an
`--init` affordance (Pint, phpcs, etc.). **Phase 07 edit (small):
extend the missing-config notification surface; ~20 LOC of SDK
contract, ~10 LOC of Psalm-side data.**

**Cost of not fixing:** Psalm port keeps a ~30 LOC custom action
class; not catastrophic, but the affordance is a generic shape and
will repeat once we look at the other tools.

### 4.2. **New gap**: "recreate cache" affordance on timeout

**What it is:** Psalm's `PsalmAnnotatorProxy.getAdditionalTimeoutActions()`
returns a single `AnAction` labelled "Create cache" that, when the
on-the-fly run times out, lets the user manually warm the Psalm
cache by invoking `psalm` once with no file argument (just the
configured options). The action runs synchronously via
`QualityToolProcessCreator.getToolOutput`.

PHPStan has **no** override of `getAdditionalTimeoutActions` and
ships zero such actions.

**Today:** Hard-coded in `PsalmAnnotatorProxy` as an anonymous
inner-class `AnAction` (~25 LOC).

**Recommendation:** Generalise to a small slot on `QualityTool`:

- `public val onTimeoutActions: List<TimeoutAction>`
- Each `TimeoutAction` carries (label, argv-builder, post-run
  callback).
- The SDK's runner emits a `Timeout` outcome that the
  `QualityToolsAnnotator` turns into a notification; that
  notification iterates `onTimeoutActions` to build extra buttons.

This is **also relevant to PHPStan** — its `phpstan analyse` can
benefit from a "rebuild result cache" action against the
`tmp/cache` directory. So this is not really Psalm-only; it's just
that Psalm is the *only legacy plugin* that currently surfaces such
an action. **Phase 05/07 joint edit (small): a runner outcome plus
a UI surface to attach actions to it.**

**Cost of not fixing:** ~25 LOC of Psalm-specific Swing per port.
Acceptable but mildly duplicative.

### 4.3. Latent: tool-side "warmup before first run" hook

Related to §4.2 but distinct: Psalm's cache directory must exist
before the first run. The legacy plugin doesn't enforce this — it
just lets the first run be slow and exposes the "Create cache"
button as a recovery affordance. A more invasive design would let
`QualityTool` declare a `coldStartWarmup` hook that the runner
invokes once per profile.

I would **not** add this to the SDK now. It's speculative; the §4.2
affordance covers the user-facing case. Mentioned here only so the
plugin author guide can record the design space.

### 4.4. Latent: legacy XML tag with cross-tool name collision

`PsalmRemoteConfiguration` is annotated `@Tag("psalm_fixer_by_interpreter")`.
The `_fixer_` infix is a copy-paste from
PHPCSFixer's tag name — almost certainly a bug at JetBrains, but
it's been shipping for years and any migration must accept that
exact tag verbatim or else existing remote-interpreter Psalm configs
will fail to load.

**Recommendation:** The Psalm migration step in the new plugin must
register `psalm_fixer_by_interpreter` (alongside any saner new tag,
if introduced) as a legacy XML tag the migrator recognises. This is
a one-line entry in `PsalmMigration.legacyTags`; no SDK change. Just
documenting it so the implementer doesn't "fix the typo" and lose
backward compat.

---

## 5. Generic-code overhead — what we'd write if we ported now

Following §4, if we shipped Psalm today on the SDK *as specified
after the PHPStan gap-fix pass*, the Psalm-specific code is even
**smaller** than PHPStan's: fewer options, no completion
contributor, no autoload-path quirks, no full-project flag.

| Code we'd write in the Psalm port | Generic? | Action |
| --- | --- | --- |
| `PsalmTool : QualityTool` | unique | keep |
| `PsalmOptionsSchema : OptionsSchema` | unique | keep (4 specs vs. PHPStan's 6) |
| `PsalmComposerOnDetectedHook` | unique | keep (PHPStan gap §4.5 fix needed) |
| `PsalmVersionValidator : BinaryValidator` | unique | keep (PHPStan gap §4.1 fix needed) |
| `PsalmMigration : Migrator` | unique | keep, including the `psalm_fixer_by_interpreter` legacy tag |
| ⚠ `psalm --init` generate-config action | **generic** | resolve via §4.1 |
| ⚠ "Create cache" timeout action | **generic** | resolve via §4.2 |
| (no completion contributor; not applicable) | — | — |
| (no stderr filter; not exercised) | — | — |

**Two new ⚠ items beyond the PHPStan list, both small.** Each is
~20–30 LOC of generic-shaped code per tool. Combined with the six
items already on the PHPStan ledger that's eight items across two
tools — the curve is flattening, which is the desired signal.

---

## 6. Concrete file list for the Psalm port (post-gap-fix)

Assuming **PHPStan-plan gaps §4.1, §4.3, §4.4, §4.5, §4.7, §4.8,
§4.9** **and** the two new gaps **§4.1, §4.2 here** are all merged
into the SDK, the Psalm plugin shrinks to roughly:

**Required**:

- `PsalmTool.kt` (~70 LOC) — id, modes (`analyze` on-the-fly,
  `analyze-full` batch), capabilities, options schema, buildArgs.
  Smaller than `PhpStanTool` because no full-project/level/memory
  branches.
- `PsalmOptionsSchema.kt` (~30 LOC) — four specs + mode schemas.
- `PsalmVersionValidator.kt` (~30 LOC) — `Psalm.* v?([\d.]*)` regex
  + `dev-master` special case.
- `PsalmComposerOnDetectedHook.kt` (~70 LOC) — `psalm.xml` /
  `psalm.xml.dist` discovery, `scripts.psalm` `--config=`/`-c`
  parsing.
- `PsalmMigration.kt` (~70 LOC) — legacy XML → new storage, including
  the `psalm_fixer_by_interpreter` tag.
- `PsalmInitConfigAction.kt` data (~15 LOC) — declares the
  `psalm --init . 3` invocation that the §4.1 hook runs.
- `PsalmCacheWarmupAction.kt` data (~15 LOC) — declares the §4.2
  cache-warmup invocation.
- `META-INF/plugin.xml` (~40 LOC) — registrations.

**Optional** (small features that stay Psalm-specific):

- `PsalmBundle.properties` — i18n.
- `inspection.html` — description for the inspection.

**Total: ~340 LOC + bundle/HTML**, vs. ~2,460 LOC today. **~7×
reduction.** Same compression ratio as PHPStan — reassuring; it
confirms the SDK's leverage is consistent across tools, not
PHPStan-shaped.

---

## 7. Order of work (when we get to coding)

Sequenced so each step is mergeable independently. Numbered against
the existing phase-doc plan and the PHPStan port plan §7.

1. **SDK gap fixes for Psalm-introduced gaps** (small phase patches
   on top of the PHPStan gap pass):
   - **§4.1** `QualityTool.initConfigAction` → phase 07.
   - **§4.2** `QualityTool.onTimeoutActions` plus the runner
     `Timeout` outcome surface → phase 05 + 07.

   Each is ≤ 50 LOC of code; ship as a "phase 11 hardening" PR
   alongside the PHPStan ones.

2. **Psalm tool registration** — phase 01-style minimal port:
   `PsalmTool` + `PsalmOptionsSchema` + reuse the bundled
   `CheckstyleXmlReader`. Result: Psalm visible in Settings, but no
   validate button, no Composer auto-detect, no remote support, no
   `--init` action, no cache-warmup action.

3. **Psalm version detection** — `PsalmVersionValidator` wired into
   the validate button (PHPStan gap §4.1, dev-master branch).

4. **Psalm Composer auto-detect** — `PsalmComposerOnDetectedHook`
   wired into the generic `ComposerBinarySourceType` (PHPStan gap
   §4.5). Replaces `PsalmComposerConfig` entirely. Two filename
   fallbacks (`psalm.xml`, `psalm.xml.dist`).

5. **Psalm remote** — `<depends optional="true">` on the new
   `PhpInterpreterBinarySourceType` from `:php` (zero Psalm code).
   Tests against the 60-s timeout from PHPStan gap §4.4 *and*
   the `psalm_fixer_by_interpreter` legacy XML tag.

6. **Psalm migration** — `PsalmMigration` ports legacy XML +
   `PsalmSettingsTransferStartupActivity` profile→options carry-
   over into the unified storage.

7. **Psalm `--init` action** — wire the §4.1 SDK hook. Drives the
   "Generate psalm.xml" notification button.

8. **Psalm cache-warmup action** — wire the §4.2 SDK hook.

9. **Psalm inspection-shortname preservation** — verify
   `PsalmGlobal` / `PsalmValidation` are emitted by the SDK bridge.

10. **Cleanup**: delete the legacy plugin's classes once validated.

If Psalm is ported **after** PHPStan (as recommended), steps 2–6
are pure copy-and-rename of PHPStan equivalents with the simpler
options schema. Steps 7–8 are the genuinely new work and exercise
the two new SDK gaps.

---

## 8. Risks / open questions

- **Bundled vs separate plugin**: same recommendation as PHPStan
  plan §8 — start as a separate plugin to validate, upstream once
  proven.
- **Inspection-profile schema mismatch**: Pre-2024 PhpStorm stored
  Psalm options as four public fields on `PsalmGlobalInspection`
  inside the inspection profile XML. The SDK bridge must surface
  the inspection short-name *and* the migration must read those
  four fields once during transfer (same shape as PHPStan).
- **Multiple modes don't map cleanly**: Psalm has effectively *one*
  mode (`analyze`). No `--analyze` vs `--analyze --pro` distinction
  like PHPStan can have. Recommendation: single mode `analyze`,
  with `target = ToolTarget.SingleFile` for on-the-fly and
  `ToolTarget.Project` for batch.
- **`@psalm-suppress` / `@psalm-var` PHPDoc tags**: not handled by
  the legacy plugin (core PhpStorm handles them in its PHPDoc
  module). Out of scope.
- **Psalm baselines** (`--set-baseline`, `--use-baseline`,
  `--update-baseline`): see §3c — not in the legacy plugin, not in
  the port. Future work behind an `IgnorePolicyType` if requested.
- **Psalm taint mode** (`--taint-analysis`): the original brief
  mentioned a "taint-mode toggle" as a candidate option, but the
  decompiled `PsalmOptionsPanel` does **not** expose one — only
  `showInfo` / `findUnusedCode` / `findUnusedSuppress`. So the port
  inherits no taint toggle. (Users who want taint analysis pass
  `--taint-analysis` via `psalm.xml` itself or via
  `composer.json scripts.psalm` and the Composer auto-detect picks
  it up.) Same for `--show-snippet` / `--dead-code` mentioned in
  the brief — neither is wired by the legacy plugin's options
  panel. Recording this so the port doesn't accidentally invent
  toggles that the legacy product didn't ship.
- **No fix-emitter**: Psalm has `--alter` which can rewrite source,
  but the JetBrains plugin doesn't surface that and neither will we
  (it's a destructive operation best left to a separate IDE
  action). So like PHPStan, Psalm exercises **none** of the
  `ToolFix` hierarchy. Mago alone covers that side; Psalm and
  PHPStan together cover sources, remote interpreter,
  batch-vs-on-the-fly, options, Composer auto-detect, migration,
  and now (Psalm-uniquely) tool-emitted helper actions.
- **The `_fixer_` tag typo** (§4.4): documented; do not "fix" it.

---

## 9. Summary

- The full Psalm integration is **~2,460 LOC of Java/Kotlin glue
  today** spread across 20 classes in two jars. **~190 LOC less
  than PHPStan**, primarily because Psalm has no completion
  contributor, a smaller options schema (4 vs. 6 fields), and no
  `*QualityToolAnnotatorInfo` marker subclass.
- Mapped onto the proposed SDK as-specified after the PHPStan gap
  pass, ~95% of those classes disappear — *the same compression
  ratio as PHPStan*, which is the desired structural signal.
- **Eight of the nine PHPStan-list SDK gaps re-exercise verbatim**.
  No additional fixes needed beyond what PHPStan already drives.
- **Two new gaps** surface from Psalm that PHPStan did not:
  - §4.1 `QualityTool.initConfigAction` (tool-driven "generate
    config file" affordance).
  - §4.2 `QualityTool.onTimeoutActions` (tool-driven "warm
    cache" affordance on timeout).

  Both are small (≤ 50 LOC of SDK code each) and generic across
  future tools. Both should land as part of the same phase-11
  hardening PR that absorbs the PHPStan ones.
- Post-gap-fix the Psalm plugin lands at **~340 LOC** — a 7×
  reduction.
- The output format is **checkstyle XML — identical to PHPStan's** —
  so the bundled `CheckstyleXmlReader` handles Psalm with zero
  per-tool reader code.
- `psalm.xml` / `psalm.xml.dist` is the structural equivalent of
  `phpstan.neon`. Two-filename fallback is a one-line difference in
  the `OnDetectedHook` body.
- **Baseline support is out of scope** — neither legacy plugin nor
  port surfaces Psalm baselines. Future work behind a future
  `IgnorePolicyType` if requested.
- Recommendation: port Psalm **second**, immediately after PHPStan.
  It will exercise the SDK against a tool we didn't design with,
  share ~90% of its gap-fix surface with PHPStan, and surface the
  two remaining generic affordances (init-config, timeout-warmup)
  while the gap-fix machinery is still warm.
