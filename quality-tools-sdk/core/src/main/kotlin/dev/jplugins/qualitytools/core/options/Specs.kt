@file:JvmName("OptionSpecs")

package dev.jplugins.qualitytools.core.options

/** Bool spec; encodes as "true"/"false". */
public class BoolSpec(
    override val key: String,
    override val default: Boolean = false,
    override val displayName: String = key,
    override val help: String? = null,
    override val role: String = "",
) : OptionSpec<Boolean> {
    override fun encode(value: Boolean): String = value.toString()
    override fun decode(text: String): Boolean? = when (text.trim().lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }
}

public class IntSpec(
    override val key: String,
    override val default: Int = 0,
    override val displayName: String = key,
    override val help: String? = null,
    override val role: String = "",
    public val range: IntRange? = null,
) : OptionSpec<Int> {
    override fun encode(value: Int): String = value.toString()
    override fun decode(text: String): Int? {
        val n = text.trim().toIntOrNull() ?: return null
        return if (range == null || n in range) n else null
    }
}

public class StringSpec(
    override val key: String,
    override val default: String = "",
    override val displayName: String = key,
    override val help: String? = null,
    override val role: String = "",
) : OptionSpec<String> {
    override fun encode(value: String): String = value
    override fun decode(text: String): String = text
}

public class PathSpec(
    override val key: String,
    override val default: String = "",
    override val displayName: String = key,
    override val help: String? = null,
    override val role: String = "",
) : OptionSpec<String> {
    override val isPath: Boolean = true
    override fun encode(value: String): String = value
    override fun decode(text: String): String = text
}

/** Top-level builder functions used by `OptionsSchema` impls. */
public fun bool(
    key: String,
    default: Boolean = false,
    displayName: String = key,
    help: String? = null,
    role: String = "",
): BoolSpec = BoolSpec(key, default, displayName, help, role)

public fun int(
    key: String,
    default: Int = 0,
    displayName: String = key,
    help: String? = null,
    role: String = "",
    range: IntRange? = null,
): IntSpec = IntSpec(key, default, displayName, help, role, range)

public fun string(
    key: String,
    default: String = "",
    displayName: String = key,
    help: String? = null,
    role: String = "",
): StringSpec = StringSpec(key, default, displayName, help, role)

public fun path(
    key: String,
    default: String = "",
    displayName: String = key,
    help: String? = null,
    role: String = "",
): PathSpec = PathSpec(key, default, displayName, help, role)
