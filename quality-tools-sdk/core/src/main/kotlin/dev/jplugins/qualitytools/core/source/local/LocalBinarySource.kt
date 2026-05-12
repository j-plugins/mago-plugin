package dev.jplugins.qualitytools.core.source.local

import dev.jplugins.qualitytools.core.source.ConfigSource
import dev.jplugins.qualitytools.core.source.ResolveContext
import dev.jplugins.qualitytools.core.source.ResolvedBinary
import dev.jplugins.qualitytools.core.source.SimpleResolvedBinary

/**
 * Bundled source: a single local binary path. Identity path-mapper.
 */
public class LocalBinarySource(
    override val instanceId: String,
    public val path: String,
    override val displayName: String = path,
    public val extraEnv: Map<String, String> = emptyMap(),
    public val cachedDetectedVersion: String? = null,
) : ConfigSource {

    override val typeId: String = TYPE_ID

    override suspend fun resolve(ctx: ResolveContext): ResolvedBinary? =
        SimpleResolvedBinary(
            command = listOf(path),
            env = extraEnv,
            detectedVersion = cachedDetectedVersion,
        )

    public companion object {
        public const val TYPE_ID: String = "local"
    }
}
