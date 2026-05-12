# Quality Tools SDK — Provider + Scope Design (Mago-style)

> Продолжение `quality-tools-sdk-analysis.md`. Здесь — конкретный дизайн
> нового SDK, который наследует **стиль конфигурирования Mago** (workspace
> mappings + multiple modes + provider EPs + stdin-first), а не копирует
> JetBrains-овский "один interpreter → один tool path".

---

## 1. Каталог тулов, которые сейчас живут на JetBrains SDK

Чтобы не быть голословным — вот полный список тулов, использующих
`com.jetbrains.php.tools.quality.*`:

### 1.1. Bundled в PHP plugin (`php.jar`, package `com.jetbrains.php.tools.quality`)

| Package | Display name | Назначение | EP / inspection |
| --- | --- | --- | --- |
| `phpcs/` | `PHP_CodeSniffer` | Lint + autofix (`phpcbf`) | `PhpCSValidationInspection`, `PhpCSBeautifierReformatFile` |
| `phpCSFixer/` | `PHP CS Fixer` | Code style fixer | `PhpCSFixerValidationInspection`, `PhpCSFixerReformatFile`, `PhpCSFixerCheckinHandler` |
| `messDetector/` | `Mess Detector` | Static analysis | `MessDetectorValidationInspection` |
| `laravelPint/` | `Laravel Pint` | CS Fixer обёртка | `LaravelPintValidationInspection`, `LaravelPintReformatFile` |

### 1.2. Отдельные JetBrains-плагины

| Plugin ID | Display | Главные классы |
| --- | --- | --- |
| `com.intellij.php.tools.quality.phpstan` | `PHPStan` | `PhpStanQualityToolType`, `PhpStanGlobalInspection`, `PhpStanOptionsConfiguration` |
| `com.intellij.php.psalm` | `Psalm` | `PsalmQualityToolType`, `PsalmGlobalInspection`, `PsalmOptionsConfiguration` |

### 1.3. Сторонние, использующие тот же EP `com.jetbrains.php.tools.quality.type`

| Plugin | Tool |
| --- | --- |
| `com.github.xepozz.mago` | Mago (наш) |
| `ru.taptima.phalyfusion` (sub-tool) | Phalyfusion — multi-analyzer aggregator |
| `de.shyim.ideaphpstantoolbox` | PHPStan toolbox (не QualityToolType, а built around) |
| `de.martin3398.ideapsalmbaseline` | Psalm baseline (не QualityToolType) |

**Итого реально на SDK** — 6 встроенных интеграций (4 bundled + 2 plugin)
плюс наш Mago. Все они страдают от одних и тех же ограничений (см.
`quality-tools-sdk-analysis.md` §4).

### 1.4. Реализационные особенности (что переиспользовать, что выкинуть)

| Что | Где сделано хорошо | Где плохо |
| --- | --- | --- |
| Composer auto-detect | `QualityToolsComposerConfig` + `composerConfigClient` EP | Слишком много PHP-specific bind'ов |
| Remote interpreter | `QualityToolProcessCreator.createProcessHandler` для remote SDK | Hardcoded в `PhpRemoteInterpreterManager` — нельзя расширить |
| Per-tool options | Раздельный `*OptionsConfiguration` сервис | Singleton на проект, без scope |
| Reformat | `QualityToolReformatFile` + `QualityToolExternalFormatter` | Дубль логики из `QualityToolAnnotator` |
| XML parsing | `QualityToolXmlMessageProcessor` (SAX) | Нет JSON/SARIF |
| Blacklist | `QualityToolBlackList` (filePaths) | Только абсолютные пути, без glob |

---

## 2. Стиль конфигурации Mago — что мы хотим унаследовать

Извлечено из `MagoProjectConfiguration.kt`, `MagoCliOptions.kt`,
`MagoConfigurable.kt`, `MagoComposerAutoDetectActivity.kt`,
`MagoConfigurationProvider.kt`, `MagoExternalAnnotator.kt`.

### 2.1. Workspace mappings (scope!)

```kotlin
// MagoProjectConfiguration.kt
var workspaceMappings: MutableList<MagoWorkspaceMapping> = mutableListOf()
```

```kotlin
// MagoWorkspaceMapping.kt
class MagoWorkspaceMapping {
    var workspace: String = ""    // directory prefix (e.g.: "frontend/api")
    var configFile: String = ""   // path to mago.toml override
}
```

**Поведение** (`MagoCliOptions.resolveForFile`):

