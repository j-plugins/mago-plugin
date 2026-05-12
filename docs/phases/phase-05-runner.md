# Phase 05 — Runner Pipeline

## Goal

`ToolRunner` knows how to take a `ConfigProfile`, a `ToolMode`, a
`ToolTarget`, and produce a streamed sequence of process events.
Path-aware args are rewritten with `PathMapper`. `EnvMutator`s
(XDebug-off, mise shims) plug in via EP. Stdin is first-class.

## Feature

Plugin authors stop writing process-spawning code. Adding XDebug-off
becomes one class. Docker source's path mapping is honored without the
tool knowing.

## Solution

```kotlin
public interface ToolRunner {
    public suspend fun run(request: ToolRunRequest, ctx: ToolRunContext): ToolRun
}

public interface ToolRunRequest {
    public val profile: ConfigProfile
    public val mode: ToolMode
    public val target: ToolTarget
    public val timeoutMs: Long
    public val stdin: ByteArray?
    public val disableStdinFallback: Boolean
        get() = false
    /** Default 64 MiB. ToolRun.exitCode = -3 when exceeded. */
    public val maxStdoutBytes: Long
        get() = 64L * 1024 * 1024
}

public interface ToolRun {
    public val exitCode: Int
    public val stdout: String
    public val stderr: String
    public val durationMs: Long
    public val command: List<String>
    public val canceled: Boolean
}

public interface EnvMutator {
    public val id: String
    public val order: Int
        get() = 0
    public fun appliesTo(ctx: ToolRunContext): Boolean
    public fun mutate(env: MutableMap<String, String>, ctx: ToolRunContext)
}

public interface EnvMutatorRegistry {
    public fun applicable(ctx: ToolRunContext): List<EnvMutator>
}

public interface ProcessSpawnerSelector {
    public fun selectFor(resolved: ResolvedBinary, ctx: ToolRunContext): ProcessSpawner
}

/**
 * Translates ToolRunner failures into ToolMessage stream so downstream
 * code (annotator, notifier) does not need to distinguish failure
 * modes by exit code. Owner of the phase-05 <-> phase-06 seam.
 */
public interface RunnerToMessageBridge {
    public fun fromFailedRun(run: ToolRun, ctx: ToolRunContext): ToolMessage?
}
```

Cancellation flows ONLY through `ctx.cancellation`. `ProcessToolRunner`
calls `ctx.cancellation.throwIfCanceled()` between mutator steps and
after each `await()` poll. Coroutine `Job` cancellation is a side
effect, not the contract.

Concrete `ProcessToolRunner` in `:core`:

- Rejects if `request.mode.id !in request.profile.toolId-tool.modes` (precondition).
- Resolves `profile.source.resolve(ctx.toResolveContext())` to
  `ResolvedBinary`.
- If `resolve` returns `null`: returns `ToolRun(exitCode = -1,
  canceled = false, stderr = "<typeId> unavailable")`. The
  `RunnerToMessageBridge` later translates this into a
  `ToolMessage(severityLevel = "internal_error",
  category = "${typeId}.unavailable")`. **The runner itself never
  throws or emits `ToolMessage` — separation of concerns.**
- Timeout: returns `ToolRun(exitCode = -2, canceled = false)`;
  before returning, invokes `SpawnedProcess.cancel()` to destroy
  the process; bridge emits `category = "${toolId}.timeout"`.
- Cancellation observed via `ctx.cancellation`: returns
  `ToolRun(canceled = true)`; bridge emits
  `category = "${toolId}.cancelled"` (severityLevel `info`, not error).
- `IOException` from `spawn()` (binary deleted between resolve and
  spawn, missing permissions): returns
  `ToolRun(exitCode = -1, stderr = e.message)`; bridge emits
  `<typeId>.unavailable`.
- stdout exceeds `request.maxStdoutBytes`: process is destroyed,
  returns `ToolRun(exitCode = -3, canceled = false)`; bridge emits
  `<toolId>.output_too_large`.
- The whole pipeline after `spawn()` is wrapped in
  `try { ... } finally { spawned?.cancel() }` so any post-spawn
  failure destroys the process.
- `writeStdin` runs on a dedicated `Dispatchers.IO` coroutine that
  closes the stream and swallows `IOException("broken pipe")`;
  `await()` proceeds independently and is never blocked by stdin.
- `CancellationToken.onCancel { spawnedProcess.cancel() }` is wired
  so cancellation always destroys the process within 500 ms.
- The runner's coroutine scope is bound to a `Disposable` provided
  by the caller (annotator → project lifetime); on disposal, the
  scope is cancelled and all live spawners destroyed.
- Builds final `command = resolvedBinary.command + buildArgs(...)
  .map { mapIfPath(it, resolvedBinary.pathMapper) }` via
  `PathAwareArgRewriter(pathMapper)`.
- Selects spawner via injected `ProcessSpawnerSelector` — defaults
  to `JvmProcessSpawner`; `:php` registers `IntellijProcessSpawner`
  with higher priority when `resolved.pathMapper` is non-identity
  or `command[0] == "docker"`.
- For `ToolArg` with `pathPrefix != null`: rewrites only after the
  prefix; for plain path args: rewrites the whole value.
