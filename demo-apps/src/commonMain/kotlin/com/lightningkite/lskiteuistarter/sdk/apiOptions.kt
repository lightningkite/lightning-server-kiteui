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
    Local("Local", "http://localhost:8080", "ws://localhost:8080"),
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