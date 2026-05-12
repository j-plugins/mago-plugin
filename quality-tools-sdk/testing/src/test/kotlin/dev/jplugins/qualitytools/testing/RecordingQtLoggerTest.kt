package dev.jplugins.qualitytools.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingQtLoggerTest {

    @Test
    fun `log entries are captured in order`() {
        val l = RecordingQtLogger()
        l.log("info", "first")
        l.log("warn", "second")
        l.log("error", "third", IllegalStateException("oops"))
        val all = l.all()
        assertEquals(3, all.size)
        assertEquals("info", all[0].level)
        assertEquals("first", all[0].message)
        assertEquals("warn", all[1].level)
        assertEquals("third", all[2].message)
        assertEquals("oops", all[2].throwable?.message)
    }

    @Test
    fun `assertLoggedOnce passes for exactly one match`() {
        val l = RecordingQtLogger()
        l.log("warn", "Xdebug warning ignored")
        l.log("info", "unrelated")
        l.assertLoggedOnce("warn", "Xdebug")
    }

    @Test
    fun `assertLoggedOnce fails when nothing matched`() {
        val l = RecordingQtLogger()
        l.log("warn", "other")
        val ex = try { l.assertLoggedOnce("warn", "Xdebug"); null } catch (e: IllegalStateException) { e }
        assertEquals(true, ex?.message?.contains("Expected exactly one") == true)
    }

    @Test
    fun `assertLoggedOnce fails when multiple matched`() {
        val l = RecordingQtLogger()
        l.log("warn", "Xdebug active 1")
        l.log("warn", "Xdebug active 2")
        val ex = try { l.assertLoggedOnce("warn", "Xdebug"); null } catch (e: IllegalStateException) { e }
        assertEquals(true, ex?.message?.contains("saw 2") == true)
    }

    @Test
    fun `clear resets the buffer`() {
        val l = RecordingQtLogger()
        l.log("info", "a")
        l.log("info", "b")
        l.clear()
        assertEquals(emptyList<RecordingQtLogger.Entry>(), l.all())
    }
}
