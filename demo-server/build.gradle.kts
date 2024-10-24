import org.gradle.api.internal.file.archive.ZipFileTree
import proguard.gradle.ProGuardTask
import java.util.*

plugins {
    alias(serverlibs.plugins.kotlinJvm)
    alias(serverlibs.plugins.serialization)
    alias(serverlibs.plugins.ksp)
    application
    alias(serverlibs.plugins.graalVmNative)
    alias(serverlibs.plugins.shadow)
}

group = "com.lightningkite.lightningserver"

dependencies {
    api(serverlibs.lightningServerShared)
    api(serverlibs.lightningServerAws)
    api(serverlibs.lightningServerAzure)
    api(serverlibs.lightningServerCore)
    api(serverlibs.lightningServerTesting)
    api(serverlibs.lightningServerDynamodb)
    api(serverlibs.lightningServerFirebase)
    api(serverlibs.lightningServerKtor)
    api(serverlibs.lightningServerMemcached)
    api(serverlibs.lightningServerMongo)
    api(serverlibs.lightningServerRedis)
    api(serverlibs.lightningServerSentry)
    api(serverlibs.lightningServerSftp)
    ksp(serverlibs.lightningServerProcessor)
    implementation(serverlibs.kotlinerCli)
    implementation(serverlibs.ktorCallLogging)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

kotlin {

    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

application {
    mainClass.set("com.lightningkite.lightningserver.demo.MainKt")
}


tasks.create("lambda", Copy::class.java) {
    group = "deploy"
    this.destinationDir = project.buildDir.resolve("dist/lambda")
    val jarTask = tasks.getByName("jar")
    dependsOn(jarTask)
    val output = jarTask.outputs.files.find { it.extension == "jar" }!!
    from(zipTree(output))
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}
tasks.create("rebuildTerraform", JavaExec::class.java) {
    group = "deploy"
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("com.lightningkite.lightningserver.demo.MainKt")
    args("terraform")
    workingDir(project.rootDir)
}
tasks.create("serve", JavaExec::class.java) {
    group = "application"
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("com.lightningkite.lightningserver.demo.MainKt")
    args("serve")
    workingDir(project.rootDir)
}
tasks.withType(Zip::class) {
    isZip64 = true
}

fun env(name: String, profile: String) {
    val mongoProfile = file("${System.getProperty("user.home")}/.mongo/profiles/$profile.env")

    if(mongoProfile.exists()) {
        tasks.create("deployServer${name}Init", Exec::class.java) {
            group = "deploy"
            this.dependsOn("lambda", "rebuildTerraform")
            this.environment("AWS_PROFILE", "$profile")
            val props = Properties()
            mongoProfile.reader().use { props.load(it) }
            props.entries.forEach {
                environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' '))
            }
            this.executable = "terraform"
            this.args("init")
            this.workingDir = file("terraform/$name")
        }
        tasks.create("deployServer${name}", Exec::class.java) {
            group = "deploy"
            this.dependsOn("deployServer${name}Init")
            this.environment("AWS_PROFILE", "$profile")
            val props = Properties()
            mongoProfile.reader().use { props.load(it) }
            props.entries.forEach { environment(it.key.toString().trim('"', ' '), it.value.toString().trim('"', ' ')) }
            this.executable = "terraform"
            this.args("apply", "-auto-approve")
            this.workingDir = file("terraform/$name")
        }
    }
}
env("example", "default")

tasks.create("proguardTest", ProGuardTask::class) {
    this.injars(tasks.getByName("shadowJar"))
    this.outjars("${buildDir}/outputs/proguarded.jar")
    File("${System.getProperty("java.home")}/jmods").listFiles()?.filter { it.extension == "jmod" }?.forEach {
        this.libraryjars(it)
    }
//    this.libraryjars("${System.getProperty("java.home")}/lib/rt.jar".also { println("rt jar is ${it}") })
    this.libraryjars(configurations.runtimeClasspath)
    this.configuration("src/main/proguard.pro")
//    this.keepnames("com.lightningkite.lightningserver.demo.**")
//    this.keepnames("com.lightningkite.lightningserver.demo.AwsHandler")
}