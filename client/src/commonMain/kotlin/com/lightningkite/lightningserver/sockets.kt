package com.lightningkite.lightningserver

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.lightningserver.websocket.MultiplexMessage
import com.lightningkite.uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json

private val shared = HashMap<String, TypedWebSocket<MultiplexMessage, MultiplexMessage>>()
fun multiplexSocket(
    url: String,
    path: String,
    params: Map<String, List<String>>,
    json: Json,
    pingTime: Long = 30_000L,
    log: Console? = null
): RetryWebsocket {
    val shared = shared.getOrPut(url) {
        val s = retryWebsocket({ websocket(url) }, pingTime, log = log)
        s.typed(json, MultiplexMessage.serializer(), MultiplexMessage.serializer())
    }
    val channelOpen = Property(false)
    val channel = uuid().toString()
    return object : RetryWebsocket {
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