- Applies `EnvMutator`s in `order` priority.
- Spawns process via `ProcessSpawner` (interface; `:core` ships
  `JvmProcessSpawner` using `ProcessBuilder`).
- Stdin: writes bytes to process input stream, closes it.
- Cancellation: respects `coroutineContext.cancellation` →
  `process.destroyForcibly()`.

`ProcessSpawner` interface keeps the `:core` test friendly (no need
for real OS process; the `:testing` module provides
`RecordedProcessSpawner`).

```kotlin
public interface ProcessSpawner {
    public suspend fun spawn(request: SpawnRequest): SpawnedProcess
}

public interface SpawnedProcess {
    public val pid: Long?
    public suspend fun await(): ProcessResult
    public suspend fun cancel()
    public suspend fun writeStdin(bytes: ByteArray)
}
```

`ProcessPool` for limited concurrency (default 5, configurable via
storage):

```kotlin
public interface ProcessPool {
    public suspend fun <T> withPermit(toolId: String, block: suspend () -> T): T
}
```

## Deliverables

`:core/runner/`:

- `ToolRunner.kt`, `ToolRunRequest.kt`, `ToolRun.kt`
- `EnvMutator.kt`, `EnvMutatorRegistry.kt`
- `ProcessSpawner.kt`, `SpawnedProcess.kt`, `SpawnRequest.kt`,
  `ProcessResult.kt`, `ProcessSpawnerSelector.kt`
- `ProcessToolRunner.kt`
- `JvmProcessSpawner.kt`
- `ProcessPool.kt`, `SemaphoreProcessPool.kt`
- `PathAwareArgRewriter.kt`
- `RunnerToMessageBridge.kt`, `DefaultRunnerToMessageBridge.kt`

Tests (`:core` + `:testing`):

- `ProcessToolRunnerTest.kt` — happy path, exit-code-non-zero,
  cancellation, stdin write.
- `PathAwareArgRewriterTest.kt` — covers `--config=/abs/path`,
  `--workspace=/x`, bare path args, non-path args.
- `EnvMutatorOrderTest.kt`.

## Acceptance criteria

- [ ] `ToolRunner` is a plain interface; concrete `ProcessToolRunner`
      composes `ProcessSpawner + ProcessPool + EnvMutators`.
- [ ] `JvmProcessSpawner` is the only platform-touching class but
      uses **only** `java.lang.ProcessBuilder` (no IntelliJ
      `GeneralCommandLine` in `:core`).
- [ ] Cancellation kills the process within 500ms in tests.
- [ ] `RecordedProcessSpawner` (in `:testing`) returns pre-canned
      output for matched arg lists.
- [ ] `PathAwareArgRewriter` rewrites only args where
      `ToolArg.isPath == true` AND `pathMapper.canProcess(value)`.
- [ ] No public method takes raw `Project` in `:core`.
- [ ] EP `dev.jplugins.qualityTools.envMutator` declared in
      `:ui/META-INF/quality-tools-eps.xml` with `dynamic="true"`.
- [ ] EP `dev.jplugins.qualityTools.processSpawner` declared with
      `dynamic="true"`.
- [ ] `RunnerToMessageBridge` round-trip tested: missing-binary →
      `category=<typeId>.unavailable`; timeout → `<toolId>.timeout`;
      cancellation → `<toolId>.cancelled`.
- [ ] `ToolRunRequest.cancellation` is honored within 500 ms of
      `cancellation.cancel()` (verified by `RecordedProcessSpawner`).

## `:php` `IntellijProcessSpawner` (companion spec)

Lives in `:php`, registered as a higher-priority `ProcessSpawner` via
EP. Required because remote PHP interpreters (Docker, SSH, WSL) need
the IntelliJ `PhpRemoteInterpreterManager` to wire up.

Behavior contract:

- For local interpreters and bare-binary sources: delegates to
  `JvmProcessSpawner`.
- For sources with `pathMapper.canProcess(...)`: runs through
  `GeneralCommandLine` + `manager.getRemoteToolProcessHandler(...)`.
- **Stdin via remote interpreter**: many remote interpreters (notably
  Docker) ignore stdin or never close it. `IntellijProcessSpawner`
  implements the existing `RemoteInterpreterMagoRunner.buildShellPipeline`
  workaround as the default fallback when
  `ResolvedBinary.supportsStdin == false` but the request has
  stdin:
  ```
  sh -c 'printf "%s" "$PLUGIN_STDIN_B64" | base64 -d | <exe> <args>'
  ```
  Plugin authors who want to opt OUT of the fallback set
  `SpawnRequest.disableStdinFallback = true`.

**Env-var collision rule**: SDK reserves env names starting with
`PLUGIN_` for itself (e.g. `PLUGIN_STDIN_B64`). `EnvMutator`
implementations MUST NOT write to `PLUGIN_*`. Each tool plugin should
namespace its own envs (`MAGO_*` for Mago, `PHPSTAN_*`, etc.) — phase
09 documents the rule that prepended env keys must not conflict with
the tool's CLI-recognized variables (Mago maps `MAGO_*` to config
keys, so the SDK reserved namespace deliberately avoids it).

## Out of scope

- Reader (phase 06).
- Annotator (phase 08).

## Depends on

`phase-01`, `phase-02`, `phase-03`, `phase-04`.
