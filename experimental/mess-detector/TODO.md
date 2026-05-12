# PHP Mess Detector port — TODO

Out-of-scope items deferred from the initial implementation. Each
links to the relevant section of
`docs/mess-detector/mess-detector-port-plan.md` and (where
applicable) the SDK gap that gates the work.

## SDK gaps surfaced by phpmd

- **G27** — `ListSpec<CompoundSpec>` (port plan §4.2). The legacy
  custom-rulesets surface is `List<RulesetDescriptor>` where each
  entry has `(name, originalPath)`. Until the compound-list spec
  kind lands, [`PhpMessDetectorOptionsSchema.customRulesetFiles`]
  stands in as a CSV `StringSpec` of absolute paths. The proper
  `ChoiceListSpec` modeling for the closed-set built-in toggles was
  rejected in favour of the existing six `BoolSpec`s + the CSV
  string — already enough for parity.

- **G28** — `compositeKvPathArg` (port plan §4.3). Phpmd's
  rulesets CSV mixes closed-set tokens (`codesize`, `design`, …)
  with absolute paths to custom XML files. `PathAwareArgRewriter`
  (phase 05) cannot today rewrite only the path tokens within a
  single composite argument: either it rewrites the entire string
  (wrong — `codesize` is not a path) or it skips it (wrong —
  `/abs/team.xml` does need mapping). `PhpMessDetectorBuildArgs`
  currently emits the CSV as a single non-path `plainArg`; remote
  interpreter support will need `compositeKvPathArg(parts =
  [Plain("codesize"), Path("/abs/team.xml")])` once gap G28 is
  merged.

- **G29** — `ConfigSource.staleness(...)` (port plan §4.5). The
  legacy plugin wires an explicit `PhpInterpretersStateListener`
  (`MessDetectorInterpreterStateListener`) so remote profiles
  invalidate their resolved `phpmd` path on PHP-interpreter add /
  remove / edit. The SDK has no per-source-instance staleness
  channel today. Implementing this listener is out of scope for
  this module; it lands when the SDK's `ConfigSource.staleness`
  flow is wired and `PhpInterpreterBinarySource` in `:php`
  subscribes to `PhpInterpretersStateListener.TOPIC`.

## Deferred deliverables (this module)

- **`PhpmdXmlReader`** in `:php` (port plan §4.1) — phase 06
  `ResultReader` work. Phpmd's XML schema is
  `<pmd>/<file>/<violation>` with `beginline` / `priority`
  attributes; **NOT** checkstyle. `PhpMessDetectorTool.resultReaderId`
  references the id `"phpmd-xml"` so the wiring becomes a no-op
  once the reader ships in `:php`.

- **`PhpMessDetectorComposerOnDetectedHook`** — composer
  `scripts.phpmd` parsing that flips built-in toggles and adds
  custom-ruleset rows. The descriptor in this module only handles
  the binary discovery + config-file fallback. The on-detected
  hook can be ported once the corresponding SDK hook EP lands
  (PHPStan gap 4.5).

- **`MessDetectorRulesetAnalyzer`** — SAX parser reading the
  `<ruleset name="…">` attribute from a phpmd XML file. Used
  today by the legacy Add-button flow and the composer on-detected
  hook. Lands together with the composer hook above.

- **`PhpMessDetectorMigration`** — legacy XML migration
  (`MessDetectorSettingsTransferStartupActivity`). Carries the six
  legacy boolean fields + the `List<RulesetDescriptor>` into the
  new options bag. Gates on the generic `Migrator` infrastructure
  designed for Mago (phase 10) and exercised by PHPStan.

## Out-of-scope deliberately (this plugin)

- **`MessDetectorInterpreterStateListener`** — handled by gap G29
  once it lands; no plugin-local listener should ship.

- **`cleancode` migration special-casing** — the legacy plugin
  never persisted a `cleancode` field, so migrating "absent → false"
  needs no special handling. The schema's default `false` covers it.

- **Priority-to-severity mapping** — the legacy plugin hard-codes
  ERROR for every phpmd violation; the future `PhpmdXmlReader` is
  scoped to do the priority 1–2 → error / 3 → warning / 4–5 →
  weak_warning translation (port plan §8 risk-list item).
