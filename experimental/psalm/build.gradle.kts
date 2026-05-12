plugins {
    alias(libs.plugins.kotlin)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions { freeCompilerArgs.addAll("-Xjvm-default=all") }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":quality-tools-sdk:core"))
    implementation(project(":quality-tools-sdk:php"))

    testImplementation(project(":quality-tools-sdk:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
