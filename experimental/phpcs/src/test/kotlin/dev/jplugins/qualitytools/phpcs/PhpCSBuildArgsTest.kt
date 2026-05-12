package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference: legacy `PhpCSAnnotatorProxy.getOptions` — see the
 * spec for the exact arg shape. The new SDK pattern puts
 * `mode.defaultArgs` first, then the target, then dynamic options.
 */
class PhpCSBuildArgsTest {

    private val tool = PhpCSTool.instance
    private val schema = PhpCSTool.schema
    private val lintMode = tool.modes.first { it.id == "lint" }
    private val fixMode = tool.modes.first { it.id == "fix" }

    private fun raws(args: List<ToolArg>): List<String> = args.map { it.raw }

    @Test
    fun `lint mode emits PSR12 standard as a plain non-path arg`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "PSR12"
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget("/proj/src/Foo.php"),
        )
        val raw = raws(args)
        assertEquals("--report=checkstyle", raw[0])
        assertEquals("--no-colors", raw[1])
        assertEquals("/proj/src/Foo.php", raw[2])
        assertTrue("standard=PSR12 is present", "--standard=PSR12" in raw)
        // PSR12 is a name, not a path — no kvPathArg shape.
        val standardArg = args.first { it.raw == "--standard=PSR12" }
        assertFalse("PSR12 is not a path", standardArg.isPath)
    }

    @Test
    fun `lint with Custom standard resolves to ruleset path via kvPathArg`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "Custom"
        bag[schema.customRuleset] = "/proj/phpcs.xml"
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget("/proj/src/Foo.php"),
        )
        val standardArg = args.firstOrNull { it.raw.startsWith("--standard=") }
        requireNotNull(standardArg) { "missing --standard arg" }
        assertEquals("--standard=/proj/phpcs.xml", standardArg.raw)
        assertTrue("custom ruleset arg is path-aware", standardArg.isPath)
        assertEquals("--standard=", standardArg.pathPrefix)
    }

    @Test
    fun `lint with Custom but no ruleset omits --standard entirely`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "Custom"
        // customRuleset left empty (its default).
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget(),
        )
        val raw = raws(args)
        assertTrue(
            "no --standard when Custom + empty ruleset",
            raw.none { it.startsWith("--standard") },
        )
    }

    @Test
    fun `lint emits -s when showSniffNames is true`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "PSR12"
        bag[schema.showSniffNames] = true
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget(),
        )
        assertTrue("-s present", raws(args).contains("-s"))
    }

    @Test
    fun `lint omits -s by default`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "PSR12"
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget(),
        )
        assertFalse("-s absent", raws(args).contains("-s"))
    }

    @Test
    fun `lint emits the severity flag at the default value`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "PSR12"
        // not setting severity explicitly — default is 5.
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget(),
        )
        assertTrue("severity=5 emitted by default", raws(args).contains("--severity=5"))
    }

    @Test
    fun `lint honours a non-default severity`() {
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "PSR12"
        bag[schema.severity] = 8
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget(),
        )
        assertTrue("severity=8 emitted", raws(args).contains("--severity=8"))
    }

    @Test
    fun `mode-local additionalArgs are appended last`() {
        // Use the parent bag — the per-mode overlay built by MapOptionsBag
        // falls back to the parent when the key is missing in the child,
        // which is the SDK's documented contract.
        val bag = MapOptionsBag()
        bag[schema.codingStandard] = "PSR12"
        bag[schema.lintMode.additionalArgs] = "--cache=.phpcs-cache --parallel=4"
        val args = PhpCSBuildArgs.build(
            FakeRunContext(tool, bag),
            lintMode,
            FakeTarget(),
        )
        val raw = raws(args)
        assertTrue("--cache present", raw.contains("--cache=.phpcs-cache"))
        assertTrue("--parallel present", raw.contains("--parallel=4"))
        // The two extra tokens are after the last "built-in" token.
        val severityIx = raw.indexOf("--severity=5")
        val cacheIx = raw.indexOf("--cache=.phpcs-cache")
        assertTrue("additionalArgs after severity", cacheIx > severityIx)
    }

    @Test
    fun `looksLikePath recognises absolute and separator-bearing values`() {
        assertTrue(PhpCSBuildArgs.looksLikePath("/abs/path"))
        assertTrue(PhpCSBuildArgs.looksLikePath("relative/with/sep"))
        assertFalse(PhpCSBuildArgs.looksLikePath("PSR12"))
        assertFalse(PhpCSBuildArgs.looksLikePath(""))
    }
}
