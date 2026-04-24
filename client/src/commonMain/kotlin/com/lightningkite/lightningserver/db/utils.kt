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
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


/**
 * Retrieves the current clock from coroutine context, or system clock if none is set.
 *
 * This allows suspending functions to respect test clocks without explicitly
 * passing them as parameters. The clock can be set via [withClock].
 *
 * ## Usage
 *
 * In production code:
 * ```kotlin
 * suspend fun timestampNow(): Instant {
 *     return Clock.default().now()
 * }
 * ```
 *
 * In tests:
 * ```kotlin
 * @Test
 * fun testWithFixedTime() = runTest {
 *     val fixedClock = object : Clock {
 *         override fun now() = Instant.parse("2025-01-01T00:00:00Z")
 *     }
 *
 *     withClock(fixedClock) {
 *         val timestamp = timestampNow()
 *         assertEquals(Instant.parse("2025-01-01T00:00:00Z"), timestamp)
 *     }
 * }
 * ```
 *
 * @return The clock from coroutine context, or [Clock.System] if not in a [withClock] block
 * @see withClock to set a custom clock
 * @see ClockContextElement for the context element implementation
 */
public suspend fun Clock.Companion.default(): Clock {
    return coroutineContext[ClockContextElement]?.clock ?: Clock.System
}

/**
 * Coroutine context element that carries a [Clock] instance.
 *
 * Used by [withClock] and [default] to propagate custom clocks
 * through coroutine contexts. This enables testable time-dependent code without
 * explicit clock parameters.
 *
 * @property clock The clock instance carried by this context element
 */
public class ClockContextElement(public val clock: Clock) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<ClockContextElement>
}

/**
 * Creates a delay function that synchronizes to even intervals of the given duration.
 *
 * Instead of delaying for exactly the specified duration, this function calculates
 * the time remaining until the next "boundary" of that duration. For example, with
 * a 60-second duration, it will delay until the next minute boundary (e.g., if called
 * at 10:30:25, it will delay 35 seconds until 10:31:00).
 *
 * This is useful for polling/refresh operations that should happen at predictable
 * intervals aligned with the clock, rather than drifting based on when they start.
 *
 * @param clock The clock to use for time calculations. Defaults to [Clock.System].
 * @return A suspend function that delays until the next boundary of the given duration.
 */
public fun synchronizingDelay(clock: Clock = Clock.System): suspend (duration: Duration) -> Unit {
    return {
        val inMillis = it.inWholeMilliseconds
        val n = clock.now()
        // Calculate next boundary: (currentTime % interval) gives offset, add full interval to get next
        val time = (n.toEpochMilliseconds() % inMillis).plus(inMillis).let(Instant::fromEpochMilliseconds)
        delay(time - n)
    }
}

/**
 * A Flow wrapper that can be closed/disposed to release resources.
 *
 * Extends [AutoCloseable] to allow proper cleanup of flow resources
 * when the flow is no longer needed.
 */
public interface CloseableFlow<T> : AutoCloseable {
    public val flow: Flow<T>
}

/**
 * Converts a [TypedWebSocket] into a Flow of Flows representing the WebSocket lifecycle.
 *
 * The outer Flow emits:
 * - A non-null inner Flow when the WebSocket connection opens
 * - `null` when the WebSocket connection closes
 *
 * The inner Flow emits individual messages received on the WebSocket while connected.
 * Each time the WebSocket reconnects, a new inner Flow is created and emitted.
 *
 * This allows consumers to handle reconnection logic by subscribing to new inner flows
 * as they are emitted.
 *
 * @param scope The [CoroutineScope] that manages the WebSocket lifecycle listeners.
 * @param log Optional logger for debugging connection events and message flow.
 * @return A Flow that emits inner Flows for each WebSocket connection session, or null when disconnected.
 */
