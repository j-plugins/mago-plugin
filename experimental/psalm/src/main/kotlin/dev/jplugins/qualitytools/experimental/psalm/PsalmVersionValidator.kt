package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * Parses `psalm --version` output.
 *
 * Legacy regex (decompiled `PsalmConfigurableForm.validateMessage`):
 * `Psalm.* v?([\d.]*).*` — but we don't need the trailing wildcard;
 * the `PhpToolVersionParser` already greedy-trims. Our pattern only
 * needs to capture the leading semver immediately after the tool
 * name, allowing both space-separated forms used in the wild:
 *
 *   "Psalm 5.15.0@a1b2c3" (Psalm 5.x, with git-hash suffix)
 *   "Psalm 4.30.0"        (older Psalm release)
 *
 * The `dev-master` build-string (a legacy plugin special case for
 * non-tagged Psalm checkouts) is **not** version-parseable by this
 * pattern and falls back to the validator's "Cannot determine version"
 * branch. Future work — see TODO.md — should re-introduce the
 * dev-master carve-out via `ResolvedBinary.detectedVersion` once the
 * SDK exposes a version-aware override hook (Tier-1 patch G8 already
 * supplies the seam).
 */
public object PsalmVersionValidator {

    public const val TOOL_NAME: String = "Psalm"

    public const val VERSION_PATTERN: String = "Psalm.*?(\\d+\\.\\d+(?:\\.\\d+)?)"

    public fun create(): PhpToolVersionParser = PhpToolVersionParser(
        toolName = TOOL_NAME,
        versionPattern = VERSION_PATTERN,
    )
}
