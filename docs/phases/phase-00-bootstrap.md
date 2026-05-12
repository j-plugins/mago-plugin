# Phase 00 — Bootstrap

## Goal

A new Gradle subproject `:quality-tools-sdk` exists and builds, with
four sub-modules (`core`, `php`, `ui`, `testing`). The Mago plugin
does not yet depend on it.

## Feature

Plugin authors and the future Mago migration get a place where the new
SDK lives, separated from existing Mago code so we can iterate without
touching shipped functionality.

## Solution

Single-build composite: subproject under `quality-tools-sdk/`. Module
`:core` deliberately has no IntelliJ-platform dependency — only kotlin
stdlib, kotlinx-coroutines, jetbrains-annotations. This is enforced by
build config and verified in cycle reviews.

`explicitApi()` is enabled in every module so every public symbol must
declare visibility — protects against accidental API leakage.

## Deliverables

Created in this phase (already committed in the same change):

- `quality-tools-sdk/README.md`
- `quality-tools-sdk/build.gradle.kts` (intentionally empty)
- `quality-tools-sdk/core/build.gradle.kts`
- `quality-tools-sdk/php/build.gradle.kts`
- `quality-tools-sdk/ui/build.gradle.kts`
- `quality-tools-sdk/testing/build.gradle.kts`
- `quality-tools-sdk/{core,php,ui,testing}/src/{main,test}/kotlin/`
- update to `settings.gradle.kts` to `include(...)`

## Acceptance criteria

- [ ] `./gradlew :quality-tools-sdk:core:assemble` succeeds.
- [ ] `./gradlew :quality-tools-sdk:php:assemble` succeeds.
- [ ] `./gradlew :quality-tools-sdk:ui:assemble` succeeds.
- [ ] `./gradlew :quality-tools-sdk:testing:assemble` succeeds.
- [ ] Running `./gradlew :quality-tools-sdk:core:dependencies` lists
      no `com.jetbrains.intellij.*`, no `com.jetbrains.php*`, no
      `org.jdom*`, no `com.intellij.uiDesigner*`, no AWT/Swing
      transitive entries.
- [ ] `-Xjvm-default=all` is set in every SDK module's
      `compileKotlin` (verified by `javap -v <ConfigSourceType>`
      showing default methods on the interface, not `$DefaultImpls`).
- [ ] Kotlin compiler version is pinned in `libs.versions.toml`;
      changing it requires a `CHANGELOG-ABI.md` entry (CI guard).
- [ ] `binary-compatibility-validator` Gradle plugin is applied to
      every public SDK module (`:core`, `:php`, `:ui`); the API
      dump is checked into git and changes are reviewed
      mechanically.
- [ ] `explicitApi()` is enabled in every SDK module.
- [ ] No code is present in any module yet (empty `src/main/kotlin/`).
- [ ] Existing `./gradlew :buildPlugin` for the Mago plugin still
      succeeds without changes.

## Out of scope

- Any source code.
- Maven publication.
- IntelliJ plugin metadata for any module.

## Depends on

Nothing.
