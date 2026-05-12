package dev.jplugins.qualitytools.core.options

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * Schema-driven options store. The interface is `:core`; the persistent
 * implementation lives in `:ui` (`PersistentQualityToolsProjectStorage`).
 * Tests use `MapOptionsBag` from `:testing`.
 *
 * Mutability discipline (SDK rule 12):
 *  - reads via [get] always see the most-recent uncommitted writes from
 *    the same instance;
 *  - cross-thread / cross-process visibility is guaranteed only after
 *    [commit];
 *  - writes without [commit] are lost on a fresh load.
 */
public interface OptionsBag {
    @ThreadingPolicy("any")
    public operator fun <T : Any> get(spec: OptionSpec<T>): T

    @ThreadingPolicy("any")
    public operator fun <T : Any> set(spec: OptionSpec<T>, value: T)

    /** Snapshot of every option's encoded form. Used by serialization. */
    @ThreadingPolicy("any")
    public fun snapshot(): Map<String, String>

    /** Mode-scoped overlay; falls back to top-level when a key isn't set. */
    @ThreadingPolicy("any")
    public fun mode(modeId: String): OptionsBag

    /** Atomically publish buffered writes to the storage. */
    @ThreadingPolicy("any")
    public fun commit()
}
