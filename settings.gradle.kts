rootProject.name = "lightning-server-kiteui"

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
    }
    versionCatalogs {
        create("sa") { from("com.lightningkite.stack:service-abstractions-catalog:1000.0.0") }
        create("ls") { from("com.lightningkite.stack:lightning-server-catalog:1000.0.0") }
        create("kui") { from("com.lightningkite.stack:kiteui-catalog:1000.0.0") }
    }
}

include(":client")
include(":admin")
include(":server-client-utils")

// Demo modules - by Claude
include(":demo-shared")
include(":demo-apps")
include(":demo-android")
include(":demo-server")
