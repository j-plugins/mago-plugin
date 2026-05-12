plugins {
    alias(libs.plugins.kotlin)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xcontext-receivers")
    }
}

dependencies {
    // Core must NOT depend on the IntelliJ platform.
    // It only uses the kotlin stdlib + kotlinx-coroutines.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    api("org.jetbrains:annotations:26.0.2")

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
