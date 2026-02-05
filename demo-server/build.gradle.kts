// by Claude - adapted from ls-kiteui-starter/server
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    application
}

group = "com.lightningkite.lskiteuistarter"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.lightningkite.lskiteuistarter.MainKt")
}

dependencies {
    implementation(project(":demo-shared"))
    implementation(project(":demo-apps"))
    implementation(project(":server-client-utils"))
    implementation(libs.comLightningkiteKiteuiLibraryJvmSsr)
    implementation(libs.kotlinerCli)
    implementation(libs.comLightningkiteKotlinxSerializationCsvDurable)
    implementation(libs.lightningServer.core)
    implementation(libs.comLightningkiteLightningserver.typed)
    implementation(libs.lightningServer.files)
    implementation(libs.lightningServer.media)
    implementation(libs.lightningServer.sessions)
    implementation(libs.lightningServer.sessions.email)
    implementation(libs.lightningServer.sessions.sms)
    implementation(libs.lightningServer.engine.netty)
    implementation(libs.lightningServer.engine.ktor)
    implementation(libs.services.database)
    implementation(libs.services.database.jsonfile)
    implementation(libs.services.database.mongodb)
    implementation(libs.services.notifications.firebase)
    implementation(libs.services.email.javasmtp)
    implementation(libs.services.files.s3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing") // Provides Dispatchers.Main for SSR

    ksp(libs.comLightningKiteServices.database.processor)

    testImplementation(kotlin("test"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.create("generateSdk", JavaExec::class.java) {
    group = "deploy"
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("com.lightningkite.lskiteuistarter.MainKt")
    args("sdk")
    workingDir(project.rootDir)
}
tasks.create("serve", JavaExec::class.java) {
    group = "application"
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("com.lightningkite.lskiteuistarter.MainKt")
    args("serve")
    workingDir(project.rootDir)
}
