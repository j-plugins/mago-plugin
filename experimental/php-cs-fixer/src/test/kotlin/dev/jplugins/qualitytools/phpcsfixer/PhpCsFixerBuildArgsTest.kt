package dev.jplugins.qualitytools.phpcsfixer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [PhpCsFixerBuildArgs] reproduces the legacy CLI shape
 * documented in `PhpCSFixerAnnotatorProxy` / `PhpCSFixerReformatFile`.
 */
class PhpCsFixerBuildArgsTest {

    private val tool = PhpCsFixerTool()
    private val schema = tool.schema

    @Test
    fun `format mode in-place produces fix flags and target without --dry-run`() {
        val bag = emptyBag()
        // default codingStandard = @PSR12, allowRiskyRules = false
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget(), schema,
        ).map { it.raw }

        assertEquals(
            listOf(
                "fix",
                "--no-interaction",
                "--no-ansi",
                "--using-cache=no",
                "--rules=@PSR12",
                "--allow-risky=no",
                "/proj/src/Foo.php",
            ),
            raw,
        )
        assertFalse("format mode never emits --dry-run", raw.contains("--dry-run"))
        assertFalse("format mode never emits --format=json", raw.any { it.startsWith("--format=") })
    }

    @Test
    fun `dry-run mode emits --dry-run and --format=json`() {
        val bag = emptyBag()
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.DryRunMode, TestTarget(), schema,
        ).map { it.raw }

        assertEquals(
            listOf(
                "fix",
                "--no-interaction",
                "--no-ansi",
                "--using-cache=no",
                "--rules=@PSR12",
                "--allow-risky=no",
                "--dry-run",
                "--format=json",
                "/proj/src/Foo.php",
            ),
            raw,
        )
    }

    @Test
    fun `allowRiskyRules=true emits --allow-risky=yes`() {
        val bag = emptyBag()
        bag[schema.allowRiskyRules] = true
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget(), schema,
        ).map { it.raw }

        assertTrue(raw.contains("--allow-risky=yes"))
        assertFalse(raw.contains("--allow-risky=no"))
    }

    @Test
    fun `allowRiskyRules=false emits --allow-risky=no`() {
        val bag = emptyBag()
        bag[schema.allowRiskyRules] = false
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget(), schema,
        ).map { it.raw }

        assertTrue(raw.contains("--allow-risky=no"))
        assertFalse(raw.contains("--allow-risky=yes"))
    }

    @Test
    fun `Custom standard emits --config and suppresses --allow-risky`() {
        val bag = emptyBag()
        bag[schema.codingStandard] = PhpCsFixerOptionsSchema.CUSTOM_STANDARD
        bag[schema.customConfig] = "/proj/.php-cs-fixer.php"
        // even with risky=true, --allow-risky must be suppressed for Custom
        bag[schema.allowRiskyRules] = true
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget(), schema,
        ).map { it.raw }

        assertTrue(
            "expected --config=/proj/.php-cs-fixer.php in $raw",
            raw.contains("--config=/proj/.php-cs-fixer.php"),
        )
        assertFalse("no --rules= when Custom", raw.any { it.startsWith("--rules=") })
        assertFalse("no --allow-risky when Custom", raw.any { it.startsWith("--allow-risky") })
    }

    @Test
    fun `Custom standard with empty path omits --config entirely`() {
        // Defensive: if the user picks Custom but never provides a
        // path, we don't emit a partial --config flag.
        val bag = emptyBag()
        bag[schema.codingStandard] = PhpCsFixerOptionsSchema.CUSTOM_STANDARD
        bag[schema.customConfig] = ""
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget(), schema,
        ).map { it.raw }

        assertFalse(raw.any { it.startsWith("--config") })
    }

    @Test
    fun `target is always the final positional argument`() {
        val bag = emptyBag()
        val ctx = TestRunContext(tool, bag)

        val list = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget("/proj/src/Bar.php"), schema,
        )

        val last = list.last()
        assertEquals("/proj/src/Bar.php", last.raw)
        assertTrue("target is a path arg", last.isPath)
    }

    @Test
    fun `dry-run with custom config still emits --dry-run and --format=json`() {
        val bag = emptyBag()
        bag[schema.codingStandard] = PhpCsFixerOptionsSchema.CUSTOM_STANDARD
        bag[schema.customConfig] = "/proj/.php-cs-fixer.dist.php"
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.DryRunMode, TestTarget(), schema,
        ).map { it.raw }

        assertTrue(raw.contains("--dry-run"))
        assertTrue(raw.contains("--format=json"))
        assertTrue(raw.contains("--config=/proj/.php-cs-fixer.dist.php"))
    }

    @Test
    fun `--using-cache=no is always present`() {
        // Stale-cache prevention for IDE-driven runs.
        for (mode in listOf(PhpCsFixerTool.FormatMode, PhpCsFixerTool.DryRunMode)) {
            val bag = emptyBag()
            val ctx = TestRunContext(tool, bag)
            val raw = PhpCsFixerBuildArgs.build(ctx, mode, TestTarget(), schema).map { it.raw }
            assertTrue("mode=${mode.id} missing --using-cache=no", raw.contains("--using-cache=no"))
        }
    }

    @Test
    fun `non-custom non-default coding standard is emitted via --rules=`() {
        val bag = emptyBag()
        bag[schema.codingStandard] = "@Symfony"
        val ctx = TestRunContext(tool, bag)

        val raw = PhpCsFixerBuildArgs.build(
            ctx, PhpCsFixerTool.FormatMode, TestTarget(), schema,
        ).map { it.raw }

        assertTrue(raw.contains("--rules=@Symfony"))
    }

    @Test
    fun `tool dispatches buildArgs through PhpCsFixerBuildArgs`() {
        // Cover the QualityTool.buildArgs override path so the
        // wiring between tool and builder is exercised.
        val bag = emptyBag()
        val ctx = TestRunContext(tool, bag)

        val raw = tool.buildArgs(ctx, PhpCsFixerTool.FormatMode, TestTarget()).map { it.raw }

        assertEquals("fix", raw.first())
        assertEquals("/proj/src/Foo.php", raw.last())
    }
}
