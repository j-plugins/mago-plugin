package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.php.composer.ComposerJson
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Composer auto-detection for Psalm. Two scenarios:
 *
 *  1. A typical `require-dev` declaration registers `vimeo/psalm` —
 *     `ComposerJson.requires(...)` returns `true`.
 *  2. The two-name config-file fallback resolves `psalm.xml.dist` when
 *     `psalm.xml` is absent (legacy `PsalmComposerConfig` behaviour;
 *     documented in `docs/psalm/psalm-port-plan.md` §3b).
 */
class PsalmComposerDescriptorTest {

    private val descriptor = PsalmComposerToolDescriptor.DESCRIPTOR
    private val schema = PsalmOptionsSchema()

    @Test
    fun `package name and bin name are the conventional values`() {
        assertEquals("vimeo/psalm", descriptor.packageName)
        assertEquals("psalm", descriptor.binName)
    }

    @Test
    fun `composer json with require-dev vimeo psalm star is recognised`() {
        val composerText = """
            {
                "require-dev": {"vimeo/psalm": "*"}
            }
        """.trimIndent()
        val composer = ComposerJson.parse(composerText)
        assertTrue(composer.requires("vimeo/psalm"))
    }

    @Test
    fun `composer json with require psalm is recognised`() {
        val composerText = """
            {
                "require": {"vimeo/psalm": "^5.15"}
            }
        """.trimIndent()
        val composer = ComposerJson.parse(composerText)
        assertTrue(composer.requires("vimeo/psalm"))
    }

    @Test
    fun `composer json without psalm is not recognised`() {
        val composerText = """
            {
                "require-dev": {"phpstan/phpstan": "^1.10"}
            }
        """.trimIndent()
        val composer = ComposerJson.parse(composerText)
        assertFalse(composer.requires("vimeo/psalm"))
    }

    @Test
    fun `configFileNames lists psalm xml first then psalm xml dist`() {
        assertEquals(
            listOf("psalm.xml", "psalm.xml.dist"),
            descriptor.configFileNames,
        )
    }

    @Test
    fun `applyDiscoveredConfigFile picks psalm xml when both exist`() {
        val bag = MapOptionsBag()
        val existing = setOf("/proj/psalm.xml", "/proj/psalm.xml.dist")
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.config,
            exists = existing::contains,
        )
        assertEquals("/proj/psalm.xml", found)
        assertEquals("/proj/psalm.xml", bag[schema.config])
    }

    @Test
    fun `applyDiscoveredConfigFile falls back to psalm xml dist when only dist exists`() {
        val bag = MapOptionsBag()
        val existing = setOf("/proj/psalm.xml.dist")
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.config,
            exists = existing::contains,
        )
        assertEquals("/proj/psalm.xml.dist", found)
        assertEquals("/proj/psalm.xml.dist", bag[schema.config])
    }

    @Test
    fun `applyDiscoveredConfigFile returns null when neither file exists`() {
        val bag = MapOptionsBag()
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.config,
            exists = { false },
        )
        assertNull(found)
        assertEquals("", bag[schema.config])
    }

    @Test
    fun `scriptKey defaults to psalm`() {
        assertEquals("psalm", descriptor.scriptKey)
    }

    @Test
    fun `scriptArgs is empty (no memory-limit-style mappings for Psalm)`() {
        assertTrue(descriptor.scriptArgs.isEmpty())
    }
}
