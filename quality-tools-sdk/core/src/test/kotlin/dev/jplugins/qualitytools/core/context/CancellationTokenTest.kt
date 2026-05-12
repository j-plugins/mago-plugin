package dev.jplugins.qualitytools.core.context

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class CancellationTokenTest {

    @Test
    fun `Never never reports cancelled`() {
        val t = CancellationToken.Never
        assertFalse(t.isCanceled)
    }

    @Test
    fun `Never throwIfCanceled does not throw`() {
        CancellationToken.Never.throwIfCanceled() // no exception
    }

    @Test
    fun `Never cancel is a silent no-op`() {
        val t = CancellationToken.Never
        t.cancel() // doesn't throw
        assertFalse("Never stays uncancelled after cancel()", t.isCanceled)
    }

    @Test
    fun `Never onCancel never fires the handler`() {
        var fired = false
        val handle = CancellationToken.Never.onCancel { fired = true }
        // cancel() is a no-op for Never, so handler must not fire
        CancellationToken.Never.cancel()
        assertFalse(fired)
        handle.close() // idempotent / safe
    }

    @Test
    fun `Never companion returns the same singleton`() {
        assertSame(CancellationToken.Never, CancellationToken.Never)
    }
}
