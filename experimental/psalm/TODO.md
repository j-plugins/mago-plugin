# Psalm port — deferred work

Tracks items the in-this-worktree slice intentionally leaves for
later. Each item references the SDK gap that must close first.

## Deferred per the agent's scope brief

- **PsalmCheckstyleReader** — relies on the bundled
  `CheckstyleXmlReader` from phase 06; SDK doesn't ship that
  reader yet. Once phase 06 lands, no per-tool reader code is
  needed: `resultReaderId = OutputFormats.CHECKSTYLE_XML` is
  already declared on `PsalmTool` and the same registered
  reader services PHPStan + Psalm (`docs/psalm/psalm-port-plan.md`
  §3a).

- **PsalmMigration** — needs the persistent storage layer
  (phase 04) so legacy `PsalmGlobalInspection` profile fields
  (`config`, `showInfo`, `findUnusedCode`, `findUnusedSuppress`)
  and the `@Tag("psalm_fixer_by_interpreter")` quirk
  (`docs/psalm/psalm-port-plan.md` §4.4) can be carried into the
  new `OptionsBag` + `ConfigSource` model.

- **`Generate psalm.xml` notification action** — Tier-4 gap G31
  (`docs/psalm/psalm-port-plan.md` §4.1). Needs
  `QualityTool.initConfigAction` on the SDK before we can declare
  the `psalm --init . 3` invocation here. Until then, users see
  only the generic "Open settings" notification.

- **Recreate-Psalm-cache timeout affordance** — Tier-4 gap G32
  (`docs/psalm/psalm-port-plan.md` §4.2). Needs
  `QualityTool.onTimeoutActions` + a runner `Timeout` outcome.
  Once present, declare a `CacheWarmupAction` that invokes Psalm
  with no file argument.

- **`psalm_fixer_by_interpreter` legacy XML tag** — needs the
  storage / migration layer to register the legacy tag verbatim.
  Defer until `PsalmMigration` is wired (`docs/psalm/psalm-port-plan.md`
  §4.4).

## Latent

- **`dev-master` version branch** — `PsalmVersionValidator` falls
  back to "cannot determine version" for `Psalm dev-master`
  output. The legacy plugin special-cased it; reinstating the
  carve-out should use `ResolvedBinary.detectedVersion` (Tier-1
  patch G8) once the validator gains a richer success type.
  Tracked in the unit test `dev-master variant falls back to
  cannot-determine-version` so a future change is observable.
