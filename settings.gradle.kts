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
//        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/releases/")
    }

    plugins {
        kotlin("plugin.serialization") version kotlinVersion
        id("com.google.devtools.ksp") version kspVersion
    }

    dependencyResolutionManagement {
        repositories {
            mavenLocal()
//            maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
            maven(url = "https://s01.oss.sonatype.org/content/repositories/releases/")
            google()
            gradlePluginPortal()
            mavenCentral()
            maven("https://jitpack.io")
        }

        versionCatalogs {
            create("serverlibs") { from(files("gradle/serverlibs.versions.toml"))}
        }
    }
}

include(":client")
include(":demo-server")
include(":admin")