1. `normalizedPath = filePath.normalize()`
2. Для каждого `mapping` где `workspace` — это префикс файла:
   - выбрать с **самым длинным префиксом** (longest-prefix-match).
3. Если найдено → `ResolvedWorkspace(wsFile, mapping.configFile)`.
4. Иначе — fallback на auto-detected (composer.json / project root) +
   глобальный `settings.configurationFile`.

Это **scope** в чистом виде: контекст конфигурации зависит от пути файла.
Это то, что JetBrains-овский SDK не умеет в принципе.

### 2.2. Несколько режимов одного тула (`mode`)

`MagoProjectConfiguration` хранит четыре независимых режима:

```kotlin
var guardEnabled = false
var linterEnabled = false
var formatterEnabled = true     // + enabled (kill-switch)

var analyzeAdditionalParameters = ""
var lintAdditionalParameters = ""
var guardAdditionalParameters = ""
var formatAdditionalParameters = ""
var formatAfterFix = false
```

И четыре параллельных метода в `MagoCliOptions`:
`getAnalyzeOptions`, `getLintOptions`, `getGuardOptions`,
`getFormatOptions`. Каждый режим = отдельная subcommand mago
(`analyze`/`lint`/`guard`/`fmt`) с собственным flag-стеком.

### 2.3. Stdin-first исполнение

```kotlin
// MagoRunner.kt
fun runWithStdin(
    project, exePath, args, stdinContent, timeoutMs, workDir
): ProcessOutput
```

Mago всегда предпочитает `--stdin-input` `cat file | mago analyze`, потому
что:
- baselines резолвятся с **реальным** путём, а не temp-файлом;
- содержимое — точно то, что в редакторе (не файл на диске).

Fallback на temp file — только если runner не поддерживает stdin.
Это полная противоположность подходу `QualityToolAnnotator#runOnTempFiles
= true` по умолчанию.

### 2.4. Provider EP (но multi-provider)

```kotlin
abstract class MagoConfigurationProvider : QualityToolConfigurationProvider<MagoConfiguration>() {
    companion object {
        private val EP_NAME = ExtensionPointName.create<MagoConfigurationProvider>(
            "com.github.xepozz.mago.magoConfigurationProvider"
        )
        fun getInstances(): MagoConfigurationProvider? { /* …single-pick */ }
    }
}
```

Сейчас "single-pick" (как в JB-SDK) — но **сам факт** того, что у Mago
свой EP вместо встроенного `QualityToolConfigurationProvider`, говорит:
авторы хотят разные источники конфигов, и SDK им мешает. У нас уже есть:

- `MagoRemoteConfigurationProvider` — remote interpreter;
- (планируется) auto-detect через composer;
- (планируется) DDEV/Lando/Docker.

Должно быть нативное multi-provider.

### 2.5. Auto-detect через `VfsListener`

```kotlin
// MagoComposerAutoDetectActivity.kt
override suspend fun execute(project: Project) {
    maybeSuggestOrApply(project, basePath)

    project.messageBus.connect().subscribe(VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
            val needle = "/vendor/bin/mago"
            if (events.any { it.path.endsWith(needle) }) {
                maybeSuggestOrApply(project, basePath)
            }
        }
    })
}
```

Auto-detect — это **отдельный provider**, который реагирует на VFS-
события, а не на момент создания нового конфига в Settings UI.

### 2.6. `ParametersList.parse(...)` для свободного ввода аргументов

```kotlin
add(command)
add(toWorkspaceRelativePath(resolved.workspaceDir, filePath))
addAll(ParametersList.parse(settings.analyzeAdditionalParameters))
```

В UI пользователь набирает `--no-progress --memory-limit=2G`, мы парсим
как shell-args. Каждый mode имеет такую строку.

### 2.7. Богатый отдельный UI

`MagoConfigurable.kt` — 295 строк Kotlin UI DSL (`panel { … }`) с:
- groups: Options, WorkspaceMappings, Analyzer, Formatter, Linter, Guard;
- `OnOffButton` для каждого режима;
- `JBTable` с двумя колонками для workspace mappings;
- ссылки на документацию каждого режима;
- `QualityToolConfigurationComboBox` как один маленький виджет в углу.

То есть UI у нас в 10 раз больше, чем то, что SDK даёт через
`QualityToolConfigurableForm`. SDK дал поле "путь" и timeout, всё
остальное — мы сами.

---

## 3. Дизайн нового SDK

Цель: API, в котором всё, что Mago пишет руками, выражается **в одну
строку**, а PHP-плагины используют его не ломая.

### 3.1. Слои

