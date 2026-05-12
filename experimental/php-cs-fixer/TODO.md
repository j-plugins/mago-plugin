# PHP-CS-Fixer port — TODO

Out-of-scope items deferred from the initial implementation. Each
links to the SDK gap that gates the work.

## Tier-2 SDK gaps

- **G13** — `DynamicChoiceSpec` coding-standard combobox. Today
  `codingStandard` is a `StringSpec`; it needs to be a dynamic
  enum-like spec backed by `php-cs-fixer list-sets --format=json`
  with the legacy fallback list. See
  `PhpCsFixerOptionsSchema.KNOWN_STANDARDS`.
- **G14** — `Custom` sentinel UI visibility rule.
  `customConfig` must only show when `codingStandard == "Custom"`;
  the `allowRiskyRules` checkbox must be disabled in that same
  state. Needs an `OptionVisibilityRule` mechanism on `OptionSpec`.
- **G21** — `UdiffReader` bundled in `:core`. The dry-run mode's
  output is a JSON envelope wrapping a udiff body; the reader is
  not yet implemented. `PhpCsFixerTool.DryRunMode.resultReaderId`
  references `OutputFormats.UDIFF` so that wiring becomes a no-op
  once the reader ships.
- **G22** — `InvokeMode` ToolFix: "rerun without --dry-run".
  Quick-fix on every annotator message that invokes the `format`
  mode of the same tool over the same file. Also gates the
  `formatAfterFix` option's runtime effect.

## Tool-specific deferred items

- **Format-on-commit** (gap G24) — `PhpExternalFormatterCheckinHandler`
  port. Generic SDK addition; not plugin-specific.
- **AsyncFormattingServiceAdapter wiring** — phase 07 of the SDK
  roadmap. The tool declares `executionStyle = "format"`; the
  adapter picks it up automatically once wired.
- `PhpCsFixerCheckinHandler` — see format-on-commit gap.
- `PhpCsFixerMigration` — legacy three-field XML carry-over.

## Out-of-scope deliberately (this plugin)

- `PhpCsFixerStderrFilterEnricher` — the "risky fixers" / "Loaded
  config" stderr filtering enricher, ships once `MessageEnricher`
  is generally available.
- `PhpCsFixerEnvMutator` — `PHP_CS_FIXER_ALLOW_RISKY=yes` env
  injection. Trivial; ships with the `EnvMutator` SDK EP.
- `PhpCsFixerCustomConfigWorkingDirResolver` — when the user picks
  the `Custom` standard with a path, the working dir becomes
  `dirname(customConfig)`. Gates on the per-mode
  `WorkingDirResolver` SDK gap.
