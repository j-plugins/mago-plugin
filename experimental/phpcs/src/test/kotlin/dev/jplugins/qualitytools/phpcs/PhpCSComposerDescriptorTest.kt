package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.options.string
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhpCSComposerDescriptorTest {

    @Test
    fun `descriptor declares the squizlabs package and phpcs binary`() {
        assertEquals("squizlabs/php_codesniffer", PhpCSComposerDescriptor.packageName)
        assertEquals("phpcs", PhpCSComposerDescriptor.binName)
    }

    @Test
    fun `descriptor declares phpcs xml dist xml dot dist in that order`() {
        assertEquals(
            listOf("phpcs.xml", "phpcs.xml.dist", "phpcs.dist.xml"),
            PhpCSComposerDescriptor.configFileNames,
        )
    }

    @Test
    fun `scriptKey is phpcs and scriptArgs is empty in this scope`() {
        assertEquals("phpcs", PhpCSComposerDescriptor.scriptKey)
        assertEquals(emptyList<Any>(), PhpCSComposerDescriptor.scriptArgs)
    }

    @Test
    fun `applyDiscoveredConfigFile picks phpcs xml when present`() {
        val bag = MapOptionsBag()
        val rulesetSpec = string("ruleset")
        val existing = setOf("/proj/phpcs.xml")
        val found = PhpCSComposerDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = rulesetSpec,
            exists = existing::contains,
        )
        assertEquals("/proj/phpcs.xml", found)
        assertEquals("/proj/phpcs.xml", bag[rulesetSpec])
    }

    @Test
    fun `applyDiscoveredConfigFile prefers phpcs xml over phpcs xml dist`() {
        val bag = MapOptionsBag()
        val rulesetSpec = string("ruleset")
        val existing = setOf("/proj/phpcs.xml", "/proj/phpcs.xml.dist")
        val found = PhpCSComposerDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = rulesetSpec,
            exists = existing::contains,
        )
        assertEquals("/proj/phpcs.xml", found)
    }

    @Test
    fun `applyDiscoveredConfigFile prefers phpcs xml dist over phpcs dist xml`() {
        val bag = MapOptionsBag()
        val rulesetSpec = string("ruleset")
        val existing = setOf("/proj/phpcs.xml.dist", "/proj/phpcs.dist.xml")
        val found = PhpCSComposerDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = rulesetSpec,
            exists = existing::contains,
        )
        assertEquals(
            "phpcs.xml.dist is checked before phpcs.dist.xml",
            "/proj/phpcs.xml.dist",
            found,
        )
    }

    @Test
    fun `applyDiscoveredConfigFile falls back to phpcs dist xml when it is the only one`() {
        val bag = MapOptionsBag()
        val rulesetSpec = string("ruleset")
        val existing = setOf("/proj/phpcs.dist.xml")
        val found = PhpCSComposerDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = rulesetSpec,
            exists = existing::contains,
        )
        assertEquals("/proj/phpcs.dist.xml", found)
    }

    @Test
    fun `applyDiscoveredConfigFile returns null when no candidate exists`() {
        val bag = MapOptionsBag()
        val rulesetSpec = string("ruleset")
        val found = PhpCSComposerDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = rulesetSpec,
            exists = { false },
        )
        assertNull(found)
    }

    @Test
    fun `applyComposerJson on a require-dev project parses without errors`() {
        val composer = """
            {
                "require-dev": {"squizlabs/php_codesniffer": "^3.7"},
                "scripts": {
                    "phpcs": "phpcs --standard=PSR12"
                }
            }
        """.trimIndent()
        val applied = PhpCSComposerDescriptor.applyComposerJson(composer, MapOptionsBag())
        // scriptArgs is empty for now (G13 pending) — nothing applied.
        assertEquals(0, applied)
    }
}
