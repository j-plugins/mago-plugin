# Quality Tools SDK — Integration Cookbook

> Третий документ в серии. `quality-tools-sdk-analysis.md` фиксировал
> текущее состояние JetBrains-SDK; `quality-tools-sdk-design.md`
> описывал высокоуровневый дизайн. Здесь — **рецепт-кукбук для авторов
> плагинов**: как написать новый тул, новый источник конфига (Docker,
> DDEV, mise, …), новый scope, новый reader, не залезая в SDK.
>
> **Важно (post-cycle-5):** Контрактный truth-source — это
> `docs/phases/*.md`. Некоторые сигнатуры в этом кукбуке писались до
> ревью-циклов 1–5 и могут расходиться с финальными интерфейсами
> (`requiredPluginIds: Set<String>` вместо `Set<PluginId>`, `ToolArg`
> вместо `String` в `buildArgs`, `read(run: ToolRun, ...)` вместо
> `read(out: ProcessOutput, ...)`, `ToolMessageBuilder` вместо
> `ToolMessage.copy()`, `EnvMutator.mutate(env: MutableMap, ...)`
> вместо `GeneralCommandLine`, и т. д.). При расхождении доверяйте
> фазовым докам — отдельный sync-pass обновит этот кукбук, когда `:core`
> будет реализован.

## 0. Принципы, по которым строится API

1. **Никаких sealed классов и sealed интерфейсов.** Всё, что
   расширяется — это `interface` или `abstract class`, регистрируемый
   через extension point. В предыдущем дизайн-документе были `sealed
   interface ConfigSource`, `sealed interface ConfigScope`,
   `sealed interface ToolFix` — здесь они переделаны на открытые
   интерфейсы. Это позволяет третьим плагинам добавлять собственные
   реализации без правки SDK и без рекомпиляции.
2. **Никаких abstract classes без причины.** Если кому-то нужно общее
   поведение, оно живёт в `companion object`-функции или в отдельном
   `*Helpers.kt`. Implementation inheritance — крайнее средство.
3. **Никаких enum в публичной модели данных**, кроме случаев, где
   значения буквально не расширяются. Severity — мапим в
   `HighlightDisplayLevel`, без своего enum.
4. **Discriminator — `String typeId`.** Сериализация знает, какой
   `XxxType` восстанавливать конкретный экземпляр, по `typeId`.
   Никаких `when (source) { is Local -> … is Docker -> … }`.
5. **Optional plugin dependencies — first-class.** Тип источника
   объявляет `isAvailable(project): Boolean` и `requiredPlugins:
   Set<PluginId>`; если `intellij.docker` не установлен, Docker
   source не появляется ни в UI, ни в десериализации, и существующие
   записи остаются read-only "unavailable".
6. **Hard zero zoo of "manager" services.** Один сервис на проект для
   хранения, один реестр для регистраций. Тулы — это just data.
7. **DumbAware по умолчанию.** Все внешние тулы должны работать в
   индексации.

---

## 1. Карта расширений (что вообще можно расширить)

| EP `dev.jplugins.qualityTools.*` | Что вы делаете |
| --- | --- |
| `tool` | Полностью новый тул (Rector, Pest, biome, ...). Один класс. |
| `configSourceType` | Новый способ "где найти бинарь" (Docker, mise, asdf, devbox, ddev, WSL, k8s pod, ...). |
| `configScopeType` | Новый способ "к какому файлу применить" (по glob, по VCS branch, по author, по time-of-day). |
| `resultReader` | Новый формат вывода тула (SARIF, ESLint-JSON, RDJSON, …). Готовые: `json-lines`, `sarif`, `checkstyle-xml`, `line`. |
| `ignorePolicyType` | Новый способ "когда промолчать" (custom-comment annotation, baseline file). |
| `optionRenderer` | Кастомный рендер для определённой `OptionSpec`. |
| `envMutator` | Меняет `GeneralCommandLine` перед стартом (Xdebug-off, NVM, …). |
| `processPool` | Override стратегии параллелизма. |
| `messageEnricher` | Пост-обработка `ToolMessage` после reader (добавить docURL, перевести category, etc). |

Любой EP — `dynamic="true"`. Любая регистрация переживает hot-reload.

---

## 2. Базовые контракты (минимум, чтобы понять остальные разделы)

