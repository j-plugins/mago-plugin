package dev.jplugins.qualitytools.core.context

import org.junit.Assert.assertSame
import org.junit.Test

class QtLoggerTest {

    @Test
    fun `NoOp logger swallows every level`() {
        val logger = QtLogger.NoOp
        logger.log("debug", "noise")
        logger.log("info", "still noise")
        logger.log("warn", "warning", IllegalStateException("oops"))
        logger.log("error", "error", null)
        // no observable side-effect
    }

    @Test
    fun `NoOp is a singleton`() {
        assertSame(QtLogger.NoOp, QtLogger.NoOp)
    }

    @Test
    fun `fun-interface SAM construction works`() {
        val captured = mutableListOf<String>()
        val logger = QtLogger { level, message, _ -> captured += "$level:$message" }
        logger.log("info", "hello")
        logger.log("warn", "world")
        kotlin.test.assertEquals(listOf("info:hello", "warn:world"), captured)
    }
}
