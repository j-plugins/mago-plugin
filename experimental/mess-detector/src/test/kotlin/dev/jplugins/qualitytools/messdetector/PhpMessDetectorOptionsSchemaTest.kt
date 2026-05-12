package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpMessDetectorOptionsSchemaTest {

    private val schema = PhpMessDetectorOptionsSchema()

    @Test
    fun `toolId matches PhpMessDetectorTool id`() {
        assertEquals("phpmd", schema.toolId)
    }

    // ---- legacy defaults (six bools) -----------------------------------

    @Test
    fun `default cleancode is false - parity with legacy UI which never exposed it`() {
        // Plan §4.4: cleancode is a port addition; default stays off
        // so existing user output doesn't change after migration.
        assertFalse(schema.cleancode.default)
    }

    @Test
    fun `default codesize is true`() {
        // Legacy `MessDetectorOptionsConfiguration` ships codesize on.
        assertTrue(schema.codesize.default)
    }

    @Test
    fun `default controversial is false`() {
        // Legacy default: controversial is opt-in.
        assertFalse(schema.controversial.default)
    }

    @Test
    fun `default design is true`() {
        assertTrue(schema.design.default)
    }

    @Test
    fun `default naming is true`() {
        assertTrue(schema.naming.default)
    }

    @Test
    fun `default unusedcode is true`() {
        assertTrue(schema.unusedcode.default)
    }

    @Test
    fun `default customRulesetFiles is empty CSV`() {
        // Legacy `List<RulesetDescriptor>` is empty by default.
        assertEquals("", schema.customRulesetFiles.default)
    }

    // ---- spec list shape ----------------------------------------------

    @Test
    fun `specs list exposes all seven options in declaration order`() {
        // The six bool specs in canonical (alphabetical-on-toggle) order
        // plus the CSV custom-rulesets string at the end.
        val keys = schema.specs.map { it.key }
        assertEquals(
            listOf(
                "cleancode",
                "codesize",
                "controversial",
                "design",
                "naming",
                "unusedcode",
                "customRulesetFiles",
            ),
            keys,
        )
    }

    @Test
    fun `BUILTIN_RULESETS list matches the six bool keys`() {
        assertEquals(
            listOf(
                "cleancode",
                "codesize",
                "controversial",
                "design",
                "naming",
                "unusedcode",
            ),
            PhpMessDetectorOptionsSchema.BUILTIN_RULESETS,
        )
    }

    @Test
    fun `customRulesetFiles is a plain string spec - not a path spec`() {
        // Gap G27 / G28: the CSV string holds multiple paths, but the
        // OptionSpec itself can't be marked isPath=true (would
        // path-rewrite the whole CSV including built-in tokens).
        // Per-element path-awareness lands with compositeKvPathArg.
        assertFalse(
            "customRulesetFiles MUST NOT be a PathSpec until gap G28 lands",
            schema.customRulesetFiles.isPath,
        )
    }

    // ---- mode schemas --------------------------------------------------

    @Test
    fun `mode schema exists for analyze`() {
        assertNotNull(schema.modeSchemas[PhpMessDetectorOptionsSchema.MODE_ANALYZE])
    }

    @Test
    fun `only one mode schema is declared`() {
        // Legacy plugin has a single inspection / single mode; the
        // schema should not advertise modes phpmd doesn't have.
        assertEquals(setOf("analyze"), schema.modeSchemas.keys)
    }

    // ---- bag round-trips ----------------------------------------------

    @Test
    fun `bag round-trips all six bool specs`() {
        val bag = MapOptionsBag()
        bag[schema.cleancode] = true
        bag[schema.codesize] = false
        bag[schema.controversial] = true
        bag[schema.design] = false
        bag[schema.naming] = false
        bag[schema.unusedcode] = false
        assertTrue(bag[schema.cleancode])
        assertFalse(bag[schema.codesize])
        assertTrue(bag[schema.controversial])
        assertFalse(bag[schema.design])
        assertFalse(bag[schema.naming])
        assertFalse(bag[schema.unusedcode])
    }

    @Test
    fun `bag round-trips customRulesetFiles CSV`() {
        val bag = MapOptionsBag()
        bag[schema.customRulesetFiles] = "/abs/a.xml,/abs/b.xml"
        assertEquals("/abs/a.xml,/abs/b.xml", bag[schema.customRulesetFiles])
    }
}
