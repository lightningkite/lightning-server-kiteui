// by Claude - adapted from ls-kiteui-starter/apps
import com.lightningkite.kiteui.KiteUiPluginExtension
import java.util.*

plugins {
    alias(libs.plugins.androidApp)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
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
    androidTarget()
    jvm() // Needed for SSR via StaticSiteRenderer
    iosX64()
    iosArm64()
    iosSimulatorArm64()
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
                api(libs.comLightningkiteKiteuiLibrary)
                api(libs.comLightningkiteKotlinxSerializationCsvDurable)
                api(libs.comLightningkiteLightningserver.core.shared)
                api(libs.comLightningkiteLightningserver.typed.shared)
                api(libs.comLightningkiteLightningserver.sessions.shared)
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

android {
    namespace = "com.lightningkite.lskiteuistarter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lightningkite.lskiteuistarter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.excludes.add("com/lightningkite/lightningserver/lightningdb.txt")
        resources.excludes.add("com/lightningkite/lightningserver/lightningdb-log.txt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    val props = project.rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { stream ->
        Properties().apply { load(stream) }
    }
    if (props != null && props.getProperty("signingKeystore") != null) {
        signingConfigs {
            this.create("release") {
                storeFile = project.rootProject.file(props.getProperty("signingKeystore"))
                storePassword = props.getProperty("signingPassword")
                keyAlias = props.getProperty("signingAlias")
                keyPassword = props.getProperty("signingAliasPassword")
            }
        }
        buildTypes {
            this.getByName("release") {
                this.isMinifyEnabled = false
                this.proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
                this.signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    dependencies {
        coreLibraryDesugaring(libs.androidDesugaring)
    }
}

configure<KiteUiPluginExtension> {
    this.packageName = "com.lightningkite.lskiteuistarter"
    this.iosProjectRoot = project.file("ios/app")
}
