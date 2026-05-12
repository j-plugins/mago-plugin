package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.kvPathArg
import dev.jplugins.qualitytools.core.tool.pathArg
import dev.jplugins.qualitytools.core.tool.plainArg

/**
 * Pure CLI-arg builder for Pint, called by [LaravelPintTool.buildArgs].
 *
 * Argument shape derived from the legacy
 * `LaravelPintAnnotatorProxy.getCommandLineOptions(...)` (decompiled
 * source: `/tmp/decomp/com/jetbrains/php/tools/quality/laravelPint/
 * LaravelPintAnnotatorProxy.java`):
 *
 *   1. the target file (Pint itself takes a positional file/dir arg —
 *      no `-f` / no stdin);
 *   2. optionally `--config=<path>` when a `pint.json` is configured;
 *   3. optionally `--verbose` when the user toggled it on.
 *
 * Deliberately excluded from this first cut (TODO.md):
 *   - `--preset=<laravel|symfony|psr12>` — needs the preset combobox
 *     option port (legacy `LaravelPintOptionsConfiguration.ruleset`).
 *   - `--dirty` — needs the "reformat only uncommitted files"
 *     option port (shared with the format-on-commit check-in
 *     handler gap G24).
 *   - `--test --format=xml -vvv` — these belong to the `analyze` /
 *     on-the-fly mode the legacy plugin runs; the SDK port hasn't
 *     wired that mode yet (out-of-scope here; needs phase 06
 *     ResultReader + the CS-Fixer diff-XML reader to land first).
 *
 * Kept as a top-level object so unit tests can exercise it directly
 * without instantiating the whole `QualityTool`.
 */
public object LaravelPintBuildArgs {

    public fun build(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
        schema: LaravelPintOptionsSchema,
    ): List<ToolArg> = buildList {
        // 1. positional target file (Pint takes the file/dir directly).
        add(target.toCliArg(ctx.scope))

        // 2. --config=<pint.json> when configured.
        val configPath = ctx.options[schema.customConfig].trim()
        if (configPath.isNotEmpty()) {
            add(kvPathArg("--config", configPath))
        }

        // 3. --verbose when toggled.
        if (ctx.options[schema.verbose]) {
            add(plainArg("--verbose"))
        }

        // 4. Mode-level `additionalArgs`, parsed loosely on whitespace.
        //    The SDK doesn't expose `ParametersList` in `:core`, so we
        //    use a simple split — same approach as the cookbook §3.3
        //    example. Empty / blank tokens dropped.
        val extra = ctx.options.mode(mode.id)[schema.formatAdditionalArgs]
        if (extra.isNotBlank()) {
            for (token in extra.trim().split(Regex("\\s+"))) {
                if (token.isNotEmpty()) add(plainArg(token))
            }
        }

        // 5. Per-mode `defaultArgs` (currently empty for Pint's single
        //    format mode — the binary needs no extra switches to
        //    rewrite the file in place).
        addAll(mode.defaultArgs)
    }

    /**
     * Convenience overload used only by unit tests where building a
     * `ToolTarget` from a raw path is the shorter spelling.
     */
    internal fun buildFromRawPath(
        ctx: ToolRunContext,
        mode: ToolMode,
        targetPath: String,
        schema: LaravelPintOptionsSchema,
    ): List<ToolArg> = build(
        ctx = ctx,
        mode = mode,
        target = object : ToolTarget {
            override val normalizedPath: String = targetPath
            override fun toCliArg(scope: dev.jplugins.qualitytools.core.scope.ResolvedScope) =
                pathArg(targetPath)
        },
        schema = schema,
    )
}