```kotlin
// dev.jplugins.qualityTools.tool

interface QualityTool {
    val id: String                                  // "mago", "phpstan", "rector"
    val displayName: String
    val supportedLanguages: Set<Language>
    val modes: List<ToolMode>
    val capabilities: Set<String>                   // "lint", "analyze", "fix", …
    val acceptedSourceTypeIds: Set<String>          // "local", "composer", "docker", "*"
    val resultReaderId: String                      // "json-lines", "sarif", "<your-id>"

    fun buildArgs(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget): List<String>
}

interface ToolMode {
    val id: String
    val displayName: String
    val verb: String
    val outputFormat: String                        // matches reader id, or "inherit"
    val executionStyle: String                      // "on_the_fly" | "on_save" | "manual" | "batch"
    val defaultArgs: List<String>
    val supportsStdin: Boolean
    val supportsFix: Boolean
}

interface ToolTarget {
    val normalizedPath: String
    fun toCliArg(scope: ResolvedScope): String
}

// dev.jplugins.qualityTools.configSourceType

interface ConfigSource {
    val instanceId: String                          // uuid for this profile entry
    val typeId: String                              // "local", "composer", "docker", …
    val displayName: String
    suspend fun resolve(ctx: ResolveContext): ResolvedBinary?
}

interface ConfigSourceType {
    val typeId: String
    val displayName: String

    /** Whether this source type can be offered right now. */
    fun isAvailable(project: Project): Boolean

    /** Optional dependencies (other plugins) that must be enabled. */
    val requiredPlugins: Set<PluginId>
        get() = emptySet()

    /** Show the "Add new" wizard. Returns a ConfigSource on Ok, null on Cancel. */
    fun createWizard(project: Project, tool: QualityTool, existing: List<ConfigSource>): ConfigSourceWizard?

    fun deserialize(element: Element): ConfigSource
    fun serialize(source: ConfigSource): Element

    /** Optional: watch project for autodetected sources (file appears in vendor/bin, etc). */
    fun watch(project: Project, callback: (ConfigSource) -> Unit): Disposable? = null
}

data class ResolvedBinary(
    val command: List<String>,          // ["mago"], ["docker","compose","exec","app","mago"]
    val workingDir: Path?,
    val env: Map<String, String>,
    val pathMapper: PathMapper?,        // local-path -> remote-path; null = identity
    val supportsStdin: Boolean,
)
```

Никаких `sealed` — `QualityTool`, `ToolMode`, `ToolTarget`, `ConfigSource`,
`ConfigSourceType`, `PathMapper`, `ConfigScope`, `ConfigScopeType`,
`ToolFix`, `IgnorePolicy`, `IgnorePolicyType`, `ResultReader` — все
интерфейсы, все регистрируются через свой EP.

---

## 3. Рецепт #1: интегрируем новый тул (Rector)

Сценарий: я автор плагина для Rector. Хочу подсветку в редакторе через
SDK Quality Tools.

### 3.1. `build.gradle.kts`

```kotlin
dependencies {
    intellijPlatform {
        plugin("dev.jplugins.quality-tools:1.0.0")
        plugin("com.jetbrains.php:251.x")          // PHP language support
    }
}
```

### 3.2. `plugin.xml`

```xml
<idea-plugin>
    <id>com.example.rector</id>
    <depends>dev.jplugins.quality-tools</depends>
    <depends>com.jetbrains.php</depends>

    <extensions defaultExtensionNs="dev.jplugins.qualityTools">
        <tool implementation="com.example.rector.RectorTool"/>
        <resultReader implementation="com.example.rector.RectorJsonReader"/>
        <optionsSchema implementation="com.example.rector.RectorOptionsSchema"/>
    </extensions>
</idea-plugin>
```

### 3.3. Сам тул — один файл

```kotlin
// RectorTool.kt
class RectorTool : QualityTool {
    override val id = "rector"
    override val displayName = "Rector"
    override val supportedLanguages = setOf(PhpLanguage.INSTANCE)
    override val capabilities = setOf("analyze", "fix")
    override val acceptedSourceTypeIds = setOf("*")     // works with any source
    override val resultReaderId = "rector-json"

    override val modes = listOf(
        Mode("analyze", verb = "process",
             defaultArgs = listOf("--dry-run", "--output-format=json")),
        Mode("fix",     verb = "process",
             defaultArgs = listOf("--output-format=json")),
    )

    override fun buildArgs(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget) = buildList {
        add(mode.verb)
        add(target.toCliArg(ctx.scope))
        ctx.options.string("config")?.let { add("--config=$it") }
        ctx.options.string("set")?.let { add("--set=$it") }
        addAll(mode.defaultArgs)
        addAll(ParametersList.parse(ctx.profile.modes[mode.id]?.additionalArgs.orEmpty()))
    }
}

private class Mode(
    override val id: String,
    override val verb: String,
    override val defaultArgs: List<String>,
    override val displayName: String = id.replaceFirstChar { it.uppercase() },
    override val outputFormat: String = "inherit",
    override val executionStyle: String = "on_the_fly",
    override val supportsStdin: Boolean = false,
    override val supportsFix: Boolean = id == "fix",
) : ToolMode
```

