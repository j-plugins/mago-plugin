plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions { freeCompilerArgs.addAll("-Xjvm-default=all") }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    api(project(":quality-tools-sdk:core"))

    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
    }

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
}
