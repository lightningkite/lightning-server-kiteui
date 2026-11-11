import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

buildscript {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
    }
    dependencies {
        classpath(libs.proguard)
    }
}

allprojects {
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
    alias(libs.plugins.vanniktechPublishing) apply false
    alias(libs.plugins.dokka) apply false
    `version-catalog`
    `maven-publish`
}

// Whether modules declare iOS targets. Defaults to true only on macOS, overridable with `-PlsKiteui.ios=true|false`.
// KiteUI uses cinterop, so a KiteUI published from a non-Mac host has no iOS artifacts, and declaring iOS targets
// against it breaks even commonMain metadata compilation. Against a Mac-published KiteUI, iOS can be cross-compiled
// on any host, so forcing this on there is fine.
extra["iosEnabled"] = providers.gradleProperty("lsKiteui.ios").orNull?.toBoolean()
    ?: System.getProperty("os.name").contains("Mac", ignoreCase = true)

// Every published module.  Group and version come from its own gradle.properties; `lk` (in metawork) is the only
// thing that edits the version.
subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            signAllPublications()
            // Dokka is slow, so the javadoc jar is built only on request.
            configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = providers.gradleProperty("withDocs").isPresent)
            pom {
                name.set(project.name)
                url.set("https://lightningkite-maven.s3.amazonaws.com/${project.group.toString().replace('.', '/')}/${project.name}/${project.version}/docs/index.html")
                scm {
                    connection.set("scm:git:https://github.com/lightningkite/lightning-server-kiteui.git")
                    developerConnection.set("scm:git:https://github.com/lightningkite/lightning-server-kiteui.git")
                    url.set("https://github.com/lightningkite/lightning-server-kiteui")
                }
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer { id.set("LightningKiteJoseph"); name.set("Joseph Ivie"); email.set("joseph@lightningkite.com") }
                    developer { id.set("bjsvedin"); name.set("Brady Svedin"); email.set("brady@lightningkite.com") }
                    developer { id.set("iHoonter"); name.set("Hunter"); email.set("Hunter@lightningkite.com") }
                }
            }
        }

        plugins.withId("signing") {
            extensions.configure<SigningExtension> {
                val signingKey = providers.gradleProperty("signingKey").orNull
                if (signingKey != null) useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").orNull)
            }
        }
        tasks.withType<Sign>().configureEach {
            val hasKey = providers.gradleProperty("signingKey").isPresent
            onlyIf("a signing key is configured") { hasKey }
        }

        plugins.withId("org.jetbrains.dokka") {
            dependencies.add("dokkaPlugin", "com.lightningkite:dokka-plugin-hide-optin:0.0.4")
            extensions.configure<DokkaExtension> {
                dokkaSourceSets.configureEach {
                    sourceRoots.map { it.resolve("module.md") }.firstOrNull { it.exists() }?.let { includes.from(it) }
                    sourceLink {
                        localDirectory.set(file("src/$name/kotlin"))
                        remoteUrl.set(uri("https://github.com/lightningkite/lightning-server-kiteui/tree/master/"))
                        remoteLineSuffix.set("#L")
                    }
                }
            }
        }

        extensions.configure<PublishingExtension> {
            repositories {
                maven { name = "staging"; url = uri(rootProject.layout.buildDirectory.dir("staging-repo")) }
            }
        }

        // `lk` compares a fresh dump with the last release's to choose the next version, so no dumps are committed or checked.
        listOf("org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.jvm").forEach { kotlinPlugin ->
            plugins.withId(kotlinPlugin) {
                extensions.configure<KotlinProjectExtension> {
                    @OptIn(ExperimentalAbiValidation::class)
                    abiValidation()
                }
            }
        }
        tasks.matching { it.name == "checkKotlinAbi" }.configureEach { enabled = false }
    }
}

// The catalog downstream repos pin: this repo's modules at the versions being published.
val published = subprojects.filter { it.file("gradle.properties").let { f -> f.isFile && f.readText().contains(Regex("(?m)^version=")) } }
catalog { versionCatalog { published.forEach { library(it.name, "${it.group}:${it.name}:${it.version}") } } }
publishing {
    publications.create<MavenPublication>("catalog") {
        from(components["versionCatalog"])
        groupId = "com.lightningkite.stack"
        artifactId = "lightning-server-kiteui-catalog"
        version = providers.gradleProperty("catalogVersion").getOrElse("unspecified")
    }
    repositories { maven { name = "staging"; url = uri(layout.buildDirectory.dir("staging-repo")) } }
}

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().yarnLockMismatchReportProperty = YarnLockMismatchReport.NONE
    the<YarnRootExtension>().reportNewYarnLockProperty = false
    the<YarnRootExtension>().yarnLockAutoReplaceProperty = true
}
