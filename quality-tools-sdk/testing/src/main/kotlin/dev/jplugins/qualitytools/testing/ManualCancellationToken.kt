package dev.jplugins.qualitytools.testing

import dev.jplugins.qualitytools.core.context.CancellationToken
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException

/**
 * Deterministically-controlled cancellation token. Tests `cancel()` it
 * from one thread and verify the cancellation propagates within their
 * SLA (e.g. 500 ms acceptance bullet on phase 05).
 */
public class ManualCancellationToken : CancellationToken {

    private val canceled = AtomicBoolean(false)
    private val handlers = CopyOnWriteArrayList<() -> Unit>()

    override val isCanceled: Boolean
        get() = canceled.get()

    override fun throwIfCanceled() {
        if (canceled.get()) throw CancellationException("ManualCancellationToken cancelled")
    }

    override fun cancel() {
        if (canceled.compareAndSet(false, true)) {
            handlers.forEach { runCatching { it() } }
        }
    }

    override fun onCancel(handler: () -> Unit): AutoCloseable {
        if (canceled.get()) {
            runCatching { handler() }
            return AutoCloseable {}
        }
        handlers.add(handler)
        return AutoCloseable { handlers.remove(handler) }
    }
}
