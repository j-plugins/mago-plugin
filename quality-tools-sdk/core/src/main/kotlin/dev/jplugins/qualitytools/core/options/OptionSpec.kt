package dev.jplugins.qualitytools.core.options

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * Declarative description of one option. Generic over the option's
 * runtime type. Open interface — bundled specs (BoolSpec / IntSpec /
 * StringSpec / PathSpec / ChoiceSpec / StringListSpec / ListSpec) live
 * alongside; downstream plugins may add their own.
 */
public interface OptionSpec<T : Any> {
    public val key: String
    public val displayName: String
        get() = key
    public val default: T
    public val help: String?
        get() = null

    /** Free-form role tag, e.g. `"scope_root"`, `"config_file"`. */
    public val role: String
        get() = ""

    /** Whether values of this spec should be treated as paths by the
     *  path-aware arg rewriter (true for `PathSpec`). */
    public val isPath: Boolean
        get() = false

    @ThreadingPolicy("any")
    public fun encode(value: T): String

    @ThreadingPolicy("any")
    public fun decode(text: String): T?
}
