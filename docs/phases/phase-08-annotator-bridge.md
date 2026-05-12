# Phase 08 — Annotator Bridge

## Goal

`ExternalAnnotator<CollectedInfo, AnnotationResult>` lives in `:ui` and
wires up the runner + reader + ignore + enrichers chain. Tool authors
get on-the-fly highlighting for free.

## Feature

Mago doesn't write its own `ExternalAnnotator` anymore. A 5-line
registration in `plugin.xml` (`<externalAnnotator language="PHP"
implementationClass="QualityToolsAnnotator$Php"/>`) is the whole thing.

## Solution

`QualityToolsAnnotator` is generic over language. Per-language
registrations are tiny subclasses (the IntelliJ EP `externalAnnotator`
is per-language).

`QualityToolsAnnotator` is an `open class` (not abstract — no
abstract members), takes `languageId` via constructor:

```kotlin
public open class QualityToolsAnnotator(
    protected val languageId: String,
) : ExternalAnnotator<QualityToolsAnnotator.CollectedInfo,
                      QualityToolsAnnotator.AnnotationResult>(),
    DumbAware {

    public data class CollectedInfo(
        val file: PsiFile,
        val text: String,
        val toolIds: List<String>,
    )

    public data class AnnotationResult(
        val messages: Map<String, List<ToolMessage>>,
        val internalErrors: List<ToolMessage>,        // severityLevel == "internal_error"
    )

    public final override fun collectInformation(file: PsiFile): CollectedInfo? = …
    public final override fun doAnnotate(info: CollectedInfo): AnnotationResult = …
    public final override fun apply(file: PsiFile, result: AnnotationResult, holder: AnnotationHolder) = …
}
```

Language subclasses in their own plugins:

```kotlin
public class PhpQualityToolsAnnotator : QualityToolsAnnotator("PHP")
```

— a one-line class. No abstract member; the constructor parameter
is enforced at compile time.

Pipeline inside `doAnnotate`:

1. Get list of registered `QualityTool`s where
   `supportedLanguageIds.contains(languageId) && tool.modes.any {
   it.executionStyle == "on_the_fly" }`.
2. For each tool: find the active `ConfigProfile` for the current
   file (via `ProfileSelector`).
3. For each tool: run the matched `on_the_fly` mode through
   `ToolRunner`, with `stdin = file.text` if `mode.supportsStdin`.
4. Parse stdout via the registered `ResultReader`.
5. Pass each message through registered `IgnorePolicy`s and
   `MessageEnricher`s.
6. Return aggregated `AnnotationResult`.

`apply()` translates `ToolMessage` → `AnnotationBuilder` and registers
`LocalQuickFix` for each `ToolFix` via the `ToolFixHandler` chain.

Cancellation: `doAnnotate` does NOT use `runBlocking` at the public
contract level. It uses `ProgressIndicatorUtils.runUnderIndicator` +
an `IntellijCancellationToken` adapter that wraps the
`ProgressIndicator` passed to `doAnnotate` into the `:core`
`CancellationToken` contract. The `ToolRunner` (phase 05) is
`suspend`; the annotator bridges via
`kotlinx.coroutines.runBlocking(Dispatchers.IO)` confined to the
annotator's pooled thread — this is the documented exception, since
`ExternalAnnotator.doAnnotate` already is a blocking off-EDT call.

`IntellijCancellationToken` polls the wrapped `ProgressIndicator`
inside `isCanceled` (no separate poller thread). When the user
cancels, `CancellationToken.throwIfCanceled()` raises
`ProcessCanceledException`. The runner (phase 05) catches it and
returns `ToolRun(canceled = true)`.

This avoids the documented "don't `runBlocking` inside
`ExternalAnnotator`" anti-pattern.

Notification de-duplication: `QualityToolsNotifier` (port from existing
`QualityToolsNotifier` class — already a clean concept) is moved to
`:ui`.

`format`-mode runs are NOT in the annotator. They go through
`AsyncFormattingServiceAdapter` (phase 07) which IntelliJ invokes via
the `formattingService` EP.

**format-after-fix** is a post-fix hook on the annotator:

```kotlin
public interface PostFixHook {
    public fun supports(fix: ToolFix, ctx: ToolRunContext): Boolean
    public suspend fun afterFix(fix: ToolFix, file: PsiFile, ctx: ToolRunContext)
}
```

Registered via EP. Mago's `formatAfterFix` option becomes a built-in
`FormatAfterAnyFix` PostFixHook that runs the active `format`-mode
profile after any applied fix.

## Deliverables

`:ui/annotator/`:

- `QualityToolsAnnotator.kt` (open class, takes `languageId` ctor arg)
- `IntellijCancellationToken.kt`
- `QualityToolsNotifier.kt` (port)
- `AnnotationBuilderExt.kt`
- `PostFixHook.kt` (EP)

Tests:

- `QualityToolsAnnotatorTest.kt` — covers: no tool registered, single
  tool, two tools merging messages, ignored message dropped, fix
  produced.
- `AnnotatorCancellationTest.kt`.

## Acceptance criteria

- [ ] `QualityToolsAnnotator` is the only annotator class users need to
      subclass — language subclasses are 3-line files.
- [ ] No tool can disable cancellation (timeout enforced by SDK).
- [ ] `ToolFix` round-trips: reader emits a `ReplaceFix`, annotator
      applies via `ToolFixHandler`, file is edited (covered by
      `BasePlatformTestCase`).
- [ ] `apply()` is idempotent (re-running on same input produces same
      annotations).
- [ ] `DumbAware` is honored — annotations appear during indexing.
- [ ] Concurrent runs for the same `(file, toolId)` are coalesced —
      a new `collectInformation` cancels the previous in-flight job
      before scheduling.
- [ ] `PostFixHook` exceptions are caught, surfaced via
      `InternalErrorNotifier` (`<toolId>.post_fix_failed`), and do
      NOT abort the rest of the chain.
- [ ] Messages whose `range.filePath` does not resolve to a
      `VirtualFile` in the project go to `AnnotationResult.internalErrors`,
      not inline annotations.
- [ ] If `IgnoreCommentRenderer.skip == true` for every fix,
      annotations still appear (only fix actions are dropped).
- [ ] Annotator's `CoroutineScope` is bound to the project
      `Disposable`; on project close, live runner jobs are
      cancelled within 500 ms.
- [ ] Existing `MagoExternalAnnotator` is untouched.

## Out of scope

- Migrating Mago to use this annotator (phase 09).

## Depends on

`phase-07`.
