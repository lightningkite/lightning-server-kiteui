package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.lightningserver.networking.BulkFetcher
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds


@Serializable
enum class ApiOption(val apiName: String, val http: String, val ws: String) {
    //    Production(" ", "https://", "wss://"),
//    Staging("Staging", "https://", "wss://"),
//    Dev("Dev", "https://", "wss://"),
    SameServer("Same Server", "/api", "/api"),
    // Matches the project-wide dev convention: backend on 8090 (root settings.json,
    // testing/settings.json), web frontend on 8091 proxying /api to it (vite.config.mjs). This
    // used to say 8080, which matched none of them.
    Local("Local", "http://localhost:8090", "ws://localhost:8090"),
    ;

    val baseFetcher
        get() = /*if (!debug) */BulkFetcher(
            httpBulk = "$http/meta/bulk",
            wsMultiplex = "$ws?path=/multiplex",
            pingTime = 30.seconds,
        ) /*else ConnectivityFetcher(
            http = http,
            ws = ws,
            pingTime = 30.seconds,
        )*/
    val api get() = LiveApi(baseFetcher)
    fun next(): ApiOption = ApiOption.entries[(ordinal + 1) % ApiOption.entries.size]
}

val selectedApi = PersistentProperty<ApiOption>("apiOption", getDefaultServerBackend())


expect fun getDefaultServerBackend(): ApiOption