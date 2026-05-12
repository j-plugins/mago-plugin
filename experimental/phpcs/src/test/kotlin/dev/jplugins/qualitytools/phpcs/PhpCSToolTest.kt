package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.OutputFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCSToolTest {

    private val tool = PhpCSTool.instance

    @Test
    fun `id is phpcs`() {
        assertEquals("phpcs", tool.id)
    }

    @Test
    fun `displayName is PHP_CodeSniffer`() {
        assertEquals("PHP_CodeSniffer", tool.displayName)
    }

    @Test
    fun `supportedLanguageIds is PHP only`() {
        assertEquals(setOf("PHP"), tool.supportedLanguageIds)
    }

    @Test
    fun `resultReaderId is checkstyle xml`() {
        assertEquals(OutputFormats.CHECKSTYLE_XML, tool.resultReaderId)
    }

    @Test
    fun `inspectionShortNames preserves the single legacy name`() {
        assertEquals(
            setOf("PhpCSValidationInspection"),
            tool.inspectionShortNames,
        )
    }

    @Test
    fun `capabilities is lint and fix`() {
        assertEquals(setOf(Capabilities.LINT, Capabilities.FIX), tool.capabilities)
    }

    @Test
    fun `binaryValidator is PhpCSVersionValidator`() {
        assertSame(PhpCSVersionValidator, tool.binaryValidator)
    }

    @Test
    fun `optionsSchema is PhpCSOptionsSchema`() {
        assertNotNull(tool.optionsSchema)
        assertSame(PhpCSTool.schema, tool.optionsSchema)
    }

    @Test
    fun `tool exposes both lint and fix modes`() {
        assertEquals(listOf("lint", "fix"), tool.modes.map { it.id })
    }

    @Test
    fun `lint mode shape — verb empty, defaults emit checkstyle + no-colors, supports stdin not fix`() {
        val lint = tool.modes.first { it.id == "lint" }
        assertEquals("", lint.verb)
        assertEquals(ExecutionStyles.ON_THE_FLY, lint.executionStyle)
        assertTrue(lint.supportsStdin)
        assertFalse(lint.supportsFix)
        val raws = lint.defaultArgs.map { it.raw }
        assertEquals(listOf("--report=checkstyle", "--no-colors"), raws)
        assertTrue("--standard registered as a path-aware key", "--standard" in lint.pathArgKeys)
    }

    @Test
    fun `fix mode shape — manual execution, supportsFix=true (gap G10 pending)`() {
        val fix = tool.modes.first { it.id == "fix" }
        assertEquals("", fix.verb)
        assertEquals(ExecutionStyles.MANUAL, fix.executionStyle)
        assertFalse(fix.supportsStdin)
        assertTrue("fix mode advertises supportsFix", fix.supportsFix)
    }
}
