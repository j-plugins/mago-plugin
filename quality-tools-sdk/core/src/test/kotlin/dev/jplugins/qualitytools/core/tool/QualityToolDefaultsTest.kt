package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.options.OptionsSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Exercises the interface defaults of `QualityTool` that the builder
 * doesn't always hit (`capabilities`, `acceptedSourceTypeIds`,
 * `binaryValidator`, `ui`, `inspectionShortNames`).
 */
class QualityToolDefaultsTest {

    private val minimal = object : QualityTool {
        override val id = "demo"
        override val displayName = "Demo"
        override val supportedLanguageIds = setOf("PHP")
        override val modes = listOf<ToolMode>()
        override val resultReaderId = "demo-reader"
        override val optionsSchema = object : OptionsSchema {
            override val toolId = "demo"
            override val specs = emptyList<dev.jplugins.qualitytools.core.options.OptionSpec<*>>()
        }
        override fun buildArgs(ctx: ToolRunContext, mode: ToolMode, target: ToolTarget) = emptyList<ToolArg>()
    }

    @Test
    fun `capabilities default is empty set`() {
        assertEquals(emptySet<String>(), minimal.capabilities)
    }

    @Test
    fun `acceptedSourceTypeIds default is wildcard`() {
        assertEquals(setOf("*"), minimal.acceptedSourceTypeIds)
    }

    @Test
    fun `binaryValidator default is null`() {
        assertNull(minimal.binaryValidator)
    }

    @Test
    fun `ui default is Default`() {
        assertSame(ToolUi.Default, minimal.ui)
    }

    @Test
    fun `inspectionShortNames default contains lint and batch`() {
        // The interface default is `setOf("${id}.lint", "${id}.batch")`.
        assertEquals(setOf("demo.lint", "demo.batch"), minimal.inspectionShortNames)
    }

    @Test
    fun `ToolMode interface defaults are conservative`() {
        val mode: ToolMode = object : ToolMode {
            override val id = "m"
        }
        assertEquals("m", mode.displayName)            // default == id
        assertEquals("", mode.verb)
        assertEquals(ExecutionStyles.ON_THE_FLY, mode.executionStyle)
        assertEquals(emptyList<ToolArg>(), mode.defaultArgs)
        kotlin.test.assertFalse(mode.supportsStdin)
        kotlin.test.assertFalse(mode.supportsFix)
        assertNull(mode.resultReaderId)
        assertEquals(FormattingOutputModes.STDOUT, mode.formattingOutputMode)
        assertEquals(emptySet<String>(), mode.pathArgKeys)
    }
}
