plugins {
    alias(libs.plugins.kotlin)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        // -Xcontext-receivers was retired in Kotlin 2.3 in favour of
        // context parameters; we don't use either, so drop the flag.
        // -Xjvm-default=all stays — Java consumers need real defaults
        // on interfaces (rule 17).
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // :core must NOT depend on the IntelliJ platform.
    // Only kotlin stdlib + kotlinx-coroutines + jetbrains-annotations.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    api("org.jetbrains:annotations:26.0.2")

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
