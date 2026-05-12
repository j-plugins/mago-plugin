package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.IntSpec
import dev.jplugins.qualitytools.core.options.PathSpec
import dev.jplugins.qualitytools.core.options.StringSpec
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCSOptionsSchemaTest {

    private val schema = PhpCSOptionsSchema()

    @Test
    fun `toolId is phpcs`() {
        assertEquals("phpcs", schema.toolId)
    }

    @Test
    fun `codingStandard defaults to PSR12`() {
        assertTrue(schema.codingStandard is StringSpec)
        assertEquals("PSR12", schema.codingStandard.default)
    }

    @Test
    fun `customRuleset is a PathSpec marked isPath`() {
        assertTrue(schema.customRuleset is PathSpec)
        assertTrue("customRuleset is path-aware", schema.customRuleset.isPath)
        assertEquals("", schema.customRuleset.default)
    }

    @Test
    fun `showSniffNames defaults to false`() {
        assertTrue(schema.showSniffNames is BoolSpec)
        assertEquals(false, schema.showSniffNames.default)
    }

    @Test
    fun `phpcbfPath defaults to empty string`() {
        assertTrue(schema.phpcbfPath is StringSpec)
        assertEquals("", schema.phpcbfPath.default)
    }

    @Test
    fun `severity is bounded 1 to 10 with default 5`() {
        assertTrue(schema.severity is IntSpec)
        assertEquals(5, schema.severity.default)
        assertEquals(1..10, schema.severity.range)
        // Out-of-range decode returns null per the spec contract.
        assertNull(schema.severity.decode("0"))
        assertNull(schema.severity.decode("11"))
        assertEquals(5, schema.severity.decode("5"))
        assertEquals(10, schema.severity.decode("10"))
    }

    @Test
    fun `specs list contains exactly the five tool-level specs`() {
        assertEquals(
            listOf(
                schema.codingStandard,
                schema.customRuleset,
                schema.showSniffNames,
                schema.phpcbfPath,
                schema.severity,
            ),
            schema.specs,
        )
    }

    @Test
    fun `modeSchemas exposes lint and fix in the expected order`() {
        assertEquals(setOf("lint", "fix"), schema.modeSchemas.keys)
        assertSame(schema.lintMode, schema.modeSchemas["lint"])
        assertSame(schema.fixMode, schema.modeSchemas["fix"])
    }

    @Test
    fun `lint mode schema declares enabled and additionalArgs`() {
        val keys = schema.lintMode.specs.map { it.key }
        assertEquals(listOf("enabled", "additionalArgs"), keys)
    }

    @Test
    fun `fix mode schema declares enabled and additionalArgs`() {
        val keys = schema.fixMode.specs.map { it.key }
        assertEquals(listOf("enabled", "additionalArgs"), keys)
    }

    @Test
    fun `severity encodes and decodes through OptionsBag end-to-end`() {
        val bag = MapOptionsBag()
        bag[schema.severity] = 7
        assertEquals(7, bag[schema.severity])
    }

    @Test
    fun `OptionsBag round-trips customRuleset path`() {
        val bag = MapOptionsBag()
        bag[schema.customRuleset] = "/proj/phpcs.xml"
        assertEquals("/proj/phpcs.xml", bag[schema.customRuleset])
    }
}
