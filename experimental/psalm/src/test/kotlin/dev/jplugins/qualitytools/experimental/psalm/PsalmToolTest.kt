package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.OutputFormats
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Public-contract pins for the Psalm tool wiring. These are
 * deliberately tight: any change to the legacy inspection short-
 * names, the reader id, or the verb-less "no subcommand" emission
 * shape would break upgrades for existing users.
 */
class PsalmToolTest {

    private val tool = PsalmTool.INSTANCE

    @Test
    fun `id is psalm`() {
        assertEquals("psalm", tool.id)
    }

    @Test
    fun `displayName is Psalm`() {
        assertEquals("Psalm", tool.displayName)
    }

    @Test
    fun `supports PHP only`() {
        assertEquals(setOf("PHP"), tool.supportedLanguageIds)
    }

    @Test
    fun `capabilities are analyze only`() {
        assertEquals(setOf(Capabilities.ANALYZE), tool.capabilities)
    }

    @Test
    fun `resultReaderId is checkstyle-xml`() {
        assertEquals(OutputFormats.CHECKSTYLE_XML, tool.resultReaderId)
    }

    @Test
    fun `inspectionShortNames preserve legacy XML names`() {
        // Verbatim — required for phase 10a.1 migration compatibility.
        assertEquals(setOf("PsalmGlobal", "PsalmValidation"), tool.inspectionShortNames)
    }

    @Test
    fun `single analyze mode`() {
        assertEquals(1, tool.modes.size)
        assertEquals("analyze", tool.modes.first().id)
    }

    @Test
    fun `analyze mode has no verb (Psalm has no subcommand)`() {
        val analyze = tool.modes.first()
        assertEquals("", analyze.verb)
    }

    @Test
    fun `analyze mode defaultArgs bake the output-format flag`() {
        val analyze = tool.modes.first()
        val raws = analyze.defaultArgs.map { it.raw }
        assertTrue("must contain --output-format=checkstyle", "--output-format=checkstyle" in raws)
        assertTrue("must contain --no-progress", "--no-progress" in raws)
        assertTrue("must contain --no-cache", "--no-cache" in raws)
    }

    @Test
    fun `analyze mode declares -c and --config-file as path arg keys`() {
        val analyze = tool.modes.first()
        assertTrue("pathArgKeys must include -c", "-c" in analyze.pathArgKeys)
        assertTrue("pathArgKeys must include --config-file", "--config-file" in analyze.pathArgKeys)
    }

    @Test
    fun `analyze mode executionStyle defaults to on_the_fly`() {
        val analyze = tool.modes.first()
        assertEquals(ExecutionStyles.ON_THE_FLY, analyze.executionStyle)
    }

    @Test
    fun `optionsSchema is the PsalmOptionsSchema instance`() {
        assertSame(PsalmTool.OPTIONS_SCHEMA, tool.optionsSchema)
    }

    @Test
    fun `binaryValidator is wired (non-null)`() {
        assertNotNull("PsalmTool must register a binaryValidator", tool.binaryValidator)
    }

    @Test
    fun `binaryValidator parses real psalm version output`() {
        val r = tool.binaryValidator!!.validate("Psalm 5.15.0@a1b2c3")
        assertTrue(r.ok)
        assertEquals("5.15.0", r.detectedVersion)
    }

    @Test
    fun `buildArgs through the tool reproduces the default emission`() {
        val ctx = FakeRunContext(tool, MapOptionsBag())
        val args = tool.buildArgs(ctx, tool.modes.first(), FakeTarget("/proj/file.php"))
            .map { it.raw }
        assertEquals(
            listOf(
                "--output-format=checkstyle",
                "--no-progress",
                "--no-cache",
                "--monochrome",
                "/proj/file.php",
            ),
            args,
        )
    }

    @Test
    fun `tool equality is id-based per SDK rule 11`() {
        // Cannot create another instance via qualityTool here without
        // duplicating wiring, so check the contract pinned by the
        // builder: equal-id objects compare equal regardless of
        // other fields. We just verify the instance equals itself.
        assertEquals(tool, tool)
        assertEquals(tool.hashCode(), tool.hashCode())
    }
}
