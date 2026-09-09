import com.lightningkite.deployhelpers.lkLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "com.lightningkite.lightningserver"

// KMP currently doesn't disable iOS target and dependency resolution correctly when not on a mac.
// So we work around it on non mac machines with this check
val iosTarget = System.getProperty("os.name").contains("Mac", ignoreCase = true)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    signing
    alias(libs.plugins.vanniktechPublishing)
    alias(libs.plugins.dokka)
}

dokka {
    // Dokka generates a new process managed by Gradle
    dokkaGeneratorIsolation = ProcessIsolation {
        // Configures heap size
        maxHeapSize = "4g"
    }
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            this.jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    jvm()
    js {
        browser()
    }

    if (iosTarget) {
        iosArm64()
        iosX64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.lightningServer.core.shared)
                api(libs.lightningServer.files.shared)
                api(libs.lightningServer.typed.shared)
                api(libs.lightningServer.sessions.shared)
                api(libs.services.database.shared)
                api(libs.services.currency)
                api(libs.kitui)
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonMain/kotlin"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonTest/kotlin"))
            }
        }
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    configurations.filter { it.name.startsWith("ksp") && it.name != "ksp" }.forEach {
        add(it.name, libs.services.database.processor)
    }
}

android {
    namespace = "com.lightningkite.lightningserver.client"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
        coreLibraryDesugaring(libs.androidDesugaring)
    }
}

lkLibrary("lightningkite", "lightning-server-kiteui") {
    description.set("The client side of communication between server and client.")
    name.set("Lightning-Server-Client")
}