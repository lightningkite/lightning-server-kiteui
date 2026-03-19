// by Claude - adapted from ls-kiteui-starter/shared
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
}

group = "com.lightningkite.lskiteuistarter"
version = "1.0-SNAPSHOT"

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget()
    jvm()
    js(IR) {
        browser()
    }
    // KMP currently doesn't disable iOS target and dependency resolution correctly when not on a mac.
    // So we work around it on non mac machines with this check
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.comLightningkiteLightningserver.core.shared)
                api(libs.comLightningkiteLightningserver.typed.shared)
                api(libs.comLightningkiteLightningserver.sessions.shared)
                api(libs.comLightningkiteLightningserver.files.shared)
                api(libs.lightningServer.media.shared)
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonMain/kotlin"))
            }
        }
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    configurations.filter { it.name.startsWith("ksp") && it.name != "ksp" }.forEach {
        add(it.name, libs.comLightningKiteServices.database.processor)
    }
}

android {
    namespace = "com.lightningkite.lskiteuistarter.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
        coreLibraryDesugaring(libs.androidDesugaring)
    }
}
