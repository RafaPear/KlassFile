plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.nmcp)
    jacoco
    `maven-publish`
    signing
}

group = "io.github.rafapear"
version = providers.gradleProperty("version").orElse("0.0.0-SNAPSHOT").get()

kotlin {
    jvmToolchain(24)
}

java {
    withSourcesJar()
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    description = "Assembles Javadoc jar"
    group = "build"
    dependsOn(tasks.dokkaGenerateHtml)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGenerateHtml)
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])

            artifactId = "klassfile"

            artifact(tasks.named<Jar>("sourcesJar"))
            artifact(javadocJar)

            pom {
                name = "KlassFile"
                description =
                    "A Kotlin DSL for generating JVM class files with Java's java.lang.classfile API."
                url = "https://github.com/RafaPear/KlassFile"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id = "RafaPear"
                        name = "Rafael Pereira"
                        url = "https://github.com/RafaPear"
                    }
                }

                scm {
                    connection =
                        "scm:git:git://github.com/RafaPear/KlassFile.git"
                    developerConnection =
                        "scm:git:ssh://github.com/RafaPear/KlassFile.git"
                    url = "https://github.com/RafaPear/KlassFile"
                }
            }
        }
    }
}

nmcp {
    publishAllPublications {
        username = providers.gradleProperty("centralUsername").orNull
        password = providers.gradleProperty("centralPassword").orNull
        publicationType = "AUTOMATIC"
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenKotlin"])
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