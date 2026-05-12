package dev.jplugins.qualitytools.experimental.phpstan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip CLI assertions for [buildPhpStanArgs].
 *
 * Each case pins the *exact* token sequence the legacy
 * `PhpStanGlobalInspection.getCommandLineOptions` (decompiled at
 * `/tmp/decomp/com/jetbrains/php/tools/quality/phpstan/PhpStanGlobalInspection.java`
 * lines 117-138) would emit for the same inputs — modulo the
 * documented `kvPathArg` shift for `-c` / `-a`.
 *
 * Tests are deterministic and require no IntelliJ classpath.
 */
class PhpStanBuildArgsTest {

    private val tool = PhpStanTool
    private val schema = tool.optionsSchema as PhpStanOptionsSchema
    private val mode = tool.modes.single()

    @Test
    fun `level 8 without config produces the expected CLI verbatim`() {
        val options = MapOptionsBag().apply {
            set(schema.level, 8)
            // memoryLimit, config, autoload left at default
        }
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/project/src/Foo.php")

        val args = buildPhpStanArgs(ctx, mode, target).map { it.raw }

        assertEquals(
            listOf(
                "analyze",
                "--level=8",
                "--memory-limit=2G",
                "--error-format=checkstyle",
                "--no-progress",
                "--no-ansi",
                "--no-interaction",
                "/project/src/Foo.php",
            ),
            args,
        )
    }

    @Test
    fun `config wins over level and autoload is emitted when set`() {
        val options = MapOptionsBag().apply {
            set(schema.config, "phpstan.neon")
            set(schema.autoload, "src/bootstrap.php")
            set(schema.level, 8) // intentionally set; must be ignored
        }
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/project/src/Foo.php")

        val args = buildPhpStanArgs(ctx, mode, target).map { it.raw }

        assertEquals(
            listOf(
                "analyze",
                "-c=phpstan.neon",
                "-a=src/bootstrap.php",
                "--memory-limit=2G",
                "--error-format=checkstyle",
                "--no-progress",
                "--no-ansi",
                "--no-interaction",
                "/project/src/Foo.php",
            ),
            args,
        )
        // None of the args should mention --level when config is set.
        assertTrue(
            "no --level when config is set",
            args.none { it.startsWith("--level") },
        )
    }

    @Test
    fun `memory limit is always present even when defaulted`() {
        val options = MapOptionsBag()
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/project/src/X.php")
        val args = buildPhpStanArgs(ctx, mode, target).map { it.raw }

        assertTrue(
            "memory limit must always appear",
            args.any { it == "--memory-limit=2G" },
        )
    }

    @Test
    fun `custom memory limit overrides the default`() {
        val options = MapOptionsBag().apply { set(schema.memoryLimit, "4G") }
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/x.php")
        val args = buildPhpStanArgs(ctx, mode, target).map { it.raw }

        assertTrue(args.contains("--memory-limit=4G"))
        assertTrue(
            "old default must not appear when overridden",
            args.none { it == "--memory-limit=2G" },
        )
    }

    @Test
    fun `target file is always the last argument`() {
        val options = MapOptionsBag().apply {
            set(schema.config, "ci/phpstan.neon")
            set(schema.autoload, "vendor/autoload.php")
        }
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/project/tests/MyTest.php")
        val args = buildPhpStanArgs(ctx, mode, target)

        assertEquals("/project/tests/MyTest.php", args.last().raw)
        assertTrue("the last arg is a path", args.last().isPath)
    }

    @Test
    fun `config arg is marked as path-aware for the rewriter`() {
        val options = MapOptionsBag().apply { set(schema.config, "phpstan.neon") }
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/p/Y.php")
        val args = buildPhpStanArgs(ctx, mode, target)

        val configArg = args.single { it.raw.startsWith("-c=") }
        assertTrue("config is path-aware", configArg.isPath)
        assertEquals("-c=", configArg.pathPrefix)
    }

    @Test
    fun `autoload arg is marked as path-aware for the rewriter`() {
        val options = MapOptionsBag().apply { set(schema.autoload, "boot.php") }
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/p/Z.php")
        val args = buildPhpStanArgs(ctx, mode, target)

        val autoloadArg = args.single { it.raw.startsWith("-a=") }
        assertTrue("autoload is path-aware", autoloadArg.isPath)
        assertEquals("-a=", autoloadArg.pathPrefix)
    }

    @Test
    fun `plain flags are not path-aware`() {
        val options = MapOptionsBag()
        val ctx = FakeRunContext(tool, options)
        val target = FakeTarget("/p/a.php")
        val args = buildPhpStanArgs(ctx, mode, target)

        val noProgress = args.single { it.raw == "--no-progress" }
        assertEquals(false, noProgress.isPath)
        assertNull(noProgress.pathPrefix)
    }
}