```
┌────────────────────────────────────────────────────────────────┐
│  quality-tools-sdk-core (language-agnostic)                    │
│   ├── tool model        QualityTool, Mode, Capability          │
│   ├── config model      ConfigSource, ConfigProfile, Scope     │
│   ├── runner            ToolRunner, StdinRunner, ProcessRunner │
│   ├── messages          ToolMessage, Severity, ToolFix         │
│   ├── readers           JsonLinesReader, SarifReader, SaxReader│
│   └── ignore            IgnorePolicy (glob, annotation, range) │
├────────────────────────────────────────────────────────────────┤
│  quality-tools-sdk-php                                         │
│   ├── PhpInterpreterSource    (источник = php interpreter)     │
│   ├── ComposerSource          (vendor/bin auto-detect)         │
│   ├── XdebugDisable           (env mutator)                    │
│   └── PhpFileScope            (фильтр PSI/PhpFile)             │
├────────────────────────────────────────────────────────────────┤
│  quality-tools-sdk-ui                                          │
│   ├── ProfileListPanel        (UI DSL для master/details)      │
│   ├── ScopeMappingTable                                        │
│   └── ModeGroupBuilder        (см. §3.7)                       │
├────────────────────────────────────────────────────────────────┤
│  quality-tools-sdk-testing                                     │
│   └── FakeProcessRunner, ToolFixture, recordedRun(...)         │
└────────────────────────────────────────────────────────────────┘
```

### 3.2. Тул

```kotlin
interface QualityTool {
    val id: String                              // "mago", "phpstan", "phpcs"
    val displayName: @Nls String
    val supportedLanguages: Set<Language>
    val modes: List<ToolMode>                   // analyze, lint, format, …
    val ignorePolicy: IgnorePolicy = IgnorePolicy.GlobPaths()
    val reader: ResultReader
    val capabilities: Set<Capability>

    /** Какие источники конфига этот тул умеет принимать. */
    val acceptedSources: Set<KClass<out ConfigSource>> = setOf(LocalBinarySource::class)

    /** Сборка command-line для запроса. Чистая функция от конфига+режима. */
    fun buildArgs(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget): List<String>
}

data class ToolMode(
    val id: String,                             // "analyze", "lint", "format"
    val displayName: @Nls String,
    val verb: String,                           // subcommand: "analyze", "fmt"
    val executionStyle: ExecutionStyle,         // ON_THE_FLY, ON_SAVE, MANUAL
    val outputFormat: OutputFormat,             // JSON, SARIF, CHECKSTYLE, LINE
    val defaultArgs: List<String> = emptyList(),
    val supportsStdin: Boolean = false,
    val supportsFix: Boolean = false,
)

enum class Capability { LINT, ANALYZE, FORMAT, FIX, BASELINE, BATCH }
```

Mago тогда выражается как один объект:

```kotlin
class MagoTool : QualityTool {
    override val id = "mago"
    override val displayName = "Mago"
    override val supportedLanguages = setOf(PhpLanguage.INSTANCE)
    override val modes = listOf(
        ToolMode("analyze", "Analyzer", verb = "analyze",
                 executionStyle = ON_THE_FLY, outputFormat = JSON,
                 defaultArgs = listOf("--reporting-format=json"),
                 supportsStdin = true, supportsFix = true),
        ToolMode("lint",    "Linter",   verb = "lint",
                 executionStyle = ON_THE_FLY, outputFormat = JSON,
                 supportsStdin = true, supportsFix = true),
        ToolMode("format",  "Formatter", verb = "fmt",
                 executionStyle = ON_SAVE, outputFormat = LINE),
        ToolMode("guard",   "Guard",     verb = "guard",
                 executionStyle = ON_SAVE,  outputFormat = JSON,
                 supportsStdin = true),
    )
    override val reader = JsonLinesReader(::parseMagoMessage)
    override val capabilities = setOf(LINT, ANALYZE, FORMAT, FIX)
    override val acceptedSources = setOf(
        LocalBinarySource::class,
        ComposerBinarySource::class,
        PhpInterpreterBinarySource::class,
    )

    override fun buildArgs(ctx, mode, target) = buildList {
        // см. §3.6
    }
}
```

### 3.3. Источники конфигов (config sources)

Это самая важная часть. Вместо `QualityToolConfiguration` (где
"interpreterId + toolPath" — единственные оси), вводим **sum-type**:

