package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.TypedWebSocket
import kotlinx.serialization.KSerializer


interface Fetcher {
    fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): Fetcher

    suspend operator fun <I, O> invoke(
        url: String,
        method: HttpMethod,
        inSerializer: KSerializer<I>,
        body: I,
        outSerializer: KSerializer<O>
    ): O

    fun <I, O> websocket(url: String, inSerializer: KSerializer<I>, outSerializer: KSerializer<O>): TypedWebSocket<I, O>
}

