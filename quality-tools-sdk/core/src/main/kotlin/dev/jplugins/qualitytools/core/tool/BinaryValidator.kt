package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * Tier-1 SDK patch G1.
 *
 * Drives the "Validate" button next to a tool-path field in Settings.
 * The widget itself lives in `:ui`; `:core` provides only the contract
 * so plugins can declare it without depending on Swing.
 *
 * `validate(versionOutput)` is given the *stdout* of running the tool
 * with [versionArgs] (typically `--version`). Returns a [ValidationResult]
 * that says ok/error, the parsed version (if any), and a message to show.
 *
 * Plugin authors typically delegate to a regex via
 * `:php`'s `PhpToolVersionParser`; only override if a custom protocol is
 * needed (e.g. a tool that prints to stderr only).
 */
public interface BinaryValidator {
    /** Args used to probe the tool. Default `["--version"]`. */
    public val versionArgs: List<String>
        get() = listOf("--version")

    @ThreadingPolicy("background")
    public fun validate(versionOutput: String): ValidationResult
}

public interface ValidationResult {
    public val ok: Boolean
    public val message: String

    /**
     * Parsed semantic version, if one was extracted from output. Read by
     * the SDK to populate [dev.jplugins.qualitytools.core.source.ResolvedBinary.detectedVersion]
     * (Tier-1 patch G8) when resolving.
     */
    public val detectedVersion: String?
        get() = null
}

/** Default impl plugin authors can return directly. */
public data class SimpleValidationResult(
    override val ok: Boolean,
    override val message: String,
    override val detectedVersion: String? = null,
) : ValidationResult
