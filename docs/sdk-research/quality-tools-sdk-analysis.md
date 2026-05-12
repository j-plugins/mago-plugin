# Анализ Quality Tools SDK из плагина `com.jetbrains.php`

> Исходный материал получен путём декомпиляции `php-impl-251.29188.37.zip`
> (PHP plugin для PhpStorm 2025.1, build 251.x), а также
> `phpstan-251.29188.11.zip` и `psalm-251.29188.11.zip` через CFR 0.152.
> Эти три плагина — единственные носители кода `com.jetbrains.php.tools.quality.*`.
>
> Цель документа — зафиксировать текущую архитектуру JetBrains-овского
> Quality Tools SDK, перечислить её ограничения (которые нас как авторов
> Mago плагина тормозят) и набросать дизайн нового, "своего", расширяемого
> Quality Tools SDK для IntelliJ-платформы.

---

## 1. Кто пользуется SDK

Bundled (внутри `php.jar`):
- `com.jetbrains.php.tools.quality.phpcs.*` — PHP_CodeSniffer
- `com.jetbrains.php.tools.quality.phpCSFixer.*` — PHP-CS-Fixer
- `com.jetbrains.php.tools.quality.laravelPint.*` — Laravel Pint
- `com.jetbrains.php.tools.quality.messDetector.*` — PHP Mess Detector

Отдельные плагины:
- `com.intellij.php.tools.quality.phpstan` (`phpstan.jar`) — PHPStan
- `com.intellij.php.psalm` (`psalm.jar`) — Psalm

Сторонний (наш):
- `com.github.xepozz.mago.qualityTool.*` — Mago

Точка регистрации одна — extension point `com.jetbrains.php.tools.quality.type`
с интерфейсом `QualityToolType<C>`.

---

## 2. Карта классов SDK

### 2.1. Контракты (то, что реализует автор интеграции)

| Класс | Назначение |
| --- | --- |
| `QualityToolType<C extends QualityToolConfiguration>` | Корневая точка расширения. Возвращает displayName, blacklist, configurationManager, inspection, configurationProvider, configurableForm, toolConfigurable (Configurable для Settings), projectConfiguration, createConfiguration, getGlobalTool, getInspectionId, getInspectionShortName, getHelpTopic. |
| `QualityToolConfiguration` | Модель *одной* настройки инструмента: `toolPath`, `interpreterId`, `timeout`, `maxMessagesPerFile`, два флага `asDefaultInterpreter` / `deletedFromTheList`, `id`, `presentableName`, `clone`, `compareTo`. Сериализуется через `XmlSerializer`. |
| `QualityToolConfigurationProvider<C>` | Загрузка/создание конфига из XML (`canLoad/load`) и из `PhpInterpreter`. Имеет `createNewInstance(project, all)` для UI кнопки "+" в списке. **Важно: предполагается ровно один на инструмент** — `QualityToolConfigurationProvider#getInstances()` в каждом инструменте (`PhpStanConfigurationProvider`, `PsalmConfigurationProvider`, …) логирует `LOG.error("Several providers… was found")`, если их больше одного. |
| `QualityToolConfigurationBaseManager<C>` | `PersistentStateComponent<Element>` — список `mySettings: List<C>`, сериализация `<root><{rootName}>…`. По одной такой штуке на инструмент в App-уровне и Project-уровне (одинаковые имена State, разный Storage). |
| `QualityToolConfigurationManager<C>` | "Фасад" над двумя `QualityToolConfigurationBaseManager` (App + Project). Содержит логику слияния, флага `isProjectLevel`, разделения local/remote, и `getOrCreateConfigurationByInterpreter`. |
| `QualityToolProjectConfiguration<C>` | Хранит выбранный `selectedConfigurationId` для активной конфигурации в проекте. Возвращает её через `findSelectedConfiguration`. Сюда же по факту дописываются *произвольные* поля проекта — см. `MagoProjectConfiguration` (флаги `enabled`, `formatterEnabled`, "additionalParameters" и т. п.). |
| `QualityToolBlackList` | `PersistentStateComponent` со списком `filePaths` — exclude-листы для on-the-fly анализа. |
| `QualityToolValidationInspection<T>` | `LocalInspectionTool + ExternalAnnotatorBatchInspection` — заглушка для UI инспекций; вся работа делается через ExternalAnnotator. На on-the-fly возвращает пустой массив; на batch вызывает annotator руками. |
| `QualityToolValidationGlobalInspection` (parent) | Для tool-ов, у которых "batch" режим — это полный проект (`PhpStanGlobalInspection`, `PsalmGlobalInspection`). |
| `QualityToolAnnotator<T>` | `ExternalAnnotator<QualityToolAnnotatorInfo<T>, QualityToolMessageProcessor>` + `DumbAware`. ~1200 строк. Отвечает за весь pipeline. Подклассы переопределяют `getOptions(…)`, `createMessageProcessor(…)`, `getQualityToolType()`. |
| `QualityToolMessageProcessor` (abstract) | Парсит вывод процесса. Подклассы либо парсят построчно (`parseLine`), либо через `QualityToolXmlMessageProcessor` (SAX). |
| `QualityToolConfigurableForm<C>` | Swing-форма "Tool path / Timeout / Validate" + слот для `QualityToolCustomSettings`. |
| `QualityToolConfigurableList<C>` | Master/Details список конфигов для инструмента в Settings. |
| `QualityToolReformatFile` / `QualityToolExternalFormatter` | "Прогнать форматтер по файлу" + `formattingService`. |
| `QualityToolsComposerConfig<C, T>` | Автоопределение пути к бинарю из `vendor/bin` через `composer.json`. |
| `QualityToolProcessCreator` | Static-helpers `runToolProcess` / `createProcessHandler` / `getToolOutput`. **Создаёт исключительно `PhpCommandSettings`** через `PhpCommandSettingsBuilder` / `PhpExecutionUtil`. |

