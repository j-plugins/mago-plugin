# Inventory of existing Quality Tools across IntelliJ-platform IDEs

Catalogues every tool I've come across during the SDK research that
looks like a "quality tool" in the SDK's sense (CLI/external process
that emits findings parseable into editor annotations). Used to
estimate the migration pool when the new `:quality-tools-sdk` ships.

Sources: decompilation of `com.jetbrains.php` plugin 251.x,
`phpstan` 251.x, `psalm` 251.x; JetBrains Marketplace search; my own
working knowledge of plugin ecosystems.

---

## 1. On the existing JetBrains SDK (`com.jetbrains.php.tools.quality.type` EP)

These plug into the SDK we're replacing. All are PHP-only by design
of that SDK.

### 1.1. Bundled inside `com.jetbrains.php` (`php.jar`)

| Tool | Package | What it does |
| --- | --- | --- |
| **PHP_CodeSniffer** (`phpcs` + `phpcbf`) | `com.jetbrains.php.tools.quality.phpcs` | Lint + autofix against PSR / Symfony / Drupal / Zend / custom standards. |
| **PHP-CS-Fixer** | `com.jetbrains.php.tools.quality.phpCSFixer` | Code-style fixer with `.php-cs-fixer.php` / `.php-cs-fixer.dist.php` config. |
| **Laravel Pint** | `com.jetbrains.php.tools.quality.laravelPint` | Opinionated wrapper over PHP-CS-Fixer, Laravel defaults. |
| **PHP Mess Detector** (`phpmd`) | `com.jetbrains.php.tools.quality.messDetector` | Static analysis for code "smells" — long methods, unused params, complexity. |

### 1.2. Separate JetBrains plugins (same EP)

| Plugin ID | Tool | Marketplace plugin |
| --- | --- | --- |
| `com.intellij.php.tools.quality.phpstan` | **PHPStan** | Static analyzer, levels 0–10, baselines |
| `com.intellij.php.psalm` | **Psalm** | Static analyzer (Vimeo), supports baselines, taints, dead code |

### 1.3. Third-party plugins (same EP)

| Plugin / Vendor | Tool | Note |
| --- | --- | --- |
| `com.github.xepozz.mago` | **Mago** | Our plugin — PHP toolchain on Rust |
| `ru.taptima.phalyfusion` | **Phalyfusion** | Aggregator over multiple analyzers; presents unified report |
| `de.shyim.ideaphpstantoolbox` | **PHPStan Toolbox** | Adds baseline UI, ignore-management on top of PHPStan (not via QualityToolType, but adjacent) |
| `de.martin3398.ideapsalmbaseline` | **psalm-baseline** | Baseline-warnings overlay for Psalm |

**Inventory on the JetBrains SDK: 6 mainline integrations + at least
3 community plugins. All PHP.**

---

## 2. PHP tools that ignore the JetBrains SDK

Plenty of PHP quality tools live in IntelliJ via their own
`ExternalAnnotator` / `LocalInspection` — i.e., they duplicate the
SDK's machinery rather than reusing it.

| Tool | Community plugin? | Why bypass the SDK |
| --- | --- | --- |
| **Rector** | yes (`com.github.dimanech.rector` etc.) | Wants its own diff/preview UI |
| **Deptrac** | community | Cross-file dependency analysis |
| **GrumpHP** | community | Pre-commit hook orchestrator |
| **Infection** | community | Mutation testing (lots of per-line metadata) |
| **PHPBench** | community | Performance benchmarking — not really a linter |
| **Pest** | community | Test runner — fits "soft yes" for the SDK |
| **Phan** | community (older) | Older analyzer, similar to PHPStan |
| **Castor / Tinker / phpinsights** | community / none | Various |

These plugins re-implement: process spawning, timeout, stdin handling,
result parsing, blacklist, settings UI, persistent state. **Net
duplication: hundreds to thousands of LOC per plugin.**

---

## 3. Quality tools in other languages — no shared SDK

IntelliJ has **no common SDK for external quality tools beyond PHP**.
Each language ecosystem maintains its own `ExternalAnnotator`
infrastructure, reinventing the wheel.

