package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.context.ThreadingPolicy
import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.tool.BinaryValidator

/**
 * Registry entry for one kind of [ConfigSource]. Registered via the
 * `dev.jplugins.qualityTools.configSourceType` EP in `:ui`'s master
 * EP file (phase 07).
 *
 * Carries Tier-1 patches:
 *  - G2 — [defaultTimeoutMs] override per source type
 *  - G3 — [onDetected] callback after `watch()` produces a new source
 *  - G1/G8 (indirect) — [binaryValidator] consumed by the validate
 *    widget and to populate [ResolvedBinary.detectedVersion]
 */
public interface ConfigSourceType {
    /** Registry discriminator. Must be globally unique; vendor-prefixed
     *  (`"acme.docker"` not `"docker"`). */
    public val typeId: String

    /** Old type ids to accept on deserialize, for migration. */
    public val aliasTypeIds: Set<String>
        get() = emptySet()

    public val displayName: String

    /** Required IntelliJ plugin ids (string-typed to keep `:core` free of platform). */
    public val requiredPluginIds: Set<String>
        get() = emptySet()

    /**
     * Tier-1 patch G2.
     *
     * Default timeout (ms) when a [dev.jplugins.qualitytools.core.profile.ConfigProfile]
     * is freshly created against this source. Lets remote sources start at
     * 60 s instead of inheriting the local 30 s default without per-tool code.
     */
    public val defaultTimeoutMs: Long
        get() = 30_000L

    /**
     * Tier-1 patches G1/G8 wiring.
     *
     * If non-null, the SDK calls `validate()` once after [createWizard] /
     * [watch] produces a source, stores any returned `detectedVersion` on
     * the resulting [ResolvedBinary], and surfaces the result in the
     * "Validate" button widget in Settings.
     */
    public val binaryValidator: BinaryValidator?
        get() = null

    /** Cheap availability check. Hidden from "Add new" when false. */
    @ThreadingPolicy("any")
    public fun isAvailable(ctx: AvailabilityContext): Boolean

    /** Returns null when this source can't be created interactively. */
    @ThreadingPolicy("edt")
    public fun createWizard(ctx: WizardContext): ConfigSourceWizard?

    /** Watch the filesystem for auto-detectable sources. Returned closeable
     *  is closed by the registry on project close / plugin unload. */
    @ThreadingPolicy("background")
    public fun watch(
        ctx: WatchContext,
        onDetected: (ConfigSource) -> Unit,
    ): AutoCloseable? = null

    /**
     * Tier-1 patch G3.
     *
     * Called by the SDK right after a source produced by [watch] or
     * [createWizard] is added to the user's profile list. Plugins use
     * this hook to enrich the profile's [OptionsBag] from project-side
     * config files (e.g. `phpstan.neon`, `composer.json scripts.X`).
     *
     * Runs once per source per project. MUST be idempotent — the SDK is
     * allowed to invoke it again after a `watch` re-detection.
     */
    @ThreadingPolicy("background")
    public fun onDetected(
        source: ConfigSource,
        ctx: ResolveContext,
        bag: OptionsBag,
    ) {
        // default: no enrichment
    }

    @ThreadingPolicy("any")
    public fun deserialize(element: SerializedSourceElement): ConfigSource

    @ThreadingPolicy("any")
    public fun serialize(source: ConfigSource): SerializedSourceElement
}
