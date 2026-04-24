import com.lightningkite.deployhelpers.*

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    id("signing")
    alias(libs.plugins.vanniktechPublishing)
}

dependencies {
    api(libs.lightningServer.typed)
    api(libs.kitui.jvm.ssr)
}

kotlin {
    explicitApi()
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

lkLibrary("lightningkite", "lightning-server-kiteui") {
    description.set("Server-side utils that only make sense along-side the client")
}