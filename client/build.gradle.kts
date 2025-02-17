import com.lightningkite.deployhelpers.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "com.lightningkite.lightningserver"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
//    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktechMavenPublish)
    id("signing")
}

val lk = project.lk {
}

kotlin {
    targetHierarchy.default()
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            this.jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    jvm()
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(lk.lightningServer("shared", 4))
                api(lk.kiteUi(4))
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonMain/kotlin"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
            kotlin {
                srcDir(file("build/generated/ksp/common/commonTest/kotlin"))
            }
        }
    }
}

android {
    namespace = "com.lightningkite.lightningserver"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    configurations.filter { it.name.startsWith("ksp") && it.name != "ksp" }.forEach {
        add(it.name, lk.lightningServer("processor", 4))
    }
}

mavenPublishing {
    // publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(group.toString(), name, version.toString())
    pom {
        name.set("Lightning-server-Client")
        description.set("The client side of communication between server and client.")
        github("lightningkite", "lightning-server-kiteui")

        licenses {
            mit()
        }

        developers {
            joseph()
            brady()
        }
    }
}
android {
    namespace = "com.lightningkite.lightningserver.client"
    compileSdk = 34

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
