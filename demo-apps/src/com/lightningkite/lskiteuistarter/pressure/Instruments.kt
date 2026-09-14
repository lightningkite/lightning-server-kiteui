package com.lightningkite.lskiteuistarter.pressure

import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What the cache actually did, as opposed to what the screen looks like.
 *
 * A cache bug usually looks fine: the list is plausible, the names are right.  What gives it away is
 * the request count - a read that should have been free but wasn't, or a refetch that should have
 * happened and didn't.  So every call the cache makes to the server passes through here first.
 */
class Instruments {
    data class Call(val op: String, val detail: String, val at: Instant)

    /** Most recent calls, newest last.  Bounded so a long soak doesn't grow without limit. */
    val calls: Signal<List<Call>> = Signal(emptyList())
    val counters: Signal<Map<String, Int>> = Signal(emptyMap())

    /** Milliseconds added to every HTTP call, for seeing what the cache does while a fetch is in flight. */
    val latencyMs: Signal<Int> = Signal(0)

    /** Makes every HTTP call fail.  The update socket is left alone; this is an HTTP-only failure. */
    val offline: Signal<Boolean> = Signal(false)

    /**
     * Holds the update socket down.  HTTP is left alone, so this is the "real-time channel is gone
     * but the server is still reachable" case - the one where a cache can quietly go stale.
     */
    val socketCut: Signal<Boolean> = Signal(false)

    private val socketCutListeners = mutableListOf<(Boolean) -> Unit>()

    /** Registers a socket to be cut and restored along with [socketCut]. */
    fun onSocketCut(action: (Boolean) -> Unit) {
        socketCutListeners += action
    }

    fun cutSocket(cut: Boolean) {
        if (socketCut.value == cut) return
        socketCut.value = cut
        for (listener in socketCutListeners) listener(cut)
    }

    /** Records something that happened without it counting as a request. */
    fun note(what: String) {
        calls.value = (calls.value + Call(what, "", Clock.System.now())).takeLast(200)
    }

    /**
     * Records a call and applies whatever failure is currently configured.
     *
     * Called before the real request, so the count reflects intent even when the call then fails.
     */
    suspend fun gate(op: String, detail: String = "") {
        val call = Call(op, detail, Clock.System.now())
        calls.value = (calls.value + call).takeLast(200)
        counters.value = counters.value + (op to (counters.value[op] ?: 0) + 1)
        latencyMs.value.takeIf { it > 0 }?.let { delay(it.toLong()) }
        if (offline.value) throw OfflineException(op)
    }

    /** Zeroes the counters, so a scenario can be measured from a clean start. */
    fun reset() {
        calls.value = emptyList()
        counters.value = emptyMap()
    }

    fun count(op: String): Int = counters.value[op] ?: 0

    /** Every request made since the last [reset]. */
    fun total(): Int = counters.value.values.sum()
}

/** Thrown in place of a real request while [Instruments.offline] is set. */
class OfflineException(op: String) : Exception("Simulated offline: $op was not sent")