→ Всё. Никакого `RectorQualityToolType`, `RectorConfiguration`,
`RectorConfigurationManager` × 2, `RectorBlackList`,
`RectorAnnotatorProxy`, `RectorOptionsConfiguration`,
`RectorGlobalInspection`, `RectorValidationInspection`,
`RectorSettingsTransferStartupActivity`. Одно `class RectorTool`.

### 3.4. Reader

```kotlin
class RectorJsonReader : ResultReader {
    override val id = "rector-json"

    override fun read(out: ProcessOutput, ctx: ToolRunContext) = flow {
        val root = JsonParser.parseString(out.stdout).asJsonObject
        for (entry in root.getAsJsonArray("file_diffs")) {
            val diff = entry.asJsonObject
            emit(ToolMessage(
                severity = HighlightDisplayLevel.WARNING,
                range    = SourceRange.file(diff["file"].asString),
                category = diff["applied_rectors"][0].asString,
                title    = "Rector suggestion",
                description = diff["diff"].asString,
                fixes    = listOf(PatchFix(diff["diff"].asString)),
            ))
        }
    }
}
```

### 3.5. Options schema (опционально)

```kotlin
class RectorOptionsSchema : OptionsSchema {
    override val toolId = "rector"

    val config = path("config", filter = ext("php"))
    val set    = string("set",  default = "PHP_84")

    val analyze = mode("analyze") { enabled(default = true);  additionalArgs() }
    val fix     = mode("fix")     { enabled(default = false); additionalArgs() }
}
```

UI генерируется автоматически.

**Итого на интеграцию Rector — 3 файла, ~80 строк.**

---

## 4. Рецепт #2: новый источник бинаря — Docker (guiding example)

Это **главный пример того, для чего весь дизайн затевался**. Я ставлю
Docker plugin, ставлю плагин Mago, и хочу, чтобы в Settings/Quality
Tools/Mago кнопка `+` показала пункт "From Docker", из которого
получился бы профиль с docker compose exec mago в `app` сервисе.

### 4.1. Где живёт код

Это **отдельный плагин**, например `com.github.xepozz.mago-docker`,
с `optional`-зависимостью на Docker:

```xml
<idea-plugin>
    <id>com.github.xepozz.mago-docker</id>
    <depends>com.github.xepozz.mago</depends>
    <depends>dev.jplugins.quality-tools</depends>
    <depends optional="true" config-file="docker-only.xml">Docker</depends>

    <extensions defaultExtensionNs="dev.jplugins.qualityTools">
        <configSourceType implementation="com.github.xepozz.mago.docker.DockerSourceType"/>
    </extensions>
</idea-plugin>
```

`docker-only.xml` подключает всё, что упадёт, если Docker plugin вдруг
не активен:

```xml
<idea-plugin>
    <actions>
        <action id="MagoDocker.RestartContainer"
                class="com.github.xepozz.mago.docker.RestartAction"/>
    </actions>
</idea-plugin>
```

Сам `configSourceType` остаётся зарегистрирован всегда, но через
`isAvailable(project)` он скажет SDK "меня не предлагай, если Docker
выключен".

### 4.2. `DockerSource` — обычный POKO

```kotlin
class DockerSource : ConfigSource {
    override val typeId = "docker"

    override var instanceId: String = ""
    override var displayName: String = ""

    var composeFile: String = ""           // docker-compose.yml path
    var service: String = "app"            // service name
    var workingDir: String = "/var/www/html"
    var executable: String = "mago"        // path inside container
    var pathMappings: MutableList<DockerPathMapping> = mutableListOf()
    var extraExecArgs: List<String> = emptyList()    // ["-T"] for no-tty
    var environment: Map<String, String> = emptyMap()
    var allowLocalFallback: Boolean = false          // <- "только из remote" по умолчанию

    override suspend fun resolve(ctx: ResolveContext): ResolvedBinary? {
        if (!isDockerPluginAvailable()) return null
        return ResolvedBinary(
            command = buildList {
                add("docker")
                add("compose")
                if (composeFile.isNotBlank()) { add("-f"); add(composeFile) }
                add("exec")
                addAll(extraExecArgs.ifEmpty { listOf("-T") })   // -T = no TTY
                for ((k, v) in environment) { add("--env"); add("$k=$v") }
                add("--workdir"); add(workingDir)
                add(service)
                add(executable)
            },
            workingDir = ctx.project.basePath?.let(Path::of),
            env = emptyMap(),
            pathMapper = DockerPathMapper(pathMappings),
            supportsStdin = true,                                // docker compose exec умеет stdin
        )
    }
}

data class DockerPathMapping(
    var host: String = "",                  // /Users/me/proj
    var container: String = "",             // /var/www/html
)
```

