plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Mago"

include(
    ":quality-tools-sdk:core",
    ":quality-tools-sdk:php",
    ":quality-tools-sdk:ui",
    ":quality-tools-sdk:testing",
    ":quality-tools-sdk:testing-junit5",
    ":experimental:php-cs-fixer",
    ":experimental:laravel-pint",
    ":experimental:mess-detector",
    ":experimental:phpstan",
    ":experimental:psalm",
)
