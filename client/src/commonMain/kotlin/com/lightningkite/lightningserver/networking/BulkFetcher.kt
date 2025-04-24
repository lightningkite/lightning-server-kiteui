package com.lightningkite.lightningserver.networking

import com.lightningkite.UUID
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.Console
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.RequestBodyText
import com.lightningkite.kiteui.RequestResponse
import com.lightningkite.kiteui.RetryWebsocket
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.kiteui.connectivityFetch
import com.lightningkite.kiteui.httpHeaders
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.retryWebsocket
import com.lightningkite.kiteui.typed
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.typed.BulkRequest
import com.lightningkite.lightningserver.typed.BulkResponse
import com.lightningkite.lightningserver.websocket.MultiplexMessage
import com.lightningkite.readable.AppScope
import com.lightningkite.readable.Property
import com.lightningkite.readable.Readable
import com.lightningkite.readable.reactiveScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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

    val shared = retryWebsocket(
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
    ).typed(json, MultiplexMessage.Companion.serializer(), MultiplexMessage.Companion.serializer())

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
        val channelOpen = Property(false)
        val channel = UUID.Companion.random().toString()

        init {
            shared.onMessage { message ->
                if (message.channel == channel) {
                    if (message.start) {
                        channelOpen.value = true
                        onOpenList.forEach { it() }
                    }
                    message.data?.let { data ->
                        onMessageList.forEach { it(data) }
                    }
                    if (message.end) {
                        channelOpen.value = false
                        onCloseList.forEach { it(-1) }
                    }
                }
            }
            shared.onClose {
                channelOpen.value = false
            }
        }

        override val connected: Readable<Boolean>
            get() = channelOpen
        val shouldBeOn = Property(0)

        override fun beginUse(): () -> Unit {
            shouldBeOn.value++
            val parent = shared.beginUse()
            return {
                parent()
                shouldBeOn.value--
            }
        }

        val lifecycle = CoroutineScope(Job()).apply {
            reactiveScope {
                val shouldBeOn = shouldBeOn() > 0
                val isOn = channelOpen()
                val parentConnected = shared.connected()
                if (shouldBeOn && parentConnected && !isOn) {
                    shared.send(
                        MultiplexMessage(
                            channel = channel,
                            path = path,
                            queryParams = params,
                            start = true
                        )
                    )
                } else if (!shouldBeOn && parentConnected && isOn) {
                    shared.send(
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
            shared.send(
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
            shared.send(
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