### 4.3. `PathMapper` — маппинг хост ↔ контейнер

`PathMapper` — общий интерфейс SDK. Авторы могут реализовать его как
угодно; Docker делает это так:

```kotlin
interface PathMapper {
    fun toRemote(localPath: String): String
    fun toLocal(remotePath: String): String
    fun canProcess(localPath: String): Boolean
}

class DockerPathMapper(private val mappings: List<DockerPathMapping>) : PathMapper {
    override fun canProcess(localPath: String): Boolean =
        mappings.any { localPath.startsWith(it.host) }

    override fun toRemote(localPath: String): String =
        mappings.firstOrNull { localPath.startsWith(it.host) }
            ?.let { localPath.replaceFirst(it.host, it.container) }
            ?: localPath

    override fun toLocal(remotePath: String): String =
        mappings.firstOrNull { remotePath.startsWith(it.container) }
            ?.let { remotePath.replaceFirst(it.container, it.host) }
            ?: remotePath
}
```

`ToolRunner` SDK сам применяет `pathMapper` к аргументам тула — в духе
того, как `RemoteInterpreterMagoRunner` сейчас руками мэппит
`--workspace=` и `--config=`. Mode-аргументы тоже мэппятся, потому что
SDK знает, что аргумент — это путь (см. §4.6 ниже).

### 4.4. `DockerSourceType`

```kotlin
class DockerSourceType : ConfigSourceType {
    override val typeId = "docker"
    override val displayName = "Docker container"
    override val requiredPlugins = setOf(PluginId.getId("Docker"))

    override fun isAvailable(project: Project): Boolean =
        PluginManagerCore.getPlugin(PluginId.getId("Docker"))?.isEnabled == true

    override fun createWizard(project: Project, tool: QualityTool, existing: List<ConfigSource>):
            ConfigSourceWizard = DockerSourceWizard(project, tool, existing)

    override fun deserialize(element: Element): ConfigSource =
        XmlSerializer.deserialize(element, DockerSource::class.java)

    override fun serialize(source: ConfigSource): Element =
        XmlSerializer.serialize(source as DockerSource)
}
```

`DockerSourceWizard` — модальный dialog с шагами:
1. выбор `docker-compose.yml` из проекта (через `FileChooserDescriptor`),
2. выбор сервиса (live список — `DockerComposeRunConfigurationType.getServices(...)` из Docker plugin),
3. опционально импортнуть mappings из конфигурации Docker-проекта
   (Docker plugin их хранит в `.idea/`),
4. путь к бинарю (`/var/www/html/vendor/bin/mago` или просто `mago`),
5. поле "only remote, no local fallback" — checkbox `allowLocalFallback`.

После Ok возвращает `DockerSource` с заполненными полями.

### 4.5. UI настройки источника

Кастомный UI не обязателен — стандартный SDK рендерер сделает форму на
основании рефлексии полей `DockerSource`. Если хочется красивее —
регистрируем:

```kotlin
class DockerSourceRenderer : ConfigSourceRenderer<DockerSource> {
    override val typeId = "docker"
    override fun render(panel: Panel, source: DockerSource, project: Project) {
        panel.row("Compose file:") {
            textFieldWithBrowseButton(...).bindText(source::composeFile)
        }
        panel.row("Service:") {
            comboBox(getDockerServices(project, source.composeFile))
                .bindItem(source::service.toNullableProperty())
        }
        panel.row("Path mappings:") {
            cell(buildMappingsTable(source.pathMappings)).align(AlignX.FILL)
        }
        panel.row {
            checkBox("Allow local fallback when container is down")
                .bindSelected(source::allowLocalFallback)
                .comment("If unchecked, on-the-fly analysis stops when the container is unavailable.")
        }
    }
}
```

EP `dev.jplugins.qualityTools.configSourceRenderer`.

### 4.6. Path-aware аргументы

SDK по умолчанию не знает, какие аргументы тула — это пути, а какие
нет. Решается двумя путями:

(a) `ToolMode` декларирует **шаблон аргументов** — флаги, значения
которых нужно мэппить:

