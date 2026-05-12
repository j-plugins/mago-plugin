package dev.jplugins.qualitytools.php.composer

import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsBag

/**
 * Declarative descriptor of how to auto-detect a PHP quality tool
 * shipped via Composer. Replaces ~1 636 LOC of duplicated
 * `<Tool>ComposerConfig` classes (promotion-analysis.md §2.6).
 *
 * Authors write a per-tool descriptor; the future
 * `ComposerBinarySourceType` (phase 02, lives in `:php` too) consumes
 * it to drive auto-detection. Until then, the descriptor is
 * directly usable by per-tool tests verifying the data is correct.
 *
 * Example:
 *
 *     val descriptor = ComposerToolDescriptor(
 *         packageName = "phpstan/phpstan",
 *         binName = "phpstan",
 *         configFileNames = listOf("phpstan.neon", "phpstan.neon.dist"),
 *         scriptKey = "phpstan",
 *         scriptArgs = listOf(
 *             FlagToOption(flag = "--memory-limit", spec = memoryLimitSpec),
 *             FlagToOption(flag = "--level", spec = levelSpec),
 *         ),
 *     )
 *
 *     // `:php`'s future `ComposerBinarySourceType.onDetected` will call:
 *     descriptor.applyComposerJson(composerJsonText, bag)
 *     descriptor.applyDiscoveredConfigFile(rootDir, bag, configFileSpec)
 */
public class ComposerToolDescriptor(
    public val packageName: String,
    public val binName: String,
    public val configFileNames: List<String> = emptyList(),
    public val scriptKey: String = packageName.substringAfter('/'),
    public val scriptArgs: List<FlagToOption<*>> = emptyList(),
) {

    /** Pair (CLI flag → OptionSpec). When the flag is present in the
     *  composer script, its value is decoded and written to the bag. */
    public data class FlagToOption<T : Any>(
        public val flag: String,
        public val spec: OptionSpec<T>,
    )

    /**
     * Walks the composer.json text, finds the relevant `scripts.<key>`
     * value, and copies every [FlagToOption.flag] it can decode into
     * [bag] via the matching `OptionSpec`. Returns the count of options
     * touched.
     *
     * Silent on missing scripts / unparseable JSON — best-effort.
     */
    public fun applyComposerJson(composerJsonText: String, bag: OptionsBag): Int {
        val composer = ComposerJson.parse(composerJsonText)
        val scriptLine = composer.script(scriptKey) ?: return 0
        var applied = 0
        for (mapping in scriptArgs) {
            val raw = ComposerScriptArgExtractor.extract(scriptLine, mapping.flag)
                ?: continue
            if (applyTyped(mapping, raw, bag)) applied++
        }
        return applied
    }

    /**
     * Writes the path of the first existing config file from
     * [configFileNames] (resolved relative to [rootDir]) into the
     * given [configSpec]. Returns the absolute path used, or `null`
     * when no config file was found.
     *
     * The "exists" predicate is injected so tests don't need an
     * actual filesystem and `:php` doesn't need to know about
     * `java.nio.file.Files`.
     */
    public fun applyDiscoveredConfigFile(
        rootDir: String,
        bag: OptionsBag,
        configSpec: OptionSpec<String>,
        exists: (String) -> Boolean = { java.nio.file.Files.exists(java.nio.file.Paths.get(it)) },
    ): String? {
        val sep = if (rootDir.endsWith("/")) "" else "/"
        for (name in configFileNames) {
            val candidate = "$rootDir$sep$name"
            if (exists(candidate)) {
                bag[configSpec] = candidate
                return candidate
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> applyTyped(
        mapping: FlagToOption<T>,
        raw: String,
        bag: OptionsBag,
    ): Boolean {
        val decoded = mapping.spec.decode(raw) ?: return false
        bag[mapping.spec] = decoded
        return true
    }
}
