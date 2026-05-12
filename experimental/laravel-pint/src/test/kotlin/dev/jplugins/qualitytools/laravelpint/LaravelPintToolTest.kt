package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.FormattingOutputModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LaravelPintToolTest {

    private val tool = LaravelPintTool()

    @Test
    fun `id is the canonical hyphenated form`() {
        assertEquals("laravel-pint", tool.id)
        assertEquals(LaravelPintTool.ID, tool.id)
    }

    @Test
    fun `displayName is human-readable`() {
        assertEquals("Laravel Pint", tool.displayName)
    }

    @Test
    fun `supportedLanguageIds contains PHP only`() {
        assertEquals(setOf("PHP"), tool.supportedLanguageIds)
    }

    @Test
    fun `capabilities expose only format (first cut)`() {
        // The port plan's eventual surface is "format" + "analyze"
        // (when the on-the-fly mode lands); for now the first cut
        // is format-only.
        assertEquals(setOf(Capabilities.FORMAT), tool.capabilities)
        assertEquals(setOf("format"), tool.capabilities)
    }

    @Test
    fun `exactly one mode is registered with id format`() {
        assertEquals(1, tool.modes.size)
        val mode = tool.modes.single()
        assertEquals(LaravelPintTool.MODE_FORMAT, mode.id)
        assertEquals("format", mode.id)
    }

    @Test
    fun `format mode uses format execution style and in_place output`() {
        val mode = tool.modes.single()
        assertEquals(ExecutionStyles.FORMAT, mode.executionStyle)
        assertEquals(FormattingOutputModes.IN_PLACE, mode.formattingOutputMode)
    }

    @Test
    fun `format mode does NOT support stdin`() {
        // Legacy Pint cannot read PHP source over stdin reliably; we
        // pin supportsStdin=false here to lock that contract in.
        assertFalse(tool.modes.single().supportsStdin)
    }

    @Test
    fun `format mode declares empty defaultArgs (Pint takes file directly)`() {
        // Pint's bare invocation against a path already rewrites in
        // place; no flags belong here in `defaultArgs`.
        assertTrue(tool.modes.single().defaultArgs.isEmpty())
    }

    @Test
    fun `inspectionShortNames preserves the legacy snake_case verbatim`() {
        // The legacy plugin's `LaravelPintValidationInspection` short
        // name is `Laravel_Pint_validation_tool` (not `LaravelPintLint`
        // or `LaravelPintValidation`). "Fixing" the casing breaks every
        // user's persisted inspection profile XML — the SDK rule 33
        // bullet (non-PascalCase tolerated) is what permits this.
        assertEquals(
            setOf("Laravel_Pint_validation_tool"),
            tool.inspectionShortNames,
        )
        // Constant identity check: don't let anyone "normalize" this.
        assertEquals(
            "Laravel_Pint_validation_tool",
            LaravelPintTool.LEGACY_INSPECTION_SHORT_NAME,
        )
        // And it must NOT be the default `id`.lint / `id`.batch shape.
        assertFalse(tool.inspectionShortNames.contains("laravel-pint.lint"))
        assertFalse(tool.inspectionShortNames.contains("laravel-pint.batch"))
    }

    @Test
    fun `binaryValidator is wired and is the LaravelPintVersionValidator singleton`() {
        val v = tool.binaryValidator
        assertNotNull(v)
        assertSame(LaravelPintVersionValidator, v)
    }

    @Test
    fun `optionsSchema is a LaravelPintOptionsSchema and shares toolId`() {
        assertTrue(tool.optionsSchema is LaravelPintOptionsSchema)
        assertEquals(tool.id, tool.optionsSchema.toolId)
        // The `schema` accessor returns the same instance as the
        // interface-typed `optionsSchema`, so callers in `buildArgs`
        // can use it for type-safe spec lookup.
        assertSame(tool.optionsSchema, tool.schema)
    }

    @Test
    fun `resultReaderId matches the CS-Fixer diff-XML reader`() {
        // Pint reuses CS-Fixer's diff-XML reader once the on-the-fly
        // mode lands; we pin the reader id here so both ports converge
        // on the same string.
        assertEquals("phpcsfixer-diff-xml", tool.resultReaderId)
    }

    @Test
    fun `acceptedSourceTypeIds defaults to any (star)`() {
        // We don't restrict to a specific source type — Local,
        // Composer, and PHP-interpreter sources are all valid hosts
        // for Pint.
        assertEquals(setOf("*"), tool.acceptedSourceTypeIds)
    }
}
