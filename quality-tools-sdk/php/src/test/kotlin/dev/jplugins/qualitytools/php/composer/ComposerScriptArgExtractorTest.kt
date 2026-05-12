package dev.jplugins.qualitytools.php.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerScriptArgExtractorTest {

    @Test
    fun `eq-style flag is extracted`() {
        val r = ComposerScriptArgExtractor.extract(
            "phpstan analyse --memory-limit=4G --level=8",
            "--memory-limit",
        )
        assertEquals("4G", r)
    }

    @Test
    fun `space-style flag is extracted`() {
        val r = ComposerScriptArgExtractor.extract(
            "phpstan analyse --memory-limit 4G",
            "--memory-limit",
        )
        assertEquals("4G", r)
    }

    @Test
    fun `eq-style with double-quoted value strips quotes`() {
        val r = ComposerScriptArgExtractor.extract(
            "psalm --config=\"/path with spaces/psalm.xml\"",
            "--config",
        )
        assertEquals("/path with spaces/psalm.xml", r)
    }

    @Test
    fun `eq-style with single-quoted value strips quotes`() {
        val r = ComposerScriptArgExtractor.extract(
            "psalm --config='psalm.xml'",
            "--config",
        )
        assertEquals("psalm.xml", r)
    }

    @Test
    fun `missing flag returns null`() {
        val r = ComposerScriptArgExtractor.extract(
            "phpstan analyse",
            "--memory-limit",
        )
        assertNull(r)
    }

    @Test
    fun `first occurrence wins`() {
        val r = ComposerScriptArgExtractor.extract(
            "phpstan --level=5 && phpstan --level=8",
            "--level",
        )
        assertEquals("5", r)
    }

    @Test
    fun `eq-style is preferred over space-style when both shapes appear later`() {
        // The eq-style appears first; that's what should be picked.
        val r = ComposerScriptArgExtractor.extract(
            "tool --x=eq --y other --x value",
            "--x",
        )
        assertEquals("eq", r)
    }

    @Test
    fun `value with multiple dashes is preserved`() {
        val r = ComposerScriptArgExtractor.extract(
            "phpstan --autoload-file=/abs/path-with-dashes/bootstrap.php",
            "--autoload-file",
        )
        assertEquals("/abs/path-with-dashes/bootstrap.php", r)
    }

    @Test
    fun `space-style with quoted value strips quotes`() {
        val r = ComposerScriptArgExtractor.extract(
            "phpstan analyse --memory-limit \"4G\"",
            "--memory-limit",
        )
        assertEquals("4G", r)
    }
}