| Language | Tools commonly integrated | Hosting plugin |
| --- | --- | --- |
| **JavaScript / TypeScript** | ESLint, Prettier, JSHint, StandardJS, Biome, oxlint, TSLint (deprecated) | `JavaScriptLanguage` plugin (WebStorm / IDEA Ultimate) |
| **Python** | Pylint, Flake8, mypy, Ruff, Bandit, pydocstyle, pyright | `python` plugin (PyCharm) |
| **Java** | Checkstyle, PMD, SpotBugs / FindBugs, ErrorProne | each as own community plugin |
| **Kotlin** | Detekt, ktlint | community |
| **Go** | golangci-lint, gofmt, golint, staticcheck | `go` plugin (GoLand) |
| **Ruby** | RuboCop, Reek, Brakeman | `ruby` plugin (RubyMine) |
| **Rust** | Clippy, rustfmt | Rust plugin (RustRover) |
| **Shell** | shellcheck, shfmt | shell-script plugin |
| **YAML** | yamllint | YAML plugin |
| **CSS / SCSS** | stylelint | CSS plugin |
| **Markdown** | markdownlint, vale | Markdown plugin |
| **SQL** | sqlfluff, sql-lint | DB plugin |
| **C/C++** | clang-tidy, cppcheck | CLion built-in (different SDK) |
| **Terraform / OpenTofu** | tflint, tfsec, checkov | community plugins |
| **Docker** | hadolint | community |
| **Ansible** | ansible-lint | community |
| **Nix** | statix, deadnix | community |

**Order-of-magnitude estimate: 40-50 quality tools** are integrated
into IntelliJ today, each as a bespoke `ExternalAnnotator`. None of
them share infrastructure with each other or with PHP.

---

## 4. Cross-language tooling above the language-plugin layer

These don't fit the "external CLI" pattern cleanly — they have their
own runtime model.

| Tool | What it does | Why it doesn't use the SDK |
| --- | --- | --- |
| **SonarLint** (SonarSource plugin) | Embeds the SonarSource engine; rules run in JVM | In-process, not external |
| **Qodana** (JetBrains) | Cloud-based code analysis, SARIF results imported back | Imports reports, doesn't run tools |
| **AI Assistant code review** | LLM-based commentary | Different paradigm entirely |
| **Built-in inspections** | `LocalInspectionTool` shipped with each language | Native to the platform |
| **Code With Me linters** | Mirrored from host's local diagnostics | Not a separate tool |

---

## 5. Migration pool estimate

Reasonable first wave to migrate onto our `:quality-tools-sdk`:

**Tier 1 — drop-in fits**, no behavior change for users:

- Mago (we ship it, drives the design)
- Pest (test runner, fits "soft yes")
- Rector (large but well-modeled by AggregateFix + ExternalFileEditFix)
- oxlint / biome / ruff (currently bespoke `ExternalAnnotator`,
  trivial port)
- shellcheck (35-LOC port per the cookbook hello-world)

**Tier 2 — strategic targets** that would benefit the most:

- PHP-CS-Fixer / Laravel Pint / Mess Detector (the four bundled in
  the PHP plugin) — would require a JetBrains PR
- PHPStan / Psalm — JetBrains plugins, would also require a JB PR
- ESLint / Prettier — bundled in WebStorm, JetBrains-owned

**Tier 3 — unlikely to migrate** (bespoke for good reasons):

- SonarLint (in-process engine)
- Qodana (different model)
- C/C++ clang-tidy (CLion has its own infra)
- Anything LSP-shaped

---

## 6. What this inventory tells us about the SDK design

Three observations from the catalog:

1. **The PHP-only SDK has 6+ adopters in a decade**, none outside
   PHP. The artificial language boundary directly limited
   adoption — consistent with the design problems we documented in
   `quality-tools-sdk-analysis.md`.
2. **Each non-PHP language has 4–8 tools that all reinvent the same
   ExternalAnnotator scaffolding.** A language-agnostic SDK should
   immediately attract these.
3. **No tool is "obviously not migratable"** in the YES tier from
   the [cookbook decision matrix](../guides/creating-a-quality-tool-plugin.md#1-is-your-tool-a-quality-tool).
   The hard-NO list (LSP, deep-PSI refactor, test runners with
   pass/fail) covers ~5 tools out of 50+, mostly because they use a
   fundamentally different model (in-process, daemon, persistent
   protocol), not because the SDK is missing features.

Reference plugin for migration patterns: Mago, once phase 10 ships
(`src/main/kotlin/com/github/xepozz/mago/v2/`).