public fun <SEND, RECEIVE> TypedWebSocket<SEND, RECEIVE>.toFlow(scope: CoroutineScope, log: Log? = null): Flow<Flow<RECEIVE>?> {
    val out = MutableSharedFlow<Flow<RECEIVE>?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)
    var current: MutableSharedFlow<RECEIVE> = MutableSharedFlow(replay = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)

    onOpen {
        // Create a fresh flow for this connection session
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

/**
 * Ensures that a sort order is "total" by appending a sort on the `_id` field if not already present.
 *
 * A "total" sort order guarantees a unique ordering for all items, which is essential for
 * cursor-based pagination to work correctly. Without this, items with equal values in the
 * sort fields could be returned in inconsistent order across page boundaries.
 *
 * This function checks if the last sort field is already `_id`, and if not, appends an
 * ascending sort on `_id` to make the sort order deterministic.
 *
 * **GOTCHA**: This assumes that all models have an `_id` field that is Comparable. If the
 * model doesn't have `_id`, this will throw a runtime exception.
 *
 * @param serializer The serializer for type T, used to access field metadata.
 * @return A new sort list with `_id` appended if necessary, or the original list if already total.
 * @throws NullPointerException if the serializer doesn't have serializable properties.
 * @throws ClassCastException if the `_id` field is not Comparable.
 */
public fun <T> List<SortPart<T>>.ensureTotal(serializer: KSerializer<T>): List<SortPart<T>> {
    // Check if already sorting by _id as the last field
    if (lastOrNull()?.field?.properties?.singleOrNull()?.name == "_id") return this

    // Find the _id property and append it to the sort
    @Suppress("UNCHECKED_CAST")
    return this + SortPart(
        DataClassPathAccess(
            DataClassPathSelf<T>(serializer),
            serializer.serializableProperties!!.find { it.name == "_id" } as SerializableProperty<T, Comparable<*>>))
}


/**
 * Base implementation of [ResourceUse] with reference counting.
 *
 * This class provides activate/deactivate lifecycle hooks that are called when
 * the resource transitions between unused and in-use states. Subclasses override
 * [activate] and [deactivate] to implement resource acquisition and cleanup logic.
 *
 * The reference counting ensures that:
 * - [activate] is called only when the first use begins
 * - [deactivate] is called only when the last use ends
 *
 * **Thread Safety**: This implementation is NOT thread-safe. It should only be
 * used from a single thread or with external synchronization.
 */
public abstract class BaseResourceUse : ResourceUse {
    /**
     * Called when the resource transitions from 0 uses to 1 use.
     * Override to implement resource initialization logic.
     */
    protected open fun activate() {}

    /**
     * Called when the resource transitions from 1 use to 0 uses.
     * Override to implement resource cleanup logic.
     */
    protected open fun deactivate() {}

    /** Current number of active uses of this resource. */
    public var uses: Int = 0
        private set

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

/**
 * Suspends until the [Reactive] value matches the given predicate.
 *
 * If the current value already matches, returns immediately.
 * Otherwise, waits for the value to change to one that matches.
 *
 * The listener is automatically cleaned up when the condition is met or if
 * the coroutine is cancelled.
 *
 * @param matching Predicate that tests whether the current value satisfies the wait condition.
 */
public suspend fun <T> Reactive<T>.waitFor(matching: (T)->Boolean) {
    // Quick check: if already matches, return immediately
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


/**
 * A [Reactive] wrapper that debounces change notifications.
 *
 * The state value is always immediately available from the source, but listeners
 * are only notified after a period of inactivity (no changes for [duration]).
 *
 * This is useful for expensive operations that should only happen after the user
 * has stopped making changes (e.g., auto-save, search-as-you-type).
 */
public data class DebounceReactive<T>(val source: Reactive<T>, val scope: CoroutineScope, val duration: Duration) : Reactive<T>, Listenable by DebounceListenable(source, scope, duration) {
    override val state: ReactiveState<T> get() = source.state
}

/**
 * A [Listenable] wrapper that debounces change notifications.
 *
 * Listeners are only invoked after [duration] has passed since the last change.
 * If multiple changes occur within the debounce window, only the final one triggers a notification.
 *
 * **Thread Safety**: The changeCount increment is not atomic, so this should be used with
 * care in multi-threaded scenarios.
 */
public data class DebounceListenable(val source: Listenable, val scope:CoroutineScope, val duration: Duration) : Listenable {
    private var changeCount = 0

    override fun addListener(listener: () -> Unit): () -> Unit {
        return source.addListener {
            val num = ++changeCount
            scope.launch {
                delay(duration)
                // Only invoke if no newer changes have occurred
                if (num == changeCount) listener()
            }
        }
    }
}

/**
 * Creates a debounced version of this [Reactive] that delays listener notifications.
 *
 * @param scope The [CoroutineScope] used to launch delay coroutines.
 * @param timeMs The debounce delay in milliseconds.
 * @see DebounceReactive
 */
public fun <T> Reactive<T>.debounce(scope: CoroutineScope, timeMs: Long): Reactive<T> = DebounceReactive(this, scope, timeMs.milliseconds)

/**
 * Creates a debounced version of this [Reactive] that delays listener notifications.
 *
 * @param scope The [CoroutineScope] used to launch delay coroutines.
 * @param duration The debounce delay duration.
 * @see DebounceReactive
 */
public fun <T> Reactive<T>.debounce(scope: CoroutineScope, duration: Duration): Reactive<T> = DebounceReactive(this, scope, duration)

/**
 * Creates a [Reactive] that only notifies listeners when the value actually changes.
 *
 * This is implemented using a lens identity transformation, which internally tracks
 * previous values and only fires when they differ.
 *
 * Useful for preventing unnecessary UI updates when the same value is set repeatedly.
 */
public fun <T> Reactive<T>.requireDifferenceForListener(): Reactive<T> = lens { it }

/**
 * Wraps a [Reactive] so that it automatically manages a [ResourceUse] based on listener count.
 *
 * The resource is acquired when the first listener is added and released when the last
 * listener is removed. This is useful for expensive resources that should only be active
 * while being observed (e.g., WebSocket connections, polling timers).
 *
 * @param resource The resource to manage based on listener lifecycle.
 * @return A new [Reactive] that delegates to this one but manages the resource.
 */
public fun <T> Reactive<T>.uses(resource: ResourceUse): Reactive<T> {
    return object : Reactive<T> {
        override val state: ReactiveState<T>
            get() = this@uses.state

        var uses = 0
        var r: (() -> Unit)? = null

        override fun addListener(listener: () -> Unit): () -> Unit {
            // Begin using the resource when first listener is added
            if (uses++ == 0) {
                r = resource.beginUse()
            }
            val original = this@uses.addListener(listener)
            return {
                original()
                // Stop using the resource when last listener is removed
                if (--uses == 0) {
                    r?.invoke()
                    r = null
                }
            }
        }
    }
}

/**
 * Creates a [ResourceUse] that launches a coroutine when the resource is used.
 *
 * The coroutine runs in a child scope of [parentScope] and is automatically cancelled
 * when the resource is no longer used.
 *
 * This is useful for resources that need background work (e.g., polling, listening to events).
 *
 * @param parentScope The parent scope for the coroutine. Defaults to [AppScope].
 * @param action The suspend function to execute while the resource is in use.
 * @return A [ResourceUse] that manages the coroutine lifecycle.
 */
public fun ResourceUse(parentScope: CoroutineScope = AppScope, action: suspend CoroutineScope.() -> Unit): ResourceUse =
    object : ResourceUse {
        override fun beginUse(): () -> Unit {
            val job = Job(parentScope.coroutineContext[Job])
            CoroutineScope(parentScope.coroutineContext + job).launch(block = action)
            return { job.cancel() }
        }
    }


/**
 * A delay mechanism that can be interrupted externally or by parent [InterruptibleDelay] instances.
 *
 * This forms a hierarchy where interrupting a parent also interrupts all children.
 * Useful for cancelling nested delay operations when outer operations complete.
 *
 * Example use case: Cancelling all polling delays when a WebSocket connection is established.
 *
 * @param parent Optional parent delay that can also interrupt this one.
 */
public class InterruptibleDelay(public val parent: InterruptibleDelay? = null) {
    private val listenable = BasicListenable()

    /**
     * Interrupts any active delays on this instance, causing them to complete immediately.
     * Does not affect delays on parent or child instances.
     */
    public fun interrupt() { listenable.invokeAll() }

    /**
     * Creates a child [InterruptibleDelay] that will be interrupted if this one is interrupted.
     *
     * @return A new child delay instance.
     */
    public fun child(): InterruptibleDelay = InterruptibleDelay(this)

    /**
     * Delays for the specified duration, or until this delay (or any parent) is interrupted.
     *
     * @param duration How long to delay (if not interrupted).
     */
    public suspend fun delay(duration: Duration) {
        // Race between the time delay and interrupt signals from this and all parents
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

/**
 * Races multiple suspend functions, returning the result of whichever completes first.
 *
 * All racing operations are launched concurrently, and the flow completes as soon as
 * any one of them produces a result.
 *
 * **Note**: Other racing operations are not explicitly cancelled and may continue running
 * until the flow collector is cancelled.
 *
 * @param races Suspend functions to race against each other.
 * @return The result of the first function to complete.
 */
public suspend fun <R> race(vararg races: suspend () -> R): R {
    return channelFlow {
        for (race in races) {
            launch { send(race()) }
        }
    }.first()
}

/**
 * Gets the current time using the coroutine context's clock if available, or the system clock.
 *
 * This allows for testable time-dependent code by using [ClockContextElement] in tests
 * to inject a fake/controllable clock.
 *
 * @return The current instant according to the context clock or system clock.
 */
internal fun CoroutineScope.now(): Instant = coroutineContext[ClockContextElement]?.clock?.now() ?: Clock.System.now()

/*
 * API IMPROVEMENT RECOMMENDATIONS:
 *
 * 1. Thread safety for BaseResourceUse and debounce implementations
 *    - BaseResourceUse.uses counter is not thread-safe
 *    - DebounceListenable.changeCount is not thread-safe
 *    - Consider using AtomicInteger or adding documentation about single-threaded usage
 *
 * 2. Add throttle() complement to debounce()
 *    - Debounce waits for inactivity, throttle limits frequency
 *    - Throttle would be useful for rate-limiting expensive operations
 *
 * 3. Improve synchronizingDelay edge case handling
 *    - What happens if duration is 0 or negative?
 *    - What happens if duration > epoch time?
 *    - Add validation or document expected behavior
 *
 * 4. Add cancel safety to race() function
 *    - Currently racing operations may continue after first completes
 *    - Consider explicitly cancelling losers or documenting this behavior more clearly
 *    - Could add a raceCancelling() variant that cancels losers
 *
 * 5. Consider adding timeout variants
 *    - waitFor() could have a timeout parameter
 *    - InterruptibleDelay.delay() could take max duration
 *
 * 6. Add more WebSocket flow utilities
 *    - toFlow() could have retry/reconnection parameters
 *    - Could add error handling configuration
 *    - Consider adding backpressure strategy options
 *
 * 7. ensureTotal() should handle missing _id more gracefully
 *    - Currently throws NPE/ClassCastException
 *    - Could return Result<List<SortPart<T>>> or add validation
 *    - Could add a ensureTotalOrNull() variant
 *
 * 8. requireDifferenceForListener() needs better naming
 *    - The name doesn't clearly convey it filters duplicate notifications
 *    - Consider distinctUntilChanged() (matches RxJava/Flow naming)
 *    - Add example usage in documentation
 *
 * 9. ResourceUse factory function shadows interface name
 *    - Having both ResourceUse interface and ResourceUse() factory is confusing
 *    - Consider renaming factory to resourceUseFromCoroutine() or similar
 *
 * 10. Add structured concurrency support
 *     - Some utilities don't properly propagate cancellation
 *     - Consider adding CoroutineScope extensions where appropriate
 *     - Document cancellation behavior clearly
 *
 * 11. Memory leak potential in listeners
 *     - Several implementations create closures that could retain references
 *     - Consider adding cleanup guidance in documentation
 *     - Could add leak detection in debug builds
 */
