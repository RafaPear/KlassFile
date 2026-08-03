plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.sonarqube)
    jacoco

    // Apply the ktlint plugin for code style checking.
    // id("org.jlleitschuh.gradle.ktlint")
}

val generateDokkaModule = tasks.register<Copy>("generateDokkaModule") {
    description = "Generates a Dokka module file for the project."
    group = "documentation"

    from(layout.projectDirectory.file("README.md"))
    into(layout.buildDirectory.dir("dokka"))
    rename { "module.md" }

    filter { line ->
        if (line.startsWith("# ")) "# Module KlassFile" else line
    }
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from(
            generateDokkaModule.map {
                layout.buildDirectory.file("dokka/module.md").get().asFile
            }
        )
    }

    pluginsConfiguration.html.footerMessage.set("Rafael Pereira")
}

tasks.matching { it.name.startsWith("dokka") }.configureEach {
    dependsOn(generateDokkaModule)
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