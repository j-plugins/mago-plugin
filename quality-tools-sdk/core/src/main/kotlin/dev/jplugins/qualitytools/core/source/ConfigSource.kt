package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * One entry on the user's per-tool profile list. A `ConfigSource` knows
 * how to produce a runnable [ResolvedBinary] (e.g. local path, composer
 * vendor binary, php-interpreter-script, docker exec).
 *
 * Open interface — `:php`, `:ui`, and third-party plugins add their own.
 * Discriminator for serialization is [typeId]; see [ConfigSourceType] for
 * the registry entry.
 */
public interface ConfigSource {
    /** Stable per-instance id (uuid). */
    public val instanceId: String

    /** Registry discriminator (`"local"`, `"composer"`, `"docker"`, …). */
    public val typeId: String

    /** User-visible label. */
    public val displayName: String

    /**
     * Resolve into a runnable binary, or `null` if the source is currently
     * unavailable (container down, interpreter missing, etc.). The runner
     * translates `null` into a `ToolMessage(severityLevel = "internal_error",
     * category = "<typeId>.unavailable")` via `RunnerToMessageBridge`.
     *
     * Implementations MUST NOT throw — return `null` and log via
     * `ctx.logger` for diagnostic info.
     */
    @ThreadingPolicy("background")
    public suspend fun resolve(ctx: ResolveContext): ResolvedBinary?
}
