package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.source.local.LocalBinarySourceType.ImmutableSerializedElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the default `child()` / `childrenNamed()` helpers on the
 * `SerializedSourceElement` interface using the bundled
 * `ImmutableSerializedElement` data class.
 */
class SerializedSourceElementTest {

    @Test
    fun `child returns first matching child`() {
        val root = ImmutableSerializedElement(
            name = "source",
            children = listOf(
                ImmutableSerializedElement("env", mapOf("k" to "X")),
                ImmutableSerializedElement("env", mapOf("k" to "Y")),
                ImmutableSerializedElement("other"),
            ),
        )
        val first = root.child("env")
        assertEquals(mapOf("k" to "X"), first?.attributes)
    }

    @Test
    fun `child returns null when no match`() {
        val root = ImmutableSerializedElement(name = "source")
        assertNull(root.child("nope"))
    }

    @Test
    fun `childrenNamed returns all matches in order`() {
        val root = ImmutableSerializedElement(
            name = "source",
            children = listOf(
                ImmutableSerializedElement("a"),
                ImmutableSerializedElement("env", mapOf("i" to "1")),
                ImmutableSerializedElement("b"),
                ImmutableSerializedElement("env", mapOf("i" to "2")),
            ),
        )
        val envs = root.childrenNamed("env")
        assertEquals(2, envs.size)
        assertEquals("1", envs[0].attributes["i"])
        assertEquals("2", envs[1].attributes["i"])
    }

    @Test
    fun `childrenNamed returns empty list when no match`() {
        val root = ImmutableSerializedElement(name = "source")
        kotlin.test.assertTrue(root.childrenNamed("env").isEmpty())
    }

    @Test
    fun `attributes default to empty map and text to null`() {
        val e = ImmutableSerializedElement(name = "x")
        assertEquals(emptyMap<String, String>(), e.attributes)
        assertNull(e.text)
        kotlin.test.assertTrue(e.children.isEmpty())
    }
}
