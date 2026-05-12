package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.php.composer.ComposerToolDescriptor

/**
 * Composer auto-detect descriptor for PHP-CS-Fixer.
 *
 * The package is `friendsofphp/php-cs-fixer`; the vendored binary is
 * `vendor/bin/php-cs-fixer` (handled generically by the SDK's
 * `ComposerBinarySourceType`).
 *
 * Config-file discovery prefers `.php-cs-fixer.php` over
 * `.php-cs-fixer.dist.php` (a project's local file wins over the
 * committed dist fallback).
 *
 * The script key is `cs-fix`, matching the convention popularised by
 * the package's own `composer.json` and reused across many
 * downstream projects. No script-arg extraction is configured —
 * PHP-CS-Fixer doesn't take common flags whose values we'd want to
 * port back into the user's options bag (the legacy plugin did parse
 * `--config=` / `--rules=`, but those go through the dedicated
 * `customConfig` / `codingStandard` flow, not Composer's script
 * line).
 */
public val PhpCsFixerComposerToolDescriptor: ComposerToolDescriptor =
    ComposerToolDescriptor(
        packageName = "friendsofphp/php-cs-fixer",
        binName = "php-cs-fixer",
        configFileNames = listOf(".php-cs-fixer.php", ".php-cs-fixer.dist.php"),
        scriptKey = "cs-fix",
        scriptArgs = emptyList(),
    )
