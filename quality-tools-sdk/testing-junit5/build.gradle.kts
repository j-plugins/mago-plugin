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
    api(project(":quality-tools-sdk:testing"))
    api("org.junit.jupiter:junit-jupiter-api:5.11.0")
    api("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}
