import com.lightningkite.kiteui.KiteUiPlugin
import com.lightningkite.kiteui.KiteUiPluginExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.util.*

plugins {
    alias(serverlibs.plugins.kotlinMultiplatform)
    alias(serverlibs.plugins.ksp)
    alias(serverlibs.plugins.serialization)
    alias(serverlibs.plugins.dokka)
    alias(serverlibs.plugins.kiteui)
    alias(serverlibs.plugins.vite)
}
apply<KiteUiPlugin>()

group = "com.lightningkite"
version = "1.0-SNAPSHOT"

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

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
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonMain/kotlin"))
                srcDir(file("build/generated/kiteui"))
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
        add(it.name, serverlibs.lightningServerProcessor)
    }
}

configure<KiteUiPluginExtension> {
    this.packageName = "com.lightningkite.lightningserver.admin"
    this.iosProjectRoot = project.file("../example-app-ios/KiteUI Example App")
}

kotlin {
    targets
        .matching { it is org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget }
        .configureEach {
            this as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

            compilations.getByName("main") {
                this.kotlinOptions {
//                    this.freeCompilerArgs += "-Xruntime-logs=gc=info"
//                    this.freeCompilerArgs += "-Xallocator=mimalloc"
                }
            }
        }
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
env("lk", "lk")
