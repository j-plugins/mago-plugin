package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.php.composer.ComposerJson
import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCsFixerComposerDescriptorTest {

    private val descriptor = PhpCsFixerComposerToolDescriptor
    private val schema = PhpCsFixerOptionsSchema()

    @Test
    fun `package and binary identify PHP-CS-Fixer`() {
        assertEquals("friendsofphp/php-cs-fixer", descriptor.packageName)
        assertEquals("php-cs-fixer", descriptor.binName)
    }

    @Test
    fun `script key is cs-fix`() {
        assertEquals("cs-fix", descriptor.scriptKey)
    }

    @Test
    fun `scriptArgs is empty - no common script flags to extract`() {
        assertTrue(descriptor.scriptArgs.isEmpty())
    }

    @Test
    fun `configFileNames lists modern files in preference order`() {
        assertEquals(
            listOf(".php-cs-fixer.php", ".php-cs-fixer.dist.php"),
            descriptor.configFileNames,
        )
    }

    @Test
    fun `composer-json with the package is detected`() {
        val composer = """
            {
              "require-dev": {
                "friendsofphp/php-cs-fixer": "^3.50"
              },
              "scripts": {
                "cs-fix": "php-cs-fixer fix"
              }
            }
        """.trimIndent()
        val parsed = ComposerJson.parse(composer)
        assertTrue(parsed.requires(descriptor.packageName))
    }

    @Test
    fun `composer-json with package in require (not require-dev) is also detected`() {
        val composer = """
            {
              "require": { "friendsofphp/php-cs-fixer": "^3.0" }
            }
        """.trimIndent()
        val parsed = ComposerJson.parse(composer)
        assertTrue(parsed.requires(descriptor.packageName))
    }

    @Test
    fun `composer-json without the package is not detected`() {
        val composer = """
            {
              "require-dev": { "phpstan/phpstan": "^1.10" }
            }
        """.trimIndent()
        val parsed = ComposerJson.parse(composer)
        assertEquals(false, parsed.requires(descriptor.packageName))
    }

    @Test
    fun `applyComposerJson is a no-op for cs-fixer since scriptArgs is empty`() {
        val bag = MapOptionsBag()
        val composer = """
            {
              "scripts": {
                "cs-fix": "php-cs-fixer fix --rules=@Symfony --allow-risky=yes"
              }
            }
        """.trimIndent()
        val applied = descriptor.applyComposerJson(composer, bag)
        assertEquals("scriptArgs is empty so nothing is applied", 0, applied)
        // bag stays at defaults.
        assertEquals("@PSR12", bag[schema.codingStandard])
        assertEquals(false, bag[schema.allowRiskyRules])
    }

    @Test
    fun `applyDiscoveredConfigFile prefers .php-cs-fixer.php over .dist.php`() {
        val bag = MapOptionsBag()
        val both = setOf("/proj/.php-cs-fixer.php", "/proj/.php-cs-fixer.dist.php")
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customConfig,
            exists = both::contains,
        )
        assertEquals("/proj/.php-cs-fixer.php", found)
        assertEquals("/proj/.php-cs-fixer.php", bag[schema.customConfig])
    }

    @Test
    fun `applyDiscoveredConfigFile picks dist file when modern file is absent`() {
        val bag = MapOptionsBag()
        val onlyDist = setOf("/proj/.php-cs-fixer.dist.php")
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customConfig,
            exists = onlyDist::contains,
        )
        assertEquals("/proj/.php-cs-fixer.dist.php", found)
        assertEquals("/proj/.php-cs-fixer.dist.php", bag[schema.customConfig])
    }

    @Test
    fun `applyDiscoveredConfigFile returns null when no config file exists`() {
        val bag = MapOptionsBag()
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customConfig,
            exists = { false },
        )
        assertNull(found)
        assertEquals("", bag[schema.customConfig])
    }
}
