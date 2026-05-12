package dev.jplugins.qualitytools.core.tool

/**
 * One element of a CLI argument list. Path-awareness is intrinsic to the
 * argument rather than a side table on the tool, so a `PathMapper` can be
 * applied uniformly by `PathAwareArgRewriter` (phase 05).
 *
 * Open interface — consumers may add their own kinds (e.g. quoted, env-substituted).
 *
 * Use the top-level factory functions in [ToolArgs] for the bundled kinds.
 */
public interface ToolArg {
    /** The raw token as it would appear on the command line. */
    public val raw: String

    /** Whether the rewriter should consider this arg for path mapping. */
    public val isPath: Boolean

    /**
     * For args of shape `--key=value` whose value is a path, this is the
     * `--key=` prefix; the rewriter only rewrites what comes after it.
     * Null for plain bare-path args or non-path args.
     */
    public val pathPrefix: String?
}