### 2.2. Внутренние/UI

| Класс | Назначение |
| --- | --- |
| `QualityToolAnnotatorInfo<T>` | DTO: `psiFile`, `inspection`, `profile`, `project`, `interpreterId`, `toolPath`, `maxMessagesPerFile`, `timeout`, `isOnTheFly`, `contextElement`. |
| `QualityToolMessage` (+ `Severity` enum: `INTERNAL_ERROR`, `ERROR`, `WARNING`) | Сообщение от тула. |
| `QualityToolXmlMessageProcessor` | SAX-обёртка над `parseLine` — собирает буфер, парсит, делегирует `XMLMessageHandler`. |
| `QualityToolProcessHandler` | `OSProcessHandler` со встраиванием в `QualityToolMessageProcessor`. |
| `QualityToolAnnotationAppender` | Маппит `QualityToolMessage` → `AnnotationBuilder` / `ProblemDescriptor`. |
| `QualityToolCommonConfigurable`, `QualityToolsIgnoreFilesConfigurable`, `QualityToolsOptionsPanel`, `QualityToolsExcludedFilesActionProvider`, `QualityToolsNotifier` | UI/Settings. |
| `PhpExternalFormatterCheckinHandler`, `QualityToolCodeStyleSettingsModifier` | VCS / Code Style интеграция. |

### 2.3. Per-tool опции (важно!)

Опции, специфичные для конкретного тула, хранятся **в отдельном
сервисе**, а не в `QualityToolConfiguration`. Например для PHPStan:

```java
@State(name="PhpStanOptionsConfiguration", storages=@Storage("php.xml"))
public class PhpStanOptionsConfiguration extends QualityToolsOptionsConfiguration
        implements PersistentStateComponent<PhpStanOptionsConfiguration> {
    private boolean fullProject = false;
    private String memoryLimit = "2G";
    private int level = 4;
    private String config = "";
    private String autoload = "";
    ...
}
```

`QualityToolsOptionsConfiguration` — это пустой маркерный base class. Никакой
общей модели/UI для опций SDK не предоставляет. Каждый тул пишет свою
панель (`PhpStanOptionsPanel`, `PhpCSFixerOptionsPanel`, …) на форме IntelliJ
UI Designer.

---

## 3. Жизненный цикл on-the-fly анализа

`QualityToolAnnotator#collectAnnotatorInfo` → `doAnnotate` → `apply` (от
`ExternalAnnotator`). Внутри `doAnnotate`:

1. Если PSI-файл в "AI Assistant snippet" — пропустить.
2. Только trusted project, только active project (см.
   `ProjectUtil.getActiveProject()`).
3. Если `runOnTempFiles() == true` (по умолчанию `true`):
   - Создать временный файл через `PhpSdkFileTransfer.createFile(...)` (если
     remote — передать туда через SFTP/Docker bind).
   - Сохранить путь в `QualityToolAnnotatorInfo.tempFile`.
4. Получить `options = getOptions(filePath, inspection, profile, project,
   isOnTheFly)` — список аргументов CLI.
5. Если on-the-fly: добавить substitution `tempFileName → originalFileName`,
   чтобы в сообщениях имя файла отображалось как у пользователя.
