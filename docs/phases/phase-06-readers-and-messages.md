# Phase 06 — Readers, Messages, Fixes, Ignore

## Goal

Tool process output becomes a `Flow<ToolMessage>` via pluggable
readers. Messages carry rich metadata (category, doc URL, tags, fixes).
Ignore policies decide which messages reach the user. Everything
extensible.

## Feature

A new tool brings its own `ResultReader` (e.g. SARIF). Quick-fixes
from the tool propagate as IntelliJ `LocalQuickFix` automatically.
Annotation-based ignores (`@phpstan-ignore`) work via a pluggable
`IgnorePolicy`.

## Solution

```kotlin
public interface ResultReader {
    public val id: String

    /**
     * Parses [run.stdout] / [run.stderr] into a sequence of messages.
     *
     * The `Flow` is cold and replayable. Implementations MUST call
     * `ctx.cancellation.throwIfCanceled()` at least once per record.
     * Implementations MUST NOT retain references to parsed strings
     * past flow completion.
     */
    public fun read(
        run: ToolRun,
        ctx: ToolRunContext,
    ): Flow<ToolMessage>
}

public interface ResultReaderRegistry {
    public fun byId(id: String): ResultReader?
    public fun all(): List<ResultReader>
}

public interface IgnorePolicyRegistry {
    public fun forPolicy(policy: IgnorePolicy): IgnorePolicyType?
    public fun deserializeByType(typeId: String, element: SerializedSourceElement): IgnorePolicy?
}

public interface MessageEnricherRegistry {
    /** Returns enrichers in order; tie-broken by class name. */
    public fun applicable(message: ToolMessage, ctx: ToolRunContext): List<MessageEnricher>
}

public interface ToolMessage {
    public val severityLevel: String          // "error"|"warning"|"weak_warning"|"info"|"hint"|"internal_error"
    public val range: SourceRange
    public val category: String
    public val ruleId: String?                // sub-identifier ("unused-variable"); category is the parent ("lint")
    public val title: String
    public val description: String?
    public val notes: List<String>            // additional lines below description
        get() = emptyList()
    public val help: String?
    public val documentationUrl: String?
    public val fixes: List<ToolFix>
    public val tags: Set<String>
    public val relatedRanges: List<RelatedRange>
        get() = emptyList()
}

/**
 * Helper builder — since [ToolMessage] is an open interface,
 * `copy()` is unavailable. [ToolMessageBuilder.from(message)] +
 * `.with(...)` is the recommended pattern for [MessageEnricher].
 */
public interface ToolMessageBuilder {
    public companion object {
        public fun from(message: ToolMessage): ToolMessageBuilder
    }
    public fun withSeverity(level: String): ToolMessageBuilder
    public fun withRange(range: SourceRange): ToolMessageBuilder
    public fun withCategory(category: String): ToolMessageBuilder
    public fun withRuleId(ruleId: String?): ToolMessageBuilder
    public fun withTitle(title: String): ToolMessageBuilder
    public fun withDescription(description: String?): ToolMessageBuilder
    public fun withNotes(notes: List<String>): ToolMessageBuilder
    public fun withDocumentationUrl(url: String?): ToolMessageBuilder
    public fun withFixes(fixes: List<ToolFix>): ToolMessageBuilder
    public fun withRelatedRanges(ranges: List<RelatedRange>): ToolMessageBuilder
    public fun build(): ToolMessage
}

public interface SourceRange {
    public val filePath: String?              // null for whole-project messages
    public val startLine: Int                 // 1-based; 0 = unknown
    public val startColumn: Int               // 1-based; 0 = unknown
    public val endLine: Int                   // 0 = same as start
    public val endColumn: Int                 // 0 = end-of-line

    /**
     * Whether [startColumn]/[endColumn] are byte offsets (true) or
     * character offsets (false). Readers that emit bytes (mago, some
     * Rust tools) set this true; the annotator converts via
     * [ByteToCharOffsetConverter].
     */
    public val isByteOffset: Boolean
        get() = false
}

public interface RelatedRange {
    public val range: SourceRange
    public val title: String                  // "Origin", "Cause", …
    public val kind: String                   // "navigation" | "context" | "duplicate"
}

public object SeverityLevels {
    public const val ERROR: String = "error"
    public const val WARNING: String = "warning"
    public const val WEAK_WARNING: String = "weak_warning"
    public const val INFO: String = "info"
    public const val HINT: String = "hint"
    public const val INTERNAL_ERROR: String = "internal_error"
}
```

Fallback rule for unknown `severityLevel` / `safety` / `scopeType`:
`:ui` `SeverityMapping` and `ToolFixHandler` both map unknown values
to `WEAK_WARNING` / `safe` / `line` respectively and log once via
`QtLogger` (one-shot per (toolId, value) pair). Acceptance bullets
below enforce this.

