// by Claude - the Android app shell for :demo-apps.  AGP 9 doesn't allow com.android.application in a
// KMP module, so the code (including MainActivity) stays in :demo-apps and this module only packages it.
import java.util.*

plugins {
    alias(libs.plugins.androidApp)
}

android {
    // Must differ from :demo-apps' namespace; applicationId is what identifies the installed app.
    namespace = "com.lightningkite.lskiteuistarter.android"
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
}

dependencies {
    implementation(project(":demo-apps"))
    coreLibraryDesugaring(libs.androidDesugaring)
}
