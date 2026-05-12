package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.message.SeverityLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins the wire values of the public constants. These strings appear
 * in `:ui`'s `SeverityMapping`, in user XML, in plugin authors'
 * code, and in the legacy `<inspection_tool>` profile files. A
 * rename is a major-version break (rule 6 / rule 19).
 */
class ConstantsTest {

    @Test
    fun `SeverityLevels values are stable strings`() {
        assertEquals("error", SeverityLevels.ERROR)
        assertEquals("warning", SeverityLevels.WARNING)
        assertEquals("weak_warning", SeverityLevels.WEAK_WARNING)
        assertEquals("info", SeverityLevels.INFO)
        assertEquals("hint", SeverityLevels.HINT)
        assertEquals("internal_error", SeverityLevels.INTERNAL_ERROR)
    }

    @Test
    fun `ExecutionStyles values are stable strings`() {
        assertEquals("on_the_fly", ExecutionStyles.ON_THE_FLY)
        assertEquals("on_save", ExecutionStyles.ON_SAVE)
        assertEquals("manual", ExecutionStyles.MANUAL)
        assertEquals("batch", ExecutionStyles.BATCH)
        assertEquals("format", ExecutionStyles.FORMAT)
    }

    @Test
    fun `FormattingOutputModes values are stable strings`() {
        assertEquals("stdout", FormattingOutputModes.STDOUT)
        assertEquals("in_place", FormattingOutputModes.IN_PLACE)
    }

    @Test
    fun `Capabilities values are stable strings`() {
        assertEquals("lint", Capabilities.LINT)
        assertEquals("analyze", Capabilities.ANALYZE)
        assertEquals("fix", Capabilities.FIX)
        assertEquals("format", Capabilities.FORMAT)
        assertEquals("baseline", Capabilities.BASELINE)
        assertEquals("batch", Capabilities.BATCH)
        assertEquals("inspect", Capabilities.INSPECT)
    }

    @Test
    fun `OutputFormats values are stable strings`() {
        assertEquals("checkstyle-xml", OutputFormats.CHECKSTYLE_XML)
        assertEquals("json-lines", OutputFormats.JSON_LINES)
        assertEquals("sarif", OutputFormats.SARIF)
        assertEquals("line", OutputFormats.LINE)
        assertEquals("udiff", OutputFormats.UDIFF)
        assertEquals("inherit", OutputFormats.INHERIT)
    }

    @Test
    fun `ToolUi Default and Hidden are distinct`() {
        assertNotSame(ToolUi.Default, ToolUi.Hidden)
    }

    @Test
    fun `ToolUi Default is a stable singleton`() {
        assertSame(ToolUi.Default, ToolUi.Default)
        assertSame(ToolUi.Hidden, ToolUi.Hidden)
    }
}
