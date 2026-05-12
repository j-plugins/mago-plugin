package dev.jplugins.qualitytools.testing

import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsBag

/**
 * In-memory `OptionsBag` for unit tests. Writes are immediately visible
 * to subsequent reads; [commit] is a no-op (no persistent backing).
 *
 * Per-mode overlay falls back to the top-level bag, matching the SDK
 * contract.
 */
public class MapOptionsBag private constructor(
    private val data: MutableMap<String, String>,
    private val parent: OptionsBag?,
) : OptionsBag {

    public constructor(initial: Map<String, String> = emptyMap()) :
        this(initial.toMutableMap(), parent = null)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(spec: OptionSpec<T>): T {
        val raw = data[spec.key] ?: return parent?.get(spec) ?: spec.default
        return spec.decode(raw) ?: spec.default
    }

    override fun <T : Any> set(spec: OptionSpec<T>, value: T) {
        data[spec.key] = spec.encode(value)
    }

    override fun snapshot(): Map<String, String> =
        ((parent?.snapshot() ?: emptyMap()) + data).toMap()

    override fun mode(modeId: String): OptionsBag = MapOptionsBag(mutableMapOf(), parent = this)

    override fun commit() {
        // no-op for in-memory test impl
    }
}
