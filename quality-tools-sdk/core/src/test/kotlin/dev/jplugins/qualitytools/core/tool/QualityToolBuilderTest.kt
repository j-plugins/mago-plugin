package dev.jplugins.qualitytools.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the cycle-1 acceptance bullet "Pest in ≤ 4 LOC" and the
 * builder's defaulting behaviour.
 */
class QualityToolBuilderTest {

    @Test
    fun `four-LOC pest-like tool compiles and exposes correct shape`() {
        val pest: QualityTool = qualityTool("pest") {
            displayName = "Pest"
            languages("PHP")
            resultReaderId = "pest-junit-xml"
            mode("test") { verb = "test" }
        }

        assertEquals("pest", pest.id)
        assertEquals("Pest", pest.displayName)
        assertEquals(setOf("PHP"), pest.supportedLanguageIds)
        assertEquals("pest-junit-xml", pest.resultReaderId)
        assertEquals(1, pest.modes.size)
        assertEquals("test", pest.modes.first().id)
    }

    @Test
    fun `equality and hashCode are id-based per binding rule 11`() {
        val a = qualityTool("foo") {
            resultReaderId = "x"
            mode("m") {}
        }
        val b = qualityTool("foo") {
            resultReaderId = "y" // different reader, same id
            displayName = "Different label"
        }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = qualityTool("bar") { resultReaderId = "x"; mode("m") {} }
        assertTrue(a != c)
    }

    @Test
    fun `inspectionShortNames default to PascalCase Lint and Batch`() {
        val phpstanLike = qualityTool("phpstan") {
            resultReaderId = "checkstyle-xml"
            mode("analyze") {}
        }
        assertEquals(setOf("PhpstanLint", "PhpstanBatch"), phpstanLike.inspectionShortNames)
    }

    @Test
    fun `custom inspectionShortNames win over the default`() {
        val t = qualityTool("phpstan") {
            resultReaderId = "checkstyle-xml"
            inspectionShortNames.addAll(listOf("PhpStanGlobal", "PhpStanValidation"))
            mode("analyze") {}
        }
        assertEquals(setOf("PhpStanGlobal", "PhpStanValidation"), t.inspectionShortNames)
    }

    @Test
    fun `buildArgs lambda is invoked for each call`() {
        val tool = qualityTool("foo") {
            resultReaderId = "checkstyle-xml"
            mode("m") { defaultArgs.add(plainArg("--fast")) }
            buildArgs { _, mode, _ -> mode.defaultArgs + plainArg("--extra") }
        }
        val mode = tool.modes.first()
        val args = tool.buildArgs(
            FakeRunContext(tool),
            mode,
            FakeTarget(),
        )
        assertEquals(listOf("--fast", "--extra"), args.map { it.raw })
    }

    @Test
    fun `missing resultReaderId is a clear error at build()`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            qualityTool("broken") {
                displayName = "Broken"
                // no resultReaderId set
                mode("m") {}
            }
        }
        assertTrue("error message names the tool id", ex.message!!.contains("broken"))
    }

    @Test
    fun `binaryValidator slot is settable`() {
        val v = object : BinaryValidator {
            override fun validate(versionOutput: String) =
                SimpleValidationResult(true, "ok", "1.0")
        }
        val tool = qualityTool("foo") {
            resultReaderId = "x"
            mode("m") {}
            binaryValidator = v
        }
        assertSame(v, tool.binaryValidator)
    }

    @Test
    fun `ui defaults to Default; Hidden is opt-in`() {
        val visible = qualityTool("a") { resultReaderId = "x"; mode("m") {} }
        assertSame(ToolUi.Default, visible.ui)

        val hidden = qualityTool("b") {
            resultReaderId = "x"
            mode("m") {}
            ui = ToolUi.Hidden
        }
        assertSame(ToolUi.Hidden, hidden.ui)
    }

    @Test
    fun `optionsSchema default is empty schema with matching toolId`() {
        val t = qualityTool("x") { resultReaderId = "r"; mode("m") {} }
        assertEquals("x", t.optionsSchema.toolId)
        assertTrue(t.optionsSchema.specs.isEmpty())
    }

    @Test
    fun `mode declares all per-mode patches with sane defaults`() {
        val t = qualityTool("x") {
            resultReaderId = "r"
            mode("analyze") { verb = "analyze"; supportsStdin = true }
        }
        val m = t.modes.single()
        assertEquals("analyze", m.id)
        assertEquals("analyze", m.verb)
        assertEquals(ExecutionStyles.ON_THE_FLY, m.executionStyle)
        assertEquals(true, m.supportsStdin)
        assertEquals(null, m.resultReaderId) // tool-level wins
        assertEquals(FormattingOutputModes.STDOUT, m.formattingOutputMode)
    }

    @Test
    fun `tool reports as itself in toString for debuggability`() {
        val t = qualityTool("phpstan") { resultReaderId = "x"; mode("m") {} }
        assertNotNull(t.toString())
        assertTrue("toString includes id (debuggability rule 22)", "phpstan" in t.toString())
    }

    // --- minimal fixtures local to this test (no :testing dep on test classpath) ---
    private class FakeRunContext(override val tool: QualityTool) :
        dev.jplugins.qualitytools.core.context.ToolRunContext {
        override val projectId = "p"
        override val basePath: String? = null
        override val profile: dev.jplugins.qualitytools.core.profile.ConfigProfile
            get() = error("not needed for this test")
        override val scope: dev.jplugins.qualitytools.core.scope.ResolvedScope = NoopScope
        override val options: dev.jplugins.qualitytools.core.options.OptionsBag
            get() = error("not needed for this test")
        override val cancellation = dev.jplugins.qualitytools.core.context.CancellationToken.Never
        override val logger = dev.jplugins.qualitytools.core.context.QtLogger.NoOp
    }
    private object NoopScope : dev.jplugins.qualitytools.core.scope.ResolvedScope {
        override val workspaceDir = "/"
        override fun relativize(absolute: String) = absolute
    }
    private class FakeTarget : ToolTarget {
        override val normalizedPath = "/p/x.php"
        override fun toCliArg(scope: dev.jplugins.qualitytools.core.scope.ResolvedScope) =
            pathArg(normalizedPath)
    }
}
