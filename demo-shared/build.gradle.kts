// by Claude - adapted from ls-kiteui-starter/shared
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
}

group = "com.lightningkite.lskiteuistarter"
version = "1.0-SNAPSHOT"

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget()
    jvm()
    js {
        browser()
    }
    
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.lightningServer.core.shared)
                api(libs.lightningServer.typed.shared)
                api(libs.lightningServer.sessions.shared)
                api(libs.lightningServer.files.shared)
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
        add(it.name, libs.services.database.processor)
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
