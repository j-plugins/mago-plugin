package dev.jplugins.qualitytools.php.composer

/**
 * Tiny `composer.json` reader — no JSON library, no IntelliJ. Just
 * enough to answer the questions the six legacy `<Tool>ComposerConfig`
 * classes ask:
 *
 *  - Does `require` or `require-dev` declare a given package?
 *  - What's the value of `scripts.<key>`? (Either a string or a list
 *    of strings; we return the whole thing joined by `\n`.)
 *
 * Implementation is a hand-rolled lexer over the JSON subset
 * `composer.json` actually uses (no exotic Unicode escapes, no
 * scientific notation). Robust against `// comments` because Composer
 * itself rejects them — we don't need to be more permissive than the
 * tool we're feeding.
 *
 * Errors return `ComposerJson(emptyMap())` rather than throwing — this
 * is a best-effort discovery helper, not a validator.
 */
public class ComposerJson(
    public val raw: Map<String, Any?>,
) {

    public fun requires(packageName: String): Boolean {
        val require = raw["require"] as? Map<*, *>
        val requireDev = raw["require-dev"] as? Map<*, *>
        return require?.containsKey(packageName) == true ||
            requireDev?.containsKey(packageName) == true
    }

    public fun script(name: String): String? {
        val scripts = raw["scripts"] as? Map<*, *> ?: return null
        val entry = scripts[name] ?: return null
        return when (entry) {
            is String -> entry
            is List<*> -> entry.joinToString("\n") { it?.toString().orEmpty() }
            else -> null
        }
    }

    public companion object {
        public fun parse(text: String): ComposerJson {
            return try {
                val parser = JsonParser(text)
                val value = parser.parseValue()
                @Suppress("UNCHECKED_CAST")
                val map = value as? Map<String, Any?> ?: emptyMap()
                ComposerJson(map)
            } catch (_: Throwable) {
                // best-effort
                ComposerJson(emptyMap())
            }
        }
    }
}

/**
 * Internal JSON parser supporting the subset composer.json uses:
 * objects, arrays, strings, numbers, booleans, null. Throws on
 * malformed input; the public [ComposerJson.parse] catches.
 */
private class JsonParser(private val text: String) {
    private var pos = 0

    fun parseValue(): Any? {
        skipWs()
        return when (val c = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBool()
            'n' -> parseNull()
            in '0'..'9', '-' -> parseNumber()
            else -> error("unexpected '$c' at $pos")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        skipWs()
        val map = linkedMapOf<String, Any?>()
        if (peek() == '}') { pos++; return map }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            expect(':')
            val value = parseValue()
            map[key] = value
            skipWs()
            when (val c = peek()) {
                ',' -> { pos++ }
                '}' -> { pos++; return map }
                else -> error("expected ',' or '}' got '$c' at $pos")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        skipWs()
        val list = mutableListOf<Any?>()
        if (peek() == ']') { pos++; return list }
        while (true) {
            list += parseValue()
            skipWs()
            when (val c = peek()) {
                ',' -> { pos++ }
                ']' -> { pos++; return list }
                else -> error("expected ',' or ']' got '$c' at $pos")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            val c = next()
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    when (val esc = next()) {
                        '"', '\\', '/' -> sb.append(esc)
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('')
                        'u' -> {
                            val hex = text.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> error("bad escape \\$esc at $pos")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseBool(): Boolean = when {
        text.regionMatches(pos, "true", 0, 4) -> { pos += 4; true }
        text.regionMatches(pos, "false", 0, 5) -> { pos += 5; false }
        else -> error("expected boolean at $pos")
    }

    private fun parseNull(): Any? {
        require(text.regionMatches(pos, "null", 0, 4)) { "expected null at $pos" }
        pos += 4
        return null
    }

    private fun parseNumber(): Number {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        val literal = text.substring(start, pos)
        return literal.toLongOrNull() ?: literal.toDouble()
    }

    private fun skipWs() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char =
        if (pos < text.length) text[pos] else error("unexpected end of input at $pos")

    private fun next(): Char =
        if (pos < text.length) text[pos++] else error("unexpected end of input at $pos")

    private fun expect(c: Char) {
        if (next() != c) error("expected '$c' at ${pos - 1}")
    }
}
