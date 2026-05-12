package dev.jplugins.qualitytools.testing

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCancellationTokenTest {

    @Test
    fun `not cancelled until cancel called`() {
        val t = ManualCancellationToken()
        assertFalse(t.isCanceled)
        t.throwIfCanceled() // does not throw
    }

    @Test
    fun `cancel makes isCanceled true`() {
        val t = ManualCancellationToken()
        t.cancel()
        assertTrue(t.isCanceled)
    }

    @Test
    fun `throwIfCanceled raises CancellationException after cancel`() {
        val t = ManualCancellationToken()
        t.cancel()
        try {
            t.throwIfCanceled()
            kotlin.test.fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertTrue("message names the token type",
                e.message?.contains("ManualCancellationToken") == true)
        }
    }

    @Test
    fun `cancel is idempotent`() {
        val t = ManualCancellationToken()
        var fires = 0
        t.onCancel { fires++ }
        t.cancel()
        t.cancel()
        t.cancel()
        assertEquals(1, fires)
    }

    @Test
    fun `onCancel handler fires on cancel`() {
        val t = ManualCancellationToken()
        var fired = false
        t.onCancel { fired = true }
        assertFalse("not fired before cancel", fired)
        t.cancel()
        assertTrue("fired on cancel", fired)
    }

    @Test
    fun `onCancel after already cancelled fires immediately`() {
        val t = ManualCancellationToken()
        t.cancel()
        var fired = false
        t.onCancel { fired = true }
        assertTrue("fires synchronously when token already cancelled", fired)
    }

    @Test
    fun `onCancel close-handle prevents the callback`() {
        val t = ManualCancellationToken()
        var fired = false
        val h = t.onCancel { fired = true }
        h.close()
        t.cancel()
        assertFalse(fired)
    }

    @Test
    fun `onCancel handlers fire even when one throws`() {
        val t = ManualCancellationToken()
        var second = false
        t.onCancel { throw IllegalStateException("boom") }
        t.onCancel { second = true }
        t.cancel() // does not propagate
        assertTrue(second)
    }

    @Test
    fun `cross-thread cancel is observed within 500ms`() {
        val t = ManualCancellationToken()
        val latch = CountDownLatch(1)
        val observer = Thread {
            while (!t.isCanceled) {
                Thread.sleep(5)
            }
            latch.countDown()
        }
        observer.start()
        Thread.sleep(20)
        t.cancel()
        assertTrue("observed within 500ms", latch.await(500, TimeUnit.MILLISECONDS))
        observer.join()
    }
}
