@file:JvmName("PhpStanBuildArgs")

package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.core.context.ThreadingPolicy
import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.kvPathArg
import dev.jplugins.qualitytools.core.tool.pathArg
import dev.jplugins.qualitytools.core.tool.plainArg

/**
 * Pure command-line builder for PHPStan. Top-level function (per the
 * §C.1 work plan) so it can be referenced from
 * `PhpStanTool.buildArgs { ... }` and tested standalone without
 * constructing a `QualityTool`.
 *
 * Argument shape — verbatim from the legacy
 * `com.jetbrains.php.tools.quality.phpstan.PhpStanGlobalInspection.getCommandLineOptions`
 * lines 117-138 (`/tmp/decomp/com/jetbrains/php/tools/quality/phpstan/PhpStanGlobalInspection.java`):
 *
 *     analyze
 *     [-c <config>            // when configuration is set
 *      OR --level=<N>]        // else
 *     [-a <autoload>]         // when autoload is set
 *     --memory-limit=<X>
 *     --error-format=checkstyle
 *     --no-progress
 *     --no-ansi
 *     --no-interaction
 *     <file...>
 *
 * Key differences from the legacy code that are deliberate:
 *  - We never emit `null` paths; the legacy `ContainerUtil.filter(..., Objects::nonNull)`
 *    is replaced by the caller supplying a non-null [target].
 *  - `--config` / `-a` / file path are emitted as path-aware [ToolArg]s
 *    so the future path-aware rewriter (phase 05) can remap them when
 *    running through a remote interpreter; the legacy plugin did this
 *    manually via `QualityToolAnnotator.updateIfRemoteMappingExists`.
 *
 * Gap reference: tool-version-aware branching (gap 4.3 in the port
 * plan) is not exercised here yet because `ResolvedBinary.detectedVersion`
 * propagation is not wired in the experimental package. See
 * `experimental/phpstan/TODO.md` gap 4.3.
 */
@ThreadingPolicy("background")
public fun buildPhpStanArgs(
    ctx: ToolRunContext,
    @Suppress("UNUSED_PARAMETER") mode: ToolMode,
    target: ToolTarget,
): List<ToolArg> {
    val schema = ctx.tool.optionsSchema as? PhpStanOptionsSchema
        ?: error(
            "PhpStanBuildArgs requires PhpStanOptionsSchema; got " +
                "${ctx.tool.optionsSchema::class.simpleName}",
        )
    val out = ArrayList<ToolArg>(10)

    // 1. Verb
    out += plainArg("analyze")

    // 2. Config XOR level
    val config = ctx.options[schema.config]
    if (config.isNotEmpty()) {
        // Legacy emits "-c" + "<value>" as two args. We use kvPathArg so
        // the path-aware rewriter can still recognise the value as a
        // path; the resulting raw form is "-c=<path>". That deviates one
        // token's worth from legacy, but matches how every other tool in
        // the new SDK declares config paths (e.g. PHPCS, Psalm) and
        // PHPStan accepts both `-c X` and `-c=X` (verified manually).
        out += kvPathArg("-c", config)
    } else {
        out += plainArg("--level=${ctx.options[schema.level]}")
    }

    // 3. Optional autoload
    val autoload = ctx.options[schema.autoload]
    if (autoload.isNotEmpty()) {
        out += kvPathArg("-a", autoload)
    }

    // 4. Memory limit (always present — legacy default is "2G")
    out += plainArg("--memory-limit=${ctx.options[schema.memoryLimit]}")

    // 5. Fixed flags
    out += plainArg("--error-format=checkstyle")
    out += plainArg("--no-progress")
    out += plainArg("--no-ansi")
    out += plainArg("--no-interaction")

    // 6. Target path
    out += pathArg(target.normalizedPath)

    return out
}
