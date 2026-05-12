package dev.jplugins.qualitytools.php.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerJsonTest {

    private val realFixture = """
        {
            "name": "vendor/some-pkg",
            "require": {
                "php": "^8.1",
                "phpstan/phpstan": "^1.10"
            },
            "require-dev": {
                "phpunit/phpunit": "^10.0",
                "vimeo/psalm": "^5.0"
            },
            "scripts": {
                "phpstan": "phpstan analyse --memory-limit=4G --level=8",
                "psalm": [
                    "psalm --show-info=true",
                    "psalm --diff"
                ],
                "test": "phpunit"
            }
        }
    """.trimIndent()

    @Test
    fun `parses real-world composer json`() {
        val c = ComposerJson.parse(realFixture)
        assertTrue(c.requires("phpstan/phpstan"))
        assertTrue(c.requires("vimeo/psalm"))
        assertTrue(c.requires("phpunit/phpunit"))
        assertFalse(c.requires("nonexistent/package"))
    }

    @Test
    fun `script returns string scripts as-is`() {
        val c = ComposerJson.parse(realFixture)
        assertEquals(
            "phpstan analyse --memory-limit=4G --level=8",
            c.script("phpstan")
        )
    }

    @Test
    fun `script joins list scripts with newline`() {
        val c = ComposerJson.parse(realFixture)
        val s = c.script("psalm")!!
        assertTrue("psalm --show-info=true" in s)
        assertTrue("psalm --diff" in s)
        assertTrue("\n" in s)
    }

    @Test
    fun `missing script returns null`() {
        val c = ComposerJson.parse(realFixture)
        assertNull(c.script("does-not-exist"))
    }

    @Test
    fun `malformed json yields empty descriptor not exception`() {
        val c = ComposerJson.parse("this is not json {")
        assertFalse(c.requires("anything"))
        assertNull(c.script("any"))
    }

    @Test
    fun `empty json yields empty descriptor`() {
        val c = ComposerJson.parse("{}")
        assertFalse(c.requires("anything"))
        assertNull(c.script("any"))
    }

    @Test
    fun `composer with only require-dev still finds packages`() {
        val c = ComposerJson.parse("""{"require-dev": {"phpstan/phpstan": "*"}}""")
        assertTrue(c.requires("phpstan/phpstan"))
    }

    @Test
    fun `composer with only require still finds packages`() {
        val c = ComposerJson.parse("""{"require": {"phpunit/phpunit": "*"}}""")
        assertTrue(c.requires("phpunit/phpunit"))
    }

    @Test
    fun `escape sequences in strings are decoded`() {
        val c = ComposerJson.parse("""{"name": "a\nb\t\\c"}""")
        assertEquals("a\nb\t\\c", c.raw["name"])
    }

    @Test
    fun `numeric and boolean values are preserved`() {
        val c = ComposerJson.parse("""{"count": 42, "flag": true, "missing": null}""")
        assertEquals(42L, c.raw["count"])
        assertEquals(true, c.raw["flag"])
        assertNull(c.raw["missing"])
    }

    @Test
    fun `nested objects survive round-trip`() {
        val c = ComposerJson.parse("""{"a": {"b": {"c": "deep"}}}""")
        @Suppress("UNCHECKED_CAST")
        val a = c.raw["a"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val b = a["b"] as Map<String, Any?>
        assertEquals("deep", b["c"])
    }

    @Test
    fun `empty arrays and objects parse to empty collections`() {
        val c = ComposerJson.parse("""{"x": [], "y": {}}""")
        assertEquals(emptyList<Any?>(), c.raw["x"])
        assertEquals(emptyMap<String, Any?>(), c.raw["y"])
    }
}
