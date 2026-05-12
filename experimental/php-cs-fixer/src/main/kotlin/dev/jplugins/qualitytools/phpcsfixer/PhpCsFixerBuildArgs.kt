package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.kvPathArg
import dev.jplugins.qualitytools.core.tool.plainArg

/**
 * Produces the legacy PHP-CS-Fixer CLI per
 * `PhpCSFixerAnnotatorProxy.getOptions(...)` /
 * `PhpCSFixerReformatFile.getOptions(...)`.
 *
 * Two modes are supported:
 *
 *  * `format` — runs `fix --no-interaction --no-ansi --using-cache=no`
 *    over the target so PHP-CS-Fixer rewrites the file in place.
 *  * `dry-run` — same plus `--dry-run --format=json`, so the result
 *    is a JSON diff envelope (consumed by the future `UdiffReader`,
 *    gap **G21**).
 *
 * For each mode:
 *  * if [PhpCsFixerOptionsSchema.codingStandard] equals the sentinel
 *    `"Custom"`, `--config=<path>` is emitted with the bag's
 *    [PhpCsFixerOptionsSchema.customConfig];
 *  * otherwise, `--rules=<standard>` is emitted with the chosen preset;
 *  * `--allow-risky=yes|no` is emitted **only** when the standard is
 *    not `Custom` (the legacy plugin suppresses it for custom
 *    configs because the config file controls risky behaviour).
 */
public object PhpCsFixerBuildArgs {

    /**
     * Build the argv list for one run.
     *
     * @param ctx run context, used for the [PhpCsFixerOptionsSchema]
     *   options bag.
     * @param mode the requested mode; its `defaultArgs` form the
     *   non-target prefix (e.g. `["fix", "--no-interaction",
     *   "--no-ansi"]`).
     * @param target the file or directory being processed.
     * @param schema the schema instance — supplied so callers can
     *   re-use a single instance across invocations.
     */
    public fun build(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
        schema: PhpCsFixerOptionsSchema,
    ): List<ToolArg> {
        val bag = ctx.options
        val standard = bag[schema.codingStandard]
        val isCustom = standard == PhpCsFixerOptionsSchema.CUSTOM_STANDARD

        val args = mutableListOf<ToolArg>()

        // Mode-defined prefix (the verb + the legacy non-interactive flags).
        args.addAll(mode.defaultArgs)

        // Always disable the cache: stale cache entries on a path the
        // IDE just rewrote produce confusing diff output.
        args.add(plainArg("--using-cache=no"))

        // Config vs preset selection.
        if (isCustom) {
            val path = bag[schema.customConfig]
            if (path.isNotEmpty()) {
                args.add(kvPathArg("--config", path))
            }
        } else if (standard.isNotEmpty()) {
            args.add(plainArg("--rules=$standard"))
        }

        // `--allow-risky` is suppressed for custom configs (the
        // config file controls risky behaviour).
        if (!isCustom) {
            val risky = if (bag[schema.allowRiskyRules]) "yes" else "no"
            args.add(plainArg("--allow-risky=$risky"))
        }

        // Dry-run gets the JSON envelope; format mode leaves stdout alone.
        if (mode.id == PhpCsFixerOptionsSchema.MODE_DRY_RUN) {
            args.add(plainArg("--dry-run"))
            args.add(plainArg("--format=json"))
        }

        // The target always goes last (PHP-CS-Fixer accepts it
        // positionally after the flags).
        args.add(target.toCliArg(ctx.scope))

        return args.toList()
    }
}
