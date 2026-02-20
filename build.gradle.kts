plugins {
    kotlin("jvm") version "2.3.10"
    `maven-publish`
    application
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "me.nebula"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("http://127.0.0.1:8090/snapshots") }
}

dependencies {
    implementation("me.nebula:Gravity:1.0-SNAPSHOT")
    implementation("net.minestom:minestom:2026.02.09-1.21.11")
    implementation("net.kyori:adventure-text-minimessage:4.20.0")
}

application {
    mainClass.set("me.nebula.orbit.Orbit")
}

kotlin {
    jvmToolchain(25)
}

tasks.shadowJar {
    archiveFileName.set("Orbit.jar")
    mergeServiceFiles()
}

tasks.distTar { dependsOn(tasks.shadowJar) }
tasks.distZip { dependsOn(tasks.shadowJar) }
tasks.startScripts { dependsOn(tasks.shadowJar) }

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifact(tasks.shadowJar)
        }
    }
    repositories {
        maven {
            name = "snapshots"
            url = uri("http://127.0.0.1:8090/snapshots")
            isAllowInsecureProtocol = true
            credentials {
                username = findProperty("snapshotsUsername") as String? ?: ""
                password = findProperty("snapshotsToken") as String? ?: ""
            }
        }
    }
}
