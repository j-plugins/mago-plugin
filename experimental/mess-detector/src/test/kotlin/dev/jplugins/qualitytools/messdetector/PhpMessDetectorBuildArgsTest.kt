package dev.jplugins.qualitytools.messdetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [PhpMessDetectorBuildArgs] reproduces the legacy
 * positional CLI documented in
 * `com.jetbrains.php.tools.quality.messDetector.MessDetectorAnnotator
 * .getOptions(...)` and the ruleset join in
 * `MessDetectorValidationInspection.getRuleSetsOption(...)`.
 *
 *     phpmd <target-file> <format> <rulesets-csv>
 *
 * The `<rulesets-csv>` is one token; built-in ruleset short-names
 * (`cleancode|codesize|controversial|design|naming|unusedcode`) and
 * absolute paths to custom XML files share that token, separated by
 * commas — see plan §4.2.
 */
class PhpMessDetectorBuildArgsTest {

    private val tool = PhpMessDetectorTool()
    private val schema = tool.schema

    @Test
    fun `default toggles emit codesize design naming unusedcode CSV`() {
        // Schema defaults: codesize=design=naming=unusedcode=true,
        // cleancode=controversial=false, no custom paths.
        val ctx = TestRunContext(tool, emptyBag())

        val raw = PhpMessDetectorBuildArgs.build(
            ctx, PhpMessDetectorTool.AnalyzeMode, TestTarget(), schema,
        ).map { it.raw }

        assertEquals(
            listOf(
                "/proj/src/Foo.php",
                "xml",
                "codesize,design,naming,unusedcode",
            ),
            raw,
        )
    }

    @Test
    fun `all six toggles on produce canonical-order CSV`() {
        // cleancode first (port addition), then legacy order:
        // codesize, design, naming, controversial, unusedcode.
        val bag = emptyBag()
        bag[schema.cleancode] = true
        bag[schema.codesize] = true
        bag[schema.controversial] = true
        bag[schema.design] = true
        bag[schema.naming] = true
        bag[schema.unusedcode] = true
        val ctx = TestRunContext(tool, bag)

        val raw = PhpMessDetectorBuildArgs.build(
            ctx, PhpMessDetectorTool.AnalyzeMode, TestTarget(), schema,
        ).map { it.raw }

        assertEquals(
            listOf(
                "/proj/src/Foo.php",
                "xml",
                "cleancode,codesize,design,naming,controversial,unusedcode",
            ),
            raw,
        )
    }

    @Test
    fun `custom-ruleset CSV is merged after built-in toggles`() {
        val bag = emptyBag()
        // default built-ins (codesize, design, naming, unusedcode)
        bag[schema.customRulesetFiles] =
            "/abs/team/foo.xml,/abs/team/bar.xml"
        val ctx = TestRunContext(tool, bag)

        val raw = PhpMessDetectorBuildArgs.build(
            ctx, PhpMessDetectorTool.AnalyzeMode, TestTarget(), schema,
        ).map { it.raw }

        assertEquals(
            listOf(
                "/proj/src/Foo.php",
                "xml",
                "codesize,design,naming,unusedcode,/abs/team/foo.xml,/abs/team/bar.xml",
            ),
            raw,
        )
    }

    @Test
    fun `custom-ruleset CSV with whitespace around entries is trimmed`() {
        // Tolerate copy-pasted lists with stray spaces around commas.
        val bag = emptyBag()
        bag[schema.cleancode] = false
        bag[schema.codesize] = false
        bag[schema.design] = false
        bag[schema.naming] = false
        bag[schema.controversial] = false
        bag[schema.unusedcode] = false
        bag[schema.customRulesetFiles] =
            " /abs/a.xml , /abs/b.xml ,, /abs/c.xml "
        val ctx = TestRunContext(tool, bag)

        val csv = PhpMessDetectorBuildArgs.composeRulesetsCsv(ctx, schema)

        assertEquals("/abs/a.xml,/abs/b.xml,/abs/c.xml", csv)
    }

    @Test
    fun `no toggles and no custom paths produces empty CSV`() {
        // Legacy `MessDetectorAnnotator.getOptions` short-circuits to
        // null when this is empty — that decision lives outside
        // `buildArgs`. Here we just verify the CSV is `""`.
        val bag = emptyBag()
        bag[schema.cleancode] = false
        bag[schema.codesize] = false
        bag[schema.design] = false
        bag[schema.naming] = false
        bag[schema.controversial] = false
        bag[schema.unusedcode] = false
        val ctx = TestRunContext(tool, bag)

        val csv = PhpMessDetectorBuildArgs.composeRulesetsCsv(ctx, schema)

        assertEquals("", csv)
    }

    @Test
    fun `built-in only with cleancode and codesize matches enable-order`() {
        val bag = emptyBag()
        bag[schema.cleancode] = true
        bag[schema.codesize] = true
        // Turn the legacy defaults off.
        bag[schema.design] = false
        bag[schema.naming] = false
        bag[schema.controversial] = false
        bag[schema.unusedcode] = false
        val ctx = TestRunContext(tool, bag)

        val csv = PhpMessDetectorBuildArgs.composeRulesetsCsv(ctx, schema)

        assertEquals("cleancode,codesize", csv)
    }

    @Test
    fun `target is always the first positional argument`() {
        val bag = emptyBag()
        val ctx = TestRunContext(tool, bag)

        val list = PhpMessDetectorBuildArgs.build(
            ctx,
            PhpMessDetectorTool.AnalyzeMode,
            TestTarget("/proj/src/Bar.php"),
            schema,
        )

        val first = list.first()
        assertEquals("/proj/src/Bar.php", first.raw)
        assertTrue("target is a path arg", first.isPath)
    }

    @Test
    fun `format token is always xml`() {
        // Legacy plugin hard-codes "xml" as the format; the
        // PhpmdXmlReader assumes the same. Future flexibility (sarif?)
        // would mean a new mode, not a flag.
        val ctx = TestRunContext(tool, emptyBag())
        val list = PhpMessDetectorBuildArgs.build(
            ctx, PhpMessDetectorTool.AnalyzeMode, TestTarget(), schema,
        )
        assertEquals("xml", list[1].raw)
    }

    @Test
    fun `rulesets CSV token has no leading or trailing comma`() {
        // Defensive against the "empty toggle then customPath" join.
        val bag = emptyBag()
        bag[schema.cleancode] = false
        bag[schema.codesize] = false
        bag[schema.design] = false
        bag[schema.naming] = false
        bag[schema.controversial] = false
        bag[schema.unusedcode] = false
        bag[schema.customRulesetFiles] = "/abs/team.xml"
        val ctx = TestRunContext(tool, bag)

        val csv = PhpMessDetectorBuildArgs.composeRulesetsCsv(ctx, schema)

        assertFalse("no leading comma", csv.startsWith(","))
        assertFalse("no trailing comma", csv.endsWith(","))
        assertEquals("/abs/team.xml", csv)
    }

    @Test
    fun `tool dispatches buildArgs through PhpMessDetectorBuildArgs`() {
        // Cover the QualityTool.buildArgs override path.
        val ctx = TestRunContext(tool, emptyBag())

        val raw = tool.buildArgs(
            ctx, PhpMessDetectorTool.AnalyzeMode, TestTarget(),
        ).map { it.raw }

        assertEquals("/proj/src/Foo.php", raw.first())
        assertEquals("xml", raw[1])
        assertEquals("codesize,design,naming,unusedcode", raw[2])
    }
}