```kotlin
sealed interface ConfigSource {
    val id: String                  // stable id for serialization & UI
    val displayName: @Nls String

    /** Resolve into something runnable. Может быть suspend для I/O. */
    suspend fun resolve(ctx: ResolveContext): ResolvedBinary?
}

/** Локальный бинарь / .phar. */
data class LocalBinarySource(
    override val id: String,
    val path: Path,
    val extraEnv: Map<String, String> = emptyMap(),
) : ConfigSource

/** vendor/bin/<tool> из composer.json. */
data class ComposerBinarySource(
    override val id: String,
    val packageName: String,            // "carthage-software/mago"
    val binName: String,                // "mago"
    val composerRoot: Path? = null,     // auto if null
) : ConfigSource

/** Через PHP interpreter (для тех, кого надо запускать как `php script.php`). */
data class PhpInterpreterBinarySource(
    override val id: String,
    val interpreterId: String,
    val scriptPath: String,
) : ConfigSource

/** Docker / DDEV / Lando — внешний CLI запускается в контейнере. */
data class ContainerBinarySource(
    override val id: String,
    val providerId: String,             // "docker", "ddev", "lando"
    val service: String,
    val command: List<String>,
    val workdirMapping: PathMapping? = null,
) : ConfigSource

/** Mise/asdf shim. */
data class ShimBinarySource(
    override val id: String,
    val shim: String,                   // "mise"
    val toolName: String,               // "mago"
    val version: String? = null,
) : ConfigSource
```

**EP**:

```xml
<extensions defaultExtensionNs="dev.jplugins.qualityTools">
    <configSourceProvider implementation="…"/>
</extensions>
```

```kotlin
interface ConfigSourceProvider<S : ConfigSource> {
    val id: String
    val displayName: @Nls String
    val sourceClass: KClass<S>
    val supportedTools: Set<String>                     // tool ids; "*" = all

    /** "Add new" button entry. */
    fun createWizard(project: Project, tool: QualityTool): ConfigSourceWizard<S>?

    /** Watch for filesystem/SDK changes that should re-detect. */
    fun watch(project: Project, callback: (S) -> Unit): Disposable? = null
}
```

Несколько провайдеров **разрешены одновременно**. UI кнопки `+` в списке
показывает popup с источниками — как `New | From Composer | From PHP
Interpreter | From Docker…`.

Auto-detect (типа `vendor/bin/mago`) — это `watch(...)` метод. Сейчас в
Mago это `MagoComposerAutoDetectActivity` — переедет в
`ComposerConfigSourceProvider`.

### 3.4. Профиль конфига (то, что юзер реально настраивает)

```kotlin
data class ConfigProfile(
    val id: String,                                  // uuid
    val displayName: String,                         // "vendor mago"
    val toolId: String,
    val source: ConfigSource,
    val options: OptionsBag,
    val scope: ConfigScope = ConfigScope.EntireProject,
    val modes: Map<String, ModeSettings> = emptyMap(),  // per-mode override
)

data class ModeSettings(
    val enabled: Boolean = true,
    val additionalArgs: String = "",                 // ParametersList format
    val customConfig: Path? = null,                  // mode-specific config file
)
```

`OptionsBag` — schema-driven storage (см. §3.7).

### 3.5. Scope (ровно то, как Mago делает workspace mappings)

```kotlin
sealed interface ConfigScope {
    fun matches(target: ToolTarget): Boolean
    fun specificity(target: ToolTarget): Int          // higher = more specific

    object EntireProject : ConfigScope { ... }

    /** Долготопрефиксный matcher как у Mago. */
    data class WorkspaceRoot(val root: Path) : ConfigScope {
        override fun matches(target) = target.normalized.startsWith("$root/") || target.normalized == root
        override fun specificity(target) = if (matches(target)) root.toString().length else 0
    }

    /** Glob: "src/**", "tests/**". */
    data class Glob(val patterns: List<String>) : ConfigScope

    /** IntelliJ Module. */
    data class IntellijModule(val moduleId: String) : ConfigScope

    /** Custom matcher (например VCS branch == "main"). */
    class Predicate(val match: (ToolTarget) -> Boolean) : ConfigScope
}
```

Resolution policy:

```kotlin
class ProfileSelector(private val all: List<ConfigProfile>) {
    fun selectFor(target: ToolTarget, toolId: String, modeId: String): ConfigProfile? =
        all.asSequence()
            .filter { it.toolId == toolId }
            .filter { it.modes[modeId]?.enabled != false }
            .filter { it.scope.matches(target) }
            .maxByOrNull { it.scope.specificity(target) }
}
```

