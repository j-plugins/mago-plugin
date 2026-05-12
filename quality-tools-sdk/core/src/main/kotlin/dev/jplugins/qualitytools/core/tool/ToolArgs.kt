@file:JvmName("ToolArgs")

package dev.jplugins.qualitytools.core.tool

/** Plain non-path argument (e.g. `--no-progress`). */
public fun plainArg(raw: String): ToolArg = ToolArgImpl(raw, isPath = false, pathPrefix = null)

/** Bare path argument (e.g. `src/Foo.php`). */
public fun pathArg(raw: String): ToolArg = ToolArgImpl(raw, isPath = true, pathPrefix = null)

/**
 * `--key=value` argument where `value` is a path. The rewriter, given a
 * non-identity `PathMapper`, will rewrite only the part after `--key=`.
 *
 *     kvPathArg("--config", "/abs/phpstan.neon")
 *       => raw  = "--config=/abs/phpstan.neon"
 *          pathPrefix = "--config="
 */
public fun kvPathArg(key: String, value: String): ToolArg {
    val prefix = if (key.endsWith("=")) key else "$key="
    return ToolArgImpl(raw = "$prefix$value", isPath = true, pathPrefix = prefix)
}

private data class ToolArgImpl(
    override val raw: String,
    override val isPath: Boolean,
    override val pathPrefix: String?,
) : ToolArg
