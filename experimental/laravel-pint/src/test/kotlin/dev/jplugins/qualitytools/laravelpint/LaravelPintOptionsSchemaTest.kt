package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.PathSpec
import dev.jplugins.qualitytools.core.options.StringSpec
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaravelPintOptionsSchemaTest {

    private val schema = LaravelPintOptionsSchema()

    @Test
    fun `toolId matches LaravelPintTool ID`() {
        assertEquals(LaravelPintTool.ID, schema.toolId)
        assertEquals("laravel-pint", schema.toolId)
    }

    @Test
    fun `customConfig spec uses configured key and is a PathSpec`() {
        assertEquals(LaravelPintOptionsSchema.CUSTOM_CONFIG_KEY, schema.customConfig.key)
        assertEquals("customConfig", schema.customConfig.key)
        assertTrue(
            "customConfig must be a PathSpec so the rewriter can remap it",
            schema.customConfig is PathSpec,
        )
        assertTrue(schema.customConfig.isPath)
        assertEquals("config_file", schema.customConfig.role)
    }

    @Test
    fun `verbose spec defaults to false and is a BoolSpec`() {
        assertEquals(LaravelPintOptionsSchema.VERBOSE_KEY, schema.verbose.key)
        assertEquals("verbose", schema.verbose.key)
        assertTrue(schema.verbose is BoolSpec)
        assertFalse(schema.verbose.default)
    }

    @Test
    fun `top-level specs list contains exactly customConfig and verbose`() {
        val keys = schema.specs.map { it.key }.toSet()
        assertEquals(setOf("customConfig", "verbose"), keys)
    }

    @Test
    fun `format modeSchema exists with enabled + additionalArgs`() {
        val format = schema.modeSchemas[LaravelPintTool.MODE_FORMAT]
        assertNotNull("format modeSchema missing", format)
        val byKey = format!!.specs.associateBy { it.key }
        assertTrue(
            "format mode must have an `enabled` spec",
            byKey[LaravelPintOptionsSchema.MODE_FORMAT_ENABLED_KEY] is BoolSpec,
        )
        assertTrue(
            "format mode must have an `additionalArgs` spec",
            byKey[LaravelPintOptionsSchema.MODE_FORMAT_ADDITIONAL_ARGS_KEY] is StringSpec,
        )
    }

    @Test
    fun `format mode enabled defaults to true`() {
        assertTrue(schema.formatEnabled.default)
    }

    @Test
    fun `verbose round-trips through MapOptionsBag`() {
        val bag = MapOptionsBag()
        assertEquals(false, bag[schema.verbose])
        bag[schema.verbose] = true
        assertEquals(true, bag[schema.verbose])
    }

    @Test
    fun `customConfig round-trips a relative path`() {
        val bag = MapOptionsBag()
        assertEquals("", bag[schema.customConfig])
        bag[schema.customConfig] = "config/pint.json"
        assertEquals("config/pint.json", bag[schema.customConfig])
    }

    @Test
    fun `mode bag falls back to top-level for non-mode options`() {
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "pint.json"
        val modeBag = bag.mode(LaravelPintTool.MODE_FORMAT)
        // The mode overlay sees top-level config through fallback.
        assertEquals("pint.json", modeBag[schema.customConfig])
    }

    @Test
    fun `no modeSchema is registered for any non-format mode id`() {
        // Pint has exactly one mode; this pins the surface so we
        // notice if a future analyze/lint mode lands without a schema.
        assertEquals(setOf(LaravelPintTool.MODE_FORMAT), schema.modeSchemas.keys)
    }
}
