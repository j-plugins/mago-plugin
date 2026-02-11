package com.github.xepozz.mago.analysis

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class MagoCliOptionsTest : BasePlatformTestCase() {

    fun `test ensureMagoPath with empty path`() {
        assertEquals("", MagoCliOptions.ensureMagoPath(""))
    }

    fun `test ensureMagoPath with absolute path`() {
        val absolutePath = "/usr/bin/php"
        assertEquals(absolutePath, MagoCliOptions.ensureMagoPath(absolutePath))
    }

    fun `test ensureMagoPath with already prefixed relative path`() {
        assertEquals("./src/index.php", MagoCliOptions.ensureMagoPath("./src/index.php"))
        assertEquals(".\\src\\index.php", MagoCliOptions.ensureMagoPath(".\\src\\index.php"))
    }

    fun `test ensureMagoPath with unprefixed relative path`() {
        val expected = "./src/index.php"
        assertEquals(expected, MagoCliOptions.ensureMagoPath("src/index.php"))
    }

    fun `test toRelativePath with path inside project`() {
        val basePath = project.basePath!!
        val filePath = File(basePath, "src/main.php").absolutePath

        val expected = "./src/main.php"
        assertEquals(expected, MagoCliOptions.toRelativePath(basePath, filePath))
    }

    fun `test toRelativePath with path outside project`() {
        val filePath = "/tmp/other.php"
        val result = MagoCliOptions.toRelativePath("", filePath)
        assertTrue(result.contains("tmp/other.php"))
    }

    fun `test toRelativePath with Windows paths`() {
        val basePath = "D:\\projects"
        val filePath = "D:\\projects\\src\\index.php"

        val result = MagoCliOptions.toRelativePath(basePath, filePath)

        val expected = "./src/index.php"
        assertEquals(expected, result)
    }

    fun `test toRelativePath with Windows paths2`() {
        val basePath = "D:\\projects"
        val filePath = "\\\\?\\D:\\projects\\index.php"

        val result = MagoCliOptions.toRelativePath(basePath, filePath)

        val expected = "./index.php"
        assertEquals(expected, result)
    }

    fun `test toRelativePath with Unix paths`() {
        val basePath = "/home/user/project"
        val filePath = "/home/user/project/src/main.php"

        val relative = FileUtil.getRelativePath(basePath, filePath, '/')
        val result = MagoCliOptions.ensureMagoPath(relative ?: filePath)

        val expected = "./src/main.php"
        assertEquals(expected, result)
    }

    fun `test ensureMagoPath with Windows paths`() {
        val path1 = "\\\\?\\D:\\projects\\index.php"
        val path2 = "D:\\projects\\index.php"

        assertEquals(path1, MagoCliOptions.ensureMagoPath(path1))
        assertEquals(path2, MagoCliOptions.ensureMagoPath(path2))
    }

    fun `test ensureMagoPath with Unix paths`() {
        val path = "/home/user/project/src/main.php"
        assertEquals("/home/user/project/src/main.php", MagoCliOptions.ensureMagoPath(path))
    }
}
