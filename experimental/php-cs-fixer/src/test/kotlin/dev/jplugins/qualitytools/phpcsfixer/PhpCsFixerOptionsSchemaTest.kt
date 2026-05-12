package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCsFixerOptionsSchemaTest {

    private val schema = PhpCsFixerOptionsSchema()

    @Test
    fun `toolId matches PhpCsFixerTool id`() {
        assertEquals("php-cs-fixer", schema.toolId)
    }

    @Test
    fun `default codingStandard is @PSR12`() {
        assertEquals("@PSR12", schema.codingStandard.default)
    }

    @Test
    fun `default customConfig is empty`() {
        assertEquals("", schema.customConfig.default)
    }

    @Test
    fun `default allowRiskyRules is false`() {
        assertFalse(schema.allowRiskyRules.default)
    }

    @Test
    fun `default formatAfterFix is false`() {
        assertFalse(schema.formatAfterFix.default)
    }

    @Test
    fun `customConfig is marked as a path spec`() {
        assertTrue("customConfig must report isPath=true", schema.customConfig.isPath)
    }

    @Test
    fun `customConfig role is config_file for the path-aware rewriter`() {
        assertEquals("config_file", schema.customConfig.role)
    }

    @Test
    fun `specs list exposes all four options in declaration order`() {
        val keys = schema.specs.map { it.key }
        assertEquals(
            listOf("codingStandard", "customConfig", "allowRiskyRules", "formatAfterFix"),
            keys,
        )
    }

    @Test
    fun `mode schemas exist for format and dry-run`() {
        assertNotNull(schema.modeSchemas[PhpCsFixerOptionsSchema.MODE_FORMAT])
        assertNotNull(schema.modeSchemas[PhpCsFixerOptionsSchema.MODE_DRY_RUN])
    }

    @Test
    fun `Custom sentinel constant is the legacy value`() {
        // Legacy `PhpCSFixerOptionsConfiguration.getCodingStandard()`
        // uses the literal string "Custom"; preserving the exact
        // value keeps migrated profiles working.
        assertEquals("Custom", PhpCsFixerOptionsSchema.CUSTOM_STANDARD)
    }

    @Test
    fun `KNOWN_STANDARDS contains the legacy fallback list plus Custom`() {
        val known = PhpCsFixerOptionsSchema.KNOWN_STANDARDS
        assertTrue(known.contains("@PSR1"))
        assertTrue(known.contains("@PSR2"))
        assertTrue(known.contains("@PSR12"))
        assertTrue(known.contains("@Symfony"))
        assertTrue(known.contains("@DoctrineAnnotation"))
        assertTrue(known.contains("@PHP70Migration"))
        assertTrue(known.contains("@PHP71Migration"))
        assertTrue(known.contains("Custom"))
    }

    @Test
    fun `bag round-trips codingStandard string`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "@Symfony"
        assertEquals("@Symfony", bag[schema.codingStandard])
    }

    @Test
    fun `bag round-trips bool specs`() {
        val bag = MapOptionsBag()
        bag[schema.allowRiskyRules] = true
        bag[schema.formatAfterFix] = true
        assertTrue(bag[schema.allowRiskyRules])
        assertTrue(bag[schema.formatAfterFix])
    }

    @Test
    fun `bag round-trips customConfig path`() {
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "/abs/.php-cs-fixer.php"
        assertEquals("/abs/.php-cs-fixer.php", bag[schema.customConfig])
    }
}
