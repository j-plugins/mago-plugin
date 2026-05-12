package dev.jplugins.qualitytools.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tier-1 patch G9 (per-mode reader id override) and the
 * format-mode companion fields landed in `ToolMode`.
 */
class ToolModePatchesTest {

    private class Mode(
        override val id: String,
        override val verb: String = "",
        override val executionStyle: String = ExecutionStyles.ON_THE_FLY,
        override val resultReaderId: String? = null,
        override val formattingOutputMode: String = FormattingOutputModes.STDOUT,
    ) : ToolMode

    @Test
    fun `G9 default resultReaderId is null so tool-level wins`() {
        val m = Mode(id = "analyze")
        assertNull(m.resultReaderId)
    }

    @Test
    fun `G9 mode override is observable`() {
        val m = Mode(id = "analyze", resultReaderId = "phpstan-json")
        assertEquals("phpstan-json", m.resultReaderId)
    }

    @Test
    fun `executionStyle constants are stable strings`() {
        assertEquals("on_the_fly", ExecutionStyles.ON_THE_FLY)
        assertEquals("format", ExecutionStyles.FORMAT)
        assertEquals("batch", ExecutionStyles.BATCH)
    }

    @Test
    fun `formattingOutputMode default is stdout`() {
        val m = Mode(id = "format", executionStyle = ExecutionStyles.FORMAT)
        assertEquals(FormattingOutputModes.STDOUT, m.formattingOutputMode)
    }

    @Test
    fun `formattingOutputMode can be overridden to in_place`() {
        val m = Mode(
            id = "format",
            executionStyle = ExecutionStyles.FORMAT,
            formattingOutputMode = FormattingOutputModes.IN_PLACE,
        )
        assertEquals("in_place", m.formattingOutputMode)
    }
}
