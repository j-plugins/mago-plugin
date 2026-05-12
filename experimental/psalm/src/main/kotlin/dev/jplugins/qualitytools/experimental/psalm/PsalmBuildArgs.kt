package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.kvPathArg
import dev.jplugins.qualitytools.core.tool.plainArg

/**
 * Pure CLI-building function for Psalm.
 *
 * Mirrors the legacy `PsalmGlobalInspection.getCommandLineOptions(...)` at
 * `/tmp/decomp/com/jetbrains/php/tools/quality/psalm/PsalmGlobalInspection.java`
 * lines 244–266:
 *
 *  1. `--output-format=checkstyle` — provided by `mode.defaultArgs`
 *     (the SDK convention for verb-less tools is to bake the
 *     output-format into `defaultArgs`).
 *  2. `--no-progress`, `--no-cache` — also from `mode.defaultArgs`.
 *  3. `-c <path>` only when [PsalmOptionsSchema.config] is non-empty
 *     (Psalm accepts both `-c` and `--config-file`; the legacy plugin
 *     uses the short form, so we preserve it for byte-for-byte
 *     compatibility).
 *  4. `--show-info=true`, `--find-unused-code`,
 *     `--find-unused-psalm-suppress` — conditional on the corresponding
 *     boolean specs.
 *  5. `--monochrome` — always, matching the legacy behaviour (Psalm
 *     uses it instead of `--no-ansi`).
 *  6. The target file path — single positional argument, last.
 *
 * Pure function: no I/O, no Project, no PSI. Suitable for unit
 * testing with `MapOptionsBag`.
 */
public object PsalmBuildArgs {

    public fun build(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
        schema: PsalmOptionsSchema,
    ): List<ToolArg> {
        val options = ctx.options
        val result = mutableListOf<ToolArg>()

        // 1. Mode-provided defaults: --output-format=checkstyle, --no-progress, --no-cache.
        result.addAll(mode.defaultArgs)

        // 2. Optional config path (-c <path>). Legacy uses the short form.
        val configPath = options[schema.config]
        if (configPath.isNotEmpty()) {
            result += kvPathArg("-c", configPath)
        }

        // 3. Conditional boolean flags, in the legacy emission order.
        if (options[schema.showInfo]) {
            result += plainArg("--show-info=true")
        }
        if (options[schema.findUnusedCode]) {
            result += plainArg("--find-unused-code")
        }
        if (options[schema.findUnusedPsalmSuppress]) {
            result += plainArg("--find-unused-psalm-suppress")
        }

        // 4. --monochrome (always, per legacy).
        result += plainArg("--monochrome")

        // 5. Target file path, last.
        result += target.toCliArg(ctx.scope)

        return result.toList()
    }
}
