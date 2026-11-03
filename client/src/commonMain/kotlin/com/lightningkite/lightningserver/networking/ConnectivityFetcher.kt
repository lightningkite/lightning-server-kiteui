package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.RequestBodyText
import com.lightningkite.kiteui.connectivityFetch
import com.lightningkite.kiteui.HttpHeaders
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.retryWebSocket
import com.lightningkite.kiteui.typed
import com.lightningkite.lightningserver.HttpMethod
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.typed.ClientWebSocket
import com.lightningkite.lightningserver.typed.Fetcher
import com.lightningkite.lightningserver.typed.RequestMetadata
import com.lightningkite.services.data.StringArrayFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Claude: a function rather than a secondary constructor, whose JVM signature would clash with the primary's.
@Deprecated("Use metadata", ReplaceWith("ConnectivityFetcher(http, ws, json, pingTime, metadata = { for ((name, value) in calculator()) append(name, value) })"))
public fun ConnectivityFetcher(
    http: String,
    ws: String,
    json: Json = DefaultJson,
    pingTime: Duration = 5_000.milliseconds,
    calculator: suspend () -> List<Pair<String, String>>,
): ConnectivityFetcher = ConnectivityFetcher(http, ws, json, pingTime, metadata = { for ((name, value) in calculator()) append(name, value) })

@OptIn(ExperimentalSerializationApi::class)
public class ConnectivityFetcher(
    public val http: String,
    public val ws: String,
    public val json: Json = DefaultJson,
    public val pingTime: Duration = 5_000.milliseconds,
    public val metadata: suspend RequestMetadata.() -> Unit = {},
) : Fetcher {
    override fun withMetadata(block: suspend RequestMetadata.() -> Unit): Fetcher =
        ConnectivityFetcher(http, ws, json, pingTime, metadata = { metadata(); block() })

    override suspend fun <I, O> invoke(
        url: String,
        method: HttpMethod,
        inSerializer: KSerializer<I>,
        body: I,
        outSerializer: KSerializer<O>,
    ): O {
        return connectivityFetch(
            url = "$http${url.asPath()}",
            method = method.kiteUi,
            headers = {
                HttpHeaders(RequestMetadata().apply { set("Accept", "application/json"); metadata() }.toList())
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

    override fun <I, O> webSocket(
        url: String,
        inSerializer: KSerializer<I>,
        outSerializer: KSerializer<O>,
    ): ClientWebSocket<I, O> {
        return retryWebSocket(
            underlyingSocket = { com.lightningkite.kiteui.webSocket("$ws${url.asPath()}".withQuery(RequestMetadata().apply { metadata() })) },
            pingTime = pingTime.inWholeMilliseconds
        ).typed(json, inSerializer, outSerializer).toClientWebSocket()
    }

    /**
     * Endpoint URLs arrive relative to the server root ("users/query"), but generated SDKs and the
     * admin schema disagree on whether they carry a leading slash, so normalize before joining to
     * [http]/[ws] - both of which end without one.
     */
    private fun String.asPath(): String = if (startsWith("/")) this else "/$this"

    private val stringArrayFormat = StringArrayFormat(json.serializersModule)

    override fun <T> url(value: T, serializer: KSerializer<T>): String = stringArrayFormat.encodeToString(serializer, value)
}