→ "longest-prefix-match" из `MagoCliOptions.resolveForFile` — частный
случай.

### 3.6. Mode-aware buildArgs

```kotlin
data class ToolRunContext(
    val project: Project,
    val profile: ConfigProfile,
    val mode: ToolMode,
    val target: ToolTarget,
    val scope: ResolvedScope,                  // workspace dir + config file
)

sealed interface ToolTarget {
    val normalized: String                     // forward slashes, absolute

    data class SingleFile(val path: Path) : ToolTarget
    data class Files(val paths: List<Path>) : ToolTarget
    data class Stdin(val originalPath: Path, val content: String) : ToolTarget
    object Project : ToolTarget
}
```

И Mago build:

```kotlin
override fun buildArgs(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget) = buildList {
    addWorkspace(ctx.scope.workspace)
    addConfig(ctx.scope.configFile)
    add(mode.verb)

    when (target) {
        is ToolTarget.Stdin -> {
            add(ctx.scope.relativize(target.originalPath))
            add("--stdin-input")
        }
        is ToolTarget.SingleFile -> add(ctx.scope.relativize(target.path))
        is ToolTarget.Files -> addAll(target.paths.map { ctx.scope.relativize(it) })
        ToolTarget.Project -> Unit
    }

    addAll(mode.defaultArgs)
    addAll(ParametersList.parse(ctx.profile.modes[mode.id]?.additionalArgs ?: ""))
}
```

→ Весь `MagoCliOptions.kt` стянулся в 15 строк, без дублирования
`buildOptionsForSingleFile` / `buildOptionsForStdin`.

### 3.7. Schema-driven options + UI

Опции — не строки внутри `*OptionsConfiguration`, а декларативный
schema:

```kotlin
class MagoOptionsSchema : OptionsSchema {
    override val tool = "mago"

    val debug              = bool("debug", default = false)
    val configurationFile  = path("configurationFile", filter = ext("toml"))
    val formatAfterFix     = bool("formatter.formatAfterFix", default = false)

    val analyze = mode("analyze") {
        enabled(default = true)
        additionalArgs()
    }
    val lint = mode("lint") {
        enabled(default = false)
        additionalArgs()
    }
    val format = mode("format") {
        enabled(default = true)
        additionalArgs()
        bool("formatAfterFix", default = false)
    }
    val guard = mode("guard") {
        enabled(default = false)
        additionalArgs()
    }

    val workspaceMappings = list("workspaceMappings") {
        path("workspace", role = SCOPE_ROOT)
        path("configFile", filter = ext("toml"))
    }
}
```

UI генерируется автоматически (Kotlin UI DSL), но плагин может
переопределить рендеринг каждой ноды через `OptionRenderer`. Workspace
mappings — стандартная нода `list { path; path }`, отрендеренная
`JBTable` декоратором — без 60 строк boilerplate'а как в
`MagoConfigurable.kt`.

`OptionsBag.snapshot()` сериализуется в XML через `@Property name=`-
конверторы.

### 3.8. Runner и stdin-first

```kotlin
interface ToolRunner {
    suspend fun run(ctx: ToolRunContext, args: List<String>): ToolRun
    suspend fun runStdin(ctx: ToolRunContext, args: List<String>, stdin: ByteArray): ToolRun
}

class ProcessToolRunner(
    private val resolver: BinaryResolver,
    private val processPool: ToolProcessPool,
    private val mutators: List<EnvMutator> = emptyList(),   // XdebugDisable etc.
) : ToolRunner { … }

interface EnvMutator { fun mutate(cmd: GeneralCommandLine, ctx: ToolRunContext) }
```

`mode.supportsStdin = true` → annotator выбирает `runStdin`, иначе
делает temp-file + `run` (как сейчас в `MagoExternalAnnotator`
fallback).

Pool — настраиваемый, не глобальная константа 5.

### 3.9. Reader-ы и сообщения

```kotlin
interface ResultReader {
    fun read(stdout: InputStream, stderr: InputStream, ctx: ToolRunContext): Flow<ToolMessage>
}

class JsonLinesReader(val parse: (JsonElement) -> ToolMessage?) : ResultReader
object SarifReader : ResultReader
object CheckstyleXmlReader : ResultReader
class LineReader(val parse: (String) -> ToolMessage?) : ResultReader
```