```kotlin
class Mode(...) : ToolMode {
    override val pathArgs = setOf("--workspace", "--config")
}
```

(b) Тул помечает аргумент специально через obvious helper:

```kotlin
override fun buildArgs(ctx, mode, target) = buildList {
    add(mode.verb)
    add(ctx.markAsPath(target.toCliArg(ctx.scope)))
    ctx.options.path("config")?.let {
        add(ctx.markAsPath("--config=${ctx.markAsPath(it.toString())}"))
    }
}
```

`ctx.markAsPath(...)` оборачивает значение в `PathArg("...")` (маркер-
класс); `ToolRunner` после `buildArgs` обходит результат и применяет
`pathMapper.toRemote(...)` ко всем `PathArg` (включая значение после
`=`).

### 4.7. "Только remote" режим

Сценарий: пользователь хочет, чтобы локальный mago никогда не запускался
по ошибке — даже если контейнер недоступен. `DockerSource.allowLocalFallback
= false` (default) обеспечивает:

- `DockerSource.resolve()` возвращает `null`, если `docker ps` показывает,
  что service down.
- `ToolRunner` ловит `null` и **не падает на fallback**, а возвращает
  пустой `ToolRun(exitCode = -1, messages = listOf(internalError(…)))`.
- Annotator показывает balloon "Mago: Docker service `app` is down" с
  actions "Start container" / "Open Docker tool window" / "Disable
  Mago" — все они приходят через `messageEnricher` для `INTERNAL_ERROR`
  с категорией `docker.unavailable`.

### 4.8. Итог по Docker

| Что | Сколько строк |
| --- | --- |
| `DockerSource.kt` (POKO + resolve) | ~70 |
| `DockerSourceType.kt` | ~25 |
| `DockerSourceWizard.kt` | ~100 |
| `DockerSourceRenderer.kt` (optional) | ~30 |
| `DockerPathMapper.kt` | ~20 |
| `plugin.xml` + bundle | ~30 |
| **итого** | **~275 строк отдельного плагина**, ноль правок Mago/SDK |

И тот же путь годится для DDEV, Lando, Kubernetes pod (`kubectl exec`),
Vagrant, SSH-only хоста и т. д. — каждый = отдельный
`ConfigSourceType` в своём плагине.

---

## 5. Рецепт #3: новый scope (например "VCS branch")

Сценарий: хочу, чтобы на ветке `main` тул работал в strict-режиме, а на
feature-ветках — в lite. → новый `ConfigScope`, чувствительный к VCS.

```kotlin
class VcsBranchScope : ConfigScope {
    override val typeId = "vcs-branch"
    var branchPattern: String = ""           // glob: "main", "release/*"

    override fun matches(target: ToolTarget, ctx: MatchContext): Boolean {
        val current = ctx.project.vcsBranch() ?: return false
        return Glob.match(branchPattern, current)
    }

    override fun specificity(target: ToolTarget, ctx: MatchContext): Int =
        if (matches(target, ctx)) branchPattern.length else 0
}

class VcsBranchScopeType : ConfigScopeType {
    override val typeId = "vcs-branch"
    override val displayName = "VCS branch"
    override fun createWizard(project: Project) = VcsBranchScopeWizard(project)
    override fun deserialize(element: Element) =
        XmlSerializer.deserialize(element, VcsBranchScope::class.java)
    override fun serialize(scope: ConfigScope) =
        XmlSerializer.serialize(scope as VcsBranchScope)
}
```

```xml
<extensions defaultExtensionNs="dev.jplugins.qualityTools">
    <configScopeType implementation="com.example.vcsscope.VcsBranchScopeType"/>
</extensions>
```

`ProfileSelector` без правок видит новый scope: для каждого профиля он
смотрит на `scope.matches(target, ctx)`, при равенстве выбирает
максимальный `scope.specificity(target, ctx)`. Если два scope-а
matchнули с одним specificity — порядок профилей в списке решает.

То же самое — для scope-а "по author последнего коммита",
"по `JAVA_HOME` env-var", "по моду IDE (light / dark)", … Один класс,
одна регистрация.

---

## 6. Рецепт #4: новый reader (SARIF для самописного линтера)

```kotlin
class SarifReader : ResultReader {
    override val id = "sarif-2.1"

    override fun read(out: ProcessOutput, ctx: ToolRunContext) = flow {
        val log = sarifMapper.readValue<SarifLog>(out.stdout)
        for (run in log.runs) for (result in run.results) {
            emit(ToolMessage(
                severity = mapLevel(result.level),
                range = result.locations.first().toSourceRange(ctx),
                category = result.ruleId,
                title = result.message.text,
                fixes = result.fixes.map { it.toReplaceFix() },
            ))
        }
    }
}
```

