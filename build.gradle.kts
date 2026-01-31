plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

group = "com.autostartstop"
version = "1.0.1-beta"

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
    
    // GitHub API for update checker (releases)
    implementation(libs.github.api)
    
    // bStats for metrics
    implementation(libs.bstats.velocity)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    
    shadowJar {
        archiveFileName = "${project.name}-${project.version}.jar"
        archiveClassifier = ""
        
        // Relocate to avoid conflicts with other plugins
        relocate("org.bstats", "${project.group}.bstats")
        relocate("org.kohsuke", "${project.group}.githubapi.kohsuke")
    }
    
    jar {
        enabled = false
    }
    
    build {
        dependsOn(shadowJar)
    }
}