```kotlin
data class ToolMessage(
    val severity: Severity,                       // HINT..INTERNAL_ERROR (7 levels)
    val range: SourceRange,                       // file + line/col
    val category: String,                         // "mago.unused-variable"
    val title: @Nls String,
    val description: @Nls String? = null,
    val help: @Nls String? = null,                // "Did you mean…"
    val documentationUrl: String? = null,
    val fixes: List<ToolFix> = emptyList(),
    val tags: Set<MessageTag> = emptySet(),       // DEPRECATED, UNUSED, …
)

sealed interface ToolFix {
    val title: @Nls String
    data class Replace(val range: SourceRange, val newText: String) : ToolFix
    data class Patch(val unifiedDiff: String) : ToolFix
    data class RunCli(val args: List<String>) : ToolFix
    data class AddIgnore(val scope: IgnoreScope, val rule: String) : ToolFix
}
```

`ToolFix.Replace` / `Patch` автоматически становятся `LocalQuickFix`.

### 3.10. Ignore policy

Соединяет три уровня ignore:

```kotlin
sealed interface IgnorePolicy {
    fun isIgnored(target: ToolTarget, message: ToolMessage?, ctx: ToolRunContext): Boolean

    /** "Path blacklist" как в QualityToolBlackList, но с glob. */
    data class GlobPaths(val patterns: List<String> = emptyList()) : IgnorePolicy

    /** Аннотации/комментарии в коде. */
    data class CodeAnnotation(val markerComment: String, val nextLineOnly: Boolean = true) : IgnorePolicy

    /** Tool сам решает; SDK ничего не делает. */
    object DelegateToTool : IgnorePolicy

    /** Композит. */
    data class All(val policies: List<IgnorePolicy>) : IgnorePolicy
}
```

### 3.11. Persistence

Всё, что пользователь настраивает в проекте — один
`PersistentStateComponent`:

```kotlin
@State(name = "QualityTools", storages = [Storage("quality-tools.xml")])
@Service(Service.Level.PROJECT)
class QualityToolsProjectStorage : PersistentStateComponent<QualityToolsState> {
    @Property data class QualityToolsState(
        @XCollection var profiles: MutableList<SerializedProfile> = mutableListOf(),
        @XMap var activeProfileByToolMode: MutableMap<String, String> = mutableMapOf(),
    )
    …
}
```

Это означает: один файл, один сервис, все тулы внутри.
PhpStorm-овский `php.xml` остаётся для совместимости — `MigrationActivity`
переносит при первом старте.

### 3.12. Регистрация

```xml
<extensions defaultExtensionNs="dev.jplugins.qualityTools">
    <tool implementation="com.github.xepozz.mago.MagoTool"/>
    <optionsSchema tool="mago" implementation="com.github.xepozz.mago.MagoOptionsSchema"/>
    <configSourceProvider implementation="com.github.xepozz.mago.sources.ComposerMagoProvider"/>
    <configSourceProvider implementation="com.github.xepozz.mago.sources.RemotePhpMagoProvider"/>
    <resultReader tool="mago" implementation="com.github.xepozz.mago.MagoJsonReader"/>
</extensions>
```

И zero-XML альтернатива для тестов:

```kotlin
qualityToolsTesting {
    register(MagoTool())
    register(MagoOptionsSchema())
}
```

---

## 4. Как существующие тулы выглядят на новом SDK

### 4.1. Mago (нам важнее всего)

```kotlin
class MagoTool : QualityTool {
    override val id = "mago"
    override val modes = listOf(
        ToolMode("analyze",  "Analyzer",  verb = "analyze",
                 outputFormat = JSON, supportsStdin = true, supportsFix = true),
        ToolMode("lint",     "Linter",    verb = "lint",
                 outputFormat = JSON, supportsStdin = true, supportsFix = true),
        ToolMode("format",   "Formatter", verb = "fmt",   outputFormat = LINE),
        ToolMode("guard",    "Guard",     verb = "guard", outputFormat = JSON, supportsStdin = true),
    )
    override val reader = JsonLinesReader(::parseMagoMessage)
}

class MagoOptionsSchema : OptionsSchema {
    val configurationFile = path("configurationFile", filter = ext("toml"))
    val workspaceMappings = list("workspaceMappings") {
        path("workspace", role = SCOPE_ROOT)
        path("configFile", filter = ext("toml"))
    }
    val modeAnalyze = mode("analyze") { enabled(true);  additionalArgs() }
    val modeLint    = mode("lint")    { enabled(false); additionalArgs() }
    val modeFormat  = mode("format")  { enabled(true);  additionalArgs(); bool("formatAfterFix") }
    val modeGuard   = mode("guard")   { enabled(false); additionalArgs() }
}

class ComposerMagoProvider : ConfigSourceProvider<ComposerBinarySource> {
    override val supportedTools = setOf("mago")
    override fun watch(project, cb): Disposable =
        project.composerWatcher().observeBinary("carthage-software/mago", "mago", cb)
}
```

