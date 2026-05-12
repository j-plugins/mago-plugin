package dev.jplugins.qualitytools.experimental.phpstan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [phpStanComposerToolDescriptor] decodes the legacy
 * `scripts.phpstan` shape into the right options. Work plan §C.1
 * spells out the test case verbatim.
 */
class PhpStanComposerDescriptorTest {

    private val schema = PhpStanOptionsSchema()
    private val descriptor = phpStanComposerToolDescriptor(schema)

    @Test
    fun `scripts phpstan with memory-limit 4G and level 8 is decoded`() {
        val composerJson = """
            {
                "require-dev": { "phpstan/phpstan": "^1.10" },
                "scripts": {
                    "phpstan": "phpstan --memory-limit=4G --level=8"
                }
            }
        """.trimIndent()
        val bag = MapOptionsBag()
        val applied = descriptor.applyComposerJson(composerJson, bag)

        assertEquals(2, applied)
        assertEquals("4G", bag[schema.memoryLimit])
        assertEquals(8, bag[schema.level])
    }

    @Test
    fun `descriptor declares the legacy package name and bin name`() {
        assertEquals("phpstan/phpstan", descriptor.packageName)
        assertEquals("phpstan", descriptor.binName)
    }

    @Test
    fun `descriptor recognises both phpstan_neon and the dist variant`() {
        assertEquals(
            listOf("phpstan.neon", "phpstan.neon.dist"),
            descriptor.configFileNames,
        )
    }

    @Test
    fun `applyDiscoveredConfigFile writes the first matching path into config`() {
        val bag = MapOptionsBag()
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.config,
            exists = { it == "/proj/phpstan.neon" },
        )
        assertEquals("/proj/phpstan.neon", found)
        assertEquals("/proj/phpstan.neon", bag[schema.config])
    }

    @Test
    fun `missing scripts section results in zero options applied`() {
        val composerJson = """{ "require-dev": { "phpstan/phpstan": "^1.10" } }"""
        val bag = MapOptionsBag()
        val applied = descriptor.applyComposerJson(composerJson, bag)
        assertEquals(0, applied)
        // Defaults preserved.
        assertEquals("2G", bag[schema.memoryLimit])
        assertEquals(4, bag[schema.level])
    }

    @Test
    fun `out-of-range level in composer script is rejected and bag stays at default`() {
        val composerJson = """
            { "scripts": { "phpstan": "phpstan --memory-limit=4G --level=99" } }
        """.trimIndent()
        val bag = MapOptionsBag()
        val applied = descriptor.applyComposerJson(composerJson, bag)
        // Only --memory-limit applies; --level=99 is rejected by the
        // IntSpec range 0..10 (legacy PhpStanOptionsPanel spinner was
        // 0..8; we accept 0..10 — anything beyond is still rejected).
        assertEquals(1, applied)
        assertEquals("4G", bag[schema.memoryLimit])
        assertEquals(4, bag[schema.level]) // default
    }

    @Test
    fun `scriptKey is phpstan to match the legacy composer convention`() {
        assertEquals("phpstan", descriptor.scriptKey)
    }

    @Test
    fun `scriptArgs contain both --memory-limit and --level mappings`() {
        val flags = descriptor.scriptArgs.map { it.flag }.toSet()
        assertEquals(setOf("--memory-limit", "--level"), flags)
        assertTrue(
            "memoryLimit maps to the schema's memoryLimit spec",
            descriptor.scriptArgs.any { it.flag == "--memory-limit" && it.spec === schema.memoryLimit },
        )
        assertTrue(
            "--level maps to the schema's level spec",
            descriptor.scriptArgs.any { it.flag == "--level" && it.spec === schema.level },
        )
    }
}
