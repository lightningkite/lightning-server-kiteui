@file:UseContextualSerialization(Instant::class, UUID::class, ServerFile::class)

package com.lightningkite.lightningserver.demo

import com.lightningkite.kotlinercli.cli
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.aws.terraformAws
import com.lightningkite.lightningserver.cache.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.lightningserver.ktor.runServer
import com.lightningkite.lightningserver.pubsub.LocalPubSub
import com.lightningkite.lightningserver.settings.loadSettings
import com.lightningkite.lightningserver.typed.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import java.io.File
import kotlinx.datetime.Instant
import java.util.*

fun setup() {
    Server
}

private fun serve() {
    loadSettings(File("settings.json"))
    runServer(LocalPubSub, LocalCache)
}

fun terraform() {
    Server
    terraformAws("com.lightningkite.lightningserver.demo.AwsHandler", "demo", File("demo/terraform2"))
}

fun main(vararg args: String) {
    cli(
        arguments = args,
        setup = ::setup,
        available = listOf(::serve, ::terraform, ::dbTest, ::sdk),
    )
}

fun sdk(): Unit {
    Server
    SDK2.write("com.lightningkite.specialtest", generateSequence(File(".").absoluteFile) { it.parentFile.takeIf { it.exists() } }.dropWhile { it.name != "lightning-server-kiteui" }.first().resolve("client/src/commonTest/kotlin/com/lightningkite/specialtest"))
}

fun dbTest(): Unit = runBlocking {
    Server
    loadSettings(File("settings.json"))
}