→ Из ~2 000 строк нашего glue-кода остаётся ~300.

### 4.2. PHPStan на новом SDK

```kotlin
class PhpStanTool : QualityTool {
    override val id = "phpstan"
    override val modes = listOf(
        ToolMode("analyze", "Analyzer", verb = "analyze",
                 outputFormat = CHECKSTYLE_XML, supportsStdin = false),
    )
    override val reader = CheckstyleXmlReader
    override val ignorePolicy = IgnorePolicy.All(listOf(
        IgnorePolicy.GlobPaths(),
        IgnorePolicy.CodeAnnotation("@phpstan-ignore-next-line"),
    ))
}

class PhpStanOptionsSchema : OptionsSchema {
    val level         = int("level", default = 4, range = 0..10)
    val memoryLimit   = string("memoryLimit", default = "2G")
    val config        = path("config", filter = ext("neon"))
    val autoload      = path("autoload", filter = ext("php"))
    val fullProject   = bool("fullProject", default = false)
}
```

Никаких отдельных `PhpStanGlobalInspection` + `PhpStanValidationInspection`
+ `PhpStanOptionsConfiguration` + `PhpStanProjectConfiguration` +
`PhpStanConfigurationManager` (×2) + `PhpStanAnnotatorProxy` —
вместо 15+ файлов один schema + один tool.

### 4.3. PHP_CodeSniffer

```kotlin
class PhpCsTool : QualityTool {
    override val id = "phpcs"
    override val modes = listOf(
        ToolMode("lint", "Code Sniffer", verb = "",
                 outputFormat = CHECKSTYLE_XML),
        ToolMode("fix",  "Code Beautifier", verb = "",
                 outputFormat = LINE),                         // phpcbf
    )
}
```

`phpcs` и `phpcbf` — два разных бинаря, но **один тул**. На текущем
SDK для этого нужно два `QualityToolType`. На новом — два source-а в
одном профиле или per-mode `executable override`.

### 4.4. Sub-tool (Phalyfusion-style aggregator)

Aggregator-у нужны "вложенные" тулы. На новом SDK:

```kotlin
class PhalyfusionTool : QualityTool {
    override val modes = listOf(ToolMode("aggregate", ..., outputFormat = SARIF))
    override val reader = SarifReader
}
```

Никаких хаков; SARIF читается из коробки.

---

## 5. Миграционный план (детальнее предыдущего)

### Этап 0 — сейчас

✅ `quality-tools-sdk-analysis.md` (фиксация state-of-the-art).
🟡 этот документ (RFC).

### Этап 1 — внутри `mago-plugin` появляется `:sdk:core` (gradle подмодуль)

- модели: `QualityTool`, `ToolMode`, `ToolMessage`, `Severity`,
  `ConfigSource`, `ConfigProfile`, `ConfigScope`, `OptionsSchema`,
  `OptionsBag`;
- `ProfileSelector` с longest-prefix-match;
- `JsonLinesReader`, `LineReader`;
- `ProcessToolRunner` (без PHP-deps);
- адаптер `LegacyQualityToolTypeAdapter` — мост между новым SDK и
  EP `com.jetbrains.php.tools.quality.type` (чтобы UI/Settings JetBrains-
  овский продолжал видеть тул).

Pull request: ~3000 строк, без break-ов поведения для пользователя.

### Этап 2 — миграция Mago на свой же SDK

- `MagoCliOptions` исчезает; его место — `MagoTool.buildArgs`;
- `MagoProjectConfiguration` ужимается до `OptionsBag` + `profiles`;
- `MagoConfigurationProvider` исчезает в пользу `ConfigSourceProvider`;
- `MagoExternalAnnotator` использует новый `ToolRunner` напрямую.

Pull request: -1500 строк, +400 строк.

### Этап 3 — выделение SDK в `j-plugins/quality-tools-sdk`

- gradle composite build / publishing на Marketplace;
- `<depends>dev.jplugins.qualityTools</depends>` в `plugin.xml`;
- semver + changelog;
- docs site.

### Этап 4 — пилот не-PHP

- `oxlint` для JS/TS на ровно этом же SDK — proof, что core реально
  language-agnostic;
- Biome — fmt + lint в одном тулле.

### Этап 5 — predicated PR в bundled JetBrains-плагины

