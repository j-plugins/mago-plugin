package dev.jplugins.qualitytools.core.source

/**
 * Output of [ConfigSource.resolve]: everything `ProcessToolRunner` needs
 * to spawn the tool. Plain data; no behaviour.
 *
 * Tier-1 patch G8: [detectedVersion] propagates the result of an earlier
 * `BinaryValidator.validate` invocation so `QualityTool.buildArgs` can
 * branch on the tool's version without re-running `--version`.
 */
public interface ResolvedBinary {
    public val command: List<String>
    public val workingDir: String?
        get() = null
    public val env: Map<String, String>
        get() = emptyMap()
    public val pathMapper: PathMapper
        get() = PathMapper.Identity
    public val supportsStdin: Boolean
        get() = true

    /**
     * Tier-1 patch G8.
     *
     * The semantic version string parsed from `--version` output by
     * `BinaryValidator`, or null if not known. Populated by the runner
     * when the source's `ConfigSourceType.binaryValidator` returned a
     * version; preserved across runs.
     */
    public val detectedVersion: String?
        get() = null
}

/** Plain data carrier used when a source has no extra fields. */
public data class SimpleResolvedBinary(
    override val command: List<String>,
    override val workingDir: String? = null,
    override val env: Map<String, String> = emptyMap(),
    override val pathMapper: PathMapper = PathMapper.Identity,
    override val supportsStdin: Boolean = true,
    override val detectedVersion: String? = null,
) : ResolvedBinary