6. `workingDirectory = getWorkingDir(project, inspection)` — по умолчанию
   `project.basePath`, перезаписывается через `updateIfRemote`.
7. `QualityToolProcessCreator.runToolProcess(annotatorInfo, blackList,
   messageProcessor, workingDirectory, transfer, env, options)`:
   - Если interpreter remote — построить через `PhpCommandSettingsBuilder`,
     `manager.createPathMapper(...)`, отдать `getRemoteToolProcessHandler`.
   - Иначе — `GeneralCommandLine` через `interpreter.pathToPhpExecutable`,
     добавить script-path (`toolPath`) + args + env.
   - Если интерпретатор не задан/path — простой `cl.setExePath(path)` без
     PHP-обёртки.
   - Process timeout = `info.isOnTheFly() ? info.timeout : 15 минут`.
8. После завершения процесса временный файл удаляется через `transfer.delete`.
9. `messageProcessor.parseLine(line)` для каждой строки stdout (вызывается
   из `QualityToolProcessHandler`).
10. Если `messageProcessor.isFatalError` — annotator возвращает `null`.

Maximum параллельных тулов: `Executors.newFixedThreadPool(5, ...)` —
*глобально на IDE*.

---

## 4. Перечисленные проблемы текущего SDK

### 4.1. PHP-only по жёстким зависимостям

- `QualityToolType<C>` сам по себе ничего PHP-зависимого не содержит, но
  он подразумевает, что:
  - `QualityToolConfiguration.getInterpreterId()` возвращает id из
    `PhpInterpretersManagerImpl` (`com.jetbrains.php.config.interpreters.*`);
  - `QualityToolProjectConfiguration#findConfigurationByInterpreter` зовёт
    `PhpInterpretersManagerImpl.getInstance(project).findInterpreterById`;
  - `QualityToolConfigurationProvider#fillDefaultSettings` принимает
    `PhpSdkAdditionalData` и зовёт `PhpRemoteInterpreterManager`;
  - `QualityToolConfigurationManager#onInterpretersUpdate` — про PHP
    интерпретаторы.
- `QualityToolValidationInspection#getGroupPath()` хардкодит
  `PhpInspection.GROUP_PATH_GENERAL`.
- `QualityToolAnnotator#isFileSuitable` фильтрует `PhpFile` и
  `PhpLanguage.INSTANCE`.
- `QualityToolProcessCreator` строит команду через `PhpCommandSettings`,
  `PhpCommandLinePathProcessor`, `PhpExecutionUtil`, `PhpRemoteInterpreterManager`,
  XDebug-disable через `XdebugUtil.getXDebugVersion`.
- `QualityToolMessageProcessor` берёт `Registry.intValue("php.quality.tools.messages.limit")`.
- `QualityToolsComposerConfig` целиком построен поверх
  `com.jetbrains.php.composer.*`.

→ Использовать SDK для не-PHP инструмента возможно только через хаки и
ценой `<depends>com.jetbrains.php</depends>` (наш `MagoQualityToolType` —
живой пример).

### 4.2. Один интерпретатор / один источник тула на конфигурацию

В модели `QualityToolConfiguration`:

- Поля только `toolPath`, `interpreterId`, `timeout`. `interpreterId`
  жёстко означает "id из PhpInterpretersManagerImpl".
- Откуда тул реально берётся — **не моделируется**. Возможные источники
  в реальной разработке:
  1. локальный бинарь (`/usr/local/bin/phpstan`),
  2. `vendor/bin/<tool>` после `composer install`,
  3. `tools/phive`,
  4. `mise/asdf` shim,
  5. PHAR в `bin/`,
  6. Docker-контейнер (`docker compose exec`),
  7. Devcontainer / dev shell (`devbox`, `nix`, `flake.nix`),
  8. WSL-путь,
  9. Pre-built CI образ.
- Сейчас можно создать несколько конфигов через
  `QualityToolConfigurationProvider#createNewInstance`, но "создать новый"
  ВСЕГДА означает "выбрать PHP interpreter из списка PHP interpreters" —
  см. `getOrCreateConfigurationByInterpreter`.
