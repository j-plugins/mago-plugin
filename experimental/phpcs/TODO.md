# PhpCS plugin — deferred work

Items intentionally left out of this scope. Each links back to the
SDK-side gap (Gxx) noted in `docs/phpcs/phpcs-port-plan.md` and the
cross-tool gap synthesis.

## G10 — Secondary binary on a `ConfigSource` (the `phpcbf` path)

`phpcbf` is a separate executable from `phpcs`. Today we declare the
`fix` mode on `PhpCSTool` and store the `phpcbf` path as an
`OptionSpec` (`PhpCSOptionsSchema.phpcbfPath`). The right home is a
secondary `BinaryDescriptor` on the same `ConfigSource`, selected via
`ToolMode.binaryRole`, so that:

- the runner (phase 05) can swap binaries when dispatching `fix`;
- `AutoToolSettingsPanel` (phase 07) renders one validate-button + path
  field per descriptor without any per-tool Swing;
- the remote PHP-interpreter source wizard iterates descriptors and
  maps both paths through the same path-mapper (gap G15).

Once those land, `phpcbfPath` is dropped from `OptionsBag` and the
test in `PhpCSOptionsSchemaTest` for it goes away with it.

## G13 — `DynamicChoiceSpec` for the coding-standard combobox

`codingStandard` is a plain `StringSpec` with `default = "PSR12"`. The
legacy plugin populated the combobox by running `phpcs -i` against the
selected profile and parsing the English-sentence response. Until
`DynamicChoiceSpec` exists (phase 04 + 07), we cannot:

- enumerate the seven built-in standards as a closed set;
- discover the Composer-installed standards (`Drupal`, `WordPress`,
  `Yii2`, …) at edit time;
- gate the `customRuleset` path field on `codingStandard == "Custom"`
  (needs `enabledWhen` — sub-gap of G13).

`PhpCSComposerToolDescriptor.scriptArgs` is empty for the same reason
— adopting `--standard=X` from `scripts.phpcs` is only useful when the
combobox can offer `X` as a valid choice.

## G12 — Format-mode auto-wired actions

A `fix` mode (or any `executionStyle = "format"` mode with
`supportsFix = true`) should auto-register both:

- a "Reformat with PHP_CodeSniffer" `<action>` under the editor /
  Code menu;
- an Alt-Enter intention attached to each `fixable=1` message.

Today this is done by hand in the legacy `PhpCSBeautifierReformatFile`
+ `PhpCSBeautifierReformatFileAction`. The SDK is expected to generate
both from the single `ToolMode` declaration; until then the `fix` mode
is declared but cannot be invoked through any UI affordance.

## G11 — Per-message `ruleId` / `fixable` tag extraction in the reader

The bundled `CheckstyleXmlReader` (phase 06) must extract:

- `source="Standard.Category.Rule"` → `ToolMessage.ruleId`;
- `fixable="1"` → `ToolMessage.tags = setOf("fixable")`.

This is gated on phase 06 `ResultReader` work and the
`MessageEnricher` SAM. The phpcs port ships nothing on this front in
this scope: the `showSniffNames` option is wired into the option
schema but the actual prepend-rule-id behaviour lives in a phase-06
enricher that does not exist yet.

## Phase 06 — `PhpCSXmlMessageProcessor` port

The legacy SAX parser does several phpcs-specific things on top of the
standard checkstyle reader (severity coalescing, sniff-name prepend,
`fixable=1` → quick-fix). Porting it is held until phase 06's
`ResultReader` + `MessageEnricher` contracts solidify.

## PhpCSMigration

Migrating the eight legacy fields off
`PhpCSValidationInspection` / `PhpCSOptionsConfiguration` into the
new `OptionsBag` is a phase 10c-style job. The shape mirrors the
PHPStan migration but with eight fields instead of five
(`IGNORE_WARNINGS`, `CODING_STANDARD`, `CUSTOM_RULESET_PATH`,
`WARNING_HIGHLIGHT_LEVEL_NAME`, `SHOW_SNIFF_NAMES`,
`USE_INSTALLED_PATHS`, `INSTALLED_PATHS`, `EXTENSIONS`). Held until
the SDK ships its unified migration helper.

## G15 — Two-binary wizard rows in the remote-interpreter source

Generic consequence of G10: `PhpInterpreterBinarySourceType` must
loop over `BinaryDescriptor`s when rendering the wizard. Phpcs is the
first tool to exercise it, php-cs-fixer / laravel-pint will follow.

---

Tracked references:

- `docs/phpcs/phpcs-port-plan.md` — the source spec (§4.1 G10, §4.3
  G12, §4.4 G13, §4.10 G15, §4.2 G11).
- Legacy code: `/tmp/decomp/com/jetbrains/php/tools/quality/phpcs/`.
- SDK foundation: `quality-tools-sdk/`.
