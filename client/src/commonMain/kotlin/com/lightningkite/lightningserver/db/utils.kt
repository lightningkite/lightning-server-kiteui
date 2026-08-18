package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.SortPart
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.DataClassPathAccess
import com.lightningkite.services.database.DataClassPathSelf
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.serializableProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration


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
public suspend fun <T> Reactive<T>.waitFor(matching: (T) -> Boolean) {
    // Quick check: if already matches, return immediately
    state.onSuccess {
        if (matching(it)) return
    }

    var close: (() -> Unit)? = null
    try {
        suspendCancellableCoroutine<Unit> { cont ->
            close = addListener {
                state.onSuccess {
                    if (matching(it)) cont.resume(Unit)
                }
            }
        }
    } finally {
        close?.invoke()
    }
}


/**
 * Keeps showing the last successfully retrieved value while the source is loading or has failed.
 *
 * Sources like [ModelCache] report loading and failure honestly, which means a refresh that goes
 * stale or errors out will blank the view.  Wrap them in this when the already-displayed data is
 * still worth showing - typically for read-only displays that refresh in the background.
 *
 * ```kotlin
 * val users = cache.list(query, maximumAge = 30.seconds).showPreviousOnLoadOrError()
 * ```
 *
 * Nothing is remembered until a success has actually been observed, so the first load still
 * reports loading and then any failure.
 */
public fun <T> Reactive<T>.showPreviousOnLoadOrError(): Reactive<T> {
    return object : Reactive<T> {
        private var lastSuccessfulState: ReactiveState<T>? = null
        override val state: ReactiveState<T>
            get() {
                val otherState = this@showPreviousOnLoadOrError.state
                return otherState.handle(
                    success = { lastSuccessfulState = otherState; otherState },
                    exception = { lastSuccessfulState ?: otherState },
                    notReady = { lastSuccessfulState ?: otherState },
                )
            }

        override fun addListener(listener: () -> Unit): Release = this@showPreviousOnLoadOrError.addListener(listener)
    }
}

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
@OptIn(ExperimentalAtomicApi::class)
public class InterruptibleDelay(public val parent: InterruptibleDelay? = null) {
    private val listenable = BasicListenable()
    private val num = AtomicInt(0)
    internal val pendingListenerCountForTesting: Int get() = listenable.listenerCount

    /**
     * Interrupts any active delays on this instance, causing them to complete immediately.
     * Does not affect delays on parent or child instances.
     */
    public fun interrupt() {
        num.incrementAndFetch()
        listenable.invokeAll()
    }

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
                val current = inter.num.load()
                suspend {
                    var closer: () -> Unit = {}
                    suspendCancellableCoroutine { cont ->
                        var resumed: Boolean = false
                        cont.invokeOnCancellation {
                            closer()
                        }
                        closer = inter.listenable.addListener {
                            if (!resumed) {
                                resumed = true
                                cont.resume(Unit)
                            }
                        }
                        if (inter.num.load() > current) {
                            if (!resumed) {
                                resumed = true
                                cont.resume(Unit)
                            }
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