`severityLevel` is a plain string. The `:ui` module maps it to
`HighlightDisplayLevel`. Authors are free to invent new levels (e.g.
`"informational"`); the UI maps unknown values to `weak_warning` and
logs once.

```kotlin
public interface ToolFix {
    public val title: String
    public val safety: String                 // "safe" | "risky" | "experimental"
}

public interface ReplaceFix : ToolFix {
    public val range: SourceRange
    public val newText: String
}

public interface PatchFix : ToolFix {
    public val unifiedDiff: String
}

public interface CliFix : ToolFix {
    public val args: List<ToolArg>
    public val description: String
}

public interface IgnoreFix : ToolFix {
    public val scopeType: String              // "line" | "method" | "class" | "file" | "category"
    public val ruleId: String?
    public val commentTemplate: String?       // e.g. "@mago-expect {ruleId}"; null = use default
    public val insertion: SourceRange?        // where to put the comment; null = "just before line"
    public val rendererId: String?            // optional: override default rendering via IgnoreCommentRenderer EP
        get() = null
}

/**
 * Plugged-in renderer that takes the raw IgnoreFix and produces the
 * actual edit. Needed because Mago suppression is not a simple
 * "insert comment" — it merges into existing PHPDoc, deduplicates
 * codes, normalizes single-line to multi-line, and walks enclosing
 * scopes to detect existing suppression.
 *
 * Registered via EP `dev.jplugins.qualityTools.ignoreCommentRenderer`.
 */
public interface IgnoreCommentRenderer {
    public val id: String
    public val toolId: String                 // scope by tool to avoid collisions
    public fun render(fix: IgnoreFix, file: PsiFileLike, ctx: ToolRunContext): IgnoreRenderResult?
}

public interface IgnoreRenderResult {
    public val edits: List<ReplaceFix>        // could be the new comment OR a merged-into-existing edit
    public val skip: Boolean                  // true if already suppressed somewhere enclosing
}

public interface PsiFileLike {
    public val text: String
    public val path: String
}

public interface DeleteFileFix : ToolFix {
    public val filePath: String
}

public interface ExternalFileEditFix : ToolFix {
    public val filePath: String
    public val patch: String                  // unified diff for a different file
}
```

Cross-file fixes (the `MagoEdit.path` case from existing Mago) live
as `ExternalFileEditFix`. Delete-file fixes (`MagoRemoveRedundantFileAction`)
have a dedicated kind so UI can render the right confirmation dialog.

A new fix kind = a new interface; downstream UI registers a
`ToolFixHandler` (EP in `:ui`) to render it. SDK doesn't try to
enumerate fix kinds.

**Aggregate fixes** (the `MagoApplyEditSubmenuAction` submenu with
"Apply all SAFE" / "Apply all" pattern):

```kotlin
public interface AggregateFix : ToolFix {
    public val children: List<ToolFix>
    public val safety: String                 // worst-case safety of children
    public val grouping: String               // "by-safety" | "by-rule" | "by-file" | …
}
```

The UI handler unrolls one `AggregateFix` into a submenu with N+1
`IntentionAction`s — N children + one "apply all". Plain `ToolFix`
remains the unit; aggregation is metadata.

Ready-made readers in `:core`:

- `JsonLinesReader(parse: (JsonElement) -> ToolMessage?)` — handles
  `\n`-delimited JSON.
- `LineReader(parse: (String) -> ToolMessage?)`.
- `CheckstyleXmlReader` — SAX-based; uses platform-agnostic
  `javax.xml.parsers`.
- `SarifReader` — lives in `:core`. Hand-rolled parser, no `kotlinx-
  serialization` dependency, only the subset of SARIF 2.1 needed
  (`runs[].results[]`).

Byte→char offset conversion is handled by an in-`:core` utility
`ByteToCharOffsetConverter` that takes a `String` and returns a
`SourceRange` with `isByteOffset = false`. Readers MAY emit
`SourceRange.isByteOffset = true` to defer conversion to the
annotator (phase 08), which has the file content cached.

`IgnorePolicy`:

```kotlin
public interface IgnorePolicy {
    public val typeId: String
    public fun isIgnored(target: ToolTarget, message: ToolMessage, ctx: ToolRunContext): Boolean
}

public interface IgnorePolicyType {
    public val typeId: String
    public val aliasTypeIds: Set<String> get() = emptySet()
    public val displayName: String
    public fun deserialize(element: SerializedSourceElement): IgnorePolicy
    public fun serialize(policy: IgnorePolicy): SerializedSourceElement
    public fun createWizard(ctx: WizardContext): IgnorePolicyWizard?
}
```

Bundled in `:core`:

- `GlobPathIgnorePolicy` (typeId = `"glob"`)
- `CommentAnnotationIgnorePolicy` (typeId = `"comment"`) — checks the
  line above the message for `markerComment` (e.g. `// mago-ignore`).
  Doesn't require PSI; reads raw file via `IoFileReader` interface (so
  `:testing` can stub).

