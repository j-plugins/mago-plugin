package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the CLI shape produced by the legacy
 * `LaravelPintAnnotatorProxy.getCommandLineOptions(project, paths)`
 * for the slice we currently surface (file + `--config` + `--verbose`
 * + free-form extras). `--preset` / `--dirty` / `--test` /
 * `--format=xml` belong to follow-up phases (see `TODO.md`).
 */
class LaravelPintBuildArgsTest {

    private val tool = LaravelPintTool()
    private val schema = tool.schema
    private val formatMode = tool.modes.single { it.id == LaravelPintTool.MODE_FORMAT }

    @Test
    fun `default options emit just the target file`() {
        val bag = MapOptionsBag()
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertEquals(listOf("/proj/src/Foo.php"), args.map { it.raw })
        // The target is path-aware so the rewriter can remap when
        // running over a remote interpreter.
        assertTrue(args.single().isPath)
    }

    @Test
    fun `customConfig adds --config flag with key-value path arg`() {
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "./pint.json"
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertEquals(
            listOf("/proj/src/Foo.php", "--config=./pint.json"),
            args.map { it.raw },
        )
        // `kvPathArg` correctly marks the value half as a path with prefix.
        val configArg = args.last()
        assertTrue("--config value must be path-aware", configArg.isPath)
        assertEquals("--config=", configArg.pathPrefix)
    }

    @Test
    fun `blank customConfig does NOT emit --config`() {
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "   "  // whitespace-only
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertEquals(listOf("/proj/src/Foo.php"), args.map { it.raw })
        assertFalse(args.any { it.raw.startsWith("--config") })
    }

    @Test
    fun `verbose toggle adds --verbose flag`() {
        val bag = MapOptionsBag()
        bag[schema.verbose] = true
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertEquals(
            listOf("/proj/src/Foo.php", "--verbose"),
            args.map { it.raw },
        )
        // `--verbose` is a plain flag, not a path.
        assertFalse(args.last().isPath)
    }

    @Test
    fun `verbose false (default) does NOT emit --verbose`() {
        val ctx = FakeRunContext(tool, options = MapOptionsBag())
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertFalse(args.any { it.raw == "--verbose" })
    }

    @Test
    fun `customConfig + verbose together preserves order`() {
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "config/pint.json"
        bag[schema.verbose] = true
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        // Order: target → --config → --verbose. The legacy plugin
        // emits config-before-preset; we preserve target-first then
        // pre-`--verbose` config to match the legacy proxy.
        assertEquals(
            listOf("/proj/src/Foo.php", "--config=config/pint.json", "--verbose"),
            args.map { it.raw },
        )
    }

    @Test
    fun `additionalArgs are appended after the standard flags`() {
        val bag = MapOptionsBag()
        bag[schema.verbose] = true
        val modeBag = bag.mode(LaravelPintTool.MODE_FORMAT)
        modeBag[schema.formatAdditionalArgs] = "--bail --using=laravel"
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertEquals(
            listOf(
                "/proj/src/Foo.php",
                "--verbose",
                "--bail",
                "--using=laravel",
            ),
            args.map { it.raw },
        )
    }

    @Test
    fun `blank additionalArgs do nothing`() {
        val bag = MapOptionsBag()
        val modeBag = bag.mode(LaravelPintTool.MODE_FORMAT)
        modeBag[schema.formatAdditionalArgs] = "   \t  "
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        )
        assertEquals(listOf("/proj/src/Foo.php"), args.map { it.raw })
    }

    @Test
    fun `does NOT emit --test, --format, or -vvv in format mode`() {
        // Regression bullet from the port plan §8: the legacy plugin's
        // analyze path appended `--test --format=xml -vvv`; reformat
        // mode stripped them by `options.size() - 1` indexing — a
        // fragile pattern. The SDK port keeps these args out of the
        // format mode entirely; this test pins that.
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "pint.json"
        bag[schema.verbose] = true
        val ctx = FakeRunContext(tool, options = bag)
        val args = LaravelPintBuildArgs.build(
            ctx, formatMode, FakeTarget("/proj/src/Foo.php"), schema,
        ).map { it.raw }
        assertFalse(args.any { it == "--test" })
        assertFalse(args.any { it.startsWith("--format=") })
        assertFalse(args.any { it == "-vvv" })
    }

    @Test
    fun `tool buildArgs delegates to the same builder`() {
        // Sanity-check: calling LaravelPintTool.buildArgs directly is
        // equivalent to calling LaravelPintBuildArgs.build.
        val bag = MapOptionsBag()
        bag[schema.customConfig] = "pint.json"
        val ctx = FakeRunContext(tool, options = bag)
        val target = FakeTarget("/proj/src/Foo.php")
        val direct = LaravelPintBuildArgs.build(ctx, formatMode, target, schema)
        val viaTool = tool.buildArgs(ctx, formatMode, target)
        assertEquals(direct.map { it.raw }, viaTool.map { it.raw })
    }
}
