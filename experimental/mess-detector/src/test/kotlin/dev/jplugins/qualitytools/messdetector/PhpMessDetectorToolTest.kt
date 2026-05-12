package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpMessDetectorToolTest {

    private val tool = PhpMessDetectorTool()

    @Test
    fun `tool id is phpmd`() {
        assertEquals("phpmd", tool.id)
    }

    @Test
    fun `display name is PHP Mess Detector`() {
        assertEquals("PHP Mess Detector", tool.displayName)
    }

    @Test
    fun `supports PHP only`() {
        assertEquals(setOf("PHP"), tool.supportedLanguageIds)
    }

    @Test
    fun `capabilities are analyze`() {
        // Plan: phpmd is a project-wide analyser, not a fixer/formatter.
        assertEquals(setOf(Capabilities.ANALYZE), tool.capabilities)
    }

    @Test
    fun `tool default reader id is phpmd-xml`() {
        // The reader itself is not yet bundled; see TODO.md.
        assertEquals("phpmd-xml", tool.resultReaderId)
    }

    @Test
    fun `inspection short names preserve the single legacy entry`() {
        // Plan §4.8: phpmd has ONE legacy inspection. NOT the
        // lint+batch pair PHPStan uses.
        assertEquals(
            setOf("MessDetectorValidationInspection"),
            tool.inspectionShortNames,
        )
    }

    @Test
    fun `inspection short names set has size 1`() {
        // Explicit cardinality assertion — guards against an
        // accidental fan-out to "<id>.lint" / "<id>.batch" default
        // if the field is ever forgotten.
        assertEquals(1, tool.inspectionShortNames.size)
    }

    @Test
    fun `binary validator is wired and is the phpmd-tuned one`() {
        assertNotNull(tool.binaryValidator)
        assertEquals(PhpMessDetectorVersionValidator, tool.binaryValidator)
    }

    @Test
    fun `one mode is declared - analyze`() {
        val ids = tool.modes.map { it.id }
        assertEquals(listOf(PhpMessDetectorOptionsSchema.MODE_ANALYZE), ids)
    }

    @Test
    fun `analyze mode verb is empty - phpmd is positional`() {
        // No subcommand: phpmd takes `<target> <format> <rulesets>`.
        assertEquals("", PhpMessDetectorTool.AnalyzeMode.verb)
    }

    @Test
    fun `analyze mode is ON_THE_FLY execution style`() {
        assertEquals(
            ExecutionStyles.ON_THE_FLY,
            PhpMessDetectorTool.AnalyzeMode.executionStyle,
        )
    }

    @Test
    fun `analyze mode default args are empty - constructed dynamically in buildArgs`() {
        // Plan: target + format + rulesets are all built dynamically
        // in PhpMessDetectorBuildArgs; no static prefix.
        assertTrue(PhpMessDetectorTool.AnalyzeMode.defaultArgs.isEmpty())
    }

    @Test
    fun `analyze mode does not advertise stdin or fix support`() {
        assertFalse(
            "stdin is gated on the tempfile registry switch (legacy)",
            PhpMessDetectorTool.AnalyzeMode.supportsStdin,
        )
        assertFalse(
            "phpmd does not emit fixes",
            PhpMessDetectorTool.AnalyzeMode.supportsFix,
        )
    }

    @Test
    fun `optionsSchema is wired and matches the tool id`() {
        assertEquals(tool.id, tool.optionsSchema.toolId)
    }

    @Test
    fun `tool equality - id is stable across instances`() {
        val a = PhpMessDetectorTool()
        val b = PhpMessDetectorTool()
        assertEquals(a.id, b.id)
    }
}
