package com.github.xepozz.mago.qualityTool

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class MagoAnnotatorProxyTest : BasePlatformTestCase() {

    fun `test ensureMagoPath with empty path`() {
        assertEquals("", MagoAnnotatorProxy.ensureMagoPath(""))
    }

    fun `test ensureMagoPath with absolute path`() {
        val absolutePath = "/usr/bin/php"
        assertEquals(absolutePath, MagoAnnotatorProxy.ensureMagoPath(absolutePath))
    }

    fun `test ensureMagoPath with already prefixed relative path`() {
        assertEquals("./src/index.php", MagoAnnotatorProxy.ensureMagoPath("./src/index.php"))
        assertEquals(".\\src\\index.php", MagoAnnotatorProxy.ensureMagoPath(".\\src\\index.php"))
    }

    fun `test ensureMagoPath with unprefixed relative path`() {
        // Ожидаем, что добавится ./
        val expected = "./src/index.php"
        assertEquals(expected, MagoAnnotatorProxy.ensureMagoPath("src/index.php"))
    }

    fun `test toWorkspaceRelativePath with path inside project`() {
        val basePath = project.basePath!!
        val filePath = File(basePath, "src/main.php").absolutePath

        val expected = "./src/main.php"
        assertEquals(expected, MagoAnnotatorProxy.toRelativePath(basePath, filePath))
    }

    fun `test toWorkspaceRelativePath with path outside project`() {
        val filePath = "/tmp/other.php"
        val result = MagoAnnotatorProxy.toRelativePath("", filePath)
        assertTrue(result.contains("tmp/other.php"))
    }

    fun `test toWorkspaceRelativePath with Windows paths`() {
        val basePath = "D:\\projects"
        val filePath = "D:\\projects\\src\\index.php"

        val result = MagoAnnotatorProxy.toRelativePath(basePath, filePath)

        val expected = "./src/index.php"
        assertEquals(expected, result)
    }

    fun `test toWorkspaceRelativePath with Windows paths2`() {
        val basePath = "D:\\projects"
        val filePath = "\\\\?\\D:\\projects\\index.php"

        val result = MagoAnnotatorProxy.toRelativePath(basePath, filePath)

        val expected = "./index.php"
        assertEquals(expected, result)
    }

    fun `test toWorkspaceRelativePath with Unix paths`() {
        val basePath = "/home/user/project"
        val filePath = "/home/user/project/src/main.php"

        val relative = FileUtil.getRelativePath(basePath, filePath, '/')
        val result = MagoAnnotatorProxy.ensureMagoPath(relative ?: filePath)

        val expected = "./src/main.php"
        assertEquals(expected, result)
    }

    fun `test ensureMagoPath with Windows paths`() {
        val path1 = "\\\\?\\D:\\projects\\index.php"
        val path2 = "D:\\projects\\index.php"

        assertEquals(path1, MagoAnnotatorProxy.ensureMagoPath(path1))
        assertEquals(path2, MagoAnnotatorProxy.ensureMagoPath(path2))
    }


    fun `test ensureMagoPath with Unix paths`() {
        val path = "/home/user/project/src/main.php"
        assertEquals("/home/user/project/src/main.php", MagoAnnotatorProxy.ensureMagoPath(path))
    }
}