`MessageEnricher`:

```kotlin
public interface MessageEnricher {
    public val order: Int get() = 0
    public fun supports(message: ToolMessage, ctx: ToolRunContext): Boolean
    public fun enrich(message: ToolMessage, ctx: ToolRunContext): ToolMessage
}
```

Composition order: `Reader → ignore filter → enrichers → annotator`.

## Deliverables

`:core/message/`:

- `ToolMessage.kt`, `SourceRange.kt`
- `ToolFix.kt`, `ReplaceFix.kt`, `PatchFix.kt`, `CliFix.kt`, `IgnoreFix.kt`
- `MessageEnricher.kt`

`:core/reader/`:

- `ResultReader.kt`
- `JsonLinesReader.kt`, `LineReader.kt`
- `CheckstyleXmlReader.kt`
- `SarifReader.kt`

`:core/ignore/`:

- `IgnorePolicy.kt`, `IgnorePolicyType.kt`, `IgnorePolicyWizard.kt`
- `GlobPathIgnorePolicy.kt`, `GlobPathIgnorePolicyType.kt`
- `CommentAnnotationIgnorePolicy.kt`, `CommentAnnotationIgnorePolicyType.kt`
- `IoFileReader.kt` (interface for I/O abstraction)

Tests:

- `JsonLinesReaderTest.kt`, `CheckstyleXmlReaderTest.kt`,
  `SarifReaderTest.kt`
- `GlobPathIgnorePolicyTest.kt`, `CommentAnnotationIgnorePolicyTest.kt`
- `MessageEnricherChainTest.kt`

## Acceptance criteria

- [ ] `ResultReader.read` MUST NOT throw on malformed input or
      partial buffer. Parse failures are surfaced as a single
      `ToolMessage(severityLevel = "internal_error",
      category = "<toolId>.parse_error")` and the flow completes
      normally.
- [ ] `ResultReader.read` MUST call `ctx.cancellation.throwIfCanceled()`
      at least once per emitted record (cancellation acceptance
      test).
- [ ] `ByteToCharOffsetConverter` clamps offsets to `[0, text.length]`
      and snaps to the next codepoint boundary; out-of-range
      offsets log one warning per (toolId, file) via `QtLogger`.
- [ ] `ToolFix` is a plain interface; `ReplaceFix`, `PatchFix`,
      `CliFix`, `IgnoreFix`, `DeleteFileFix`, `ExternalFileEditFix`,
      `AggregateFix` extend it without `sealed`.
- [ ] `severityLevel` is `String`, not enum. Unknown values → fallback
      `weak_warning` and one-shot log warning (per (toolId, value)).
- [ ] `ToolFix.safety` is `String` ("safe"|"risky"|"experimental");
      unknown → "safe" + one-shot log.
- [ ] `IgnoreFix.scopeType` String fallback → "line" + one-shot log.
- [ ] `ToolMessage.relatedRanges` carries secondary annotations
      (Mago's `secondaryAnnotations` use case).
- [ ] `SourceRange.isByteOffset` defaults to false; readers may
      set true; `ByteToCharOffsetConverter` produces char-offset
      range with utf-8 round-trip test.
- [ ] `SarifReader` parses the official SARIF 2.1 fixture without
      reflective access to the schema (hand-rolled parser, kotlinx
      .serialization optional).
- [ ] `CommentAnnotationIgnorePolicy` does not require PSI — pure I/O
      against `IoFileReader`.
- [ ] `MessageEnricher.order` allows deterministic chain (tested).
- [ ] No reader leaks raw `InputStream` to consumers — they get
      `ToolRun` (already buffered) by phase 05 contract.
- [ ] Adding a new `ToolFix` interface (e.g. `RefactorFix`) does not
      require changes in `:core`.

## EPs declared in this phase

- `dev.jplugins.qualityTools.resultReader` (`dynamic="true"`)
- `dev.jplugins.qualityTools.ignorePolicyType` (`dynamic="true"`)
- `dev.jplugins.qualityTools.messageEnricher` (`dynamic="true"`)
- `dev.jplugins.qualityTools.ignoreCommentRenderer` (`dynamic="true"`)

Also defined in `:core`:

- `Glob.match(pattern: String, value: String): Boolean` — top-level
  helper used by `GlobPathIgnorePolicy`, `GlobScope`, and the
  cookbook `VcsBranchScope` example. Supports `**`, `*`, `?`,
  `[abc]`. JDK-only, no `java.nio.file.PathMatcher` (avoids JBR
  edge cases).

All three are added to phase 07's master `:ui/META-INF/quality-tools-eps.xml`.

## Out of scope

- Quick-fix → `LocalQuickFix` bridge (phase 07).
- Annotator integration (phase 08).

## Depends on

`phase-05`.
