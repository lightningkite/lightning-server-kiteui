import com.lightningkite.deployhelpers.developer
import com.lightningkite.deployhelpers.github
import com.lightningkite.deployhelpers.mit
import com.lightningkite.deployhelpers.standardPublishing
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "com.lightningkite.lightningserver"

plugins {
    alias(serverlibs.plugins.kotlinMultiplatform)
    alias(serverlibs.plugins.androidLibrary)
    alias(serverlibs.plugins.ksp)
    alias(serverlibs.plugins.serialization)
    alias(serverlibs.plugins.dokka)
    id("signing")
    `maven-publish`
}

kotlin {
    targetHierarchy.default()
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            this.jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    jvm()
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(serverlibs.lightningServerShared)
                api(serverlibs.kiteUI)
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonMain/kotlin"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonTest/kotlin"))
            }
        }
    }
}

android {
    namespace = "com.lightningkite.lightningserver"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    configurations.filter { it.name.startsWith("ksp") && it.name != "ksp" }.forEach {
        add(it.name, serverlibs.lightningServerProcessor)
    }
}

standardPublishing {
    name.set("Lightning-server-Client")
    description.set("The client side of communication between server and client.")
    github("lightningkite", "lightning-server")

    licenses {
        mit()
    }

    developers {
        developer(
            id = "LightningKiteJoseph",
            name = "Joseph Ivie",
            email = "joseph@lightningkite.com",
        )
        developer(
            id = "bjsvedin",
            name = "Brady Svedin",
            email = "brady@lightningkite.com",
        )
    }
}

android {
    namespace = "com.lightningkite.lightningserver.client"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
        coreLibraryDesugaring(serverlibs.androidDesugaring)
    }
}
