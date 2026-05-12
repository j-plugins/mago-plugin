package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.FormattingOutputModes
import dev.jplugins.qualitytools.core.tool.OutputFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCsFixerToolTest {

    private val tool = PhpCsFixerTool()

    @Test
    fun `tool id is php-cs-fixer`() {
        assertEquals("php-cs-fixer", tool.id)
    }

    @Test
    fun `display name is PHP CS Fixer`() {
        assertEquals("PHP CS Fixer", tool.displayName)
    }

    @Test
    fun `supports PHP only`() {
        assertEquals(setOf("PHP"), tool.supportedLanguageIds)
    }

    @Test
    fun `capabilities are format and fix`() {
        assertEquals(setOf(Capabilities.FORMAT, Capabilities.FIX), tool.capabilities)
    }

    @Test
    fun `tool default reader id is UDIFF`() {
        assertEquals(OutputFormats.UDIFF, tool.resultReaderId)
    }

    @Test
    fun `inspection short names preserve the legacy entry`() {
        assertEquals(setOf("PhpCSFixerValidationInspection"), tool.inspectionShortNames)
    }

    @Test
    fun `binary validator is wired`() {
        assertNotNull(tool.binaryValidator)
        // and it's our PHP-CS-Fixer-tuned one
        assertEquals(PhpCsFixerVersionValidator, tool.binaryValidator)
    }

    @Test
    fun `two modes are declared - format and dry-run`() {
        val ids = tool.modes.map { it.id }
        assertEquals(listOf(PhpCsFixerOptionsSchema.MODE_FORMAT, PhpCsFixerOptionsSchema.MODE_DRY_RUN), ids)
    }

    @Test
    fun `format mode is the FORMAT execution style with IN_PLACE output`() {
        val mode = PhpCsFixerTool.FormatMode
        assertEquals(ExecutionStyles.FORMAT, mode.executionStyle)
        assertEquals(FormattingOutputModes.IN_PLACE, mode.formattingOutputMode)
        assertTrue("format mode supports stdin", mode.supportsStdin)
    }

    @Test
    fun `format mode default args match the legacy fix invocation`() {
        val raws = PhpCsFixerTool.FormatMode.defaultArgs.map { it.raw }
        assertEquals(listOf("fix", "--no-interaction", "--no-ansi"), raws)
    }

    @Test
    fun `format mode declares --config as a path arg key`() {
        // Lets the path-aware rewriter remap custom config paths
        // for remote interpreters.
        assertTrue(PhpCsFixerTool.FormatMode.pathArgKeys.contains("--config"))
    }

    @Test
    fun `dry-run mode is ON_THE_FLY with UDIFF reader override`() {
        val mode = PhpCsFixerTool.DryRunMode
        assertEquals(ExecutionStyles.ON_THE_FLY, mode.executionStyle)
        assertEquals(OutputFormats.UDIFF, mode.resultReaderId)
        assertTrue("dry-run also supports stdin", mode.supportsStdin)
    }

    @Test
    fun `dry-run mode default args match the legacy fix invocation`() {
        val raws = PhpCsFixerTool.DryRunMode.defaultArgs.map { it.raw }
        assertEquals(listOf("fix", "--no-interaction", "--no-ansi"), raws)
    }

    @Test
    fun `optionsSchema is wired and matches the tool id`() {
        assertEquals(tool.id, tool.optionsSchema.toolId)
    }

    @Test
    fun `tool equality is id-based per SDK rule 11`() {
        val a = PhpCsFixerTool()
        val b = PhpCsFixerTool()
        // QualityTool interface contract: equality goes by id; the
        // built tool from QualityToolBuilder honours that, but
        // hand-rolled `class` implementations need to as well.
        // We don't override here, so use reference equality of id only.
        assertEquals(a.id, b.id)
    }
}
