package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.kvPathArg
import dev.jplugins.qualitytools.core.tool.plainArg
import java.io.File

/**
 * Builds the phpcs / phpcbf CLI arg list for a single run.
 *
 * Reference: legacy `PhpCSAnnotatorProxy.getOptions(...)`:
 *
 *     <file>
 *     -n                  (when ignoreWarnings — not modelled in this scope)
 *     --standard=<...>    (resolving "Custom" -> customRuleset path)
 *     --runtime-set installed_paths <paths>   (not in scope)
 *     --encoding=utf-8    (carried by mode.defaultArgs in this port)
 *     --report=xml        (we use checkstyle — mode.defaultArgs carries
 *                          --report=checkstyle and --no-colors)
 *     --extensions=...    (not in scope; bridge-side concern)
 *     --stdin-path=<file> (when stdin)
 *
 * Order discipline:
 *  - `mode.defaultArgs` first — they are the run-shape (formats,
 *    progress flags) and carry the verb when present.
 *  - target second — `<file>` (or `-` when stdin).
 *  - dynamic per-options args next.
 *  - free-form `additionalArgs` last.
 *
 * NOTE: the legacy plugin places `<file>` first; the new SDK pattern
 * is "default args first, then target". The two are equivalent on the
 * wire (phpcs is fully option-order-independent except for the
 * trailing positional). We follow the new SDK convention.
 */
public object PhpCSBuildArgs {

    /** Heuristic: does the value look like a filesystem path?
     *  Used to decide between [kvPathArg] (path-aware) and [plainArg]
     *  (a built-in standard name). */
    @JvmStatic
    public fun looksLikePath(value: String): Boolean {
        if (value.isEmpty()) return false
        if (value.startsWith("/") || value.startsWith("\\")) return true
        if (value.length >= 2 && value[1] == ':') return true // Windows drive letter
        return value.contains(File.separatorChar)
    }

    @JvmStatic
    public fun build(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg> {
        val schema = ctx.tool.optionsSchema as? PhpCSOptionsSchema
            ?: return mode.defaultArgs + target.toCliArg(ctx.scope)
        val bag = ctx.options
        val out = mutableListOf<ToolArg>()

        // 1. Mode-shape (verb if any, plus --report=checkstyle + --no-colors for lint).
        out += mode.defaultArgs

        // 2. Target — file path, or stdin sentinel for stdin runs.
        out += target.toCliArg(ctx.scope)

        // 3. --standard=<X> — "Custom" pivots onto the custom-ruleset path.
        val codingStandardRaw = bag[schema.codingStandard]
        val rulesetPath = bag[schema.customRuleset]
        val resolvedStandard: String? = when {
            codingStandardRaw.equals("Custom", ignoreCase = true) ->
                rulesetPath.takeIf { it.isNotBlank() }
            codingStandardRaw.isNotBlank() -> codingStandardRaw
            else -> null
        }
        if (resolvedStandard != null) {
            out += if (looksLikePath(resolvedStandard)) {
                kvPathArg("--standard", resolvedStandard)
            } else {
                plainArg("--standard=$resolvedStandard")
            }
        }

        // 4. -s — show sniff names alongside each message.
        if (bag[schema.showSniffNames]) {
            out += plainArg("-s")
        }

        // 5. --severity=<N>. phpcs accepts 1..10 (spec-enforced).
        // We always emit the flag so the CLI shape is predictable for
        // tests / log diffing; phpcs treats the default 5 as a no-op.
        val severity = bag[schema.severity]
        out += plainArg("--severity=$severity")

        // 6. Mode-local additionalArgs — free-form, space-separated, last.
        val modeBag = bag.mode(mode.id)
        val perModeSchema = when (mode.id) {
            "fix" -> schema.fixMode
            else -> schema.lintMode
        }
        val extra = modeBag[perModeSchema.additionalArgs]
        if (extra.isNotBlank()) {
            // Tokenize on whitespace — phpcs args don't need shell quoting
            // and the SDK's `String -> ToolArg` boundary is intentionally
            // dumb. Quoting/escaping is a phase 05 concern.
            extra.trim().split(Regex("""\s+""")).forEach { token ->
                out += plainArg(token)
            }
        }

        return out
    }
}
