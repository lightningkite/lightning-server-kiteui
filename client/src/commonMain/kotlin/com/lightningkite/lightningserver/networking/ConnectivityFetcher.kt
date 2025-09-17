package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.RequestBodyText
import com.lightningkite.kiteui.connectivityFetch
import com.lightningkite.kiteui.httpHeaders
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.retryWebsocket
import com.lightningkite.kiteui.typed
import com.lightningkite.lightningserver.HttpMethod
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.typed.ClientWebSocket
import com.lightningkite.lightningserver.typed.Fetcher
import com.lightningkite.services.data.StringArrayFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSerializationApi::class)
class ConnectivityFetcher(
    val http: String,
    val ws: String,
    val json: Json = DefaultJson,
    val pingTime: Duration = 5_000.milliseconds,
    val calculator: suspend () -> List<Pair<String, String>> = { listOf() },
) : Fetcher {
    override fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): Fetcher =
        ConnectivityFetcher(http, ws, json, pingTime, calculator)

    override suspend fun <I, O> invoke(
        url: String,
        method: HttpMethod,
        inSerializer: KSerializer<I>,
        body: I,
        outSerializer: KSerializer<O>,
    ): O {
        return connectivityFetch(
            url = "$http$url",
            method = method.kiteUi,
            headers = {
                httpHeaders(listOf("Accept" to "application/json") + calculator())
            },
            body = if (inSerializer.descriptor.serialName != "kotlin.Unit")
                RequestBodyText(json.encodeToString(inSerializer, body), "application/json")
            else null
        ).let {
            if (!it.ok) {
                val text = it.text()
                throw try {
                    val e = json.decodeFromString(LSError.serializer(), text)
                    LsErrorException(e)
                } catch (e: Exception) {
                    LsErrorException(LSError(it.status.toInt(), "Unknown", message = text))
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                if (outSerializer.descriptor.serialName == "kotlin.Unit") return Unit as O
                try {
                    json.decodeFromString(outSerializer, it.text())
                } catch (e: SerializationException) {
                    throw SerializationException(
                        "Failed to parse ${outSerializer.descriptor.serialName} from response",
                        e
                    ).also { it.printStackTrace() }
                }
            }
        }
    }

    override fun <I, O> websocket(
        url: String,
        inSerializer: KSerializer<I>,
        outSerializer: KSerializer<O>,
    ): ClientWebSocket<I, O> {
        return retryWebsocket(
            underlyingSocket = {
                val headers = calculator()
                var url = "$ws?path=$url"
                url = if (headers.isNotEmpty()) {
                    url + "&${headers.joinToString("&") { "${it.first}=${it.second}" }}"
                } else url
                com.lightningkite.kiteui.websocket(url)
            },
            pingTime = pingTime.inWholeMilliseconds
        ).typed(json, inSerializer, outSerializer).toClientWebSocket()
    }

    private val stringArrayFormat = StringArrayFormat(json.serializersModule)

    override fun <T> url(value: T, serializer: KSerializer<T>): String = stringArrayFormat.encodeToString(serializer, value)
}