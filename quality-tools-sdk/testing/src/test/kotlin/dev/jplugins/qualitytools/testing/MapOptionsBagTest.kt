package dev.jplugins.qualitytools.testing

import dev.jplugins.qualitytools.core.options.bool
import dev.jplugins.qualitytools.core.options.int
import dev.jplugins.qualitytools.core.options.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapOptionsBagTest {

    private val debug = bool("debug", default = false)
    private val level = int("level", default = 0)
    private val configFile = string("configFile", default = "phpstan.neon")

    @Test
    fun `default is returned when key not set`() {
        val bag = MapOptionsBag()
        assertEquals(false, bag[debug])
        assertEquals(0, bag[level])
        assertEquals("phpstan.neon", bag[configFile])
    }

    @Test
    fun `set and immediate get returns the written value`() {
        val bag = MapOptionsBag()
        bag[debug] = true
        bag[level] = 8
        assertEquals(true, bag[debug])
        assertEquals(8, bag[level])
    }

    @Test
    fun `initial map seeds the bag`() {
        val bag = MapOptionsBag(initial = mapOf("debug" to "true", "level" to "5"))
        assertEquals(true, bag[debug])
        assertEquals(5, bag[level])
    }

    @Test
    fun `snapshot reflects every committed encoded value`() {
        val bag = MapOptionsBag()
        bag[debug] = true
        bag[configFile] = "custom.neon"
        val snap = bag.snapshot()
        assertEquals("true", snap["debug"])
        assertEquals("custom.neon", snap["configFile"])
    }

    @Test
    fun `mode overlay falls back to parent for unset keys`() {
        val bag = MapOptionsBag()
        bag[level] = 5
        val analyze = bag.mode("analyze")
        // parent's value is visible through the overlay
        assertEquals(5, analyze[level])
    }

    @Test
    fun `mode overlay shadows parent for set keys`() {
        val bag = MapOptionsBag()
        bag[level] = 5
        val analyze = bag.mode("analyze")
        analyze[level] = 9
        assertEquals(9, analyze[level])
        // parent unchanged
        assertEquals(5, bag[level])
    }

    @Test
    fun `mode overlay snapshot merges parent + own`() {
        val bag = MapOptionsBag()
        bag[debug] = true
        bag[level] = 3
        val analyze = bag.mode("analyze")
        analyze[level] = 8
        val snap = analyze.snapshot()
        assertEquals("true", snap["debug"]) // from parent
        assertEquals("8", snap["level"])     // overridden
    }

    @Test
    fun `commit is a no-op for in-memory bag`() {
        val bag = MapOptionsBag()
        bag[debug] = true
        bag.commit() // does not throw, does not lose data
        assertEquals(true, bag[debug])
    }

    @Test
    fun `decoder failure falls back to default`() {
        // Bag was seeded with an undecodable int value; reading returns
        // the spec default rather than a parse error.
        val bag = MapOptionsBag(initial = mapOf("level" to "not-a-number"))
        assertEquals(0, bag[level])
    }

    @Test
    fun `independent mode overlays don't see each other`() {
        val parent = MapOptionsBag()
        val a = parent.mode("analyze")
        val b = parent.mode("lint")
        a[level] = 9
        // b is a sibling overlay and shouldn't see a's write
        assertNotEquals(9, b[level])
        assertEquals(0, b[level])
    }
}
