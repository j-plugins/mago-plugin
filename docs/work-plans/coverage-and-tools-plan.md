# Work plan — SDK coverage + `:php` foundation + parallel tool agents

> Scope of this work item, recorded so reviewers can verify every track
> landed. Driven from the verifier's last pass on commit `a009323`
> and the user's directive to cover the SDK 100%, write PHP-integration
> tests, and develop each quality-tool plugin in parallel worktrees.

## Guiding principles

1. **No code is altered to make a test pass.** If a line cannot be
   covered by a *real* scenario, it stays uncovered and we delete the
   would-be test rather than write a placeholder.
2. **Tests assert behaviour, not shape.** A test that just verifies
   "this constant equals that constant" is deleted unless it locks in
   a public-contract value.
3. **Parallelism is real.** Each tool plugin is implemented by an
   independent agent in its own git worktree so they don't fight over
   files.
4. **Plugin code that depends on phase 05/06/07/08 is marked TODO
   with the gap number**, not stubbed with placeholder behaviour.

## Track A — `:core` and `:testing` test coverage

### A.1. Inventory: every public symbol that needs a test

Already-covered files (existing tests confirmed against `a009323`):

- `tool/ToolArg.kt` + `tool/ToolArgs.kt` — `ToolArgTest`.
- `tool/BinaryValidator.kt` — `BinaryValidatorTest`.
- `tool/QualityToolBuilder.kt` — `QualityToolBuilderTest`.
- `tool/ToolMode.kt` — `ToolModePatchesTest`.
- `source/ConfigSourceType.kt` defaults — `ConfigSourceTypePatchesTest`.
- `source/local/LocalBinarySourceType.kt` — `LocalBinarySourceTypeTest`.
- `migration/LegacyInspectionFieldsMigrator.kt` — its own test.
- `options/Specs.kt` — `SpecsTest`.

Files needing new tests:

