// by Claude - adapted from ls-kiteui-starter/server
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    application
}

group = "com.lightningkite.lskiteuistarter"

application {
    mainClass.set("com.lightningkite.lskiteuistarter.MainKt")
}

dependencies {
    implementation(project(":demo-shared"))
    implementation(project(":demo-apps"))
    implementation(project(":server-client-utils"))
    implementation("com.lightningkite.kiteui:library-jvmssr:${kui.library.asProvider().get().version}")
    implementation(libs.kotlinerCli)
    implementation(libs.kotlinx.serialization.csv.durable)
    implementation(ls.core)
    implementation(ls.typed)
    implementation(ls.files)
    implementation(ls.media)
    implementation(ls.sessions)
    implementation(ls.sessions.email)
    implementation(ls.sessions.sms)
    implementation(ls.sessions.totp)
    implementation(ls.engine.netty)
    implementation(ls.engine.ktor)
    implementation(sa.database)
    implementation(sa.database.jsonfile)
    implementation(sa.database.mongodb)
    implementation(sa.notifications.fcm)
    implementation(sa.email.javasmtp)
    implementation(sa.files.s3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing") // Provides Dispatchers.Main for SSR

    ksp(sa.database.processor)

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
