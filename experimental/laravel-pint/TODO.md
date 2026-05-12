# `experimental/laravel-pint` — TODO

First-cut scope intentionally ships only the Pint **format** mode on
top of the `:quality-tools-sdk`. The following items remain explicitly
out of scope of the initial port; each is a separate follow-up. Track
against `docs/laravel-pint/laravel-pint-port-plan.md`.

## Out of scope of the first cut

- [ ] `LaravelPintXmlMessageProcessor` port — depends on the phase 06
      `ResultReader` API + the CS-Fixer diff-XML reader (shared with
      the PHP-CS-Fixer port). Pint emits the same
      `<report><file><applied_fixer .../>...</file></report>` payload
      so we can reuse it verbatim once the CS-Fixer port lands.

- [ ] `analyze` / on-the-fly mode (`executionStyle = "on_the_fly"`).
      Adds `--test --format=xml -vvv` defaults and wires the diff-XML
      reader. Currently the Pint tool exposes **only** the `format`
      mode; restoring on-the-fly Laravel-Pint inspections is the next
      milestone after the XML reader.

- [ ] Format-on-commit check-in handler — gap G24, shared with
      CS-Fixer. Pint should appear as one of the radio choices in
      `Settings → Version Control → Commit → Run external formatter`.
      Driven by a future `CommitFormatterRegistry` in `:php`; Pint
      contributes nothing beyond having a `format` mode + the
      `"format"` capability.

- [ ] `LaravelPintMigration` — reads the legacy XML state components
      (`LaravelPint`, `LaravelPintOptionsConfiguration`,
      `LaravelPintProjectConfiguration`, `LaravelPintBlackList`, and
      `LaravelPintRemoteConfiguration` when the remote-interpreter
      plugin is enabled) into the unified
      `QualityToolsProjectStorage`. ≈50 LOC; awaits the migrator EP.

- [ ] Preset combobox port (`laravel` / `symfony` / `psr12` /
      `defined in pint.json`). The sentinel `"defined in pint.json"`
      must round-trip verbatim through migration and `buildArgs`
      (legacy `LaravelPintAnnotatorProxy.addPresetOption`). Pint
      sentinel: skip emitting `--preset=` when the value is literally
      `"defined in pint.json"`.

- [ ] `reformatOnlyUncommittedFiles` toggle → `--dirty` flag.
      Cross-cuts the format-on-commit work above.

- [ ] `LaravelPintComposerOnDetectedHook` parsing `scripts.pint` for
      `--preset=` / `--config=` / `--dirty`. Today the
      `LaravelPintComposerToolDescriptor` declares zero `scriptArgs`;
      once the preset / dirty options land, they slot in here.

- [ ] Stderr suppression of `[OK] Your system is ready to run the
      application.` — a small `MessageEnricher`. Only relevant once
      the on-the-fly mode is wired (the format path discards stderr).

- [ ] Remote PHP interpreter support (`<depends optional="true">` on
      the `:php` interpreter source type). Zero Pint-side code expected.

## Acceptance bullet shared with PHPStan port plan §4.4

The SDK already lets `inspectionShortNames` hold arbitrary strings
(see `QualityToolBuilder.inspectionShortNames`); Pint's
`Laravel_Pint_validation_tool` is the canonical regression test for
that bullet — covered by `LaravelPintToolTest`.
