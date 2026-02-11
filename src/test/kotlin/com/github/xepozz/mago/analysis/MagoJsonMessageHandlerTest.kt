package com.github.xepozz.mago.analysis

import com.github.xepozz.mago.model.MagoProblemDescription
import com.github.xepozz.mago.model.MagoSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MagoJsonMessageHandlerTest : BasePlatformTestCase() {
    private val handler = MagoJsonMessageHandler()

    fun `test empty object returns empty list`() {
        assertEquals(emptyList<MagoProblemDescription>(), handler.parseJson("{}", "analysis"))
    }

    fun `test null JSON returns empty list`() {
        assertEquals(emptyList<MagoProblemDescription>(), handler.parseJson("null", "analysis"))
    }

    fun `test empty issues returns empty list`() {
        assertEquals(emptyList<MagoProblemDescription>(), handler.parseJson("""{"issues": []}""", "analysis"))
    }

    fun `test levelToSeverity mapping`() {
        assertEquals(MagoSeverity.ERROR, handler.levelToSeverity("Error"))
        assertEquals(MagoSeverity.WARNING, handler.levelToSeverity("Warning"))
        assertEquals(MagoSeverity.INFO, handler.levelToSeverity("Help"))
        assertEquals(MagoSeverity.INFO, handler.levelToSeverity("Note"))
        assertEquals(MagoSeverity.INFO, handler.levelToSeverity("Unknown"))
        assertEquals(MagoSeverity.INFO, handler.levelToSeverity(null))
    }

    fun `test parse single issue extracts all fields`() {
        val json = $$"""
        {
          "issues": [{
            "code": "undefined-variable",
            "level": "Error",
            "message": "Undefined variable: `$res`.",
            "help": "Check for typos.",
            "notes": ["Did you mean `$result`?"],
            "edits": [],
            "annotations": [{
              "kind": "Primary",
              "message": "Variable used here",
              "span": {
                "file_id": {"path": "/tmp/test.php"},
                "start": {"line": 5, "offset": 42},
                "end": {"offset": 46}
              }
            }]
          }]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")

        assertEquals(1, problems.size)
        val p = problems[0]
        assertEquals("undefined-variable", p.code)
        assertEquals(MagoSeverity.ERROR, p.severity)
        assertEquals(5, p.lineNumber)
        assertEquals(42, p.startChar)
        assertEquals(46, p.endChar)
        assertEquals($$"Undefined variable: `$res`", p.myMessage)
        assertEquals("/tmp/test.php", p.myFile)
        assertEquals("analysis", p.category)
        assertEquals("Check for typos.", p.help)
        assertEquals(listOf($$"Did you mean `$result`?"), p.notes)
    }

    fun `test message trailing dot is trimmed`() {
        val json = buildSingleIssueJson(
            code = "test",
            level = "Warning",
            message = "Trailing dot removed.",
            offset = 0 to 5
        )
        val problems = handler.parseJson(json, "analysis")
        assertEquals("Trailing dot removed", problems[0].myMessage)
    }

    fun `test message without trailing dot is unchanged`() {
        val json = buildSingleIssueJson(
            code = "test",
            level = "Warning",
            message = "No trailing dot",
            offset = 0 to 5
        )
        val problems = handler.parseJson(json, "analysis")
        assertEquals("No trailing dot", problems[0].myMessage)
    }

    fun `test category is passed through`() {
        val json = buildSingleIssueJson(code = "test-rule", level = "Warning", message = "Test.", offset = 0 to 5)

        assertEquals("lint", handler.parseJson(json, "lint")[0].category)
        assertEquals("guard", handler.parseJson(json, "guard")[0].category)
        assertEquals("analysis", handler.parseJson(json, "analysis")[0].category)
    }

    fun `test Secondary annotations are filtered out by default`() {
        val json = """
        {
          "issues": [{
            "code": "some-check",
            "level": "Error",
            "message": "Some error.",
            "help": "",
            "notes": [],
            "edits": [],
            "annotations": [
              {
                "kind": "Primary",
                "message": "Primary annotation",
                "span": {
                  "file_id": {"path": "/tmp/test.php"},
                  "start": {"line": 1, "offset": 10},
                  "end": {"offset": 15}
                }
              },
              {
                "kind": "Secondary",
                "message": "Secondary annotation",
                "span": {
                  "file_id": {"path": "/tmp/test.php"},
                  "start": {"line": 2, "offset": 20},
                  "end": {"offset": 25}
                }
              }
            ]
          }]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")
        assertEquals(1, problems.size)
        assertEquals("Some error", problems[0].myMessage)
    }

    fun `test type-inspection keeps Secondary annotations`() {
        val json = """
        {
          "issues": [{
            "code": "type-inspection",
            "level": "Error",
            "message": "Type mismatch.",
            "help": "",
            "notes": [],
            "edits": [],
            "annotations": [
              {
                "kind": "Primary",
                "message": "Primary",
                "span": {
                  "file_id": {"path": "/tmp/test.php"},
                  "start": {"line": 1, "offset": 10},
                  "end": {"offset": 15}
                }
              },
              {
                "kind": "Secondary",
                "message": "Related type issue",
                "span": {
                  "file_id": {"path": "/tmp/test.php"},
                  "start": {"line": 2, "offset": 20},
                  "end": {"offset": 25}
                }
              }
            ]
          }]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")
        assertEquals(2, problems.size)
        assertEquals("Type mismatch", problems[0].myMessage)
        assertEquals("Related type issue", problems[1].myMessage)
    }

    fun `test parse issue with edits and replacements`() {
        val json = """
        {
          "issues": [{
            "code": "missing-return-type",
            "level": "Warning",
            "message": "Missing return type.",
            "help": "Add return type.",
            "notes": [],
            "edits": [
              [
                {"name": "Add void return type", "path": "/tmp/test.php"},
                [{"range": {"start": 50, "end": 50}, "new_text": ": void", "safety": "safe"}]
              ]
            ],
            "annotations": [{
              "kind": "Primary",
              "message": "Function missing return type",
              "span": {
                "file_id": {"path": "/tmp/test.php"},
                "start": {"line": 3, "offset": 20},
                "end": {"offset": 30}
              }
            }]
          }]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")
        assertEquals(1, problems.size)

        val edit = problems[0].edits.single()
        assertEquals("Add void return type", edit.name)
        assertEquals("/tmp/test.php", edit.path)

        val replacement = edit.replacements.single()
        assertEquals(50, replacement.start)
        assertEquals(50, replacement.end)
        assertEquals(": void", replacement.newText)
        assertEquals("safe", replacement.safety)
    }

    fun `test multiple edits per issue`() {
        val json = """
        {
          "issues": [{
            "code": "fix-me",
            "level": "Error",
            "message": "Needs fixing.",
            "help": "",
            "notes": [],
            "edits": [
              [
                {"name": "Fix A", "path": "/tmp/a.php"},
                [{"range": {"start": 10, "end": 15}, "new_text": "fixed_a", "safety": "safe"}]
              ],
              [
                {"name": "Fix B", "path": "/tmp/b.php"},
                [{"range": {"start": 20, "end": 25}, "new_text": "fixed_b", "safety": "potentially_unsafe"}]
              ]
            ],
            "annotations": [{
              "kind": "Primary",
              "message": "Error here",
              "span": {
                "file_id": {"path": "/tmp/a.php"},
                "start": {"line": 1, "offset": 10},
                "end": {"offset": 15}
              }
            }]
          }]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")
        assertEquals(2, problems[0].edits.size)
        assertEquals("Fix A", problems[0].edits[0].name)
        assertEquals("Fix B", problems[0].edits[1].name)
        assertEquals("safe", problems[0].edits[0].replacements[0].safety)
        assertEquals("potentially_unsafe", problems[0].edits[1].replacements[0].safety)
    }

    fun `test Windows path prefix is stripped`() {
        val json = """
        {
          "issues": [{
            "code": "test",
            "level": "Error",
            "message": "Test.",
            "help": "",
            "notes": [],
            "edits": [],
            "annotations": [{
              "kind": "Primary",
              "message": "Primary",
              "span": {
                "file_id": {"path": "\\\\?\\D:\\projects\\test.php"},
                "start": {"line": 1, "offset": 0},
                "end": {"offset": 5}
              }
            }]
          }]
        }
        """.trimIndent()
        val problems = handler.parseJson(json, "analysis")
        assertEquals("D:/projects/test.php", problems[0].myFile)
    }

    fun `test multiple issues produce multiple problems`() {
        val json = """
        {
          "issues": [
            {
              "code": "error-one",
              "level": "Error",
              "message": "First.",
              "help": "",
              "notes": [],
              "edits": [],
              "annotations": [{
                "kind": "Primary",
                "message": "First",
                "span": {
                  "file_id": {"path": "/tmp/test.php"},
                  "start": {"line": 1, "offset": 0},
                  "end": {"offset": 5}
                }
              }]
            },
            {
              "code": "error-two",
              "level": "Warning",
              "message": "Second.",
              "help": "",
              "notes": [],
              "edits": [],
              "annotations": [{
                "kind": "Primary",
                "message": "Second",
                "span": {
                  "file_id": {"path": "/tmp/test.php"},
                  "start": {"line": 3, "offset": 20},
                  "end": {"offset": 30}
                }
              }]
            }
          ]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")
        assertEquals(2, problems.size)
        assertEquals("error-one", problems[0].code)
        assertEquals("error-two", problems[1].code)
        assertEquals(MagoSeverity.ERROR, problems[0].severity)
        assertEquals(MagoSeverity.WARNING, problems[1].severity)
    }

    fun `test issue with empty annotations returns no problems`() {
        val json = """
        {
          "issues": [{
            "code": "internal",
            "level": "Error",
            "message": "Internal error.",
            "help": "",
            "notes": [],
            "edits": [],
            "annotations": []
          }]
        }
        """.trimIndent()

        assertEquals(emptyList<MagoProblemDescription>(), handler.parseJson(json, "analysis"))
    }

    fun `test issue with missing span fields returns no problems`() {
        val json = """
        {
          "issues": [{
            "code": "broken",
            "level": "Error",
            "message": "Broken.",
            "help": "",
            "notes": [],
            "edits": [],
            "annotations": [{
              "kind": "Primary",
              "message": "Missing span fields",
              "span": {
                "file_id": {"path": "/tmp/test.php"},
                "start": {},
                "end": {}
              }
            }]
          }]
        }
        """.trimIndent()

        assertEquals(emptyList<MagoProblemDescription>(), handler.parseJson(json, "analysis"))
    }

    fun `test notes are extracted correctly`() {
        val json = """
        {
          "issues": [{
            "code": "test",
            "level": "Error",
            "message": "Test.",
            "help": "Some help",
            "notes": ["Note 1", "Note 2", "Note 3"],
            "edits": [],
            "annotations": [{
              "kind": "Primary",
              "message": "Primary",
              "span": {
                "file_id": {"path": "/tmp/test.php"},
                "start": {"line": 1, "offset": 0},
                "end": {"offset": 5}
              }
            }]
          }]
        }
        """.trimIndent()

        val problems = handler.parseJson(json, "analysis")
        assertEquals(listOf("Note 1", "Note 2", "Note 3"), problems[0].notes)
    }

    private fun buildSingleIssueJson(
        code: String,
        level: String,
        message: String,
        offset: Pair<Int, Int>,
        line: Int = 1,
        filePath: String = "/tmp/test.php",
    ): String = """
        {
          "issues": [{
            "code": "$code",
            "level": "$level",
            "message": "$message",
            "help": "",
            "notes": [],
            "edits": [],
            "annotations": [{
              "kind": "Primary",
              "message": "Primary",
              "span": {
                "file_id": {"path": "$filePath"},
                "start": {"line": $line, "offset": ${offset.first}},
                "end": {"offset": ${offset.second}}
              }
            }]
          }]
        }
    """.trimIndent()
}
