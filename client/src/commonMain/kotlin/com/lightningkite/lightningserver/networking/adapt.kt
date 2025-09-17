package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.kiteui.report
import com.lightningkite.lightningserver.db.BaseResourceUse
import com.lightningkite.lightningserver.typed.ClientWebSocket
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

public typealias LsHttpMethod = com.lightningkite.lightningserver.HttpMethod

public val LsHttpMethod.kiteUi: HttpMethod get() = when (this) {
    LsHttpMethod.GET -> HttpMethod.GET
    LsHttpMethod.PUT -> HttpMethod.PUT
    LsHttpMethod.POST -> HttpMethod.POST
    LsHttpMethod.PATCH -> HttpMethod.PATCH
    LsHttpMethod.DELETE -> HttpMethod.DELETE
    LsHttpMethod.HEAD -> HttpMethod.HEAD
    else -> throw IllegalArgumentException("Unsupported HttpMethod: $this")
}

public val HttpMethod.lightningServer: LsHttpMethod get() = when (this) {
    HttpMethod.GET -> LsHttpMethod.GET
    HttpMethod.POST -> LsHttpMethod.PUT
    HttpMethod.PUT -> LsHttpMethod.POST
    HttpMethod.PATCH -> LsHttpMethod.PATCH
    HttpMethod.DELETE -> LsHttpMethod.DELETE
    HttpMethod.HEAD -> LsHttpMethod.HEAD
}


// Websockets

private class SerializerSocket<SEND, RECEIVE>(
    val wraps: ClientWebSocket<String, String>,
    val json: Json,
    val send: KSerializer<SEND>,
    val receive: KSerializer<RECEIVE>
): ClientWebSocket<SEND, RECEIVE> {
    override val connected: SharedFlow<Boolean> get() = wraps.connected
    override fun connect() = wraps.connect()
    override fun close(code: Short, reason: String) = wraps.close(code, reason)
    override fun onOpen(action: () -> Unit) = wraps.onOpen(action)
    override fun onClose(action: (Short) -> Unit) = wraps.onClose(action)

    override fun onMessage(action: (RECEIVE) -> Unit) {
        wraps.onMessage {
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

    override fun send(data: SEND) = wraps.send(json.encodeToString(send, data))
}

public fun <T> Reactive<T>.toSharedFlow(
    context: CoroutineContext = Dispatchers.Unconfined,
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): SharedFlow<T> {
    val fullContext = context + CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Reactive.reportException(throwable)
        }
    }

    var listener: (() -> Unit)? = null

    val flow = MutableSharedFlow<T>(replay, extraBufferCapacity, onBufferOverflow)

    fun CoroutineScope.startListening() {
        flow.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
            .onEach { isActive ->
                if (isActive) listener = this@toSharedFlow.addListener {
                    this@toSharedFlow.state.onSuccess { flow.tryEmit(it) }
                }
                else {
                    // No longer active, stop listening
                    listener?.invoke()
                    cancel()
                }
            }
            .launchIn(this)
    }

    return flow.onSubscription {
        if (listener == null) CoroutineScope(fullContext).startListening()
    }
}

private class ReactiveClientWebSocket<SEND, RECEIVE>(
    val wraps: ClientWebSocket<SEND, RECEIVE>
): TypedWebSocket<SEND, RECEIVE>, BaseResourceUse() {
    override val connected: Reactive<Boolean> = remember { wraps.connected() }

    override fun activate() {
        wraps.connect()
    }

    override fun deactivate() {
        wraps.close(1000, "Closing because no one is listening")
    }

    override fun close(code: Short, reason: String) = wraps.close(code, reason)
    override fun onClose(action: (Short) -> Unit) = wraps.onClose(action)
    override fun onMessage(action: (RECEIVE) -> Unit) = wraps.onMessage(action)
    override fun onOpen(action: () -> Unit) = wraps.onOpen(action)
    override fun send(data: SEND) = wraps.send(data)
}

private class LightningServerWebSocket<SEND, RECEIVE>(
    val wraps: TypedWebSocket<SEND, RECEIVE>
): ClientWebSocket<SEND, RECEIVE> {
    override val connected: SharedFlow<Boolean> by lazy { wraps.connected.toSharedFlow() }

    override fun connect() {
        wraps.beginUse()
    }

    override fun close(code: Short, reason: String) = wraps.close(code, reason)
    override fun onClose(action: (Short) -> Unit) = wraps.onClose(action)
    override fun onOpen(action: () -> Unit) = wraps.onOpen(action)
    override fun onMessage(action: (RECEIVE) -> Unit) = wraps.onMessage(action)
    override fun send(data: SEND) = wraps.send(data)
}

public fun <S, R> ClientWebSocket<String, String>.typed(json: Json, send: KSerializer<S>, receive: KSerializer<R>): ClientWebSocket<S, R> = SerializerSocket(this, json, send, receive)

public fun <S, R> ClientWebSocket<S, R>.toTypedWebsocket(): TypedWebSocket<S, R> =
    if (this is LightningServerWebSocket<S, R>) wraps
    else ReactiveClientWebSocket(this)

public fun <S, R> TypedWebSocket<S, R>.toClientWebSocket(): ClientWebSocket<S, R> =
    if (this is ReactiveClientWebSocket<S, R>) wraps
    else LightningServerWebSocket(this)