Когда SDK устаканится, открыть `youtrack.jetbrains.com/issue/WI-…`
с предложением заменить `com.jetbrains.php.tools.quality.*` на наш SDK
для bundled тулов. Это уже политика, но имеет шанс.

---

## 6. Что я **сознательно НЕ закладываю** в дизайн

Чтобы не повторять ошибку JetBrains-SDK (всё в один класс):

1. **VCS / pre-commit hooks** — не в SDK. Это отдельный плагин
   `quality-tools-vcs` поверх SDK. У Mago сейчас этого нет — и хорошо.
2. **FUS / телеметрия** — не в SDK. Каждый плагин-консьюмер решает сам.
3. **Code-style settings sharing** (как `QualityToolCodeStyleSettingsModifier`)
   — отдельный модуль `quality-tools-codestyle`.
4. **Inspection profile import/export** — отдельный модуль.
5. **Внутри SDK нет UI Designer (.form)** — только Kotlin UI DSL.

---

## 7. Acceptance criteria

Считаем SDK успешным, если выполнено всё из списка:

- [ ] Mago plugin компилируется и работает поверх SDK, при этом из
      `com.github.xepozz.mago.*` удалено ≥ 1000 строк кода;
- [ ] добавить новый source (например DDEV) — это один класс ≤ 100 строк
      без правок SDK;
- [ ] добавить новый mode (например `mago dependency-analyze`) — это
      одно поле в `MagoTool.modes`, без правок UI;
- [ ] workspace mappings — стандартная нода в `OptionsSchema`, не
      ручной `JBTable`;
- [ ] есть unit-тест "feed JSON output → assert 3 messages with
      `Severity.WARNING`" без процессов;
- [ ] не-PHP интеграция (oxlint) укладывается в ≤ 300 строк;
- [ ] публичные API задокументированы (KDoc + `docs/` сайт).

---

## 8. Открытые на сейчас вопросы (нужно решение перед стартом этапа 1)

1. **Где жить SDK во время этапа 1**: gradle subproject внутри
   `j-plugins/mago-plugin` (`:sdk-core`, `:sdk-php`, …) или отдельный
   репозиторий с самого начала? Я бы стартовал subproject, выделил после
   этапа 2.
2. **Совместимость с `com.jetbrains.php.tools.quality.QualityToolType`**:
   адаптер односторонний (новый → старый), двунаправленный, или старый
   API мы вообще игнорим? Я бы делал односторонний — чтобы наш тул
   продолжал жить в Settings/PHP/QualityTools и юзеры ничего не
   заметили.
3. **Где хранить `quality-tools.xml`**: project root (`.idea/`),
   workspace, или совсем application? Mago сейчас разлит по
   `php.xml` + `workspace.xml` — мы хотим один общий?
4. **Cancellation runner-а**: coroutine-cancel честный (через
   `Process.destroy(force=true)`) или soft? JetBrains-овский SDK делает
   `handler.destroyProcess()` на timeout — этого достаточно?
5. **Per-mode binary override** (PHP_CodeSniffer = phpcs vs phpcbf) —
   это two-source profile или `ToolMode.binaryOverride: ConfigSource?`?
   Думаю второй проще.
6. **Hot-reload OptionsSchema** при изменении `.idea/quality-tools.xml`
   снаружи (git pull) — обязательно или nice-to-have?

---

## 9. Если что-то срочно нужно собрать прямо сейчас

Минимальный inception (Этап 1.5) — что я бы написал первым, ровно по
порядку:

1. `core/QualityTool.kt`, `ToolMode.kt`, `Capability.kt`.
2. `core/source/ConfigSource.kt` (sealed) + `LocalBinarySource`,
   `ComposerBinarySource`, `PhpInterpreterBinarySource`.
3. `core/source/ConfigSourceProvider.kt` + EP.
4. `core/scope/ConfigScope.kt` (sealed) + `ProfileSelector`.
5. `core/profile/ConfigProfile.kt`, `OptionsBag.kt`.
6. `core/message/ToolMessage.kt`, `Severity.kt`, `ToolFix.kt`,
   `SourceRange.kt`.
7. `core/run/ProcessToolRunner.kt`, `ToolProcessPool.kt`, `EnvMutator.kt`.
8. `core/reader/JsonLinesReader.kt`, `LineReader.kt`.
9. `core/persist/QualityToolsProjectStorage.kt`.
10. `core/adapter/LegacyQualityToolTypeAdapter.kt`.

После этого — переписываем `MagoTool` на новом SDK и валидируем дизайн
живым прогоном.
