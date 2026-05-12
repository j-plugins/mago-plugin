package dev.jplugins.qualitytools.core.migration

import dev.jplugins.qualitytools.core.migration.LegacyInspectionFieldsMigrator.FieldMapping
import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.options.bool
import dev.jplugins.qualitytools.core.options.int
import dev.jplugins.qualitytools.core.options.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier-1 patch G6 — generic `LegacyInspectionFieldsMigrator`. Verifies:
 *  - field copy succeeds across multiple spec kinds
 *  - second invocation is a no-op (marker)
 *  - missing fields fall back to spec default without error
 *  - undecodable values are skipped (warning), other fields still migrate
 */
class LegacyInspectionFieldsMigratorTest {

    private val fullProject = bool("fullProject")
    private val level = int("level", default = 0, range = 0..10)
    private val config = string("config")

    @Test
    fun `copies all fields and sets marker`() {
        val bag = TestBag()
        val legacy = FakeLegacy(
            mapOf(
                "FULL_PROJECT" to "true",
                "level" to "7",
                "config" to "phpstan.neon",
            )
        )
        val migrator = LegacyInspectionFieldsMigrator(
            toolId = "phpstan",
            mappings = listOf(
                FieldMapping("FULL_PROJECT", fullProject),
                FieldMapping("level", level),
                FieldMapping("config", config),
            ),
        )

        val applied = migrator.migrateOnce(legacy, bag)

        assertTrue(applied)
        assertTrue(bag[fullProject])
        assertEquals(7, bag[level])
        assertEquals("phpstan.neon", bag[config])
        assertEquals("true", bag.snapshot()["_sdk.legacyMigrated.phpstan"])
    }

    @Test
    fun `second run is a no-op`() {
        val bag = TestBag()
        val legacy = FakeLegacy(mapOf("level" to "5"))
        val migrator = LegacyInspectionFieldsMigrator(
            toolId = "phpstan",
            mappings = listOf(FieldMapping("level", level)),
        )

        assertTrue(migrator.migrateOnce(legacy, bag))
        assertEquals(5, bag[level])

        // tamper with bag: simulate a user changing it afterwards
        bag[level] = 9
        assertFalse(migrator.migrateOnce(legacy, bag))
        assertEquals(9, bag[level])
    }

    @Test
    fun `missing legacy field is ignored, other fields still migrate`() {
        val bag = TestBag()
        val legacy = FakeLegacy(mapOf("level" to "3")) // no config field
        val migrator = LegacyInspectionFieldsMigrator(
            toolId = "phpstan",
            mappings = listOf(
                FieldMapping("level", level),
                FieldMapping("config", config),
            ),
        )

        val applied = migrator.migrateOnce(legacy, bag)
        assertTrue(applied)
        assertEquals(3, bag[level])
        assertEquals("", bag[config]) // default
    }

    @Test
    fun `undecodable value is skipped, others migrate`() {
        val bag = TestBag()
        val legacy = FakeLegacy(
            mapOf(
                "level" to "definitely-not-an-int",
                "config" to "x.neon",
            )
        )
        val migrator = LegacyInspectionFieldsMigrator(
            toolId = "phpstan",
            mappings = listOf(
                FieldMapping("level", level),
                FieldMapping("config", config),
            ),
        )

        val applied = migrator.migrateOnce(legacy, bag)
        assertTrue(applied) // config did get applied
        assertEquals(0, bag[level]) // stayed at default
        assertEquals("x.neon", bag[config])
    }

    @Test
    fun `marker is per-toolId so unrelated migrator runs independently`() {
        val bag = TestBag()
        val phpstanMigrator = LegacyInspectionFieldsMigrator(
            toolId = "phpstan",
            mappings = listOf(FieldMapping("level", level)),
        )
        val psalmMigrator = LegacyInspectionFieldsMigrator(
            toolId = "psalm",
            mappings = listOf(FieldMapping("level", level)),
        )

        assertTrue(phpstanMigrator.migrateOnce(FakeLegacy(mapOf("level" to "3")), bag))
        assertEquals(3, bag[level])

        // psalm migrator hasn't run yet → it WILL run and overwrite
        assertTrue(psalmMigrator.migrateOnce(FakeLegacy(mapOf("level" to "8")), bag))
        assertEquals(8, bag[level])

        // both markers present
        val snap = bag.snapshot()
        assertEquals("true", snap["_sdk.legacyMigrated.phpstan"])
        assertEquals("true", snap["_sdk.legacyMigrated.psalm"])
    }

    // -------- in-test fixtures, zero deps on :testing --------

    private class FakeLegacy(
        private val fields: Map<String, String>,
    ) : LegacyInspectionFieldsMigrator.LegacyInspectionElement {
        override fun field(name: String): String? = fields[name]
    }

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