```xml
<resultReader implementation="com.example.sarif.SarifReader"/>
```

Любой `QualityTool` теперь может объявить `resultReaderId = "sarif-2.1"`
и получить парсер бесплатно.

---

## 7. Рецепт #5: ToolFix — это просто `interface`, а не sealed

`ToolFix` — обычный интерфейс:

```kotlin
interface ToolFix {
    val title: String
    fun apply(file: PsiFile, project: Project)
    fun preview(file: PsiFile): IntentionPreviewInfo? = null
}
```

SDK поставляет коробочные реализации:

```kotlin
class ReplaceFix(val range: SourceRange, val newText: String) : ToolFix
class PatchFix(val unifiedDiff: String) : ToolFix
class CliFix(val args: List<String>) : ToolFix
class IgnoreFix(val scope: IgnoreScope, val rule: String) : ToolFix
```

Свой fix добавляется одним классом без регистрации — это просто объект,
который `ResultReader` положил в `ToolMessage.fixes`. Дискриминатор не
нужен, потому что fix-ы не сериализуются.

---

## 8. Рецепт #6: IgnorePolicy расширяется

```kotlin
interface IgnorePolicy {
    val typeId: String
    fun isIgnored(target: ToolTarget, message: ToolMessage?, ctx: ToolRunContext): Boolean
}

class GlobIgnorePolicy : IgnorePolicy { override val typeId = "glob"; var patterns: List<String> = emptyList(); ... }
class AnnotationIgnorePolicy : IgnorePolicy { override val typeId = "annotation"; var marker: String = ""; ... }
class BaselineIgnorePolicy : IgnorePolicy { override val typeId = "baseline"; var baselineFile: String = ""; ... }
```

И тип:

```kotlin
interface IgnorePolicyType {
    val typeId: String
    val displayName: String
    fun isAvailable(project: Project): Boolean = true
    fun createWizard(project: Project): IgnorePolicyWizard
    fun deserialize(element: Element): IgnorePolicy
    fun serialize(policy: IgnorePolicy): Element
}
```

Регистрация — `<ignorePolicyType …>`. Свой ignore policy
(например "ignore everything older than 1 year") пишется как обычный
класс, регистрируется одной строкой.

---

## 9. Рецепт #7: пост-обработка сообщений

Хочу к каждому сообщению PHPStan приклеивать ссылку на доку:

```kotlin
class PhpStanDocsEnricher : MessageEnricher {
    override fun supports(message: ToolMessage, ctx: ToolRunContext): Boolean =
        ctx.profile.toolId == "phpstan" && message.documentationUrl == null

    override fun enrich(message: ToolMessage, ctx: ToolRunContext): ToolMessage =
        message.copy(documentationUrl = "https://phpstan.org/r/${message.category}")
}
```

```xml
<messageEnricher implementation="com.example.PhpStanDocsEnricher"/>
```

Применяется в `ToolRunner` после `ResultReader`, до показа аннотатором.
Цепочка enrichers — порядок по `order=` атрибуту.

---

## 10. Рецепт #8: env mutator для XDebug / Mise / NVM

Тулы вроде PHPStan заводятся медленнее из-за XDebug. SDK предоставляет
`EnvMutator`:

```kotlin
interface EnvMutator {
    fun appliesTo(ctx: ToolRunContext): Boolean
    fun mutate(cmd: GeneralCommandLine, ctx: ToolRunContext)
}

class XdebugOffMutator : EnvMutator {
    override fun appliesTo(ctx: ToolRunContext) =
        ctx.tool.supportedLanguages.contains(PhpLanguage.INSTANCE)

    override fun mutate(cmd: GeneralCommandLine, ctx: ToolRunContext) {
        cmd.withEnvironment("XDEBUG_MODE", "off")
    }
}

class MiseShimMutator : EnvMutator {
    override fun appliesTo(ctx) = ctx.profile.source.typeId == "mise"
    override fun mutate(cmd, ctx) {
        cmd.environment["MISE_INSTALL_PATH"] = ctx.options.string("misePath").orEmpty()
    }
}
```

Регистрация — `<envMutator order="first">…`.

---

## 11. Рецепт #9: UI options renderer для нестандартного поля

`OptionsSchema` даёт стандартный набор примитивов: `bool/int/string/path/
list/mode`. Если нужен особый виджет (например, multiselect с
автокомплитом из CLI-вывода):

