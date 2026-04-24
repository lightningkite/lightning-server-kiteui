package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningserver.*
import com.lightningkite.lightningserver.typed.BulkRequest
import com.lightningkite.lightningserver.typed.BulkResponse
import com.lightningkite.lightningserver.typed.ClientWebSocket
import com.lightningkite.lightningserver.typed.Fetcher
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.invokeAllSafe
import com.lightningkite.services.data.StringArrayFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
public class BulkFetcher(
    public val httpBulk: String,
    public val wsMultiplex: String,
    public val json: Json = DefaultJson,
    public val pingTime: Duration = 5_000.milliseconds,
    public val delay: Duration = 100.milliseconds,
    public val log: Log? = null,
    public val calculator: suspend () -> List<Pair<String, String>> = { listOf() },
) : Fetcher {
    private val stringArrayFormat = StringArrayFormat(json.serializersModule)
    override fun <T> url(value: T, serializer: KSerializer<T>): String = stringArrayFormat.encodeToString(serializer, value)

    override fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): Fetcher =
        BulkFetcher(httpBulk, wsMultiplex, json, pingTime, delay, log, calculator)

    private var fetchQueue = HashMap<String, Pair<BulkRequest, CancellableContinuation<BulkResponse>>>()
    private var scheduled = false

    override suspend fun <I, O> invoke(
        url: String,
        method: com.lightningkite.lightningserver.HttpMethod,
        inSerializer: KSerializer<I>,
        body: I,
        outSerializer: KSerializer<O>,
    ): O {
        val id = Uuid.random().toString()

        val req = BulkRequest(
            url,
            method = method.toString(),
            body =
                if (inSerializer.descriptor.serialName == "kotlin.Unit") null
                else json.encodeToString(inSerializer, body)
        )

        if (!scheduled) {
            scheduled = true
            AppScope.launch {
                delay(delay)
                fetch()
            }
        }

        return suspendCancellableCoroutine { cont ->
            fetchQueue[id] = req to cont
        }.let { it: BulkResponse ->
            @Suppress("UNCHECKED_CAST")
            if (it.error == null && outSerializer.descriptor.serialName == Unit.serializer().descriptor.serialName) Unit as O
            else if (it.result != null) json.decodeFromString(outSerializer, it.result!!)
            else throw LsErrorException(it.error ?: LSError(it.error?.http ?: 0))
        }
    }

    private suspend fun fetch() {
        val todo = fetchQueue
        fetchQueue = HashMap()
        scheduled = false
        try {
            connectivityFetch(
                url = httpBulk,
                method = HttpMethod.POST.kiteUi,
                headers = { httpHeaders(calculator()) },
                body = RequestBodyText(
                    json.encodeToString(
                        MapSerializer(String.serializer(), BulkRequest.serializer()),
                        todo.mapValues { it.value.first }
                    ),
                    "application/json"
                )
            ).let { it: RequestResponse ->
                if (!it.ok) {
                    val failed = Exception(it.status.toString() + ": " + it.text())
                    todo.values.forEach { it.second.resumeWithException(failed) }
                } else {
                    val responses = json.decodeFromString<Map<String, BulkResponse>>(it.text())
                    todo.forEach {
                        responses[it.key]?.let { response ->
                            it.value.second.resume(response)
                        } ?: it.value.second.resumeWithException(Exception("Bulk key ${it.key} not found"))
                    }
                }
            }
        } catch (e: Exception) {
            todo.values.forEach { it.second.resumeWithException(e) }
        }
    }

    public val wsMuxer: TypedWebSocket<MultiplexMessage, MultiplexMessage> = retryWebsocket(
        underlyingSocket = {
            val headers = calculator()

            val url = if(headers.isNotEmpty()){
                val terminator = if(wsMultiplex.contains('?')) '&' else '?'
                wsMultiplex + "$terminator${headers.joinToString("&"){ "${it.first}=${it.second}" }}"
            } else wsMultiplex
            websocket(url)
        },
        pingTime = pingTime.inWholeMilliseconds,
        log = log
    ).typedWithDebug(json, MultiplexMessage.serializer(), MultiplexMessage.serializer()).also { mux ->
        if (debugMode && log != null) {
            mux.onOpen {
                mux.send(MultiplexMessage(channel = "debug", start = true))
            }
        }
        mux.onMessage {
            if (log != null && it.channel == "debug") log.log("Multiplex debug: $it")
        }
    }

    override fun <I, O> websocket(
        url: String,
        inSerializer: KSerializer<I>,
        outSerializer: KSerializer<O>,
    ): ClientWebSocket<I, O> =
        WebsocketChannel(url).typed(json, inSerializer, outSerializer)

    private inner class WebsocketChannel(url: String) : ClientWebSocket<String, String> {
        val path = url.substringBefore('?')

        val params = url.substringAfter('?', "")
            .split('&')
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .groupBy({ it.first }, { it.second })


        override val connected = MutableStateFlow(false)

        val channel = Uuid.random().toString()

        init {
            wsMuxer.onMessage { message ->
                if (message.channel == channel) {
                    if (message.start) {
                        connected.value = true
                        onOpenList.invokeAllSafe()
                    }
                    message.data?.let { data ->
                        onMessageList.toList().forEach { it(data) }
                    }
                    if (message.end) {
                        connected.value = false
                        onCloseList.toList().forEach { it(-1) }
                    }
                }
            }
            wsMuxer.onClose {
                connected.value = false
                onCloseList.forEach { it(-1) }
            }
        }

        private val shouldBeOn = Signal(false)
        private var closeChannel: (() -> Unit)? = null

        override fun connect() {
            shouldBeOn.value = true
            if (closeChannel == null) closeChannel = wsMuxer.beginUse()
        }

        val lifecycle = CoroutineScope(Job()).apply {
            reactiveScope {
                val shouldBeOn = shouldBeOn()
                val isOn = connected()
                val parentConnected = wsMuxer.connected()
                if (shouldBeOn && parentConnected && !isOn) {
                    wsMuxer.send(
                        MultiplexMessage(
                            channel = channel,
                            path = path,
                            queryParams = params,
                            start = true
                        )
                    )
                } else if (!shouldBeOn && parentConnected && isOn) {
                    wsMuxer.send(
                        MultiplexMessage(
                            channel = channel,
                            path = path,
                            queryParams = params,
                            end = true
                        )
                    )
                }
            }
        }

        override fun close(code: Short, reason: String) {
            shouldBeOn.value = false
            wsMuxer.send(
                MultiplexMessage(
                    channel = channel,
                    path = path,
                    queryParams = params,
                    end = true
                )
            )
            closeChannel?.invoke()
            lifecycle.cancel()
        }

        override fun send(data: String) {
            wsMuxer.send(
                MultiplexMessage(
                    channel = channel,
                    data = data,
                )
            )
        }

        val onOpenList = ArrayList<() -> Unit>()
        val onMessageList = ArrayList<(String) -> Unit>()
        val onCloseList = ArrayList<(Short) -> Unit>()

        override fun onOpen(action: () -> Unit) {
            onOpenList.add(action)
        }

        override fun onMessage(action: (String) -> Unit) {
            onMessageList.add(action)
        }

        override fun onClose(action: (Short) -> Unit) {
            onCloseList.add(action)
        }
    }

    private fun <SEND, RECEIVE> RetryWebsocket.typedWithDebug(
        json: Json,
        send: KSerializer<SEND>,
        receive: KSerializer<RECEIVE>,
        log: Log? = null,
    ): TypedWebSocket<SEND, RECEIVE> = object : TypedWebSocket<SEND, RECEIVE> {
        override val connected: Reactive<Boolean>
            get() = this@typedWithDebug.connected

        override fun beginUse(): () -> Unit = this@typedWithDebug.beginUse()
        override fun close(code: Short, reason: String) = this@typedWithDebug.close(code, reason)
        override fun onOpen(action: () -> Unit) = this@typedWithDebug.onOpen(action)
        override fun onClose(action: (Short) -> Unit) = this@typedWithDebug.onClose(action)
        override fun onMessage(action: (RECEIVE) -> Unit) {
            this@typedWithDebug.onMessage {
                if(log != null && it.startsWith("!!! DEBUG AWS INFO !!! - ")) {
                    log.log("AWS: " + it.substringAfter("!!! DEBUG AWS INFO !!! - "))
                    return@onMessage
                }
                try {
                    action(json.decodeFromString(receive, it))
                } catch (e: CancellationException) {
                    /*squish*/
                } catch (e: Exception) {
                    @OptIn(ExperimentalSerializationApi::class)
                    Exception(
                        "Failed to decode message; expected a ${receive.descriptor.serialName} but got '${it.take(150)}'",
                        e
                    ).report()
                }
            }
        }

        override fun send(data: SEND) {
            this@typedWithDebug.send(json.encodeToString(send, data))
        }
    }
}