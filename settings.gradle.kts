rootProject.name = "lightning-server-kiteui"

pluginManagement {
    val kotlinVersion: String by settings
    val kspVersion: String by settings

    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
    }

    plugins {
        kotlin("plugin.serialization") version kotlinVersion
        id("com.google.devtools.ksp") version kspVersion
    }

    dependencyResolutionManagement {
        repositories {
            mavenLocal()
            maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
            google()
            gradlePluginPortal()
            mavenCentral()
            maven("https://jitpack.io")
        }
    }
}

include(":client")
include(":demo-server")
include(":admin")

