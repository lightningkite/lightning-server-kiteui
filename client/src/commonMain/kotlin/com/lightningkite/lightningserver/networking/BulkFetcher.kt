package com.lightningkite.lightningserver.networking

import com.lightningkite.UUID
import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.typed.BulkRequest
import com.lightningkite.lightningserver.typed.BulkResponse
import com.lightningkite.lightningserver.websocket.MultiplexMessage
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSerializationApi::class)
class BulkFetcher(
    val httpBulk: String,
    val wsMultiplex: String,
    val json: Json = DefaultJson,
    val pingTime: Duration = 5_000.milliseconds,
    val delay: Duration = 100.milliseconds,
    val log: Console? = null,
    val calculator: suspend () -> List<Pair<String, String>> = { listOf() },
) : Fetcher {
    override fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): Fetcher =
        BulkFetcher(httpBulk, wsMultiplex, json, pingTime, delay, log, calculator)

    companion object {
        val channelInitialBackoff: Duration = 100.milliseconds
        val channelMaxBackoff: Duration = 30_000.milliseconds
        val channelStableConnectionThreshold: Duration = 5_000.milliseconds
    }

    private var fetchQueue = HashMap<String, Pair<BulkRequest, CancellableContinuation<BulkResponse>>>()
    private var scheduled = false
    override suspend fun <I, O> invoke(
        url: String,
        method: HttpMethod,
        inSerializer: KSerializer<I>,
        body: I,
        outSerializer: KSerializer<O>,
    ): O {
        val id = UUID.Companion.random().toString()
        val req = BulkRequest(
            url,
            method = method.name,
            body =  if (inSerializer.descriptor.serialName != "kotlin.Unit")
                json.encodeToString(inSerializer, body)
            else null
        )
        if (!scheduled) {
            scheduled = true
            AppScope.launch {
                delay(delay)
                fetch()
            }
        }
        return suspendCancellableCoroutine<BulkResponse> { cont ->
            fetchQueue.put(id, req to cont)
        }.let { it: BulkResponse ->
            if (it.error == null && outSerializer.descriptor.serialName == Unit.serializer().descriptor.serialName) Unit as O
            else if (it.result != null) json.decodeFromString(outSerializer, it.result!!)
            else {
                throw LsErrorException(it.error?.http?.toShort() ?: 0.toShort(), it.error ?: LSError(0))
            }
        }
    }

    private suspend fun fetch() {
        val todo = fetchQueue
        fetchQueue = HashMap()
        scheduled = false
        try {
            connectivityFetch(
                url = httpBulk,
                method = HttpMethod.POST,
                headers = { httpHeaders(calculator()) },
                body = RequestBodyText(
                    json.encodeToString(
                        MapSerializer(String.serializer(), BulkRequest.Companion.serializer()),
                        todo.mapValues { it.value.first }),
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

    val remember = retryWebsocket(
        underlyingSocket = {
            val headers = calculator()

            val url = if(headers.isNotEmpty()){
                val terminator = if(wsMultiplex.contains('?')) '&' else '?'
                wsMultiplex + "$terminator${headers.joinToString("&"){ "${it.first}=${it.second}" }}"
            } else wsMultiplex
            com.lightningkite.kiteui.websocket(url)
        },
        pingTime = pingTime.inWholeMilliseconds,
        log = log
    ).typedWithDebug(json, MultiplexMessage.Companion.serializer(), MultiplexMessage.Companion.serializer()).also {
        if(debugMode && log != null) {
            it.onOpen {
                it.send(MultiplexMessage(channel = "debug", start = true))
            }
        }
        it.onMessage {
            if(log != null && it.channel == "debug") log.log("Multiplex debug: $it")
        }
    }

    override fun <I, O> websocket(
        url: String,
        inSerializer: KSerializer<I>,
        outSerializer: KSerializer<O>,
    ): TypedWebSocket<I, O> {
        return WebsocketChannel(url).typed(json, inSerializer, outSerializer)
    }

    private inner class WebsocketChannel(url: String) : RetryWebsocket {
        val path = url.substringBefore('?')
        val params = url.substringAfter('?', "")
            .split('&')
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .groupBy({ it.first }, { it.second })
        val channelOpen = Signal(false)
        val channel = UUID.Companion.random().toString()

        private var reconnectJob: Job? = null
        private val backoff = ChannelBackoff(
            initialBackoff = channelInitialBackoff,
            maxBackoff = channelMaxBackoff,
            stableConnectionThreshold = channelStableConnectionThreshold,
        )

        init {
            remember.onMessage { message ->
                if (message.channel == channel) {
                    if (message.start) {
                        backoff.onConnectionOpened()
                        channelOpen.value = true
                        onOpenList.forEach { it() }
                    }
                    message.data?.let { data ->
                        onMessageList.forEach { it(data) }
                    }
                    if (message.end) {
                        channelOpen.value = false
                        val wasIntentional = shouldBeOn.value <= 0
                        backoff.onConnectionClosed(wasIntentional)
                        if (!wasIntentional) {
                            log?.log("Channel $channel closed unexpectedly, backoff now ${backoff.currentBackoffMs}ms")
                        }
                        onCloseList.forEach { it(-1) }
                    }
                }
            }
            remember.onClose {
                channelOpen.value = false
                onCloseList.forEach { it(-1) }
            }
        }

        override val connected: Reactive<Boolean>
            get() = channelOpen
        val shouldBeOn = Signal(0)

        override fun beginUse(): () -> Unit {
            shouldBeOn.value++
            val parent = remember.beginUse()
            return {
                parent()
                shouldBeOn.value--
            }
        }

        // Reactive scope pattern: whenever any signal changes, re-evaluate what to do.
        // Only one reconnectJob runs at a time - we cancel before launching a new one.
        // If signals change rapidly during backoff, we restart the backoff (intentional -
        // we want to wait for stability before reconnecting).
        val lifecycle = CoroutineScope(Job()).apply {
            reactiveScope {
                val shouldBeOn = shouldBeOn() > 0
                val isOn = channelOpen()
                val parentConnected = remember.connected()

                // Always cancel pending reconnect when re-evaluating
                reconnectJob?.cancel()
                reconnectJob = null

                if (shouldBeOn && parentConnected && !isOn) {
                    reconnectJob = launch {
                        val backoffWithJitter = backoff.getBackoffWithJitter()
                        if (backoffWithJitter > 0) {
                            log?.log("Channel $channel backing off for ${backoffWithJitter}ms before reconnect")
                            delay(backoffWithJitter)
                        }
                        // Re-verify conditions after delay (signals may have changed)
                        if (this@WebsocketChannel.shouldBeOn.value > 0 && remember.connected.awaitOnce() && !channelOpen.value) {
                            remember.send(
                                MultiplexMessage(
                                    channel = channel,
                                    path = path,
                                    queryParams = params,
                                    start = true
                                )
                            )
                        }
                    }
                } else if (!shouldBeOn && parentConnected && isOn) {
                    remember.send(
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
            remember.send(
                MultiplexMessage(
                    channel = channel,
                    path = path,
                    queryParams = params,
                    end = true
                )
            )
            lifecycle.cancel()
        }

        override fun send(data: Blob) = throw UnsupportedOperationException()

        override fun send(data: String) {
            remember.send(
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

        override fun onBinaryMessage(action: (Blob) -> Unit) = throw UnsupportedOperationException()
        override fun onClose(action: (Short) -> Unit) {
            onCloseList.add(action)
        }
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