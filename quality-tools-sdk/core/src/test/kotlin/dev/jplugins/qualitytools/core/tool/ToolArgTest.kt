package dev.jplugins.qualitytools.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgTest {

    @Test
    fun `plainArg has no path semantics`() {
        val a = plainArg("--no-progress")
        assertEquals("--no-progress", a.raw)
        assertFalse(a.isPath)
        assertNull(a.pathPrefix)
    }

    @Test
    fun `pathArg is bare path`() {
        val a = pathArg("src/Foo.php")
        assertEquals("src/Foo.php", a.raw)
        assertTrue(a.isPath)
        assertNull(a.pathPrefix)
    }

    @Test
    fun `kvPathArg sets prefix to key=`() {
        val a = kvPathArg("--config", "/abs/phpstan.neon")
        assertEquals("--config=/abs/phpstan.neon", a.raw)
        assertTrue(a.isPath)
        assertEquals("--config=", a.pathPrefix)
    }

    @Test
    fun `kvPathArg accepts key already ending with =`() {
        val a = kvPathArg("--config=", "/abs/x")
        assertEquals("--config=/abs/x", a.raw)
        assertEquals("--config=", a.pathPrefix)
    }
}
