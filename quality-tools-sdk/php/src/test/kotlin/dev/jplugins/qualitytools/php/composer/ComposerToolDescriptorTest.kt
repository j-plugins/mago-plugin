package dev.jplugins.qualitytools.php.composer

import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.options.bool
import dev.jplugins.qualitytools.core.options.int
import dev.jplugins.qualitytools.core.options.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerToolDescriptorTest {

    private val memoryLimit = string("memoryLimit", default = "")
    private val level = int("level", default = 0, range = 0..10)
    private val config = string("configFile", default = "")
    private val allowRisky = bool("allowRisky")

    private val phpstanDescriptor = ComposerToolDescriptor(
        packageName = "phpstan/phpstan",
        binName = "phpstan",
        configFileNames = listOf("phpstan.neon", "phpstan.neon.dist"),
        scriptKey = "phpstan",
        scriptArgs = listOf(
            ComposerToolDescriptor.FlagToOption("--memory-limit", memoryLimit),
            ComposerToolDescriptor.FlagToOption("--level", level),
        ),
    )

    private val realComposer = """
        {
            "require-dev": {"phpstan/phpstan": "^1.10"},
            "scripts": {
                "phpstan": "phpstan analyse --memory-limit=4G --level=8"
            }
        }
    """.trimIndent()

    @Test
    fun `applyComposerJson sets options from composer scripts`() {
        val bag = TestBag()
        val applied = phpstanDescriptor.applyComposerJson(realComposer, bag)
        assertEquals(2, applied)
        assertEquals("4G", bag[memoryLimit])
        assertEquals(8, bag[level])
    }

    @Test
    fun `applyComposerJson on empty composer returns 0 and leaves bag at defaults`() {
        val bag = TestBag()
        val applied = phpstanDescriptor.applyComposerJson("{}", bag)
        assertEquals(0, applied)
        assertEquals("", bag[memoryLimit])
        assertEquals(0, bag[level])
    }

    @Test
    fun `applyComposerJson ignores flags whose spec rejects the value`() {
        // level spec has range 0..10; the script tries to set it to 99.
        val descriptor = ComposerToolDescriptor(
            packageName = "phpstan/phpstan",
            binName = "phpstan",
            scriptKey = "phpstan",
            scriptArgs = listOf(
                ComposerToolDescriptor.FlagToOption("--level", level),
            ),
        )
        val bag = TestBag()
        val composer = """
            {
                "scripts": {"phpstan": "phpstan --level=99"}
            }
        """.trimIndent()
        val applied = descriptor.applyComposerJson(composer, bag)
        assertEquals(0, applied)
        assertEquals(0, bag[level]) // unchanged
    }

    @Test
    fun `applyDiscoveredConfigFile picks first existing in declared order`() {
        val bag = TestBag()
        val existing = setOf("/proj/phpstan.neon.dist") // only the dist file exists
        val found = phpstanDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = config,
            exists = it::contains,
        )
        // first-existing wins per declared order; here only neon.dist exists.
        assertEquals("/proj/phpstan.neon.dist", found)
        assertEquals("/proj/phpstan.neon.dist", bag[config])
    }

    @Test
    fun `applyDiscoveredConfigFile prefers earlier-listed file when both exist`() {
        val bag = TestBag()
        val existing = setOf("/proj/phpstan.neon", "/proj/phpstan.neon.dist")
        val found = phpstanDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = config,
            exists = existing::contains,
        )
        assertEquals("/proj/phpstan.neon", found)
        assertEquals("/proj/phpstan.neon", bag[config])
    }

    @Test
    fun `applyDiscoveredConfigFile returns null when no file matches`() {
        val bag = TestBag()
        val found = phpstanDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj",
            bag = bag,
            configSpec = config,
            exists = { false },
        )
        assertNull(found)
        assertEquals("", bag[config])
    }

    @Test
    fun `applyDiscoveredConfigFile handles trailing slash on rootDir`() {
        val bag = TestBag()
        val found = phpstanDescriptor.applyDiscoveredConfigFile(
            rootDir = "/proj/",
            bag = bag,
            configSpec = config,
            exists = { it == "/proj/phpstan.neon" },
        )
        assertEquals("/proj/phpstan.neon", found)
    }

    @Test
    fun `scriptKey defaults to bin name after slash`() {
        val d = ComposerToolDescriptor(packageName = "vimeo/psalm", binName = "psalm")
        assertEquals("psalm", d.scriptKey)
    }

    @Test
    fun `bool flag is decoded correctly`() {
        val d = ComposerToolDescriptor(
            packageName = "friendsofphp/php-cs-fixer",
            binName = "php-cs-fixer",
            scriptKey = "cs-fix",
            scriptArgs = listOf(
                ComposerToolDescriptor.FlagToOption("--allow-risky", allowRisky),
            ),
        )
        val bag = TestBag()
        val composer = """
            {"scripts": {"cs-fix": "php-cs-fixer fix --allow-risky=yes"}}
        """.trimIndent()
        d.applyComposerJson(composer, bag)
        assertEquals(true, bag[allowRisky])
    }

    // ---- minimal bag fixture (no :testing dep on :php test classpath today) ----
    private class TestBag : OptionsBag {
        private val data = mutableMapOf<String, String>()
        override fun <T : Any> get(spec: OptionSpec<T>): T {
            val raw = data[spec.key] ?: return spec.default
            return spec.decode(raw) ?: spec.default
        }
        override fun <T : Any> set(spec: OptionSpec<T>, value: T) {
            data[spec.key] = spec.encode(value)
        }
        override fun snapshot(): Map<String, String> = data.toMap()
        override fun mode(modeId: String): OptionsBag = TestBag()
        override fun commit() {}
    }
}
