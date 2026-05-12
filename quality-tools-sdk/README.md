# Quality Tools SDK

Standalone Kotlin/JVM library for integrating CLI quality tools
(linters, formatters, static analyzers) into IntelliJ-platform plugins.

This is a separate Gradle subproject. Built independently of the
host plugin (the Mago plugin), it has no `com.jetbrains.php.*` runtime
dependencies in `:core` — the PHP integration lives in `:php`.

Modules:

- `:quality-tools-sdk:core` — language-agnostic contracts and runtime.
- `:quality-tools-sdk:php` — PHP-specific sources (composer, php
  interpreter, xdebug-off mutator).
- `:quality-tools-sdk:ui` — IntelliJ-platform settings UI (Kotlin UI
  DSL) bindings.
- `:quality-tools-sdk:testing` — `FakeProcessRunner` and test
  fixtures for plugin authors.

See `docs/phases/` in the repo root for the implementation plan.
