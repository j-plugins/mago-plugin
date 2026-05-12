package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.plainArg

/**
 * Produces the legacy phpmd CLI per
 * `com.jetbrains.php.tools.quality.messDetector.MessDetectorAnnotator
 * .getOptions(...)` and the helper
 * `MessDetectorValidationInspection.getRuleSetsOption(...)`.
 *
 * Phpmd takes its arguments **positionally** (no subcommand):
 *
 *     phpmd <target-file> <format> <rulesets>
 *
 * For example:
 *
 *     phpmd /abs/src/Foo.php xml codesize,design,naming,unusedcode
 *     phpmd /abs/src/Foo.php xml codesize,/abs/rules/team.xml
 *
 * The rulesets argument is **one** CSV token whose entries are either
 * a closed-set built-in name (`cleancode|codesize|controversial|
 * design|naming|unusedcode`) or an absolute path to a custom XML.
 *
 * ### Path-aware rewriting (gap **G28**) — out of scope
 *
 * The legacy implementation walks each `RulesetDescriptor.path`
 * through `PhpPathMapper` so docker / SSH interpreters see remote
 * paths, while leaving the closed-set tokens (`codesize`, etc.)
 * untouched. The SDK's `PathAwareArgRewriter` would either rewrite
 * the whole `--rulesets=` value (wrong: `codesize` is not a path)
 * or skip it (wrong: `/abs/foo.xml` does need mapping). The plan's
 * recommendation is a new `compositeKvPathArg` helper; until that
 * lands we emit the CSV as a single non-path `plainArg`. See
 * `TODO.md` — gap **G28**.
 */
public object PhpMessDetectorBuildArgs {

    /**
     * Build the argv list for the `analyze` mode.
     *
     * @param ctx run context, used for the [PhpMessDetectorOptionsSchema] bag.
     * @param mode the requested mode; its `defaultArgs` form the
     *   non-target prefix (empty for phpmd today — phpmd takes only
     *   positional args).
     * @param target the file being processed; rendered as the first
     *   positional argument.
     * @param schema the schema instance — supplied so callers can
     *   re-use a single instance across invocations.
     */
    public fun build(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
        schema: PhpMessDetectorOptionsSchema,
    ): List<ToolArg> {
        val args = mutableListOf<ToolArg>()

        // Mode-defined prefix (empty for phpmd today; surface kept for
        // forward-compatibility — e.g. a future `--strict` toggle).
        args.addAll(mode.defaultArgs)

        // Positional: target, format, rulesets.
        args.add(target.toCliArg(ctx.scope))
        args.add(plainArg("xml"))

        val rulesets = composeRulesetsCsv(ctx, schema)
        // Phpmd treats an empty rulesets argument as "no rulesets, no
        // output"; preserve legacy behaviour by always emitting the
        // token even if empty (caller is responsible for short-
        // circuiting the run when nothing's selected — see
        // `MessDetectorAnnotator.getOptions` which returns `null` in
        // that case).
        args.add(plainArg(rulesets))

        return args.toList()
    }

    /**
     * Join enabled built-in ruleset names (in canonical order) with
     * the entries of [PhpMessDetectorOptionsSchema.customRulesetFiles]
     * into a single comma-separated string.
     *
     * Canonical order matches the legacy
     * `MessDetectorValidationInspection.getRuleSetsOption` order
     * (`codesize`, `design`, `naming`, `controversial`, `unusedcode`)
     * — plus `cleancode` first when enabled (a port addition; see
     * port plan §4.4). Custom paths follow built-ins in the order the
     * user typed them.
     *
     * Empty entries in the CSV are skipped silently. The output is
     * not URL-/shell-escaped — phpmd's argv accepts paths verbatim.
     */
    public fun composeRulesetsCsv(
        ctx: ToolRunContext,
        schema: PhpMessDetectorOptionsSchema,
    ): String {
        val bag = ctx.options
        val parts = mutableListOf<String>()

        // Built-in toggles in canonical (legacy) order. `cleancode`
        // is a port addition (plan §4.4); placed first so the output
        // is stable and humans reading argv see toggles before paths.
        if (bag[schema.cleancode]) parts.add("cleancode")
        if (bag[schema.codesize]) parts.add("codesize")
        if (bag[schema.design]) parts.add("design")
        if (bag[schema.naming]) parts.add("naming")
        if (bag[schema.controversial]) parts.add("controversial")
        if (bag[schema.unusedcode]) parts.add("unusedcode")

        // Custom paths CSV — split, trim, drop blanks.
        val customCsv = bag[schema.customRulesetFiles]
        if (customCsv.isNotEmpty()) {
            customCsv.split(',')
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { parts.add(it) }
        }

        return parts.joinToString(",")
    }
}
