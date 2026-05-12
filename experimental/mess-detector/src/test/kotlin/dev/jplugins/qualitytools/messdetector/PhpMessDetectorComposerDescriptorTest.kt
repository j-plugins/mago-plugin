package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.php.composer.ComposerJson
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpMessDetectorComposerDescriptorTest {

    private val descriptor = PhpMessDetectorComposerToolDescriptor
    private val schema = PhpMessDetectorOptionsSchema()

    @Test
    fun `package and binary identify phpmd`() {
        assertEquals("phpmd/phpmd", descriptor.packageName)
        assertEquals("phpmd", descriptor.binName)
    }

    @Test
    fun `script key is phpmd`() {
        assertEquals("phpmd", descriptor.scriptKey)
    }

    @Test
    fun `scriptArgs is empty - composer ruleset routing is owned by the on-detected hook`() {
        // The legacy plugin's scripts.phpmd parsing maps the CSV
        // arg onto several heterogeneous options (built-in bools
        // + custom rows). That logic doesn't fit FlagToOption; it
        // lives in the future PhpMessDetectorComposerOnDetectedHook
        // (see TODO.md).
        assertTrue(descriptor.scriptArgs.isEmpty())
    }

    @Test
    fun `configFileNames lists phpmd_xml first then dist fallback`() {
        // Plan §1.2 / `MessDetectorComposerConfig.applyRulesetFromRoot`:
        // a project's local file wins over the committed dist file.
        assertEquals(
            listOf("phpmd.xml", "phpmd.xml.dist"),
            descriptor.configFileNames,
        )
    }

    @Test
    fun `composer-json with phpmd in require-dev is detected`() {
        val composer = """
            {
              "require-dev": {
                "phpmd/phpmd": "^2.14"
              },
              "scripts": {
                "phpmd": "phpmd src xml codesize"
              }
            }
        """.trimIndent()
        val parsed = ComposerJson.parse(composer)
        assertTrue(parsed.requires(descriptor.packageName))
    }

    @Test
    fun `composer-json with phpmd in require (not require-dev) is also detected`() {
        val composer = """
            {
              "require": { "phpmd/phpmd": "^2.0" }
            }
        """.trimIndent()
        val parsed = ComposerJson.parse(composer)
        assertTrue(parsed.requires(descriptor.packageName))
    }

    @Test
    fun `composer-json without phpmd is not detected`() {
        val composer = """
            {
              "require-dev": { "phpstan/phpstan": "^1.10" }
            }
        """.trimIndent()
        val parsed = ComposerJson.parse(composer)
        assertEquals(false, parsed.requires(descriptor.packageName))
    }

    @Test
    fun `applyComposerJson is a no-op since scriptArgs is empty`() {
        val bag = MapOptionsBag()
        val composer = """
            {
              "scripts": {
                "phpmd": "phpmd src xml codesize,design,naming"
              }
            }
        """.trimIndent()
        val applied = descriptor.applyComposerJson(composer, bag)
        assertEquals(
            "scriptArgs is empty so no FlagToOption mapping fires",
            0,
            applied,
        )
        // bag stays at defaults.
        assertEquals(true, bag[schema.codesize])
        assertEquals(false, bag[schema.controversial])
    }

    @Test
    fun `applyDiscoveredConfigFile prefers phpmd_xml over phpmd_xml_dist`() {
        val bag = MapOptionsBag()
        val both = setOf("/proj/phpmd.xml", "/proj/phpmd.xml.dist")
        // We use customRulesetFiles purely as the receiver spec for
        // the helper; in production an on-detected hook would route
        // discovered config XML into a dedicated PathSpec once one
        // exists (see TODO.md).
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customRulesetFiles,
            exists = both::contains,
        )
        assertEquals("/proj/phpmd.xml", found)
        assertEquals("/proj/phpmd.xml", bag[schema.customRulesetFiles])
    }

    @Test
    fun `applyDiscoveredConfigFile falls back to dist file when local is absent`() {
        val bag = MapOptionsBag()
        val onlyDist = setOf("/proj/phpmd.xml.dist")
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customRulesetFiles,
            exists = onlyDist::contains,
        )
        assertEquals("/proj/phpmd.xml.dist", found)
    }

    @Test
    fun `applyDiscoveredConfigFile returns null when no config file exists`() {
        val bag = MapOptionsBag()
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customRulesetFiles,
            exists = { false },
        )
        assertNull(found)
        assertEquals("", bag[schema.customRulesetFiles])
    }
}
