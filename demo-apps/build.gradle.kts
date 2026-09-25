// by Claude - adapted from ls-kiteui-starter/apps
import com.lightningkite.kiteui.KiteUiPluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.comLightningkiteKiteui)
    alias(libs.plugins.kjsplain)
    alias(libs.plugins.kfc)
}

group = "com.lightningkite.lskiteuistarter"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://jitpack.io")
}

kotlin {
    applyDefaultHierarchyTemplate()
    // The installable Android app lives in :demo-android - AGP 9 doesn't allow com.android.application in a KMP module.
    android {
        // Must match the KiteUI packageName below: the generated Resources.android.kt uses an unqualified R.
        namespace = "com.lightningkite.lskiteuistarter"
        compileSdk = 36
        minSdk = 26
        enableCoreLibraryDesugaring = true
        androidResources { enable = true }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm() // Needed for SSR via StaticSiteRenderer
    
    // :client only declares iOS targets on a Mac, so match it or iOS can't resolve the dependency.
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        iosArm64()
        iosSimulatorArm64()
    }
    js {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kitui)
                api(libs.kotlinx.serialization.csv.durable)
                api(libs.lightningServer.core.shared)
                api(libs.lightningServer.typed.shared)
                api(libs.lightningServer.sessions.shared)
                api(project(":client"))
                api(project(":demo-shared"))
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.firebaseMessagingKtx)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("firebase", "10.7.1"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
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
    coreLibraryDesugaring(libs.androidDesugaring)
}

configure<KiteUiPluginExtension> {
    this.packageName = "com.lightningkite.lskiteuistarter"
    this.iosProjectRoot = project.file("ios/app")
}