- Для каждого `QualityToolType` может существовать **только один**
  `QualityToolConfigurationProvider` (см. `getInstances()` → "Several
  providers… was found"). Это значит, что плагин-расширение, желающий
  добавить новый "источник интерпретатора" (`mise`, `phive`, etc.), вынужден
  заменять провайдера целиком, а не дополнять список.

### 4.3. Опции = глобальный singleton, не часть конфига

- `PhpStanOptionsConfiguration`, `PhpCSFixerOptionsConfiguration`,
  `MessDetectorOptionsConfiguration` и т. д. — **project-level singleton**
  на инструмент. Один level/config/autoload — на весь проект.
- → нельзя:
  - иметь разные настройки для `src/` и `tests/`;
  - иметь несколько профилей (CI vs локально, "strict" vs "loose");
  - привязать профиль опций к конкретному `QualityToolConfiguration`.
- Опции хранятся бок-о-бок с инспекцией в дублирующем виде: одни и те же
  поля живут в `PhpStanGlobalInspection` (для batch-mode inspection settings)
  и в `PhpStanOptionsConfiguration` (для on-the-fly). Между ними — ручная
  синхронизация (см. `PhpStanSettingsTransferStartupActivity`).
- Никакого расширяемого UI для опций: каждый тул — собственная Swing
  форма от руки. Нет declarative-описания "у тула есть такие-то параметры,
  такого-то типа, с таким-то UI".

### 4.4. Один на проект "selectedConfigurationId"

- `QualityToolProjectConfiguration#mySelectedConfigurationId` — одно
  значение на проект. Multi-root проекты, monorepo (фронт+бэк), несколько
  PHP-приложений с разными версиями тула — нельзя нормально настроить.

### 4.5. UI/Settings

- `QualityToolConfigurableForm` — Swing UI Designer форма, fields зашиты:
  `myToolPathField`, `myTimeoutSpinner`, `myValidateButton`. Кастомизация
  только через `QualityToolCustomSettings` (один опциональный слот). →
  Hard to extend, hard to test, не Compose / Kotlin DSL.
- `QualityToolConfigurableList` использует deprecated-стиль
  `MasterDetailsComponent`.

### 4.6. Process execution

- `QualityToolProcessCreator.QUALITY_TOOL_EXECUTOR =
  Executors.newFixedThreadPool(5, …)` — фиксированный пул на 5 потоков на
  всю IDE. Tooling-heavy проекты (формат + лайт + ст. анализ + Mago + …)
  легко в него упираются.
- Никакого batching / debounce / shared run для родственных тулов.
- Никакой "только текущий файл vs. весь проект vs. изменённые vs. через
  --baseline" — все тулы это решают руками в `getOptions`.
- `XDebug` отключение / env переменные / cwd / `path mapping` смешаны в
  одном `runToolProcess`.
- Нет hooks: `before-tool-start`, `after-tool-finish`, `on-tool-error` —
  все плагины реализуют notifications руками.

### 4.7. Сообщения / парсинг

- `parseLine(String)` — single-string API. Тулы, которые выводят JSON
  (`mago --reporting-format=json`, `phpstan --error-format=json`),
  вынуждены либо буферизовать всё в `StringBuilder` (как
  `QualityToolXmlMessageProcessor`), либо переопределять `parseLine`
  и склеивать. Никакой готовой "поток JSON-объектов" / SARIF-парсилки.
- `QualityToolMessage.Severity` фиксирован: `INTERNAL_ERROR / ERROR /
  WARNING`. Нет `HINT`, `INFO`, `DEPRECATED`, `STYLE`. `WEAK_WARNING` —
  только через override в `severityToDisplayLevel`.
- Нет model для quick-fix-actions, которые приходят *от тула* (`mago lint
  --fix` / phpstan baseline ignore): надо городить руками
  `IntentionAction[]` под каждый случай.
- `getMessagePrefix()` склеивается с `messageText` через `": "` — нет
  способа сделать богатое сообщение с заголовком / категорией / линком на
  доку.

### 4.8. Ignore / suppression

- `QualityToolBlackList` — просто список абсолютных путей. Никаких
  glob / regex / annotations-based ignore (`// @phpstan-ignore-next-line`
  обрабатывается в самом тула, SDK его не знает).
- Suppression в profile-based inspections SDK не моделирует. Mago сделал
  это руками (`MagoIgnoreAction`).

### 4.9. Reporting / Inspection results

- `QualityToolValidationGlobalInspection`/`QualityToolValidationInspection`
  парные, дублируют логику ("global" перевызывает annotator). На
  on-the-fly LocalInspection возвращает пустоту. Это всегда сбивало:
  включаешь inspection → ничего не подсвечивается, пока не запустишь
  Code → Inspect Code.

### 4.10. Тестируемость

- `QualityToolAnnotator#testTool` — захардкоженный механизм с поиском
  файла `<name>.txt` рядом с тестовым файлом. Не годится для unit-тестов
  message processor-а без файлов вообще.
- Нет API "запустить тул на тестовых данных без процесса" / "впрыснуть
  fake-output".

### 4.11. Реализация

- Большая часть SDK — Java с CFR-friendly стилем (множество
  `$$$reportNull$$$0`, `IllegalArgumentException("Argument for @NotNull
  parameter…")`). Это автогенерация ByteCode-инструментатором,
  но code-base явно Java, не Kotlin. → Sealed types, data classes, DSL —
  всё руками.
- Никакой Kotlin DSL для регистрации тула.

### 4.12. Невидимая публика

- SDK живёт в `com.jetbrains.php` плагине, версионируется вместе с PHP
  поддержкой, контракты приватны "по сути" (нет публичной документации
  для авторов плагинов), любое изменение API в новой версии PHP плагина
  ломает всех внешних потребителей (нас — в первую очередь).

---

## 5. Что нам, как Mago, в SDK не хватает уже сейчас

Просмотр кода `mago-plugin` подтверждает все болевые точки:

- `MagoConfiguration` хранит `customParameters` как plain string —
  единственный "ручка", потому что SDK не даёт способа описать структурный
  options-bean без отдельного `@State`.
- `MagoProjectConfiguration` приходится тащить кучу полей `enabled,
  guardEnabled, linterEnabled, formatterEnabled, analyzeAdditionalParameters,
  lintAdditionalParameters, guardAdditionalParameters, formatAdditionalParameters,
  formatAfterFix, configurationFile, workspaceMappings, debug` — потому что
  у SDK нет понятия "несколько режимов одного тула" (`mago analyze` vs
  `mago lint` vs `mago format` — это де-факто 3 разных профиля одного
  бинаря).
- `MagoAnnotatorProxy` — пустышка для совместимости (`parseLine {}`,
  `done {}`, `getOptions = emptyList`), весь реальный pipeline
  переписан в `MagoExternalAnnotator.kt` (440 строк). То есть SDK
  использовать целиком не получилось — пришлось писать свой annotator
  поверх `ExternalAnnotator`.
- `MagoConfigurationProvider` — наш собственный extension point, потому
  что встроенный `QualityToolConfigurationProvider` — синглтон-only и
  завязан на `PhpInterpreter`.
- `MagoCustomOptionsForm` — заглушка на 30 строк, потому что
  `QualityToolCustomSettings` слот в `QualityToolConfigurableForm` слишком
  узкий для всего, что у нас есть (`MagoConfigurable.kt` — 374 строки
  на руками собранную форму).
- Tool path / interpreter / источник в Mago — приходится решать в
  `MagoRemoteConfigurationProvider`, `LocalMagoRunner`,
  `RemoteInterpreterMagoRunner`, `MagoComposerAutoDetectActivity`, разбросано.

---

## 6. Цели нового SDK

1. **Language-agnostic**. Базовые типы не должны зависеть от PHP. PHP-обёртка
   — отдельный модуль (`-php`), который обогащает SDK поддержкой
   `PhpInterpreter`, composer, xdebug-off и т. п.
2. **Несколько источников тула**. `ToolBinary` как sum-type:
   `Local | ComposerVendor | Mise | Phive | Docker | RemoteSsh | Wsl | Custom(ExternalProvider)`.
   Свой EP для регистрации новых источников. Несколько провайдеров
   допустимы одновременно. UI кнопки `+` показывает выбор источника.
3. **Расширяемые опции**. Декларативное описание опций тула
   (`OptionSpec`), Kotlin DSL для UI, авто-биндинг к `PersistentStateComponent`.
   Per-scope опции (project / module / source-root / glob), per-mode
   опции (`analyze`/`lint`/`format`).
4. **Несколько профилей конфигурации в проекте**. `ToolProfile` =
   `binary + options + scope`. У проекта — упорядоченный список профилей
   с правилами активации (по path glob / by VCS branch / by VCS dirty).
5. **Лучше pipeline**. Process pool настраиваемый, debounce/coalesce
   запусков на тот же файл, общий кэш результатов между запусками,
   hooks `before/after/onError`.
6. **JSON/SARIF first**. `OutputReader` поверх Flow<JsonElement> /
   `SarifReader`, никакого SAX как default. `parseLine` — legacy fallback.
7. **Сильная модель сообщений**. `Severity` расширен до набора, который
   мапится в `HighlightDisplayLevel`. `QuickFix` приходит как описанный
   из тула (range + replacement) — SDK конвертирует в `IntentionAction`.
8. **Кросс-инструментальная ignore-модель**. Glob ignore + знание
   PSI-аннотаций / комментариев (`@phpstan-ignore`, `// mago-disable`).
9. **Settings UI на Kotlin DSL**. `panel { … }` + биндинги. Никаких
   `*.form` файлов от SDK; плагины-консьюмеры свободны делать своё.
10. **Тестируемость**. `FakeToolRunner` / `recordedRun(stdout, stderr, exit)`
    в `-testing` модуле. Тесты без процессов.
11. **Открытое API + versioning**. Опубликованные стабильные интерфейсы
    в `j-plugins/quality-tools-sdk`, semver, релизы через Marketplace.
12. **Kotlin first**. Sealed types, value classes, Flow, coroutines для
    запуска и cancellation. `suspend fun run(...)` вместо ручного
    `ExecutorService.submit(...).get(timeout, MILLISECONDS)`.

---

## 7. Эскиз API нового SDK

Модули:

- `quality-tools-sdk-core` — language-agnostic;
- `quality-tools-sdk-php` — PHP биндинги (composer, php-interpreter,
  xdebug);
- `quality-tools-sdk-ui` — Kotlin DSL, Settings UI;
- `quality-tools-sdk-testing` — fakes для тестов;
- `quality-tools-sdk-sarif` — парсер SARIF.

### 7.1. Тул

```kotlin
interface QualityTool {
    val id: String                  // "phpstan", "mago", …
    val displayName: @Nls String
    val supportedLanguages: Set<Language>
    val capabilities: Set<Capability> // LINT, FIX, FORMAT, ANALYZE, …
    val resultReader: ResultReader
    fun buildCommand(ctx: ToolContext, request: ToolRequest): ToolCommand
}

enum class Capability { LINT, ANALYZE, FORMAT, FIX, BASELINE, INSPECT }
```

### 7.2. Источник бинаря

```kotlin
sealed interface ToolBinarySource {
    val displayName: @Nls String
    suspend fun resolve(project: Project, module: Module?): ResolvedBinary?

    data class Local(val path: Path) : ToolBinarySource
    data class ComposerVendor(val packageId: String, val script: String) : ToolBinarySource
    data class Mise(val tool: String, val version: String?) : ToolBinarySource
    data class Phive(val phar: String) : ToolBinarySource
    data class Docker(val service: String, val cmd: List<String>) : ToolBinarySource
    data class RemoteSsh(val sdkId: String, val path: Path) : ToolBinarySource
    data class Wsl(val distro: String, val path: Path) : ToolBinarySource
    data class Custom(val providerId: String, val payload: Map<String, String>) : ToolBinarySource
}

data class ResolvedBinary(
    val command: List<String>,
    val workingDir: Path?,
    val env: Map<String, String>,
    val transfer: FileTransfer?,
)
```

EP: `qualityTools.binarySourceProvider` — позволяет внешним плагинам
зарегистрировать новый тип источника (mise, asdf, devbox, ddev, lando, …)
без правок SDK.

### 7.3. Опции тула

```kotlin
sealed interface OptionSpec<T> {
    val key: String
    val default: T
    val ui: OptionUi
}

object IntSpec : OptionSpec<Int>      // and FloatSpec, BoolSpec, EnumSpec, PathSpec, StringListSpec, …

interface ToolOptionsSchema {
    val tool: QualityTool
    val options: List<OptionSpec<*>>
    val modes: List<ToolMode> = emptyList()  // analyze / lint / format ...
}

interface OptionsStore {
    operator fun <T> get(spec: OptionSpec<T>): T
    fun <T> set(spec: OptionSpec<T>, value: T)
    fun snapshot(): Map<String, Any?>
}
```

Опции — declarative; UI генерируется автоматически в Settings, либо
override через `panel { … }`.

### 7.4. Профиль конфигурации

```kotlin
data class ToolProfile(
    val id: String,
    val displayName: String,
    val binary: ToolBinarySource,
    val options: OptionsStore,
    val scope: ProfileScope,
    val mode: ToolMode? = null,
)

sealed interface ProfileScope {
    data object EntireProject : ProfileScope
    data class SourceRoots(val roots: List<VirtualFile>) : ProfileScope
    data class Glob(val patterns: List<String>) : ProfileScope
    data class Module(val moduleId: String) : ProfileScope
}
```

Project-level state — список профилей + дефолтный.

### 7.5. Pipeline

```kotlin
interface ToolRunner {
    suspend fun run(request: ToolRequest): ToolRun
}

data class ToolRequest(
    val profile: ToolProfile,
    val target: ToolTarget,           // single file | files | project | range
    val trigger: Trigger,              // ONE_OF: onTheFly, batch, manual, save
    val timeoutMs: Long? = null,
)

data class ToolRun(
    val exitCode: Int,
    val messages: List<ToolMessage>,
    val rawStdout: Path?,
    val rawStderr: Path?,
    val durationMs: Long,
)
```

`ToolRunner` — это `coroutine`-friendly реализация поверх common
`ProcessHandler`. Cancellation работает через `coroutineContext.cancel()`,
а не `Future.get(timeout)`.

Под капотом — общий пул, лимит конкурентности per-tool/per-project, ETag-
кэш результатов по `(profile, target hash, file content hash)`.

### 7.6. Сообщения и quick-fixes

```kotlin
data class ToolMessage(
    val severity: Severity,
    val range: SourceRange,        // file + line/col-range
    val title: @Nls String,
    val description: @Nls String?,
    val category: String?,          // "phpstan.level5", "mago.unused-variable"
    val documentationUrl: String?,
    val fixes: List<ToolFix> = emptyList(),
)

sealed interface ToolFix {
    val title: @Nls String
    data class Replace(val range: SourceRange, val newText: String) : ToolFix
    data class Patch(val unifiedDiff: String) : ToolFix
    data class Cli(val args: List<String>, val applies: Filter) : ToolFix      // mago lint --fix
    data class Ignore(val scope: IgnoreScope, val rule: String?) : ToolFix
}

enum class Severity { HINT, INFO, WEAK_WARNING, WARNING, ERROR, INTERNAL_ERROR }
```

`ToolFix.Replace` / `Patch` мапятся в `LocalQuickFix` автоматически (без
плагинного кода). `ToolFix.Cli` — реализация в SDK уже есть (CS Fixer
"reformat file").

### 7.7. Reader-ы

```kotlin
interface ResultReader {
    fun read(run: RawProcessOutput, ctx: ToolContext): Flow<ToolMessage>
}

object SarifReader : ResultReader  // generic — все, кто умеют SARIF
object CheckstyleXmlReader : ResultReader
object JsonLinesReader : ResultReader
class CustomLineReader(val parse: (String) -> ToolMessage?) : ResultReader
```

### 7.8. Регистрация

`plugin.xml`:

```xml
<extensions defaultExtensionNs="dev.qualityTools">
    <tool implementation="com.github.xepozz.mago.MagoTool"/>
    <binarySourceProvider implementation="com.github.xepozz.mago.MagoBinarySources"/>
    <optionsSchema implementation="com.github.xepozz.mago.MagoOptionsSchema"/>
</extensions>
```

И DSL-альтернатива для тестов / простых интеграций:

```kotlin
qualityTool("mago") {
    displayName = "Mago"
    languages(PhpLanguage)
    capabilities(LINT, ANALYZE, FORMAT, FIX)
    binary {
        local()
        composer("xepozz/mago", script = "mago")
    }
    options {
        bool("debug")
        string("configurationFile")
        enum("reportingFormat", values = ["rich", "json", "github"])
        list("workspaceMappings") { string("from"); string("to") }
    }
    runner = JsonLinesRunner(::parseMagoJsonLine)
}
```

### 7.9. Тесты

```kotlin
class MagoLintingTest : QualityToolTest() {
    override val tool = MagoTool()

    @Test
    fun `unused variable triggers WEAK_WARNING`() = runQualityTool {
        givenFile("src/Foo.php", """<?php class Foo { function bar() { ${'$'}x = 1; } }""")
        givenRecordedRun(stdout = json("..."), exitCode = 0)
        assertMessages {
            single {
                severity == Severity.WEAK_WARNING
                category == "mago.unused-local-variable"
                range.line == 1
            }
        }
    }
}
```

---

## 8. Поэтапный план миграции

Я думаю не пытаться сделать "drop-in replacement" в один присест: это
1500+ строк JetBrains-кода и десяток attached концепций (FUS, checkin,
codeStyleSettings, …). Реалистичный путь:

**Этап 0. Документ + RFC.** Этот файл + публичная страница в GitHub
Discussion / Marketplace blog для сбора фидбека от авторов других PHP-
плагинов.

**Этап 1. Извлечь `quality-tools-sdk-core` как отдельный gradle-модуль
прямо внутри `mago-plugin`.** Сначала — чисто как `internal` API,
доступный только нам. Перенести туда:
- модель `ToolBinarySource` (без EP, hard-coded типы);
- модель `ToolMessage`, `Severity`, `ToolFix`;
- `ToolRunner` + `ProcessRunner` (coroutine-based);
- `JsonLinesReader` для Mago.

**Этап 2. Переписать Mago поверх нового SDK** (стенд-тест): убрать
`MagoAnnotatorProxy` / `MagoQualityToolType` зависимость от
`com.jetbrains.php.tools.quality.*`. Сохранить `QualityToolType` только
как тонкую "обёртку" для регистрации в PHP-плагине (чтобы кнопка
"Mago" продолжала жить в Settings/PHP/Quality Tools и users-ы без
переучения).

**Этап 3. Выделить SDK в отдельный gradle-проект** в `j-plugins`-org,
опубликовать как dependency для других плагинов.

**Этап 4.** Написать "адаптер обратной совместимости": shim, реализующий
`QualityToolType<C>` поверх нового `QualityTool` — чтобы Mago/чужие
плагины могли постепенно мигрировать.

**Этап 5.** Подружить с не-PHP языками: пилотные интеграции, например
biome / oxlint для JS/TS (там тоже всё через ExternalAnnotator делается
руками каждый раз).

---

## 9. Открытые вопросы

1. **Где хранить SDK как зависимость?** Marketplace plugin (тогда
   `<depends>` в `plugin.xml`) или fat-jar внутри каждого плагина-
   консьюмера? Первое — меньше дубликатов, но добавляет точку отказа
   и `<depends>`-каскад.
2. **Как взаимодействовать с уже встроенными `QualityToolType`-ами**
   PHPStan/Psalm/PHP_CS? Идеально — оставить их жить как есть, новый
   SDK работает рядом. Plan B — постепенно подать PR в JetBrains.
3. **Кросс-плагинная ignore-модель** (`// noinspection` от платформы
   плюс `// @phpstan-ignore` от тула плюс `// mago-disable` от нас) —
   единый или per-tool API?
4. **VCS / pre-commit hooks**. JetBrains-овский SDK имеет
   `PhpExternalFormatterCheckinHandler` — повторить или
   полагаться на external (pre-commit, lefthook)?
5. **FUS / телеметрия** — обязательная часть SDK или opt-in для каждого
   плагина-консьюмера?
6. **Лицензия и совместимость с EAP/Stable.** Если использовать
   internal API IntelliJ-платформы (`MasterDetailsComponent`,
   `PhpNamedCloneableItemsListEditor`, …) — на каждый major-release
   придётся подгонять. Лучше использовать только `OpenAPI`.

---

## 10. Что я уже посмотрел (для аудиторского следа)

Декомпиляция выполнена локально:

```
PHP plugin:        plugins.jetbrains.com/files/6610/1029472/php-impl-251.29188.37.zip
PHPStan plugin:    plugins.jetbrains.com/files/15184/883992/phpstan-251.29188.11.zip
Psalm plugin:      plugins.jetbrains.com/files/15183/884017/psalm-251.29188.11.zip
Decompiler:        CFR 0.152
```

Полностью прочитанные SDK-классы (декомпилированные):

- `QualityToolType`, `QualityToolConfiguration`,
  `QualityToolConfigurationProvider`, `QualityToolConfigurationManager`,
  `QualityToolConfigurationBaseManager`, `QualityToolProjectConfiguration`,
  `QualityToolAnnotator` (1207 строк),
  `QualityToolMessageProcessor` (388),
  `QualityToolXmlMessageProcessor`, `QualityToolAnnotatorInfo`,
  `QualityToolBlackList`, `QualityToolMessage`,
  `QualityToolProcessCreator`, `QualityToolReformatFile`,
  `QualityToolValidationInspection`, `QualityToolsComposerConfig`,
  `QualityToolConfigurableForm`, `QualityToolConfigurableList`.

Bundled tool-ы как образцы реализаций:

- `phpstan/PhpStanQualityToolType`, `PhpStanConfiguration`,
  `PhpStanOptionsConfiguration`, `PhpStanAnnotatorProxy`,
  `PhpStanGlobalInspection`, `PhpStanConfigurationManager`,
  `PhpStanConfigurationProvider`.
- `phpcs/PhpCSQualityToolType`, `PhpCSConfiguration`,
  `PhpCSConfigurableForm`.
- `phpCSFixer/PhpCSFixerQualityToolType`, `PhpCSFixerOptionsPanel`.

Наша текущая обёртка над SDK:

- `MagoQualityToolType.kt`, `MagoConfiguration.kt`,
  `MagoConfigurationManager.kt`, `MagoProjectConfiguration.kt`,
  `MagoAnnotatorProxy.kt`, `MagoCustomOptionsForm.kt`,
  `MagoExternalAnnotator.kt`, `MagoConfigurable.kt`.
