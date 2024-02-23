plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    `maven-publish`
}

group = "org.jukeboxmc.plugin.backwards"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "opencollab-releases"
        url = uri("https://repo.opencollab.dev/maven-releases/")
    }
    maven {
        name = "opencollab-snapshots"
        url = uri("https://repo.opencollab.dev/maven-snapshots/")
    }
    maven("https://repo.jukeboxmc.eu/private") {
        credentials {
            username = properties["username"].toString()
            password = properties["password"].toString()
        }
    }
}

dependencies {
    implementation("org.jukeboxmc:JukeboxMC-API:1.0.0-SNAPSHOT")
    implementation("org.jukeboxmc:JukeboxMC-Server:1.0.0-SNAPSHOT")
    implementation("org.jukeboxmc.mapping:item-mapping-generator:1.0.0-SNAPSHOT")
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        repositories {
            maven {
                url = uri("https://repo.jukeboxmc.eu/private")
                credentials.username = System.getenv("MAVEN_NAME")
                credentials.password = System.getenv("MAVEN_SECRET")
            }
        }
    }
}