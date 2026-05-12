# PHPStan port — deferred work

Items the experimental port cannot address today because they depend
on later SDK phases or on integration code that lives outside `:core`
/ `:php`. Each entry cites the **gap number from
`docs/phpstan/phpstan-port-plan.md` §4** so reviewers can trace the
deferral.

The numbering matches the port plan; do NOT renumber on local changes.

## Gaps blocked on later phases

- **gap 4.3 — Tool-version-aware command-line construction**
  `buildPhpStanArgs` currently does not branch on
  `ResolvedBinary.detectedVersion`. The validator already returns the
  detected version (see `PhpStanVersionValidator`), but the SDK does
  not yet propagate it onto `ResolvedBinary`. Land once phase 02
  ships the propagation.

- **gap 4.4 — `ConfigSourceType.defaultTimeoutMs` override (remote
  60 s)**
  Not addressable here because the experimental port has no
  `ConfigSourceType`; only `LocalBinarySourceType` is available in
  `:core` today, and remote-interpreter source is a `:php` phase-02
  deliverable.

- **gap 4.5 — Composer auto-detect `onDetected(bag)` hook**
  `phpStanComposerToolDescriptor` is ready, but the hook that calls
  `applyComposerJson` / `applyDiscoveredConfigFile` on detection is
  part of `ComposerBinarySourceType` (phase 02 in `:php`). When that
  ships, wiring is a one-liner.

- **gap 4.6 — PhpStanXdebugFilter `MessageEnricher`**
  The legacy plugin silently drops one specific Xdebug warning. The
  `MessageEnricher` EP lives in phase 06; until it lands there is
  nowhere to register a filter.

- **gap 4.8 — `PhpStanMigration` (legacy XML → unified storage)**
  Depends on phase 04 `QualityToolsProjectStorage`. The migration
  step is mechanical — copy the five public fields from
  `PhpStanGlobalInspection` profile XML into the new options bag —
  but cannot be written without the target storage type.

- **gap 4.9 — Batch-mode `inspectionStarted` cache contract**
  Requires phase 08 annotator-bridge work (per-run cache keyed by
  `(toolId, profileId)`). The experimental tool sets
  `executionStyle = ON_THE_FLY` and ignores batch mode for now; once
  phase 08 documents the cache contract, the mode can grow to support
  `ExecutionStyles.BATCH`.

## Out-of-SDK items (stay in the host plugin)

- **PHPStan Settings UI panel** — phase 07 owns the auto-generated
  panel; until then no Swing code is required. PHPStan-specific
  options will be rendered by the generic `AutoToolSettingsPanel`
  from the [PhpStanOptionsSchema] declarations.

- **`PhpStanCompletionContributor`** — completion of
  `@phpstan-require-extends` / `@phpstan-require-implements` PHPDoc
  tags is not a quality-tools concern. The legacy class is ~36 LOC of
  `<completion.contributor language="PHP">`; it stays in the host
  plugin's own `plugin.xml`. Port plan §4.10 explicitly excludes it.

- **PHPStan reader registration (`PhpStanCheckstyleReader`)** — the
  bundled `CheckstyleXmlReader` from phase 06 (`:core/readers`) will
  satisfy this for free; there is no PHPStan-specific reader needed.
  Wait for phase 06 to land then assert it serves
  `OutputFormats.CHECKSTYLE_XML`.

## Notes on testing-only deviations

- We did not add a `:testing` module dependency. `PhpStanTestFixtures.kt`
  hand-rolls `MapOptionsBag` / `FakeTarget` / `FakeRunContext` to keep
  the experimental tests independent. Once `:testing` ships the
  canonical `MapOptionsBag`, the fixtures file is a one-screen delete.

- `buildPhpStanArgs` emits `-c=<value>` / `-a=<value>` (single token
  with `kvPathArg`) where the legacy plugin emits `-c` `<value>` (two
  tokens). PHPStan's CLI accepts both forms; the deviation is
  documented inline at the call site. If a real-world regression
  surfaces, switch to emitting two plain args and re-add path-aware
  marking via a downstream `pathArgKeys` mechanism (`ToolMode.pathArgKeys`
  already declares `"-c"` and `"-a"`).
