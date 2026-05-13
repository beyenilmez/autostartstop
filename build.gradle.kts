plugins {
    `java-library`
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.blossom)
}

group = "com.autostartstop"
version = "1.1.0-beta"

repositories {
    mavenCentral()
    
    // PaperMC repository (Velocity API)
    maven("https://repo.papermc.io/repository/maven-public/")
    
    // NeuralNexus repository (AMP API)
    maven("https://maven.neuralnexus.dev/releases")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
}

sourceSets {
    main {
        blossom {
            javaSources {
                property("version", project.version.toString())
            }
        }
    }
}

dependencies {
    // Velocity API
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    
    // BoostedYAML for configuration management
    implementation(libs.boosted.yaml)
    
    // AMP API for AMP panel server control
    implementation(libs.bundles.amp)
    
    // Cron-utils for cron expression parsing
    implementation(libs.cron.utils)
    
    // Gson for JSON parsing (used in pterodactyl control api)
    implementation(libs.gson)

    // bStats for metrics
    implementation(libs.bstats.velocity)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    
    // Velocity API needed at test time for Adventure components
    testImplementation(libs.velocity.api)
}

spotless {
    java {
        googleJavaFormat()
        formatAnnotations()
    }
}

tasks {
    test {
        useJUnitPlatform {
            if (project.hasProperty("fastTests")) {
                excludeTags("velocity-boot")
            }
        }
        // VelocityBootIT boots a real Velocity proxy with the relocated jar.
        dependsOn(shadowJar)
        systemProperty(
            "autostartstop.jar",
            shadowJar.get().archiveFile.get().asFile.absolutePath
        )
        // Integration tests touch external resources (PaperMC API + on-disk velocity-cache)
        // that Gradle can't snapshot, so its UP-TO-DATE check would silently skip real
        // execution. Force re-run unless the user opts out of integration tests.
        if (!project.hasProperty("fastTests")) {
            outputs.upToDateWhen { false }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    shadowJar {
        archiveFileName = "${project.name}-${project.version}.jar"
        archiveClassifier = ""

        // Relocate to avoid conflicts with other plugins
        relocate("org.bstats", "${project.group}.bstats")
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
}
