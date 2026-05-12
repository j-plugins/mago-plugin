package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.OutputFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shape assertions on the [PhpStanTool] registration. Work plan §C.1
 * lists this set of properties verbatim.
 */
class PhpStanToolTest {

    private val tool = PhpStanTool

    @Test
    fun `id is phpstan`() {
        assertEquals("phpstan", tool.id)
    }

    @Test
    fun `displayName is PHPStan`() {
        assertEquals("PHPStan", tool.displayName)
    }

    @Test
    fun `supportedLanguageIds is exactly PHP`() {
        assertEquals(setOf("PHP"), tool.supportedLanguageIds)
    }

    @Test
    fun `inspectionShortNames preserve the legacy two short-names`() {
        assertEquals(
            setOf("PhpStanGlobal", "PhpStanValidation"),
            tool.inspectionShortNames,
        )
    }

    @Test
    fun `capabilities advertise analyze only`() {
        assertEquals(setOf(Capabilities.ANALYZE), tool.capabilities)
        assertEquals(setOf("analyze"), tool.capabilities)
    }

    @Test
    fun `resultReaderId is checkstyle-xml at the tool level`() {
        assertEquals(OutputFormats.CHECKSTYLE_XML, tool.resultReaderId)
    }

    @Test
    fun `single mode analyze is declared with the right verb`() {
        assertEquals(1, tool.modes.size)
        val mode = tool.modes.single()
        assertEquals("analyze", mode.id)
        assertEquals("analyze", mode.verb)
        assertEquals(ExecutionStyles.ON_THE_FLY, mode.executionStyle)
        assertFalse("PHPStan never reads stdin", mode.supportsStdin)
        assertFalse("PHPStan never emits fixes", mode.supportsFix)
    }

    @Test
    fun `mode resultReaderId is null so the tool-level value wins`() {
        // Tier-1 patch G9: mode.resultReaderId null = inherit.
        assertNull(tool.modes.single().resultReaderId)
    }

    @Test
    fun `mode declares the path-aware key set for the rewriter`() {
        assertEquals(
            setOf("-c", "--configuration", "-a", "--autoload-file"),
            tool.modes.single().pathArgKeys,
        )
    }

    @Test
    fun `optionsSchema is a PhpStanOptionsSchema instance`() {
        assertTrue(tool.optionsSchema is PhpStanOptionsSchema)
        assertEquals("phpstan", tool.optionsSchema.toolId)
    }

    @Test
    fun `binaryValidator is the shared PhpStanVersionValidator`() {
        assertNotNull(tool.binaryValidator)
        assertSame(PhpStanVersionValidator, tool.binaryValidator)
    }

    @Test
    fun `tool toString includes the id for debuggability rule 22`() {
        assertTrue("toString must contain id", "phpstan" in tool.toString())
    }

    @Test
    fun `phpStanSchema accessor returns the same schema instance`() {
        assertSame(tool.optionsSchema, tool.phpStanSchema)
    }
}
