package com.github.xepozz.mago.annotator

import com.github.xepozz.mago.analysis.MagoJsonMessageHandler
import com.github.xepozz.mago.model.MagoProblemDescription
import com.github.xepozz.mago.model.MagoSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class MagoAnnotationFunctionalTest : BasePlatformTestCase() {
    private val handler = MagoJsonMessageHandler()

    override fun getTestDataPath() = "src/test/testData"

    private fun phpContent(caseName: String): String {
        val dir = File(testDataPath, "magoFunctional/$caseName")
        return dir.listFiles()?.first { it.extension == "php" }?.readText()
            ?: error("No PHP file found in $dir")
    }

    private fun magoJson(caseName: String): String =
        File(testDataPath, "magoFunctional/$caseName/mago-output.json").readText()

    private fun textAtByteRange(content: String, byteStart: Int, byteEnd: Int): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return String(bytes.sliceArray(byteStart until byteEnd), Charsets.UTF_8)
    }

    // -- mixedArgumentSubstr --

    fun `test mixedArgumentSubstr - single mixed-argument problem parsed`() {
        val php = phpContent("mixedArgumentSubstr")
        val problems = handler.parseJson(magoJson("mixedArgumentSubstr"), "analysis")

        assertEquals(1, problems.size)
        assertEquals("mixed-argument", problems[0].code)
        assertEquals(MagoSeverity.ERROR, problems[0].severity)
    }

    fun `test mixedArgumentSubstr - byte offset points to dollar-var`() {
        val php = phpContent("mixedArgumentSubstr")
        val problems = handler.parseJson(magoJson("mixedArgumentSubstr"), "analysis")

        assertEquals("\$var", textAtByteRange(php, problems[0].startChar, problems[0].endChar))
    }

    fun `test mixedArgumentSubstr - line number is correct`() {
        val php = phpContent("mixedArgumentSubstr")
        val problems = handler.parseJson(magoJson("mixedArgumentSubstr"), "analysis")
        val substrLine = php.lines().indexOfFirst { it.contains("substr(") }

        assertEquals(substrLine, problems[0].lineNumber)
    }

    // -- multipleErrors --

    fun `test multipleErrors - five problems parsed`() {
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        assertEquals(5, problems.size)
    }

    fun `test multipleErrors - mixed-assignment points to dollar-var`() {
        val php = phpContent("multipleErrors")
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val p = problems.single { it.code == "mixed-assignment" }

        assertEquals(MagoSeverity.WARNING, p.severity)
        assertEquals("\$var", textAtByteRange(php, p.startChar, p.endChar))
    }

    fun `test multipleErrors - mixed-argument on substr points to dollar-var`() {
        val php = phpContent("multipleErrors")
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val p = problems.first { it.code == "mixed-argument" && it.lineNumber == 5 }

        assertEquals("\$var", textAtByteRange(php, p.startChar, p.endChar))
    }

    fun `test multipleErrors - too-many-arguments points to digit-3`() {
        val php = phpContent("multipleErrors")
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val p = problems.single { it.code == "too-many-arguments" }

        assertEquals("3", textAtByteRange(php, p.startChar, p.endChar))
    }

    fun `test multipleErrors - undefined-variable points to dollar-res`() {
        val php = phpContent("multipleErrors")
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val p = problems.single { it.code == "undefined-variable" }

        assertEquals("\$res", textAtByteRange(php, p.startChar, p.endChar))
    }

    fun `test multipleErrors - two problems share dollar-res range`() {
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val resProblems = problems.filter { it.lineNumber == 6 }

        assertEquals(2, resProblems.size)
        val codes = resProblems.map { it.code }.toSet()
        assertTrue(codes.contains("undefined-variable"))
        assertTrue(codes.contains("mixed-argument"))
        assertEquals(resProblems[0].startChar, resProblems[1].startChar)
        assertEquals(resProblems[0].endChar, resProblems[1].endChar)
    }

    fun `test multipleErrors - severities are correct`() {
        val problems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val bySeverity = problems.groupBy { it.severity }

        assertEquals(1, bySeverity[MagoSeverity.WARNING]?.size)
        assertEquals(4, bySeverity[MagoSeverity.ERROR]?.size)
    }

    // -- multipleErrorsDuplicated --

    fun `test multipleErrorsDuplicated - six problems parsed`() {
        val problems = handler.parseJson(magoJson("multipleErrorsDuplicated"), "analysis")
        assertEquals(6, problems.size)
    }

    fun `test multipleErrorsDuplicated - two too-many-arguments on different lines`() {
        val php = phpContent("multipleErrorsDuplicated")
        val problems = handler.parseJson(magoJson("multipleErrorsDuplicated"), "analysis")
        val tooMany = problems.filter { it.code == "too-many-arguments" }

        assertEquals(2, tooMany.size)
        assertFalse(
            "Expected too-many-arguments on different lines",
            tooMany[0].lineNumber == tooMany[1].lineNumber
        )
        assertEquals("3", textAtByteRange(php, tooMany[0].startChar, tooMany[0].endChar))
        assertEquals("3", textAtByteRange(php, tooMany[1].startChar, tooMany[1].endChar))
    }

    fun `test multipleErrorsDuplicated - dollar-res shifted to correct line`() {
        val php = phpContent("multipleErrorsDuplicated")
        val problems = handler.parseJson(magoJson("multipleErrorsDuplicated"), "analysis")
        val echoLine = php.lines().indexOfFirst { it.contains("echo") }
        val resProblems = problems.filter { it.lineNumber == echoLine }

        assertEquals(2, resProblems.size)
        for (p in resProblems) {
            assertEquals("\$res", textAtByteRange(php, p.startChar, p.endChar))
        }
    }

    fun `test multipleErrorsDuplicated - extra line does not affect earlier offsets`() {
        val phpSingle = phpContent("multipleErrors")
        val phpDup = phpContent("multipleErrorsDuplicated")
        val singleProblems = handler.parseJson(magoJson("multipleErrors"), "analysis")
        val dupProblems = handler.parseJson(magoJson("multipleErrorsDuplicated"), "analysis")

        val singleAssign = singleProblems.single { it.code == "mixed-assignment" }
        val dupAssign = dupProblems.single { it.code == "mixed-assignment" }
        assertEquals(singleAssign.startChar, dupAssign.startChar)
        assertEquals(singleAssign.endChar, dupAssign.endChar)
        assertEquals(
            textAtByteRange(phpSingle, singleAssign.startChar, singleAssign.endChar),
            textAtByteRange(phpDup, dupAssign.startChar, dupAssign.endChar)
        )
    }

    // -- byte-to-text edge cases --

    fun `test byte range extraction for ASCII`() {
        val text = "<?php\n\necho \$var;\n"
        assertEquals("echo", textAtByteRange(text, 7, 11))
        assertEquals("\$var", textAtByteRange(text, 12, 16))
    }

    fun `test byte range extraction for UTF-8 multibyte characters`() {
        val text = "<?php\n\$msg = 'héllo';\n"
        val helloStart = text.indexOf("héllo")
        val bytesBefore = text.substring(0, helloStart).toByteArray(Charsets.UTF_8).size
        val helloByteLen = "héllo".toByteArray(Charsets.UTF_8).size
        assertEquals("héllo", textAtByteRange(text, bytesBefore, bytesBefore + helloByteLen))
    }

    fun `test byte range for emoji content`() {
        val text = "<?php\n\$x = '🎉test';\n"
        val testStart = text.indexOf("test")
        val bytesBefore = text.substring(0, testStart).toByteArray(Charsets.UTF_8).size
        assertEquals("test", textAtByteRange(text, bytesBefore, bytesBefore + 4))
    }
}
