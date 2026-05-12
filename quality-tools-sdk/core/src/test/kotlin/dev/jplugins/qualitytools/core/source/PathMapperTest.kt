package dev.jplugins.qualitytools.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PathMapperTest {

    @Test
    fun `Identity canProcess is false`() {
        assertFalse(PathMapper.Identity.canProcess("/anything"))
        assertFalse(PathMapper.Identity.canProcess(""))
    }

    @Test
    fun `Identity toRemote and toLocal echo input`() {
        val m = PathMapper.Identity
        val path = "/usr/local/bin/phpstan"
        assertEquals(path, m.toRemote(path))
        assertEquals(path, m.toLocal(path))
    }

    @Test
    fun `custom mapper overrides defaults`() {
        val docker = object : PathMapper {
            override fun toRemote(localPath: String) =
                localPath.replace("/host/", "/var/www/")
            override fun toLocal(remotePath: String) =
                remotePath.replace("/var/www/", "/host/")
            override fun canProcess(localPath: String) = localPath.startsWith("/host/")
        }

        kotlin.test.assertTrue(docker.canProcess("/host/proj/src/Foo.php"))
        kotlin.test.assertFalse(docker.canProcess("/elsewhere/x.php"))
        assertEquals("/var/www/proj/src/Foo.php", docker.toRemote("/host/proj/src/Foo.php"))
        assertEquals("/host/proj/src/Foo.php", docker.toLocal("/var/www/proj/src/Foo.php"))
    }
}
