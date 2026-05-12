package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.PathSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the schema's four legacy fields. Locks the public contract:
 * the legacy `PsalmOptionsConfiguration` exposed exactly four
 * settings (config + three booleans) and the SDK port must match
 * one-for-one — otherwise legacy XML migration breaks.
 */
class PsalmOptionsSchemaTest {

    private val schema = PsalmOptionsSchema()

    @Test
    fun `toolId is psalm`() {
        assertEquals("psalm", schema.toolId)
    }

    @Test
    fun `specs list has exactly four entries`() {
        assertEquals(4, schema.specs.size)
    }

    @Test
    fun `every legacy key is present`() {
        val keys = schema.specs.map { it.key }.toSet()
        assertEquals(
            setOf("config", "showInfo", "findUnusedCode", "findUnusedPsalmSuppress"),
            keys,
        )
    }

    @Test
    fun `config is a PathSpec (marks isPath for the rewriter)`() {
        assertTrue(schema.config is PathSpec)
        assertTrue("config.isPath must be true", schema.config.isPath)
        assertEquals("", schema.config.default)
    }

    @Test
    fun `showInfo defaults to false`() {
        assertTrue(schema.showInfo is BoolSpec)
        assertFalse(schema.showInfo.default)
    }

    @Test
    fun `findUnusedCode defaults to false`() {
        assertFalse(schema.findUnusedCode.default)
    }

    @Test
    fun `findUnusedPsalmSuppress defaults to false`() {
        assertFalse(schema.findUnusedPsalmSuppress.default)
    }

    @Test
    fun `analyze mode schema is registered`() {
        val mode = schema.modeSchemas[PsalmOptionsSchema.MODE_ANALYZE]
        assertNotNull("modeSchemas must contain 'analyze'", mode)
        val keys = mode!!.specs.map { it.key }.toSet()
        assertEquals(setOf("enabled", "additionalArgs"), keys)
    }

    @Test
    fun `analyze enabled defaults to true`() {
        assertTrue(schema.enabled.default)
    }

    @Test
    fun `analyze additionalArgs defaults to empty`() {
        assertEquals("", schema.additionalArgs.default)
    }

    @Test
    fun `modeSchemas only contains analyze (single-mode tool)`() {
        assertEquals(setOf("analyze"), schema.modeSchemas.keys)
    }
}
