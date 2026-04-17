package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.ssr.SsrRouter
import com.lightningkite.kotlinercli.cli
import com.lightningkite.lightningserver.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.definition.*
import com.lightningkite.lightningserver.definition.builder.*
import com.lightningkite.lightningserver.deprecations.*
import com.lightningkite.lightningserver.encryption.*
import com.lightningkite.lightningserver.engine.ktor.KtorEngine
import com.lightningkite.lightningserver.http.*
import com.lightningkite.lightningserver.pathing.*
import com.lightningkite.lightningserver.runtime.*
import com.lightningkite.lightningserver.serialization.*
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.lightningserver.settings.*
import com.lightningkite.lightningserver.ssr.ChangeFreq
import com.lightningkite.lightningserver.ssr.StaticRoute
import com.lightningkite.lightningserver.ssr.StaticSiteRenderer
import com.lightningkite.lightningserver.typed.*
import com.lightningkite.lightningserver.typed.sdk.FetcherSdk
import com.lightningkite.lightningserver.typed.sdk.CachingSdk
import com.lightningkite.lightningserver.typed.sdk.SDK.write
import com.lightningkite.lightningserver.typed.sdk.SDK.writeUsingDefaultSettings
import com.lightningkite.lightningserver.typed.sdk.plus
import com.lightningkite.lightningserver.websockets.*
import com.lightningkite.lskiteuistarter.AutoRoutes
import com.lightningkite.lskiteuistarter.app
import com.lightningkite.lskiteuistarter.defaultTheme
import com.lightningkite.services.Untested
import com.lightningkite.services.cache.*
import com.lightningkite.services.data.*
import com.lightningkite.services.database.*
import com.lightningkite.services.email.*
import com.lightningkite.services.files.*
import com.lightningkite.services.notifications.*
import com.lightningkite.services.sms.*
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

private lateinit var settingsFile: KFile

fun setup(settings: KFile = KFile("settings.json")) {
    settingsFile = settings
}

private var engine: KtorEngine? = null

fun engine(setup: KtorEngine.() -> Unit) {
    engine?.let {
        setup(it)
        return
    }

    val before = TimeSource.Monotonic.markNow()
    val built = Server.build()
    println("Server built in ${before.elapsedNow()}")

    engine = KtorEngine(built).apply {
        settings.loadFromFile(settingsFile, internalSerializersModule)
        setup()
    }
}

fun serve() = engine { start(Netty) }

fun sdk() = engine {
    Utils.logger.info { "Generating FetcherSdk" }
    FetcherSdk("com.lightningkite.lskiteuistarter.sdk").write(
        KFile("demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk")
    )
    Utils.logger.info { "Generating CachingSdk" }
    CachingSdk("com.lightningkite.lskiteuistarter.sdk").write(
        KFile("demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk")
    )
    Utils.logger.info { "Done" }
}
// by Claude - Static site generation using StaticSiteRenderer
@OptIn(Untested::class)
fun staticSite() = runBlocking {
    val outputDir = File("build/static-site")
    println("Rendering static site to ${outputDir.absolutePath}...")

    val router = SsrRouter(
        routes = AutoRoutes,
        theme = defaultTheme,
        appWrapper = { navigator, dialog -> app(navigator, dialog) },
    )
    val renderer = StaticSiteRenderer(
        router = router,
        outputDir = outputDir,
        baseUrl = "https://example.com",
        scriptUrls = listOf("/lightning-server-kiteui-demo-apps.js"),
        routes = listOf(
            StaticRoute("/", priority = 1.0, changeFreq = ChangeFreq.Weekly),
            StaticRoute("/login", priority = 0.8, changeFreq = ChangeFreq.Monthly),
            StaticRoute("/dashboard", priority = 0.6, changeFreq = ChangeFreq.Monthly),
        ),
    )

    val results = renderer.renderAll()
    for (result in results) {
        println("  ${result.path} -> ${result.outputFile} (${result.bytes} bytes)")
    }
    println("Sitemap written to ${outputDir.resolve("sitemap.xml")}")
    println("Done! ${results.size} pages rendered.")
}

fun main(vararg args: String) = cli(
    arguments = args,
    setup = ::setup,
    available = listOf(
        ::serve,
        ::sdk,
        ::staticSite,
    ),
    useInteractive = true,
)


object Utils {
    val logger: KLogger = KotlinLogging.logger("com.lightningtime")

    suspend fun <T> runForEach(seconds: Int, items: Collection<T>, action: suspend (T) -> Unit): List<T> {
        val loopStart = TimeSource.Monotonic.markNow()
        val duration = seconds.seconds

        val remaining = items.toMutableList()
        while (loopStart.elapsedNow() < duration && remaining.isNotEmpty()) {
            try {
                action(remaining.removeFirst())
            } catch (e: Throwable) {
                KotlinLogging.logger("runForEach").error(e) { "Exception encountered in runForEach" }
            }
        }

        return remaining
    }

    suspend fun <T> runFor(seconds: Int, startingValue: T, action: suspend (T) -> T?): T? {

        val loopStart = TimeSource.Monotonic.markNow()
        val duration = seconds.seconds

        var value = startingValue

        while (loopStart.elapsedNow() < duration) {
            value = action(value) ?: return null
        }

        return value
    }
}