| File | What's not yet covered |
| --- | --- |
| `context/CancellationToken.kt` | `Never` companion (`cancel` no-op, `onCancel` returns useless `AutoCloseable`, `throwIfCanceled` doesn't throw) |
| `context/QtLogger.kt` | `NoOp` companion (doesn't throw on any input) |
| `source/PathMapper.kt` | `Identity` companion (`canProcess == false`, `toRemote/toLocal` round-trip) |
| `source/ResolvedBinary.kt` | `SimpleResolvedBinary` defaults (workingDir null, env empty, mapper Identity, supportsStdin true, detectedVersion null) |
| `source/SerializedSourceElement.kt` | `child()` and `childrenNamed()` default methods |
| `source/local/LocalBinarySource.kt` | `resolve()` returns a `SimpleResolvedBinary` carrying path / env / detectedVersion |
| `profile/ModeSettings.kt` | `SimpleModeSettings` defaults, equality of data class |
| `tool/ToolUi.kt` | `Default` and `Hidden` are distinct |
| `tool/Capabilities.kt` / `tool/OutputFormats.kt` / `tool/ToolMode.kt` constants | values are stable strings (locks public contract) |
| `tool/QualityTool.kt` | interface default methods (`inspectionShortNames` default, `capabilities`/`acceptedSourceTypeIds`/`binaryValidator`/`ui` defaults) via a minimal-fixture impl |
| `message/SeverityLevels.kt` | every constant value is the documented string |
| `testing/MapOptionsBag.kt` | get/set/snapshot, mode overlay falls back to parent, commit is no-op |
| `testing/ManualCancellationToken.kt` | concurrent cancel from another thread is observed within 500 ms; `onCancel` handlers fire in registration order; `cancel()` is idempotent |
| `testing/RecordingQtLogger.kt` | log entries collected, `assertLoggedOnce` happy/failure, `clear()` resets |
| `testing/FakeLegacyInspectionElement.kt` | returns null for missing key |

Plus a small `module-discipline` test in `:core` that fails the build
if a JDOM/Swing/IntelliJ class leaks into `:core` classpath (runs
`Class.forName("org.jdom.Element")` and asserts `ClassNotFoundException`).

### A.2. Tests to delete

Reading all eight existing tests: none asserts a tautology (e.g. `1 == 1`)
or boxes a private impl. **Zero deletions** in this pass.

### A.3. Acceptance

- [ ] Every file under `:core/src/main/kotlin/` has at least one assertion
      against one of its public behaviours.
- [ ] Every file under `:testing/src/main/kotlin/` has at least one
      assertion in `testing/src/test/kotlin/` (need new test dir).
- [ ] No test exists that only re-asserts a `val` definition (rule 2).
- [ ] Module-discipline test for `:core` exists.

## Track B — `:php` foundation + tests

`:php` is currently an empty module. This is the second major SDK
deliverable identified in the promotion analysis
(`promotion-analysis.md` §2.6, §3, §7). We can ship four pure-Kotlin
helpers now (no IntelliJ needed) so the per-tool agents in track C
have building blocks.

### B.1. Deliverables

1. **`PhpToolVersionParser : BinaryValidator`** — takes a
   tool-name pattern (regex) and optional minimum version; returns a
   `SimpleValidationResult`. Replaces ~600 LOC of duplicated
   `extractVersion`/`Pair.create` glue across six legacy plugins.

2. **`ComposerJson` parser** — pure-Kotlin reader for `composer.json`
   that extracts `require`/`require-dev`/`scripts.<key>`. No
   IntelliJ; uses `kotlinx.serialization` JSON OR a hand-rolled
   parser (probably the latter — fewer deps, deterministic).

3. **`ComposerScriptArgExtractor`** — extracts named flags
   (`--memory-limit=4G`, `--level=8`, `--configuration=...`)
   from a composer script line. Pure regex on a `String`.

4. **`ComposerToolDescriptor`** — declarative descriptor that future
   `ComposerBinarySourceType` (phase 02 deliverable, lives in `:php`
   too) will consume. Fields: `packageName`, `binName`,
   `configFileNames`, `scriptKey`, `scriptArgExtractors`. Pure data.

These four together let a per-tool plugin author wire Composer
discovery declaratively without re-implementing the parser.

### B.2. Out of scope for this batch

- `PhpInterpreterBinarySourceType` — needs IntelliJ types
  (`com.jetbrains.php.config.interpreters.PhpInterpreter`); defer to
  when phase 02's UI lands.
- `XdebugOffMutator` — needs `EnvMutator` interface from phase 05.
- `ComposerVendorWatcher` — needs IntelliJ VFS.
- `PhpQualityToolsAnnotator` — needs phase 08 annotator base class.
- `PhpStandardIgnoreContributor` — needs `IgnorePolicy` from phase 06.

These are documented in `promotion-analysis.md`; we ship them when
their prerequisite phase lands.

### B.3. Acceptance

- [ ] Four classes above exist in `quality-tools-sdk/php/src/main/kotlin/dev/jplugins/qualitytools/php/`.
- [ ] Each has a test file with ≥ 5 real assertions exercising real
      input (a real `composer.json` snippet for `ComposerJson`,
      real `phpstan --version` strings for `PhpToolVersionParser`,
      etc.).
- [ ] No IntelliJ imports in any of the four classes (verified by
      grep).

## Track C — parallel per-tool worktree agents

Six agents launched in parallel, each in an isolated git worktree
(`isolation: "worktree"`). Each agent receives:

- A pointer to its port plan in `docs/<tool>/<tool>-port-plan.md`.
- The current SDK state (commit `a009323`).
- The `:php` foundation (delivered in track B before agents start).
- An instruction set covering: implement what current SDK contracts
  allow; mark TODO with the gap number for things needing phase 05/06/07/08.

### C.1. Scope per agent

Each agent implements (in `experimental/<tool-id>/` within its
worktree):

1. `<Tool>Tool : QualityTool` via `qualityTool { ... }` DSL.
2. `<Tool>OptionsSchema : OptionsSchema` declaring every option from
   the legacy plugin.
3. `<Tool>buildArgs` — pure function building `List<ToolArg>` from
   `ctx, mode, target`. Path-aware args use `kvPathArg`.
4. `<Tool>VersionValidator` via `PhpToolVersionParser` (from track B).
5. `<Tool>ComposerToolDescriptor` (from track B).
6. Unit tests: `<Tool>BuildArgsTest`, `<Tool>OptionsSchemaTest`,
   `<Tool>ComposerDescriptorTest`, `<Tool>VersionValidatorTest`.

Out of scope per agent (marked TODO with gap number):

- `<Tool>JsonReader` / `<Tool>XmlReader` — needs `ResultReader`
  interface from phase 06.
- `<Tool>Migration` — needs persistent storage from phase 04.
- UI panels, settings page, formatter adapter — phase 07.
- Annotator wiring — phase 08.
- `ComposerBinarySourceType` impl — needs IntelliJ VFS, deferred.

### C.2. Per-tool list

| Agent | Tool | Plan file |
| --- | --- | --- |
| C.1 | PHPStan | `docs/phpstan/phpstan-port-plan.md` |
| C.2 | Psalm | `docs/psalm/psalm-port-plan.md` |
| C.3 | PHP_CodeSniffer | `docs/phpcs/phpcs-port-plan.md` |
| C.4 | PHP-CS-Fixer | `docs/php-cs-fixer/php-cs-fixer-port-plan.md` |
| C.5 | Laravel Pint | `docs/laravel-pint/laravel-pint-port-plan.md` |
| C.6 | Mess Detector | `docs/mess-detector/mess-detector-port-plan.md` |

### C.3. Acceptance per agent

- [ ] `<Tool>Tool` declared with `inspectionShortNames` matching the
      legacy plugin's short-names verbatim (preserved per phase 10a.1).
- [ ] `<Tool>OptionsSchema.specs` covers every legacy field the port
      plan enumerated.
- [ ] `<Tool>buildArgs` round-trip test: given a known
      `(profile, mode, target)`, produces the expected legacy CLI.
- [ ] `<Tool>VersionValidator` recognises the legacy plugin's exact
      version-output strings.
- [ ] Per-tool TODO list (gap numbers) for things deferred.
- [ ] Zero IntelliJ imports in the agent's output (everything goes
      against `:core` + `:php`).

### C.4. Acceptance across all 6

- [ ] No agent's tests fail when run against the SDK + `:php`.
- [ ] The aggregate Kotlin LOC across the 6 plugins is within 30% of
      the target reductions in `cross-tool-synthesis.md` §3.2 (for the
      slice actually implementable today).
- [ ] No duplicated helper survives — if all 6 agents end up writing
      the same regex, that goes back to `:php` as a track-B follow-up.

## Sequencing

```
        Track A (me) ────────┐
                             ├─→ Track C (6 agents in parallel worktrees)
        Track B (me) ────────┘
```

Track B lands FIRST (agents need `PhpToolVersionParser` and
`ComposerToolDescriptor`). Track A lands in parallel with B and is
finished before agents complete so their tests rely on a tested
foundation. Agents spawn after B is committed.

## Risks and mitigation

- **Cannot run gradle in this env** (network blocked). Risk: code
  doesn't compile. Mitigation: write strictly conservative Kotlin
  (no exotic types); a verification agent reviews each commit; final
  CI run on a real workstation closes the loop.
- **Agents' worktrees may produce conflicting `experimental/` dirs**.
  Mitigation: each agent works under
  `experimental/<tool-id>/` — non-overlapping paths.
- **Phase 06 readers aren't available**. Agents mark with TODO; no
  agent stalls on it.
- **`:php` is missing `EnvMutator`/`ProcessSpawner`/`PathMapper`
  remote variants**. Agents don't need them for this batch.
