import com.lightningkite.kiteui.KiteUiPlugin
import com.lightningkite.kiteui.KiteUiPluginExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.util.*

plugins {
    signing
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
//    alias(libs.plugins.dokka)
    alias(libs.plugins.vite)
    alias(libs.plugins.comLightningkiteKiteui)
}
apply<KiteUiPlugin>()

group = "com.lightningkite"

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

//    jvm()
//    androidTarget {
//        this.compilerOptions {
//            this.jvmTarget.set(JvmTarget.JVM_1_8)
//        }
//    }
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()
//    ios()
//    listOf(
//        iosX64(),
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach {
//        it.binaries.framework {
//            baseName = "library"
//        }
//    }
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
                api(project(":client"))
                api(libs.comLightningkiteKotlinxSerializationCsvDurable)
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonMain/kotlin"))
            }
        }
//        val commonJvmMain by creating {
//            dependsOn(commonMain)
//        }
//        val androidMain by getting {
//            dependsOn(commonJvmMain)
//        }
        val jsMain by getting {
            dependencies {
                implementation(npm("@js-joda/timezone", "2.3.0"))
            }
        }
    }
}
ksp {
    arg("generateFields", "true")
}

dependencies {
    configurations.filter { it.name.startsWith("ksp") && it.name != "ksp" }.forEach {
        add(it.name, libs.comLightningKiteServices.database.processor)
    }
}

configure<KiteUiPluginExtension> {
    this.packageName = "com.lightningkite.lightningserver.admin"
    this.iosProjectRoot = project.file("../example-app-ios/KiteUI Example App")
}

fun env(name: String, profile: String) {
    tasks.create("deployWeb${name}Init", Exec::class.java) {
        group = "deploy"
        this.dependsOn("viteBuild")
        this.environment("AWS_PROFILE", "$profile")
        val props = Properties()
        props.entries.forEach {
            environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' '))
        }
        this.executable = "terraform"
        this.args("init")
        this.workingDir = file("terraform/$name")
    }
    tasks.create("deployWeb${name}", Exec::class.java) {
        group = "deploy"
        this.dependsOn("deployWeb${name}Init")
        this.environment("AWS_PROFILE", "$profile")
        val props = Properties()
        props.entries.forEach { environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' ')) }
        this.executable = "terraform"
        this.args("apply", "-auto-approve")
        this.workingDir = file("terraform/$name")
    }
}
env("ls5", "lk")
env("beta", "lk")