```kotlin
class PhpStanRulesRenderer : OptionRenderer {
    override fun supports(spec: OptionSpec<*>): Boolean =
        spec.key == "phpstan.ignoredRules" && spec is StringListSpec

    override fun render(panel: Panel, spec: OptionSpec<*>, bag: OptionsBag) {
        val list = bag.list(spec as StringListSpec)
        panel.row(spec.displayName) {
            cell(buildPhpStanRuleAutocomplete(list)).align(AlignX.FILL)
        }
    }
}
```

```xml
<optionRenderer implementation="com.example.PhpStanRulesRenderer"/>
```

SDK при построении UI спрашивает renderer-ы в порядке регистрации;
первый, кто `supports(spec) == true` — рендерит. Если никто — fallback
на стандартный.

---

## 12. Рецепт #10: тестирование (без процесса)

```kotlin
class MagoLintTest : QualityToolTestCase() {
    override val tool = MagoTool()
    override val reader = JsonLinesReader(::parseMagoMessage)

    @Test
    fun `unused variable on stdin`() = runQualityTool {
        val file = givenPhpFile("src/Foo.php", "<?php class Foo { function bar() { ${'$'}x = 1; } }")
        givenProfile {
            source = local("/fake/mago")
            scope  = workspaceRoot(file.parent)
            mode("lint") { enabled = true }
        }
        givenRecordedRun(
            args = listOf("lint", "src/Foo.php", "--stdin-input", "--reporting-format=json"),
            stdout = json("""[{"file":"src/Foo.php","line":1,"column":24,"severity":"warning","code":"unused-variable","message":"…"}]"""),
            exitCode = 0,
        )
        runAnnotator(file)
        assertMessages {
            single {
                severity == HighlightDisplayLevel.WARNING
                category == "unused-variable"
                range.startLine == 1
            }
        }
    }
}
```

`QualityToolTestCase` — из модуля `quality-tools-sdk-testing`. Никаких
реальных процессов; `FakeProcessRunner` сопоставляет args → recorded
output.

Для тестирования source-type-а (например DockerSource):

```kotlin
class DockerSourceTest : ConfigSourceTestCase() {
    @Test
    fun `resolve when docker plugin missing`() = runTest {
        withoutPluginInstalled("Docker")
        val source = DockerSource().apply { service = "app" }
        assertNull(source.resolve(resolveContext()))
    }

    @Test
    fun `path mapper rewrites workspace`() {
        val src = DockerSource().apply {
            pathMappings += DockerPathMapping("/host/proj", "/var/www/html")
        }
        val resolved = runBlocking { src.resolve(resolveContext()) }!!
        assertEquals("/var/www/html/src/Foo.php",
            resolved.pathMapper!!.toRemote("/host/proj/src/Foo.php"))
    }
}
```

---

## 13. Версионирование, совместимость, deprecation

- Публичные интерфейсы SDK маркируются `@ApiStatus.Experimental` или
  `@ApiStatus.Internal`; стабильные — без аннотаций.
- SemVer: minor — добавление нового метода с `default` имплементацией
  или нового EP; major — удаление метода / переименование `typeId`.
- Любой `typeId` — **forever stable**. Если переименовали Docker source —
  оставляем `aliasTypeIds = setOf("docker", "docker-compose")` чтобы
  старые `.idea/quality-tools.xml` продолжали грузиться.
- Все новые методы в интерфейсах — с `default`-имплементацией. Зависимые
  плагины не ломаются на minor.

---

## 14. Anti-patterns (чего НЕ делать в плагине-консьюмере)

- ❌ Хранить `pathMapper` в state-е `ConfigSource`. Path mappings — да,
  сам mapper — runtime-only.
- ❌ Реализовывать `ConfigSource` через abstract base от SDK, добавляя
  свои абстрактные методы. SDK видит только интерфейс — переопределение
  методов из abstract base SDK не увидит.
- ❌ Делать `isAvailable(project) = true`, если используешь чужой плагин.
  Без явной проверки `PluginManagerCore.getPlugin(...)?.isEnabled`
  пользователь получит `ClassNotFoundException` при отключении Docker
  plugin.
- ❌ Кидать exceptions из `resolve(ctx)`. Возвращать `null` и логировать
  через `Logger`. Exception → annotator падает молча и пользователь
  думает "тул сломан".
- ❌ Делать `buildArgs` зависимым от `Project` напрямую. Получай всё из
  `ToolRunContext`. Тесты тогда не зависят от `BasePlatformTestCase`.
- ❌ Засовывать timeout в `OptionsSchema`. Timeout — это runtime
  property `ConfigProfile`, не options.
