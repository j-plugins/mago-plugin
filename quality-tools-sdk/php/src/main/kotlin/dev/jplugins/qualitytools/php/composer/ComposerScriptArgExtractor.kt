package dev.jplugins.qualitytools.php.composer

/**
 * Extracts the first occurrence of a named flag from a composer
 * `scripts.<key>` value. Five of six legacy plugins do this for
 * `--memory-limit`, `--level`, `--configuration` etc.; all five
 * inlined a slightly different regex. This consolidates them.
 *
 *     val script = "phpstan analyse --memory-limit=4G --level=8"
 *     ComposerScriptArgExtractor.extract(script, "--memory-limit") // "4G"
 *     ComposerScriptArgExtractor.extract(script, "--level") // "8"
 *     ComposerScriptArgExtractor.extract(script, "--missing") // null
 *
 * Handles two argument styles:
 *  - `--key=value` — quoted or bare;
 *  - `--key value` — next token separated by whitespace.
 *
 * For quoted values (`--key="multi word value"`), the quotes are
 * stripped from the returned value.
 */
public object ComposerScriptArgExtractor {

    /**
     * Returns the value of the first occurrence of [flag], or `null`
     * when the flag is absent. [flag] must include the leading
     * dashes (e.g. `--memory-limit`).
     */
    public fun extract(scriptLine: String, flag: String): String? {
        val text = scriptLine
        val escaped = Regex.escape(flag)
        val eqStyle = Regex("""$escaped=(?:"([^"]*)"|'([^']*)'|(\S+))""")
        eqStyle.find(text)?.let { match ->
            return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        }
        val spaceStyle = Regex("""$escaped\s+(?:"([^"]*)"|'([^']*)'|(\S+))""")
        spaceStyle.find(text)?.let { match ->
            return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        }
        return null
    }
}
