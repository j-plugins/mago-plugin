package dev.jplugins.qualitytools.core.context

/**
 * Single cancellation channel used by everything below the annotator
 * (runner, readers, enrichers, ignore policies). Bridged from
 * `ProgressIndicator` in `:ui`'s `IntellijCancellationToken`.
 *
 * Implementations are thread-safe.
 */
public interface CancellationToken {
    public val isCanceled: Boolean

    /** Throws a `CancellationException` if cancelled. Safe to call from any thread. */
    @ThreadingPolicy("any")
    public fun throwIfCanceled()

    /** Schedules cancellation; callers continue, the next `throwIfCanceled` raises. */
    @ThreadingPolicy("any")
    public fun cancel()

    /**
     * Registers a callback fired on cancellation. Returned closeable
     * unregisters. Used to wire process destruction to the single
     * cancellation channel.
     */
    @ThreadingPolicy("any")
    public fun onCancel(handler: () -> Unit): AutoCloseable

    public companion object {
        /** Token that is never cancelled. For unit tests of code paths that ignore cancellation. */
        public val Never: CancellationToken = NeverCancellationToken
    }
}

private object NeverCancellationToken : CancellationToken {
    override val isCanceled: Boolean = false
    override fun throwIfCanceled() {}
    override fun cancel(): Unit = throw UnsupportedOperationException("CancellationToken.Never cannot be cancelled")
    override fun onCancel(handler: () -> Unit): AutoCloseable = AutoCloseable { }
}
