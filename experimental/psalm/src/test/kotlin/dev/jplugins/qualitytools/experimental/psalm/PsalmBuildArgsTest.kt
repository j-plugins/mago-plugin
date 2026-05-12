package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [PsalmBuildArgs.build] reproduces the legacy
 * `PsalmGlobalInspection.getCommandLineOptions(...)` output exactly,
 * for the four boolean toggles and with/without a config path.
 */
class PsalmBuildArgsTest {

    private val schema = PsalmTool.OPTIONS_SCHEMA
    private val tool = PsalmTool.INSTANCE
    private val analyzeMode = tool.modes.first { it.id == "analyze" }
    private val target = FakeTarget("/proj/src/Foo.php")

    private fun bagOf(vararg entries: Pair<String, String>): MapOptionsBag =
        MapOptionsBag(entries.toMap())

    private fun build(bag: MapOptionsBag): List<String> {
        val ctx = FakeRunContext(tool, bag)
        return PsalmBuildArgs.build(ctx, analyzeMode, target, schema).map { it.raw }
    }

    @Test
    fun `defaults emit only the mode defaults plus --monochrome plus the target`() {
        val args = build(bagOf())
        assertEquals(
            listOf(
                "--output-format=checkstyle",
                "--no-progress",
                "--no-cache",
                "--monochrome",
                "/proj/src/Foo.php",
            ),
            args,
        )
    }

    @Test
    fun `config path emits -c=PATH between defaults and --monochrome`() {
        val args = build(bagOf("config" to "/proj/psalm.xml"))
        assertEquals(
            listOf(
                "--output-format=checkstyle",
                "--no-progress",
                "--no-cache",
                "-c=/proj/psalm.xml",
                "--monochrome",
                "/proj/src/Foo.php",
            ),
            args,
        )
    }

    @Test
    fun `showInfo true emits --show-info=true`() {
        val args = build(bagOf("showInfo" to "true"))
        assertTrue("must contain --show-info=true", args.contains("--show-info=true"))
        // Order: after defaults, before --monochrome.
        val idxShow = args.indexOf("--show-info=true")
        val idxMono = args.indexOf("--monochrome")
        assertTrue(idxShow in 0 until idxMono)
    }

    @Test
    fun `showInfo false omits the flag`() {
        val args = build(bagOf("showInfo" to "false"))
        assertFalse(args.contains("--show-info=true"))
    }

    @Test
    fun `findUnusedCode true emits --find-unused-code`() {
        val args = build(bagOf("findUnusedCode" to "true"))
        assertTrue(args.contains("--find-unused-code"))
    }

    @Test
    fun `findUnusedCode false omits the flag`() {
        val args = build(bagOf("findUnusedCode" to "false"))
        assertFalse(args.contains("--find-unused-code"))
    }

    @Test
    fun `findUnusedPsalmSuppress true emits --find-unused-psalm-suppress`() {
        val args = build(bagOf("findUnusedPsalmSuppress" to "true"))
        assertTrue(args.contains("--find-unused-psalm-suppress"))
    }

    @Test
    fun `findUnusedPsalmSuppress false omits the flag`() {
        val args = build(bagOf("findUnusedPsalmSuppress" to "false"))
        assertFalse(args.contains("--find-unused-psalm-suppress"))
    }

    @Test
    fun `all four boolean toggles together preserve legacy emission order`() {
        val args = build(
            bagOf(
                "config" to "/proj/psalm.xml.dist",
                "showInfo" to "true",
                "findUnusedCode" to "true",
                "findUnusedPsalmSuppress" to "true",
            ),
        )
        assertEquals(
            listOf(
                "--output-format=checkstyle",
                "--no-progress",
                "--no-cache",
                "-c=/proj/psalm.xml.dist",
                "--show-info=true",
                "--find-unused-code",
                "--find-unused-psalm-suppress",
                "--monochrome",
                "/proj/src/Foo.php",
            ),
            args,
        )
    }

    @Test
    fun `target file is always last`() {
        val args = build(
            bagOf(
                "config" to "/c.xml",
                "showInfo" to "true",
                "findUnusedCode" to "true",
                "findUnusedPsalmSuppress" to "true",
            ),
        )
        assertEquals("/proj/src/Foo.php", args.last())
    }

    @Test
    fun `empty config path is treated as absent (no -c emitted)`() {
        val args = build(bagOf("config" to ""))
        assertFalse("blank config string must not yield -c", args.any { it.startsWith("-c=") })
    }

    @Test
    fun `c argument is path-aware via kvPathArg`() {
        // The -c arg carries a pathPrefix so the path-aware rewriter
        // can remap it under remote interpreters; this is what
        // pathArgKeys=["-c"] on the mode and kvPathArg in buildArgs
        // promise to deliver.
        val ctx = FakeRunContext(tool, bagOf("config" to "/proj/psalm.xml"))
        val args = PsalmBuildArgs.build(ctx, analyzeMode, target, schema)
        val cArg = args.first { it.raw.startsWith("-c=") }
        assertTrue(cArg.isPath)
        assertEquals("-c=", cArg.pathPrefix)
    }
}
