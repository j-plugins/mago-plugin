package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.IntSpec
import dev.jplugins.qualitytools.core.options.PathSpec
import dev.jplugins.qualitytools.core.options.StringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schema-shape assertions. The work plan §C.1 requires "5 specs
 * present with right defaults"; we expand to also cover types, ranges,
 * and the mode-schema overlay so a regression in the schema fails
 * here loudly.
 */
class PhpStanOptionsSchemaTest {

    private val schema = PhpStanOptionsSchema()

    @Test
    fun `tool id matches the registered tool`() {
        assertEquals("phpstan", schema.toolId)
    }

    @Test
    fun `exactly five top-level specs are declared`() {
        assertEquals(5, schema.specs.size)
        val keys = schema.specs.map { it.key }.toSet()
        assertEquals(
            setOf("fullProject", "memoryLimit", "level", "config", "autoload"),
            keys,
        )
    }

    @Test
    fun `fullProject default is false`() {
        assertTrue(schema.fullProject is BoolSpec)
        assertFalse(schema.fullProject.default)
    }

    @Test
    fun `memoryLimit default is 2G`() {
        assertTrue(schema.memoryLimit is StringSpec)
        assertEquals("2G", schema.memoryLimit.default)
    }

    @Test
    fun `level default is 4 with range 0 to 10`() {
        // `schema.level` is statically typed `IntSpec`; the explicit
        // check guards against a future widening of the field's type.
        assertTrue(schema.level is IntSpec)
        assertEquals(4, schema.level.default)
        val range = schema.level.range
        assertNotNull(range)
        assertEquals(0, range!!.first)
        assertEquals(10, range.last)
    }

    @Test
    fun `level decode rejects values outside the range`() {
        assertNull(schema.level.decode("11"))
        assertNull(schema.level.decode("-1"))
        assertEquals(0, schema.level.decode("0"))
        assertEquals(10, schema.level.decode("10"))
    }

    @Test
    fun `config and autoload are path specs marked isPath`() {
        assertTrue(schema.config is PathSpec)
        assertTrue(schema.autoload is PathSpec)
        assertTrue(schema.config.isPath)
        assertTrue(schema.autoload.isPath)
        assertEquals("", schema.config.default)
        assertEquals("", schema.autoload.default)
    }

    @Test
    fun `analyze mode schema is declared with two specs`() {
        val mode = schema.modeSchemas[PhpStanOptionsSchema.ANALYZE_MODE_ID]
        assertNotNull("analyze mode schema must be declared", mode)
        assertEquals(2, mode!!.specs.size)
        assertEquals(
            setOf("enabled", "additionalArgs"),
            mode.specs.map { it.key }.toSet(),
        )
    }

    @Test
    fun `analyze enabled default is true`() {
        assertTrue(schema.analyzeMode.enabled.default)
    }

    @Test
    fun `analyze additionalArgs default is empty`() {
        assertEquals("", schema.analyzeMode.additionalArgs.default)
    }

    @Test
    fun `specs preserve insertion order so UI can render them deterministically`() {
        val orderedKeys = schema.specs.map { it.key }
        assertEquals(
            listOf("fullProject", "memoryLimit", "level", "config", "autoload"),
            orderedKeys,
        )
    }
}
