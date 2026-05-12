package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.testing.MapOptionsBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaravelPintComposerDescriptorTest {

    private val descriptor = LaravelPintComposerToolDescriptor
    private val schema = LaravelPintOptionsSchema()

    @Test
    fun `package and binary names match Composer convention`() {
        assertEquals("laravel/pint", descriptor.packageName)
        assertEquals("pint", descriptor.binName)
    }

    @Test
    fun `config file name list is pint dot json`() {
        assertEquals(listOf("pint.json"), descriptor.configFileNames)
    }

    @Test
    fun `scriptKey is pint (matches packageName suffix)`() {
        assertEquals("pint", descriptor.scriptKey)
    }

    @Test
    fun `scriptArgs is empty until preset and dirty options land`() {
        // See TODO.md: preset / dirty surface arrives with the
        // commit-handler port; until then, the descriptor declares
        // no script-arg mappings.
        assertEquals(emptyList<Any>(), descriptor.scriptArgs)
    }

    @Test
    fun `pint dot json is discovered next to composer json`() {
        val bag = MapOptionsBag()
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = schema.customConfig,
            exists = { it == "/proj/pint.json" },
        )
        assertEquals("/proj/pint.json", found)
        assertEquals("/proj/pint.json", bag[schema.customConfig])
    }

    @Test
    fun `missing pint dot json returns null and leaves bag at default`() {
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

    @Test
    fun `discovery honours trailing slash on rootDir`() {
        val bag = MapOptionsBag()
        val found = descriptor.applyDiscoveredConfigFile(
            rootDir = "/proj/",
            bag = bag,
            configSpec = schema.customConfig,
            exists = { it == "/proj/pint.json" },
        )
        assertEquals("/proj/pint.json", found)
    }

    @Test
    fun `composer json with laravel slash pint dependency parses cleanly`() {
        // applyComposerJson should silently succeed with 0 applied
        // flags (no scriptArgs configured yet) — the discovery side
        // is what matters.
        val bag = MapOptionsBag()
        val composerJson = """
            {
                "require-dev": {
                    "laravel/pint": "^1.13"
                },
                "scripts": {
                    "pint": "pint --preset=psr12 --dirty"
                }
            }
        """.trimIndent()
        val applied = descriptor.applyComposerJson(composerJson, bag)
        assertEquals(0, applied)
    }

    @Test
    fun `composer json with no pint script is silent`() {
        val bag = MapOptionsBag()
        val applied = descriptor.applyComposerJson("""{"name":"foo/bar"}""", bag)
        assertEquals(0, applied)
    }
}
