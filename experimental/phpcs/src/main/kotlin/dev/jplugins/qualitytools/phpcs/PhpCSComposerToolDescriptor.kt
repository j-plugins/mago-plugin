package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.php.composer.ComposerToolDescriptor

/**
 * Declarative Composer auto-detect descriptor for `phpcs`.
 *
 * Mirrors the "first three of four" sub-behaviours of the legacy
 * `PhpCSComposerConfig`:
 *
 *  - `squizlabs/php_codesniffer` → set the binary path to
 *    `vendor/bin/phpcs` (the consumer of this descriptor handles
 *    the path resolution; the descriptor only declares the package
 *    + bin name).
 *  - Discover one of `phpcs.xml`, `phpcs.xml.dist`,
 *    `phpcs.dist.xml` at the project root. The legacy plugin tried
 *    `.dist` last; we preserve that order in [configFileNames] so
 *    the bundled "first-existing wins" rule in
 *    `ComposerToolDescriptor.applyDiscoveredConfigFile` produces
 *    the same outcome.
 *  - Parse `scripts.phpcs` for any well-known flag (this scope
 *    leaves the list empty — see TODO).
 *
 * Out of scope (TODO.md):
 *
 *  - The side-by-side `vendor/bin/phpcbf` probe (gap G10).
 *  - The 11-entry `NonPSRStandard` table (Drupal, WordPress, …) —
 *    needs the widened on-detected hook (PHPStan §4.5 widened by
 *    phpcs §4.5).
 *  - Composer `--standard=` adoption from `scripts.phpcs` — needs
 *    the dynamic standards combobox (gap G13) to be useful.
 */
public val PhpCSComposerDescriptor: ComposerToolDescriptor = ComposerToolDescriptor(
    packageName = "squizlabs/php_codesniffer",
    binName = "phpcs",
    configFileNames = listOf(
        "phpcs.xml",
        "phpcs.xml.dist",
        "phpcs.dist.xml",
    ),
    scriptKey = "phpcs",
    scriptArgs = emptyList(),
)
