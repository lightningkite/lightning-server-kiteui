package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.kiteui.identityHashCode
import com.lightningkite.services.database.SortPart
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.DataClassPathAccess
import com.lightningkite.services.database.DataClassPathSelf
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.serializableProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


fun synchronizingDelay(clock: Clock = Clock.System): suspend (duration: Duration) -> Unit {
    return {
        val inMillis = it.inWholeMilliseconds
        val n = clock.now()
        val time = (n.toEpochMilliseconds() % inMillis).plus(inMillis).let(Instant::fromEpochMilliseconds)
        delay(time - n)
    }
}
interface CloseableFlow<T> : AutoCloseable {
    val flow: Flow<T>
}

fun <SEND, RECEIVE> TypedWebSocket<SEND, RECEIVE>.toFlow(scope: CoroutineScope, log: Log? = null): Flow<Flow<RECEIVE>?> {
    val out = MutableSharedFlow<Flow<RECEIVE>?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)
    var current: MutableSharedFlow<RECEIVE> = MutableSharedFlow(replay = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)
    onOpen {
        current = MutableSharedFlow(replay = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)
        log?.log("onOpen current ${current.identityHashCode()}, NO MERGE")
        out.tryEmit(current)
    }
    onClose { code ->
        log?.log("onClose current ${current.identityHashCode()}, NO MERGE")
        out.tryEmit(null)
    }
    onMessage {
        log?.log("received message: $it, forwarding to flow ${current.identityHashCode()}")
        current.tryEmit(it).also { log?.log("Success on send to flow? $it") }
    }
    return out
}

fun <T> List<SortPart<T>>.ensureTotal(serializer: KSerializer<T>): List<SortPart<T>> {
    if (lastOrNull()?.field?.properties?.singleOrNull()?.name == "_id") return this
    @Suppress("UNCHECKED_CAST")
    return this + SortPart(
        DataClassPathAccess(
            DataClassPathSelf<T>(serializer),
            serializer.serializableProperties!!.find { it.name == "_id" } as SerializableProperty<T, Comparable<*>>))
}


abstract class BaseResourceUse : ResourceUse {
    protected open fun activate() {}
    protected open fun deactivate() {}
    var uses = 0
    override fun beginUse(): () -> Unit {
        if (uses++ == 0) {
            activate()
        }
        var dead = false
        return label@{
            if (dead) return@label
            dead = true
            if (--uses == 0) {
                deactivate()
            }
        }
    }
}

suspend fun <T> Reactive<T>.waitFor(matching: (T)->Boolean) {
    state.onSuccess {
        if(matching(it)) return
    }
    var close: (()->Unit)? = null
    try {
        suspendCancellableCoroutine<Unit> { cont ->
            close = addListener {
                state.onSuccess {
                    if(matching(it)) cont.resume(Unit)
                }
            }
        }
    } finally {
        close?.invoke()
    }
}


data class DebounceReactive<T>(val source: Reactive<T>, val scope: CoroutineScope, val duration: Duration) : Reactive<T>, Listenable by DebounceListenable(source, scope, duration) {
    override val state: ReactiveState<T> get() = source.state
}
data class DebounceListenable(val source: Listenable, val scope:CoroutineScope, val duration: Duration) : Listenable {
    private var changeCount = 0
    override fun addListener(listener: () -> Unit): () -> Unit {
        return source.addListener {
            val num = ++changeCount
            scope.launch {
                delay(duration)
                if (num == changeCount) listener()
            }
        }
    }
}

fun <T> Reactive<T>.debounce(scope: CoroutineScope, timeMs: Long): Reactive<T> = DebounceReactive(this, scope, timeMs.milliseconds)
fun <T> Reactive<T>.debounce(scope: CoroutineScope, duration: Duration): Reactive<T> = DebounceReactive(this, scope, duration)

fun <T> Reactive<T>.requireDifferenceForListener(): Reactive<T> = lens { it }
fun <T> Reactive<T>.uses(resource: ResourceUse): Reactive<T> {
    return object : Reactive<T> {
        override val state: ReactiveState<T>
            get() = this@uses.state

        var uses = 0
        var r: (() -> Unit)? = null
        override fun addListener(listener: () -> Unit): () -> Unit {
            if (uses++ == 0) {
                r = resource.beginUse()
            }
            val original = this@uses.addListener(listener)
            return {
                original()
                if (--uses == 0) {
                    r?.invoke()
                    r = null
                }
            }
        }
    }
}

fun ResourceUse(parentScope: CoroutineScope = AppScope, action: suspend CoroutineScope.() -> Unit) =
    object : ResourceUse {
        override fun beginUse(): () -> Unit {
            val job = Job(parentScope.coroutineContext[Job])
            CoroutineScope(parentScope.coroutineContext + job).launch(block = action)
            return { job.cancel() }
        }
    }


class InterruptibleDelay(val parent: InterruptibleDelay? = null) {
    private val listenable = BasicListenable()
    fun interrupt() { listenable.invokeAll() }
    fun child(): InterruptibleDelay = InterruptibleDelay(this)
    suspend fun delay(duration: Duration) {
        val toRace = listOf(suspend { kotlinx.coroutines.delay(duration) })
            .plus(generateSequence(this) { it.parent }.map { inter ->
                suspend {
                    var closer: () -> Unit = {}
                    suspendCancellableCoroutine { cont ->
                        closer = inter.listenable.addListener {
                            cont.resume(Unit)
                        }
                    }
                    closer()
                }
            })
        race(*toRace.toTypedArray())
    }
}

suspend fun <R> race(vararg races: suspend () -> R): R {
    return channelFlow {
        for (race in races) {
            launch { send(race()) }
        }
    }.first()
}
