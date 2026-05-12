# Creating a Quality Tool Plugin

A guide for plugin authors who want to integrate a CLI-based linter,
formatter, or static analyzer into IntelliJ via the
`dev.jplugins.quality-tools` SDK.

Sections:

1. [Is your tool a "quality tool"?](#1-is-your-tool-a-quality-tool) — decision matrix.
2. [Does it depend on PHP?](#2-does-it-depend-on-php) — module choice.
3. [Minimal integration: hello-world](#3-minimal-integration-hello-world) — 50 LOC end-to-end.
4. [Realistic integration](#4-realistic-integration) — sources, modes, fixes, options.
5. [What goes in :core vs :ui vs :php](#5-what-goes-in-core-vs-ui-vs-php) — module diagram.
6. [Anti-patterns](#6-anti-patterns) — what to never do.
7. [Marketplace checklist](#7-marketplace-checklist).

---

## 1. Is your tool a "quality tool"?

The SDK fits tools that **observe code from the outside and report
back structured findings**. Use the matrix below.

### 1.1. Hard YES — use this SDK

A tool fits if **all** apply:

- **It is invoked as a separate process** (CLI, PHAR, binary, docker
  exec, …). The IDE does not link the analyzer into its own JVM.
- **It exits when done** (each run is bounded). No persistent daemon
  the IDE has to keep alive across runs.
- **Its output is parseable** into discrete findings (line + column,
  message, optional rule id, optional fix). JSON, SARIF, checkstyle
  XML, or any custom line-oriented format works.
- **The unit of work is a file, a set of files, or a project**, and
  the user wants results in the editor (squiggles, gutter actions,
  problem-tool-window) or on save.
- **The tool runs in seconds**, not minutes. Realistic on-the-fly
  budget: ≤ 5s per file. Slower tools register as `executionStyle =
  "on_save"` or `"manual"` instead.

Examples that fit:

| Tool | Languages | Why it fits |
| --- | --- | --- |
| Mago | PHP | CLI, JSON output, sub-second per file |
| PHPStan | PHP | CLI, checkstyle/json, project-level analysis |
| Psalm | PHP | CLI, json |
| PHP_CodeSniffer | PHP | CLI, json/checkstyle, has separate phpcbf fixer |
| PHP-CS-Fixer | PHP | CLI, json, in-place rewriter |
| Laravel Pint | PHP | wraps PHP-CS-Fixer |
| Pest | PHP | CLI, junit-xml — fits if you map test failures to messages; otherwise use the platform's test runner |
| Rector | PHP | CLI, dry-run JSON, fix application |
| eslint | JS/TS | CLI, json |
| oxlint | JS/TS | CLI, json |
| biome | JS/TS | CLI, json, formatter mode |
| ruff | Python | CLI, json |
| mypy | Python | CLI, line output |
| ktlint | Kotlin | CLI, json/checkstyle |
| golangci-lint | Go | CLI, json |
| clippy | Rust | CLI, cargo-json |
| shellcheck | sh | CLI, json |
| yamllint | YAML | CLI, parsable |
| stylelint | CSS | CLI, json |

### 1.2. Hard NO — wrong tool for the job

Don't use this SDK if **any** of these is true. Use the platform
mechanism in the right column instead.

| Your tool does this | Use instead |
| --- | --- |
| Maintains a persistent process with a bi-directional protocol over stdio (LSP, DAP, RIP) | `com.intellij.platform.lsp` or LSP4IJ |
| Needs deep PSI/AST integration (refactoring across a symbol graph, type inference) | Custom `LocalInspection` / `InspectionExtension` |
| Runs unit tests and reports pass/fail per test method | `RunConfigurationType` + `SMTestRunnerConsoleView` |
| Adds UI affordances only (gutter icons, status-bar, color-scheme tweaks) | Direct platform EPs (`gutterIconProvider`, `statusBarWidget`, `colorScheme`) |
| Is itself an IntelliJ-platform component (Java compiler, Kotlin compiler) | Use the platform `Compiler` / `BuildSystem` EPs |
| Requires interactive prompts mid-run (TTY-attached questions) | Not supported. Move questions to a wizard or settings panel. |
| Streams findings continuously while the user types (every keystroke) | Use `Annotator` or `InspectionExtension` directly; the SDK is debounce-friendly but not 60 Hz |
| Watches the filesystem itself to decide when to run | `VirtualFileListener` / `BulkFileListener`; you can still emit results through this SDK |
| Has a GUI of its own (separate window) | Bundle it as a regular IntelliJ tool window |
| Performs writes that depend on user confirmation per-line (interactive `git add -p`-style) | Not supported. Express as `AggregateFix` or pre-confirm via wizard. |

### 1.3. Soft YES — fits with caveats

These cases fit but you'll have to make small concessions; flag them
in your design so reviewers know.

- **Multi-file refactor tools** (Rector, codemod): use
  `ExternalFileEditFix` for cross-file edits + `AggregateFix` for
  the "apply all" UX. Per-file granularity is required.
- **Long-running project-wide analyzers** (PHPStan level 9 on a
  large monorepo): declare `executionStyle = "batch"` for that mode
  and run only on `Code → Inspect Code`. Do NOT abuse `on_the_fly`
  with a 5-minute budget.
- **Formatters that rewrite files in place** (`mago fmt`, `phpcbf`):
  declare the format mode with `formattingOutputMode = "in_place"`
  so `AsyncFormattingServiceAdapter` re-reads the file after the
  run.
- **Tools that read stdin** but the binary is behind Docker/SSH:
  set `mode.supportsStdin = true`; the SDK's `IntellijProcessSpawner`
  has a shell-pipeline fallback (`printf … | base64 -d | tool`) for
  remotes that drop stdin.

### 1.4. Quick decision checklist

Answer yes/no:

1. Does your tool produce a finite list of findings per run?
2. Each finding has a location (file + line, ideally column)?
3. Total runtime fits in a few seconds for typical input?
4. Output is text the plugin can parse without a custom protocol?
5. The user wants results to appear in the editor or problems tool
   window?

5 × yes → use this SDK.

---

## 2. Does it depend on PHP?

**Short answer: no, the SDK core is language-agnostic.** PHP-specific
features (Composer auto-detection, PHP interpreter as a source type,
XDebug-off env mutator) live in a separate module `:php` that you
depend on only if you want them.

### 2.1. Module layout

```
quality-tools-sdk/
├── core/              ← language-agnostic, no IntelliJ, no PHP
├── ui/                ← IntelliJ-platform glue, no PHP
├── php/               ← PHP-specific extras (optional)
├── testing/           ← pure-Kotlin test fixtures, no IntelliJ
└── testing-junit5/    ← JUnit 5 / Kotest extensions
```

What you need depends on what language you target:

| You're building for | Depend on |
| --- | --- |
| **Non-PHP language** (Python, JS, Go, Rust, …) | `:core`, `:ui`, `:testing` |
| **PHP** (need Composer auto-detect / php interpreter sources / XDebug-off) | `:core`, `:php`, `:ui`, `:testing` |
| **Pure library** (you'll wire UI yourself, e.g. in a CLI tool) | `:core` only |

### 2.2. Decoupling guarantees (enforced in CI)

`:core`'s build verifies it has **zero** transitive dependencies on:

- `com.jetbrains.intellij.*` (the platform)
- `com.jetbrains.php` (the PHP plugin)
- `org.jdom.*` (used in `:ui` for `<XmlSerializedSourceElement>`)
- `com.intellij.uiDesigner.*` (Swing / Kotlin UI DSL)
- AWT / Swing

This is asserted in phase 00's acceptance:

```
./gradlew :quality-tools-sdk:core:dependencies | grep -E 'intellij|jdom|swing|php'
# must produce empty output
```

So a non-PHP IDE (e.g. CLion) that includes neither the PHP plugin
nor PhpStorm-specific bits can host plugins built on `:core` + `:ui`
without issue.

### 2.3. What you actually get from `:php`

| `:php` provides | Why you'd want it |
| --- | --- |
| `ComposerBinarySourceType` | Auto-detect `vendor/bin/<tool>` from `composer.json` |
| `PhpInterpreterBinarySourceType` | Source = "via PHP interpreter" — remote SSH/Docker PHP interpreters expose the tool |
| `IntellijProcessSpawner` | Higher-priority spawner that bridges remote PHP interpreters and the base64-stdin Docker workaround |
| `XdebugOffMutator` | Disables XDebug to make startup faster |
| `PhpFileLanguageFilter` | Filters `PsiFile` to `PhpFile` only in the annotator |

If you don't need any of those for your language, **skip `:php`
entirely**. The Docker source type, mise/asdf shims, DDEV, k8s — all
of those live in their own separate plugins and depend only on
`:core`.

### 2.4. Worked example: oxlint plugin (zero PHP)

```kotlin
// build.gradle.kts
dependencies {
    intellijPlatform {
        plugin("dev.jplugins.quality-tools:1.0.0")        // ships :core + :ui + :testing
        plugin("com.intellij.plugins.javascript:251.x")   // for JS language
    }
}
```

```xml
<!-- plugin.xml -->
<idea-plugin>
    <id>com.example.oxlint</id>
    <depends>dev.jplugins.quality-tools</depends>
    <depends>com.intellij.modules.lang</depends>
    <extensions defaultExtensionNs="dev.jplugins.qualityTools">
        <tool implementation="com.example.oxlint.OxlintTool"/>
        <resultReader implementation="com.example.oxlint.OxlintJsonReader"/>
    </extensions>
</idea-plugin>
```

— no `<depends>com.jetbrains.php</depends>`, no PHP-related imports.

---

## 3. Minimal integration: hello-world

The smallest possible tool integration. We'll integrate `shellcheck`
(POSIX shell linter) since it's tiny and language-agnostic.

### 3.1. `plugin.xml`

```xml
<idea-plugin>
    <id>com.example.shellcheck</id>
    <name>ShellCheck</name>
    <depends>dev.jplugins.quality-tools</depends>
    <depends>com.intellij.modules.lang</depends>

    <extensions defaultExtensionNs="dev.jplugins.qualityTools">
        <tool implementation="com.example.shellcheck.ShellcheckTool"/>
        <resultReader implementation="com.example.shellcheck.ShellcheckJsonReader"/>
    </extensions>
</idea-plugin>
```

### 3.2. The tool — one file, ~30 LOC

```kotlin
package com.example.shellcheck

import dev.jplugins.qualitytools.core.*

class ShellcheckTool : QualityTool by qualityTool("shellcheck") {
    displayName = "ShellCheck"
    languages("Shell Script", "Bash")
    capabilities("lint")
    acceptedSourceTypes("*")
    resultReaderId = "shellcheck-json"
    mode("check") {
        verb = ""
        defaultArgs = listOf(plainArg("--format=json"))
        outputFormat = "shellcheck-json"
        executionStyle = ExecutionStyles.ON_THE_FLY
        supportsStdin = true
        pathArgKeys = setOf("--shell")  // any --flag=path keys
    }
    optionsSchema = MagoEmptyOptionsSchema("shellcheck")
    buildArgs { ctx, mode, target ->
        buildList {
            addAll(mode.defaultArgs)
            add(target.toCliArg(ctx.scope))
        }
    }
}
```

### 3.3. The reader — one file, ~25 LOC

```kotlin
package com.example.shellcheck

import dev.jplugins.qualitytools.core.*
import dev.jplugins.qualitytools.core.reader.*
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

class ShellcheckJsonReader : ResultReader {
    override val id = "shellcheck-json"

    override fun read(run: ToolRun, ctx: ToolRunContext) = flow {
        if (run.stdout.isBlank()) return@flow
        val items = Json.parseToJsonElement(run.stdout).jsonArray
        for (el in items) {
            ctx.cancellation.throwIfCanceled()
            val obj = el.jsonObject
            emit(toolMessage {
                severity = when (obj["level"]?.jsonPrimitive?.content) {
                    "error" -> SeverityLevels.ERROR
                    "warning" -> SeverityLevels.WARNING
                    else -> SeverityLevels.WEAK_WARNING
                }
                category = "shellcheck"
                ruleId = "SC${obj["code"]?.jsonPrimitive?.content}"
                title = obj["message"]!!.jsonPrimitive.content
                range = sourceRange(
                    filePath = obj["file"]!!.jsonPrimitive.content,
                    startLine = obj["line"]!!.jsonPrimitive.int,
                    startColumn = obj["column"]!!.jsonPrimitive.int,
                )
                documentationUrl = "https://www.shellcheck.net/wiki/SC${obj["code"]?.jsonPrimitive?.content}"
            })
        }
    }
}
```

### 3.4. That's it

- Tool appears in `Settings | Tools | Quality Tools | ShellCheck`.
- User adds a local binary via the source wizard.
- Open a `.sh` file — squiggles appear.
- Cancellation, timeouts, "tool not found" balloons, settings
  storage, EP discovery, hot-reload — all handled by the SDK.

Total LOC for the plugin: ~70 (including `plugin.xml`).

### 3.5. What you did NOT have to write

- No `ExternalAnnotator` subclass.
- No process spawning / waiting / cancellation polling.
- No timeout watchdog.
- No `PersistentStateComponent` for settings.
- No Settings UI panel.
- No `LocalInspectionTool` boilerplate.
- No `BalloonNotification` for missing-binary.

---

## 4. Realistic integration

A "real" tool integration adds: multiple modes, custom source type
(for auto-detect), structured options, quick fixes. We'll sketch
**Pest** (PHP test runner that fits the "soft yes" — using messages
to show test failures).

### 4.1. The tool

```kotlin
class PestTool : QualityTool by qualityTool("pest") {
    displayName = "Pest"
    languages("PHP")
    capabilities("test")
    acceptedSourceTypes("local", "composer.bin", "php.interpreter")
    resultReaderId = "pest-json"

    mode("test") {
        verb = ""
        defaultArgs = listOf(plainArg("--log-junit=-"))
        outputFormat = "pest-junit-xml"
        executionStyle = ExecutionStyles.MANUAL    // not on-the-fly
        supportsStdin = false
    }

    optionsSchema = PestOptionsSchema()
    buildArgs(::buildPestArgs)
}

private fun buildPestArgs(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget) =
    buildList {
        addAll(mode.defaultArgs)
        ctx.options.string("configurationFile")?.let { add(kvPathArg("--configuration", it)) }
        ctx.options.bool("parallel").takeIf { it }?.let { add(plainArg("--parallel")) }
        add(pathArg(target.toCliArg(ctx.scope).raw))
        addAll(ParametersList.parse(ctx.profile.modes["test"]?.additionalArgs.orEmpty())
            .map(::plainArg))
    }
```

### 4.2. Options schema

```kotlin
class PestOptionsSchema : OptionsSchema {
    override val toolId = "pest"
    override val specs = listOf(
        path("configurationFile", fileFilter = PathFilter.ext("xml", "yaml")),
        bool("parallel", default = false),
        int("coverageMinimum", default = 0, range = 0..100),
    )
    override val modeSchemas = mapOf(
        "test" to modeSchema { enabled(true); additionalArgs() },
    )
}
```

### 4.3. Custom source type — Composer auto-detect

```kotlin
class ComposerPestSourceType : ConfigSourceType {
    override val typeId = "pest.composer"
    override val displayName = "From composer (vendor/bin/pest)"
    override val requiredPluginIds = setOf("com.jetbrains.php")

    override fun isAvailable(ctx: AvailabilityContext) = ctx.hasPlugin("com.jetbrains.php")

    override fun createWizard(ctx: WizardContext) = ComposerPestWizard(ctx)

    override fun watch(ctx: WatchContext, onDetected: (ConfigSource) -> Unit): AutoCloseable? =
        ctx.composerWatcher.observeBinary("pestphp/pest", "pest") { binPath ->
            onDetected(ComposerPestSource(binPath))
        }

    override fun deserialize(element: SerializedSourceElement) =
        SerializedSourceCodec.decode(element, ComposerPestSource::class)
    override fun serialize(source: ConfigSource) =
        SerializedSourceCodec.encode(source as ComposerPestSource)
}

class ComposerPestSource(
    @SerializedField var binPath: String = "",
) : ConfigSource {
    override val typeId = "pest.composer"
    override val instanceId = "pest-composer-default"
    override val displayName = "vendor/bin/pest"
    override suspend fun resolve(ctx: ResolveContext): ResolvedBinary? =
        if (!exists(binPath)) null
        else ResolvedBinary(command = listOf(binPath), pathMapper = PathMapper.Identity)
}
```

### 4.4. Quick-fix from the tool

Most tools don't emit fixes. Some (Rector, biome, eslint) do. If
yours does, your reader emits `ToolMessage.fixes = listOf(...)`:

```kotlin
ToolMessage.fixes = listOf(
    replaceFix(range = range, newText = "use strict types", safety = "safe"),
    ignoreFix(scopeType = "line", ruleId = "no-strict-types"),
)
```

`ReplaceFix` automatically becomes a `LocalQuickFix` in the IDE —
zero extra registration.

### 4.5. Custom UI renderer (optional)

If you want a non-standard widget (autocomplete combo for rule
names, etc.):

```kotlin
class PestRulesRenderer : OptionRenderer {
    override fun supports(spec: OptionSpec<*>) = spec.key == "ignoredTests"
    override fun render(spec, bag, row) = row.cell(buildPestTestPicker(bag))
}
```

Registered via EP `dev.jplugins.qualityTools.optionRenderer`. The
default auto-rendering covers the common case — write a custom
renderer only if you need it.

### 4.6. Final plugin.xml

```xml
<idea-plugin>
    <id>com.example.pest</id>
    <name>Pest</name>
    <depends>dev.jplugins.quality-tools</depends>
    <depends>com.jetbrains.php</depends>

    <extensions defaultExtensionNs="dev.jplugins.qualityTools">
        <tool implementation="com.example.pest.PestTool"/>
        <resultReader implementation="com.example.pest.PestJunitXmlReader"/>
        <optionsSchema implementation="com.example.pest.PestOptionsSchema"/>
        <configSourceType implementation="com.example.pest.ComposerPestSourceType"/>
        <optionRenderer implementation="com.example.pest.PestRulesRenderer"/>
    </extensions>
</idea-plugin>
```

Total LOC: ~250 (including tests).

---

## 5. What goes in `:core` vs `:ui` vs `:php`

For your plugin's code (NOT the SDK itself), use this rule of thumb:

| Code you write | Module to depend on | Why |
| --- | --- | --- |
| `QualityTool`, `ToolMode`, `OptionsSchema` | `:core` | Pure data + functions |
| `ResultReader`, `MessageEnricher`, `EnvMutator` | `:core` | Logic, no UI |
| `ConfigSource`, `ConfigSourceType` (most cases) | `:core` | Plain POJO + serialization |
| `ConfigSourceWizard` impl (custom dialog) | `:ui` | Needs Swing |
| Custom `OptionRenderer` (custom widget) | `:ui` | Needs Kotlin UI DSL |
| `ToolFixHandler` for custom fix kinds | `:ui` | Needs `LocalQuickFix` |
| `IgnoreCommentRenderer` for PSI-aware suppress comments | `:ui` or downstream | Needs PSI |
| `ComposerXxxSourceType` | `:php` | Reads `composer.json` |
| `PhpInterpreterXxxSourceType` | `:php` | Touches PHP interpreter |
| Anything else PHP-specific | `:php` | Avoid leaking it elsewhere |
| Test fakes | `:testing` | Pure Kotlin |

### 5.1. Why this split matters for you

If your plugin's `:core`-equivalent code touches no PHP and no
IntelliJ, then:

- You can unit-test it in a plain Kotlin/JVM test (3-second runs).
- It compiles standalone for documentation generation, scripts, CI
  inspections.
- The same `ResultReader` is reusable in a CLI lint-runner you may
  build later, with zero IntelliJ on the classpath.

### 5.2. The "I don't know which language to declare" case

If your tool supports multiple languages (e.g. biome handles JS, TS,
JSX, CSS, JSON):

```kotlin
override val supportedLanguageIds = setOf(
    "JavaScript", "TypeScript", "JSX Harmony", "TypeScript JSX",
    "CSS", "JSON",
)
```

— string ids, not platform `Language` objects. The annotator filters
by `psiFile.language.id`.

---

## 6. Anti-patterns

Reject your own PR if you find one of these.

| ❌ | ✅ |
| --- | --- |
| `LOG.warn("...")` via `Logger.getInstance(...)` | `ctx.logger.log("warn", "...")` (`QtLogger`) |
| Throw from `ConfigSource.resolve(ctx)` | Return `null`; the runner emits `internal_error` |
| Throw from `ResultReader.read(run, ctx)` on bad input | Emit one `internal_error` message and complete the flow |
| Read `PsiFile.text` inside `buildArgs` | Read it once in the annotator, pass via `ToolRunContext` |
| Hard-code `PluginId.getId("Docker")` in `:core`-level code | `requiredPluginIds = setOf("Docker")` (string-typed) |
| Implement your own `ToolRunner` to "tweak one thing" | Implement an `EnvMutator`, `PostFixHook`, or `MessageEnricher` instead |
| Store the actual `PathMapper` in your `ConfigSource` XML | Store the path-mapping rules; build the mapper in `resolve()` |
| Put `timeout` in your `OptionsSchema` | Use `ConfigProfile.timeoutMs` (SDK-managed) |
| Forget `ctx.cancellation.throwIfCanceled()` in your reader loop | Call it once per emitted record (acceptance criterion) |
| Cache `Project` in a singleton on your tool | Pass `ToolRunContext` through; no static state |
| Mark a `mode.executionStyle = "on_the_fly"` for a 1-minute analyzer | Use `"batch"` and run via `Code → Inspect Code` |
| Register a tool directly on `com.jetbrains.php.tools.quality.type` | Register on `dev.jplugins.qualityTools.tool`; the legacy bridge surfaces it |
| Catch `CancellationException` and continue | Always rethrow — cancellation must propagate |
| Show a balloon from your reader | Emit `internal_error` with a `category`; `InternalErrorNotifier` handles balloons |
| Subclass `QualityToolsAnnotator` to add custom paint logic | Use a `MessageEnricher` or contribute an `optionRenderer` |
| Pin your `typeId` to a generic name like `"docker"` | Vendor-prefix: `"acme.docker"`; collisions log error and one wins |

---

## 7. Marketplace checklist

Before submitting your plugin:

- [ ] `QualityTool.id` is unique in the marketplace (`<vendor>.<tool>`).
- [ ] Every `typeId` (source / scope / ignore policy) is vendor-prefixed.
- [ ] `requiredPluginIds` correctly lists optional deps; verified by
      running the plugin with each named plugin disabled.
- [ ] Reader doesn't crash on empty stdout / partial output / non-UTF-8.
- [ ] Reader calls `cancellation.throwIfCanceled()` at least once per
      record (asserted by `RecordedProcessSpawner`-based test).
- [ ] Tool runs cleanly with `mago.use.v2 = true` / equivalent.
- [ ] Settings panel renders without errors when no profile exists
      (cold start).
- [ ] Persistent state round-trips through `SerializedSourceCodec`
      (unit test with `assertGoldenJson`).
- [ ] No `Logger.getInstance(...)` import in your `:core`-level code.
- [ ] No `org.jdom.*` import in your `:core`-level code.
- [ ] No `<depends>com.jetbrains.php</depends>` if your tool isn't
      actually PHP-specific.
- [ ] Documentation links in `ToolMessage.documentationUrl` are
      stable URLs (not user-specific).
- [ ] You handle the "binary unavailable" UX — `InternalErrorActionProvider`
      registered with at least one helpful action ("Install via Composer",
      "Open Settings").
- [ ] Tests run in `:testing` without IntelliJ classpath.
- [ ] CHANGELOG entry for first release.

---

## 8. Where to go from here

- Architecture: [`docs/sdk-research/quality-tools-sdk-design.md`](../sdk-research/quality-tools-sdk-design.md)
- Cookbook recipes: [`docs/sdk-research/quality-tools-sdk-cookbook.md`](../sdk-research/quality-tools-sdk-cookbook.md)
- Implementation phases: [`docs/phases/README.md`](../phases/README.md)
- Reference plugin (Mago): `src/main/kotlin/com/github/xepozz/mago/v2/`
  after phase 10 ships.
- Questions / RFC: open an issue on j-plugins/mago-plugin tagged `sdk`.
