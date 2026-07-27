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
    ).typedWithDebug(json, MultiplexMessage.serializer(), MultiplexMessage.serializer())

    private val multiplexed = MultiplexedSocket(wsMuxer, log)

    override fun <I, O> websocket(
        url: String,
        inSerializer: KSerializer<I>,
        outSerializer: KSerializer<O>,
    ): ClientWebSocket<I, O> =
        multiplexed.channel(url).typed(json, inSerializer, outSerializer)

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

/**
 * Manages multiplex channels over a single shared [muxer] socket.
 *
 * A channel is opened by sending a "start" frame tagged with a channel id, closed with an "end"
 * frame, and carries data frames in between; many channels share one physical socket.
 *
 * Message dispatch goes through a single listener on [muxer] keyed by channel id (see [channels]),
 * rather than one listener per channel.  This means a channel releases its routing structurally by
 * removing itself from [channels] on close - there is no per-channel listener that could leak.
 * Channels are reusable: [WebsocketChannel.close] is idempotent, sends exactly one "end" frame, and
 * leaves the channel able to [WebsocketChannel.connect] again later.
 */
internal class MultiplexedSocket(
    private val muxer: TypedWebSocket<MultiplexMessage, MultiplexMessage>,
    private val log: Log? = null,
) {
    /** Active channels keyed by their channel id; the dispatch listener routes messages by this. */
    private val channels = HashMap<String, WebsocketChannel>()

    init {
        if (debugMode && log != null) {
            muxer.onOpen {
                muxer.send(MultiplexMessage(channel = "debug", start = true))
            }
        }
        muxer.onMessage { message ->
            if (log != null && message.channel == "debug") log.log("Multiplex debug: $message")
            channels[message.channel]?.handleMessage(message)
        }
        // When the transport drops, notify every active channel so they reset and re-subscribe
        // once it reconnects.  Channels stay registered (only an explicit close removes them).
        muxer.onClose {
            channels.values.toList().forEach { it.handleParentClose() }
        }
    }

    /** Creates a new (not-yet-connected) multiplex channel for [url]. */
    fun channel(url: String): ClientWebSocket<String, String> = WebsocketChannel(url)

    private inner class WebsocketChannel(url: String) : ClientWebSocket<String, String> {
        val path = url.substringBefore('?')

        val params = url.substringAfter('?', "")
            .split('&')
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .groupBy({ it.first }, { it.second })


        private val connectedSignal = Signal(false)
        override val connected = connectedSignal.toSharedFlow()

        val channel = Uuid.random().toString()

        /** Dispatched from the single muxer listener for messages on this [channel]. */
        fun handleMessage(message: MultiplexMessage) {
            if (message.start) {
                connectedSignal.value = true
                onOpenList.invokeAllSafe()
            }
            message.data?.let { data ->
                onMessageList.toList().forEach { it(data) }
            }
            if (message.end) {
                connectedSignal.value = false
                onCloseList.toList().forEach { it(-1) }
            }
        }

        /**
         * Called when the underlying multiplex transport drops.  The channel stays registered so
         * [lifecycle] re-sends its "start" frame once the transport reconnects.
         */
        fun handleParentClose() {
            connectedSignal.value = false
            onCloseList.toList().forEach { it(-1) }
        }

        private val shouldBeOn = Signal(false)
        private var closeChannel: (() -> Unit)? = null

        override fun connect() {
            shouldBeOn.value = true
            channels[channel] = this
            if (closeChannel == null) closeChannel = muxer.beginUse()
        }

        /**
         * Sends the "start" frame whenever the channel should be on and the transport is connected
         * but the channel isn't open yet - covering both the initial subscribe and re-subscribing
         * after a transport reconnect.  Closing sends its single "end" frame explicitly in [close],
         * not here, to guarantee exactly one close frame.  This scope lives for the lifetime of the
         * channel (never cancelled) so the channel can be closed and reconnected repeatedly.
         */
        val lifecycle = CoroutineScope(Job()).apply {
            reactiveScope {
                val shouldBeOn = shouldBeOn()
                val isOn = connectedSignal()
                val parentConnected = muxer.connected()
                if (shouldBeOn && parentConnected && !isOn) {
                    muxer.send(
                        MultiplexMessage(
                            channel = channel,
                            path = path,
                            queryParams = params,
                            start = true
                        )
                    )
                }
            }
        }

        override fun close(code: Short, reason: String) {
            // Idempotent: a channel that's already off has nothing to close.
            if (!shouldBeOn.value) return
            shouldBeOn.value = false
            // Exactly one "end" frame.  A no-op send if the transport is already down.
            muxer.send(
                MultiplexMessage(
                    channel = channel,
                    path = path,
                    queryParams = params,
                    end = true
                )
            )
            connectedSignal.value = false
            onCloseList.toList().forEach { it(-1) }
            channels.remove(channel)
            closeChannel?.invoke()
            closeChannel = null
            // [lifecycle] is intentionally NOT cancelled so the channel can be reconnected.
        }

        override fun send(data: String) {
            muxer.send(
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
}