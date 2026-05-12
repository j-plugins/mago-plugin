package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.context.CancellationToken
import dev.jplugins.qualitytools.core.context.QtLogger
import dev.jplugins.qualitytools.core.source.local.LocalBinarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalBinarySourceTest {

    private val ctx = object : ResolveContext {
        override val projectId = "t"
        override val basePath: String? = null
        override val cancellation = CancellationToken.Never
        override val logger = QtLogger.NoOp
    }

    @Test
    fun `resolve produces a SimpleResolvedBinary with the configured path`() = runBlocking {
        val src = LocalBinarySource(instanceId = "a", path = "/usr/bin/phpstan")
        val rb = src.resolve(ctx)!!
        assertEquals(listOf("/usr/bin/phpstan"), rb.command)
        assertEquals(emptyMap<String, String>(), rb.env)
        assertSame(PathMapper.Identity, rb.pathMapper)
        assertNull(rb.detectedVersion)
    }

    @Test
    fun `resolve carries extra env and cached detected version`() = runBlocking {
        val src = LocalBinarySource(
            instanceId = "a",
            path = "/bin/phpstan",
            extraEnv = mapOf("PHPSTAN_DEBUG" to "1"),
            cachedDetectedVersion = "1.10.50",
        )
        val rb = src.resolve(ctx)!!
        assertEquals(mapOf("PHPSTAN_DEBUG" to "1"), rb.env)
        assertEquals("1.10.50", rb.detectedVersion)
    }

    @Test
    fun `displayName defaults to path`() {
        val src = LocalBinarySource(instanceId = "a", path = "/usr/bin/x")
        assertEquals("/usr/bin/x", src.displayName)
    }

    @Test
    fun `typeId is the stable constant`() {
        assertEquals("local", LocalBinarySource.TYPE_ID)
        assertEquals("local", LocalBinarySource(instanceId = "a", path = "p").typeId)
    }
}
