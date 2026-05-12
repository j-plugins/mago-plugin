# Phase 09 — Testing Module

## Goal

`:testing` ships fakes and fixtures plugin authors use to test their
integration without spinning up real processes or the IntelliJ
platform.

## Feature

Mago test suite runs in seconds, not minutes. New tool authors write
unit tests for `buildArgs`, `ConfigSource.resolve`, and `ResultReader`
in pure Kotlin.

## Solution

`:testing` depends on `:core` only. JUnit 4 (matches the host plugin's
test framework).

```kotlin
public class RecordedProcessSpawner(
    private val responses: List<RecordedResponse>,
) : ProcessSpawner {
    public data class RecordedResponse(
        val matchCommand: (List<String>) -> Boolean,
        val stdout: String = "",
        val stderr: String = "",
        val exitCode: Int = 0,
        val durationMs: Long = 10,
        val stdinAssertion: ((ByteArray?) -> Unit)? = null,
    )
    override suspend fun spawn(request: SpawnRequest): SpawnedProcess
}

public class MapOptionsBag(initial: Map<String, String> = emptyMap()) : OptionsBag
public class InMemoryQualityToolsProjectStorage : QualityToolsProjectStorage
public class FakeIoFileReader(...) : IoFileReader
public class FakePathMapper(...) : PathMapper
public class FakeMatchContext(...) : MatchContext

public abstract class QualityToolTestCase {
    protected val storage: InMemoryQualityToolsProjectStorage
    protected fun givenProfile(tool: QualityTool, build: ProfileBuilder.() -> Unit): ConfigProfile
    protected fun givenRecordedRun(stdout: String, exitCode: Int, vararg args: String)
    protected fun runAnnotator(file: FakeFile): List<ToolMessage>
    protected fun assertMessages(block: MessagesAssertionsContext.() -> Unit)
}
```

DSL helpers:

```kotlin
public fun toolProfile(tool: QualityTool, build: ProfileBuilder.() -> Unit): ConfigProfile

public class ProfileBuilder {
    public var source: ConfigSource = LocalBinarySource(instanceId = "test", path = "/fake")
    public var scope: ConfigScope = EntireProjectScope
    public fun mode(id: String, build: ModeSettingsBuilder.() -> Unit)
}
```

A second tier `:testing-ide` (NOT in this phase — phase 11 if needed)
would provide `BasePlatformTestCase`-based fixtures for end-to-end
runs with real PSI. Out of scope for now.

## Deliverables

`:testing/dev/jplugins/qualitytools/testing/`:

- Fakes:
  - `RecordedProcessSpawner.kt`
  - `MapOptionsBag.kt`
  - `InMemoryQualityToolsProjectStorage.kt`
  - `FakeIoFileReader.kt`
  - `FakePathMapper.kt`
  - `FakeMatchContext.kt`
  - `FakeAvailabilityContext.kt` (with `flipAvailability()`)
  - `FakeDisposable.kt`
  - `FakeIgnoreCommentRenderer.kt`
  - `RecordingPostFixHook.kt`
- Tokens & logging:
  - `ManualCancellationToken.kt`
  - `CountingCancellationToken.kt`
  - `RecordingQtLogger.kt` (with `assertLoggedOnce(level, contains)`)
- Registry in-memory impls (one per registry in README):
  - `InMemoryToolRegistry.kt`
  - `InMemoryConfigSourceRegistry.kt`
  - `InMemoryConfigScopeRegistry.kt`
  - `InMemoryResultReaderRegistry.kt`
  - `InMemoryIgnorePolicyRegistry.kt`
  - `InMemoryIgnoreCommentRendererRegistry.kt`
  - `InMemoryMessageEnricherRegistry.kt`
  - `InMemoryEnvMutatorRegistry.kt`
  - `InMemoryProcessSpawnerSelector.kt`
  - `InMemoryProcessPoolPolicyRegistry.kt`
  - `InMemoryFixHandlerRegistry.kt`
  - `InMemoryPostFixHookRegistry.kt`
  - `InMemoryToolRunListenerRegistry.kt`
  - `InMemoryPathMapperContributorRegistry.kt`
  - `InMemoryInternalErrorActionProviderRegistry.kt`
- DSL:
  - `QualityToolTestCase.kt`
  - `ProfileBuilder.kt`
  - `MessagesAssertionsContext.kt` (`hasMessage(...)`, `hasFix<T>()`,
    `containsExactly { ... }`)
- Golden fixtures:
  - `GoldenFixtures.kt` (load/save with `-Dgolden.update=true`)
  - `assertMessagesEqualGolden(actual, fixturePath)` helper
- Threading:
  - `ThreadingPolicyChecker.kt` (asserts current thread matches the
    `@ThreadingPolicy` annotation; eliminates Swing in unit tests)
  - `ImmediateEdtDispatcher.kt`

Companion sibling artifact `:testing-junit5` (separate gradle
module, depends on `:testing`):

- `:testing-junit5/.../QualityToolJupiterExtension.kt` — JUnit 5
  extension that wraps the same fakes.
- Convention factory `qualityToolTestEnv()` returns a struct that
  works from JUnit 4, JUnit 5, or Kotest without inheritance.

`:testing` self-tests:

- `RecordedProcessSpawnerTest.kt`
- `QualityToolTestCaseSelfTest.kt`

## Acceptance criteria

- [ ] Every registry interface in `:core` has an `InMemory*` impl
      in `:testing` (compile-time reflective check).
- [ ] `ManualCancellationToken.cancel()` from another thread causes
      any registered `ToolRunner` to return `canceled = true`
      within 500 ms — verified in a unit test without a real
      process.
- [ ] `RecordingQtLogger` self-test asserts one-shot dedup behaviour
      (same key logged only once).
- [ ] `assertMessagesEqualGolden` round-trips under
      `-Dgolden.update=true` regen.
- [ ] `ThreadingPolicyChecker` enforces `@ThreadingPolicy` annotation
      in test scope without spinning Swing.
- [ ] `:testing-junit5` sample compiles in its own self-test and
      uses no JUnit 4 imports.
- [ ] `:testing` has no IntelliJ-platform dependency.
- [ ] Documentation in module README explains 3 typical patterns:
      "test buildArgs", "test resolve()", "test reader against
      golden file".
- [ ] `RecordedProcessSpawner` raises a clear test failure when an
      unexpected command is spawned (no silent default).
- [ ] `stdinAssertion` is invoked only when stdin is actually written.
- [ ] `InMemoryQualityToolsProjectStorage` is concurrency-safe enough
      for parallel test execution.
- [ ] `QualityToolTestCase` is JUnit-version-agnostic enough to use
      from JUnit 4 (default) or JUnit 5 (opt-in subclass).
- [ ] Adding a new spec kind in phase 04 didn't require touching
      `:testing` (verified by recompiling after a synthetic
      `BigDecimalSpec` is added).

## Out of scope

- `BasePlatformTestCase` fixtures (separate later phase).
- Performance benchmarks.

## Depends on

`phase-06`. (Phase 10 depends on this phase for migration testing.)