- ❌ Реализовывать собственный `ToolRunner`. Если очень надо что-то
  специфичное — `EnvMutator` или `processPool` override; полностью свой
  runner ломает enrichers, ignore policy и кэш SDK.
- ❌ Регистрировать тул напрямую в `com.jetbrains.php.tools.quality.type`.
  Через legacy-адаптер SDK сам прокинет твой `QualityTool` туда.

---

## 15. Чеклист для подачи плагина в Marketplace

Когда автор пишет интеграцию, проверка перед публикацией:

- [ ] `QualityTool` имеет уникальный `id` (`-` или ASCII без точек);
- [ ] Каждый `ConfigSourceType.typeId` уникален в рамках всей экосистемы
      — рекомендуем `vendor.tool` (`xepozz.mago-docker` лучше чем
      просто `docker`);
- [ ] `isAvailable(project)` корректно проверяет опциональные зависимости;
- [ ] Сериализация source через `@Attribute` / `@Tag`, не Java
      сериализация;
- [ ] Опции тула имеют sensible defaults — без `apply`-а в Settings
      тул работает;
- [ ] Reader не падает на пустой stdout;
- [ ] `ToolMessage.range` валиден (line ≥ 1, column ≥ 0);
- [ ] При отсутствии бинаря annotator не спамит балунами больше 1
      раза — используй `QualityToolsNotifier.muted(profileId)`;
- [ ] Unit-тест на `ConfigSource.resolve` + reader-spec тест из
      `quality-tools-sdk-testing`.

---

## 16. FAQ

> **Q. Почему нельзя сделать `sealed interface ConfigSource` и
> "разрешённые имплементации"?**
> A. Потому что Docker, DDEV, Lando, mise, asdf, devbox, k8s — это разные
> *плагины*, каждый со своим жизненным циклом. SDK заранее не знает
> список и не должен ребилдиться при добавлении нового. Sealed
> требует, чтобы все имплементации жили в той же compilation unit, что
> и интерфейс — это уничтожает расширяемость.

> **Q. Но тогда как safe-cast в `when (source) { is DockerSource -> … }`?**
> A. Никак. Если коду понадобился `is DockerSource`, значит этот код
> *принадлежит* плагину docker-source. Снаружи source-а ты работаешь
> через интерфейс `ConfigSource` / `ResolvedBinary` / `PathMapper`,
> которые универсальны.

> **Q. Что, если разные плагины зарегистрируют ConfigSourceType с
> одинаковым `typeId = "docker"`?**
> A. SDK логирует error, оставляет первый зарегистрированный,
> остальные молча игнорит. Поэтому `typeId` рекомендуется prefix-ить
> vendor-ом (`xepozz.docker`).

> **Q. Можно ли регистрировать `ConfigSourceType` через service вместо
> EP?**
> A. Нет. EP — единственный путь, потому что нам нужен список всех
> зарегистрированных типов для UI кнопки "+".

> **Q. Что с FUS / телеметрией?**
> A. Не входит в SDK. Каждый плагин-консьюмер сам решает, и сам
> декларирует группы в `*.xml`. SDK даёт `ToolRun` событие через
> `messageBus` — слушайте и репортите.

> **Q. Compose-стек хочется обновить — Docker plugin поднял версию.
> Что ломается?**
> A. Docker plugin переименовал API → твой `DockerSource.resolve` упадёт
> на `NoClassDefFoundError`. SDK поймает, отметит source как broken,
> покажет балун с "Update mago-docker plugin" с ссылкой на marketplace.
> Это retry-safe — после обновления плагин просто работает.

---

## 17. Что я хочу, чтобы вы сделали, прочитав этот документ

1. Скажите, нет ли в принципе несогласия с подходом "только
   интерфейсы + EP" (он осознанно идёт против shorthand-удобства
   sealed-сопоставления).
2. Скажите, должен ли SDK включать готовый Docker source (моё мнение —
   нет: он живёт во внешнем плагине), или мы хотим bundle его как
   референс-имплементацию рядом с `quality-tools-sdk-php`.
3. Скажите, нужен ли вам `Capability` как enum или строки достаточно
   (моё мнение — строки, экосистема придумает свои).
4. Скажите, должны ли все имплементации `ConfigSource` быть
   serializable POJO (XML), или может быть `ConfigSource`, который сам
   себя не сериализует (runtime-only, типа "current Docker context").

После того, как договорились, я готов писать `quality-tools-sdk-core`
gradle-подмодуль и начинать миграцию Mago. План я уже описал в
конце `quality-tools-sdk-design.md` §5.
