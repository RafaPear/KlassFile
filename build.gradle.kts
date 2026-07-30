plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    jacoco
    id("org.sonarqube") version "7.3.1.8318"

    // Apply the ktlint plugin for code style checking.
    // id("org.jlleitschuh.gradle.ktlint")
}

sonar {
    properties {
        property("sonar.organization", "rafapear")
        property("sonar.projectKey", "RafaPear_KlassFile")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use the Kotlin reflection library for advanced features.
    implementation(kotlin("reflect"))

    // Use the Kotlin Test integration.
    testImplementation(kotlin("test"))
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}
