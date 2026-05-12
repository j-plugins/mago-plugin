plugins {
    alias(libs.plugins.kotlin)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions { freeCompilerArgs.addAll("-Xjvm-default=all") }
}

dependencies {
    api(project(":quality-tools-sdk:core"))
    api(libs.junit)
    api(libs.opentest4j)
    api("org.jetbrains.kotlin:kotlin-test-junit")
}
