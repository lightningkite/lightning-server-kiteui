package com.lightningkite.template

import com.lightningkite.kotlinercli.cli
import com.lightningkite.lightningserver.aws.terraform.createTerraform
import com.lightningkite.lightningserver.aws.terraformAws
import com.lightningkite.lightningserver.cache.LocalCache
import com.lightningkite.lightningserver.ktor.runServer
import com.lightningkite.lightningserver.pubsub.LocalPubSub
import com.lightningkite.lightningserver.settings.Settings
import com.lightningkite.lightningserver.settings.loadSettings
import com.lightningkite.lightningserver.typed.Documentable
import com.lightningkite.lightningserver.typed.SDK2
import com.lightningkite.lightningserver.typed.kotlinSdkLocal
import java.io.File

private lateinit var settingsFile: File

fun setup(settings: File = File("settings.json")) {
    settingsFile = settings
    println("Using settings ${settingsFile.absolutePath}")
    Server
}

private fun setup2() {
    if (!Settings.sealed)
        loadSettings(settingsFile)
}

fun serve() {
    setup2()
    runServer(LocalPubSub, Server.cache())
}

fun terraform() {
    println("Generating Terraform")
    createTerraform("com.lightningkite.template.AwsHandler", "ls-kiteui-template", File("server/terraform"))
    println("Finished Generating Terraform")
}

fun sdk() {
    SDK2.write("com.lightningkite.template.sdk", File("demo-apps/src/commonMain/kotlin/com/lightningkite/template/sdk"))
}

/**
 * Entry point for CLI.
 * Allows you to pick from several functions to run from the CLI.
 */
fun main(vararg args: String) = cli(
    args,
    ::setup,
    listOf(
        ::serve,
        ::terraform,
        ::sdk
    )
)