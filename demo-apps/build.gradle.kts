import com.lightningkite.kiteui.KiteUiPluginExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.util.*
import com.lightningkite.deployhelpers.*

plugins {
    alias(libs.plugins.androidApp)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.comLightningkiteKiteui)
    alias(libs.plugins.vite)
    id("io.sentry.android.gradle") version "4.5.1"
}

group = "com.lightningkite.template"
version = "1.0-SNAPSHOT"


kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget()
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
                api(project(":client"))
                api(libs.comLightningkiteLightningserverShared)
                api(project(":demo-shared"))
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
        val iosMain by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }


        val commonTest by getting {
            dependencies {
            }
        }
    }

    cocoapods {
        // Required properties
        // Specify the required Pod version here. Otherwise, the Gradle project version is used.
        version = "1.0"
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"
        ios.deploymentTarget = "14.0"

        // Optional properties
        // Configure the Pod name here instead of changing the Gradle project name
        name = "apps"

        framework {
            baseName = "apps"
            export(project(":demo-shared"))
            export(libs.comLightningkiteKiteuiLibrary)
            export(project(":client"))
//            podfile = project.file("../example-app-ios/Podfile")
        }
        pod("Sentry") {
            version = "~> 8.25"
            linkOnly = true
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
//        pod("Library") {
//            version = "1.0"
//            source = path(project.file("../library"))
//        }

        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }
}

android {
    namespace = "com.lightningkite.template"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lightningkite.template"
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
}

dependencies {
    coreLibraryDesugaring(libs.androidDesugaring)
}

configure<KiteUiPluginExtension> {
    this.packageName = "com.lightningkite.template"
    this.iosProjectRoot = project.file("./ios/app")
}

fun env(name: String, profile: String) {
    tasks.create("deployWeb${name}Init", Exec::class.java) {
        group = "deploy"
        this.dependsOn("viteBuild")
        this.environment("AWS_PROFILE", profile)
        val props = Properties()
        props.entries.forEach { environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' ')) }
        this.executable = "terraform"
        this.args("init")
        this.workingDir = file("terraform/$name")
    }
    tasks.create("deployWeb${name}", Exec::class.java) {
        group = "deploy"
        this.dependsOn("deployWeb${name}Init")
        this.environment("AWS_PROFILE", profile)
        val props = Properties()
        props.entries.forEach { environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' ')) }
        this.executable = "terraform"
        this.args("apply", "-auto-approve")
        this.workingDir = file("terraform/$name")
    }
}

env("default", "default")