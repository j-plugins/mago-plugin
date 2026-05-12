package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.source.local.LocalBinarySource
import dev.jplugins.qualitytools.core.source.local.LocalBinarySourceType
import dev.jplugins.qualitytools.core.source.local.LocalBinarySourceType.ImmutableSerializedElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip test required by phase 02 acceptance: serialize + deserialize
 * a `LocalBinarySource` and verify nothing is lost.
 */
class LocalBinarySourceTypeTest {

    private val type = LocalBinarySourceType()

    @Test
    fun `roundtrip preserves all fields`() {
        val source = LocalBinarySource(
            instanceId = "abc-123",
            path = "/usr/local/bin/phpstan",
            displayName = "PHPStan (system)",
            extraEnv = mapOf("PHPSTAN_DEBUG" to "1", "TZ" to "UTC"),
            cachedDetectedVersion = "1.10.50",
        )

        val ser = type.serialize(source)
        val restored = type.deserialize(ser) as LocalBinarySource

        assertEquals(source.instanceId, restored.instanceId)
        assertEquals(source.path, restored.path)
        assertEquals(source.displayName, restored.displayName)
        assertEquals(source.extraEnv, restored.extraEnv)
        assertEquals(source.cachedDetectedVersion, restored.cachedDetectedVersion)
        assertEquals(source.typeId, restored.typeId)
    }

    @Test
    fun `minimal source roundtrips with default displayName`() {
        val source = LocalBinarySource(instanceId = "x", path = "/bin/phpcs")
        val restored = type.deserialize(type.serialize(source)) as LocalBinarySource

        assertEquals("/bin/phpcs", restored.path)
        assertEquals("/bin/phpcs", restored.displayName) // default == path
        assertTrue(restored.extraEnv.isEmpty())
        assertNull(restored.cachedDetectedVersion)
    }

    @Test
    fun `missing instanceId attribute is a clear error`() {
        val bad = ImmutableSerializedElement(
            name = LocalBinarySourceType.ROOT,
            attributes = mapOf(LocalBinarySourceType.ATTR_PATH to "/bin/x"),
        )
        val ex = try { type.deserialize(bad); null } catch (e: IllegalStateException) { e }
        assertTrue("error names the missing attribute", ex?.message?.contains(LocalBinarySourceType.ATTR_INSTANCE_ID) == true)
    }

    @Test
    fun `serialize rejects a non-LocalBinarySource source`() {
        class ForeignSource : ConfigSource {
            override val instanceId = "x"
            override val typeId = "other"
            override val displayName = "other"
            override suspend fun resolve(ctx: ResolveContext): ResolvedBinary? = null
        }
        val ex = try { type.serialize(ForeignSource()); null } catch (e: IllegalArgumentException) { e }
        assertTrue("error names the wrong class for debuggability (rule 22)",
            ex?.message?.contains("ForeignSource") == true)
    }

    @Test
    fun `typeId and TYPE_ID are aligned`() {
        assertEquals(LocalBinarySource.TYPE_ID, type.typeId)
    }
}
