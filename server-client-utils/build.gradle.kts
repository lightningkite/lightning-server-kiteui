plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    id("signing")
    alias(libs.plugins.vanniktechPublishing)
}

dependencies {
//    api(project(":client"))
    api(ls.typed)
    // The artifact of kiteui library's jvmSsr target: two JVM targets can't be told apart by attributes.
    api("com.lightningkite.kiteui:library-jvmssr:${kui.library.asProvider().get().version}")
}

ksp {
    arg("generateFields", "true")
}

kotlin {
    explicitApi()
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

mavenPublishing {
    pom { description.set("Server-side utils that only make sense along-side the client") }
}