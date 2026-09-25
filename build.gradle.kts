import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

buildscript {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
    }
    dependencies {
        classpath(libs.lkGradleHelpers)
        classpath(libs.proguard)
    }
}

allprojects {
    group = "com.lightningkite.lightningserver"
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        maven("https://jitpack.io")
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.androidApp) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.graalVmNative) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.kotlin.cocoapods) apply false
    alias(libs.plugins.vanniktechPublishing) apply false
    alias(libs.plugins.dokka) apply false
}

// Whether modules declare iOS targets. Defaults to true only on macOS, overridable with `-PlsKiteui.ios=true|false`.
// KiteUI uses cinterop, so a KiteUI published from a non-Mac host has no iOS artifacts, and declaring iOS targets
// against it breaks even commonMain metadata compilation. Against a Mac-published KiteUI, iOS can be cross-compiled
// on any host, so forcing this on there is fine.
extra["iosEnabled"] = providers.gradleProperty("lsKiteui.ios").orNull?.toBoolean()
    ?: System.getProperty("os.name").contains("Mac", ignoreCase = true)

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().yarnLockMismatchReportProperty = YarnLockMismatchReport.NONE
    the<YarnRootExtension>().reportNewYarnLockProperty = false
    the<YarnRootExtension>().yarnLockAutoReplaceProperty = true
}
