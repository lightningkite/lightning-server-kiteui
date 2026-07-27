package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.services.database.CollectionUpdates
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.simplify
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.debounce
import com.lightningkite.reactive.extensions.use
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.walk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages a shared WebSocket connection for real-time collection updates with multi-consumer support.
 *
 * This class allows multiple consumers (e.g., different queries in [ModelCache]) to share a single
 * WebSocket connection. It:
 * - Combines multiple required conditions into a single OR condition
 * - Sends the combined condition to the server
 * - Distributes incoming updates to all consumers via [onChange]
 * - Handles reconnection and condition resubscription automatically
 * - Tracks which conditions are actively being monitored with timestamps
 *
 * ## Protocol
 * The WebSocket protocol is:
 * - **Client -> Server**: Send `Condition<T>` to subscribe
 * - **Server -> Client**: Send `CollectionUpdates<T, ID>` with changes
 * - **Acknowledgement**: Updates include the condition being monitored for verification
 *
 * ## Multi-Consumer Flow
 * 1. Consumer A calls `require(condition1)` - gets a [Req] handle
 * 2. Consumer B calls `require(condition2)` - gets another [Req] handle
 * 3. Socket combines: `Condition.Or(condition1, condition2)` and sends to server
 * 4. Server sends updates matching the combined condition
 * 5. Both consumers receive updates via [onChange]
 * 6. When Consumer A's handle is removed, socket sends only `condition2`
 *
 * ## Lifecycle Management
 * - Socket is kept open only while there are active requirements ([desiredRequirements] non-empty)
 * - When all requirements are removed, socket closes automatically
 * - On reconnection, all active conditions are resubscribed
 * - Requirements are debounced (100ms) to avoid spam during startup
 *
 * ## Timestamp Tracking
 * Each requirement tracks [Req.activatedAt] timestamp, which is critical for [ModelCache]
 * to determine if cached data is "live".
 *
 * ## Error Handling
 * - If acknowledgement isn't received within 4 seconds, the condition is resent
 * - If conditions don't match (server bug), detailed logging is emitted
 * - Socket closure clears all listening status, requiring refetch
 *
 * @param T The model type being monitored
 * @param ID The ID type
 * @param scope Coroutine scope for socket lifecycle and debouncing
 * @param socket The underlying typed WebSocket connection
 * @param onChange Callback invoked for every update received (shared by all consumers)
 * @param log Optional console for debugging socket operations
 */
public class SharedCollectionUpdatesSocket<T : HasId<ID>, ID : Comparable<ID>>(
    public val scope: CoroutineScope,
    public val socket: TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>>,
    public val onChange: (CollectionUpdates<T, ID>) -> Unit,
    public val log: Log? = null,  // TODO: log this somewhere that I can access later
) {
    /**
     * Creates a requirement for monitoring a specific condition.
     *
     * The returned [Req] is a [BaseResourceUse], meaning it's automatically activated when
     * used in a reactive context and deactivated when no longer needed.
     *
     * @param condition The condition to monitor (e.g., `item.active eq true`)
     * @return A resource handle that adds/removes the condition from the socket
     */
    public fun require(condition: Condition<T>): SharedCollectionUpdatesSocket<T, ID>.Req = Req(condition)

    /**
     * Signal holding the set of conditions that consumers currently want monitored.
     *
     * This is the "desired state" - what we want the socket to be listening to.
     * Updated when [Req] handles are activated/deactivated.
     *
     * The actual socket subscription state is tracked in [listeningStatus] and may lag behind
     * due to network delays or socket closure.
     */
    public val desiredRequirements: Signal<Set<SharedCollectionUpdatesSocket<T, ID>.Req>> = Signal<Set<Req>>(setOf()).also {
        it.addListener {
            log?.log("desiredRequirements is now ${it.value.joinToString { it.condition.toString() }}")
        }
    }

    /**
     * A requirement handle for monitoring a specific condition.
     *
     * This is a [BaseResourceUse], so it integrates with the reactive system:
     * - Automatically activates when `use(req)` is called in a reactive context
     * - Automatically deactivates when the reactive context ends
     *
     * ## Lifecycle
     * - **activate()**: Adds this requirement to [desiredRequirements], records [activatedAt] timestamp
     * - **deactivate()**: Removes from [desiredRequirements], clears [activatedAt]
     *
     * ## Satisfaction Checking
     * The [satisfied] property allows waiting for the socket to acknowledge this condition:
     * ```kotlin
     * val req = socket.require(myCondition)
     * req.wait() // suspends until socket confirms it's listening to myCondition
     * ```
     *
     * This is used in [ModelCache] to wait for socket connection before initial fetch,
     * avoiding the race where we fetch, then socket connects and refetches.
     *
     * @property condition The condition to monitor
     * @property activatedAt Timestamp when this requirement was activated (null if inactive)
     * @property satisfied Reactive boolean indicating if socket is currently listening to this condition
     */
    public inner class Req(public val condition: Condition<T>) : BaseResourceUse() {
        /** When this requirement was activated, or null while inactive. */
        public var activatedAt: Instant? = null
            private set

        override fun activate() {
            desiredRequirements.value += this
            activatedAt = scope.now()
        }

        override fun deactivate() {
            desiredRequirements.value -= this
            activatedAt = null
        }

        /** Reactive boolean: true when socket is confirmed to be listening to this exact condition */
        public val satisfied: ReactiveValue<Boolean> = listeningStatus.lens { it.requirements.any { it.condition == condition } }

        /** Suspends until [satisfied] becomes true (socket acknowledges this condition) */
        public suspend fun wait(): Unit = satisfied.waitFor { it }
    }

    /**
     * Signal holding the current socket listening state.
     *
     * This is the "actual state" - what the socket has confirmed it's listening to.
     * Updated when:
     * - Socket sends an acknowledgement matching a sent condition
     * - Socket closes (resets to empty)
     *
     * May lag behind [desiredRequirements] while waiting for network roundtrip.
     */
    public val listeningStatus: Signal<SharedCollectionUpdatesSocket<T, ID>.ListeningStatus<T>> = Signal<ListeningStatus<T>>(ListeningStatus()).also {
        it.addListener {
            log?.log("listeningStatus is now ${it.value.fullCondition} / ${it.value.requirements.joinToString { it.condition.toString() }}")
        }
    }

    /**
     * Snapshot of what the socket is currently listening to.
     *
     * @property fullCondition The combined OR of all [requirements] conditions (simplified)
     * @property requirements The individual requirements that are satisfied
     */
    public inner class ListeningStatus<T>(
        public val fullCondition: Condition<T> = Condition.Never,
        public val requirements: Set<Req> = setOf()
    )

    init {
        /**
         * Main socket management logic.
         *
         * Handles:
         * - Combining [desiredRequirements] into a single condition
         * - Sending conditions to the socket
         * - Waiting for acknowledgements
         * - Retrying on timeout
         * - Reopening socket when requirements change
         */

        /**
         * Tracks the last condition we sent to the socket while waiting for acknowledgement.
         * Null means we're not waiting for any acknowledgement.
         *
         * Used to prevent sending multiple updates simultaneously and to match incoming
         * acknowledgements with sent conditions.
         */
        var lastSent: ListeningStatus<T>? = null

        /**
         * Attempts to update the socket's listening condition to match [desiredRequirements].
         *
         * This function:
         * 1. Checks if socket is connected (can't send if closed)
         * 2. Checks if we're waiting for acknowledgement (can't send while pending)
         * 3. Combines all [desiredRequirements] into a single OR condition
         * 4. Optimizes: skips send if condition is equivalent to current
         * 5. Sends the condition to the socket
         * 6. Starts a 4-second timeout for acknowledgement retry
         *
         * Called when:
         * - Socket opens/reopens
         * - [desiredRequirements] changes (debounced)
         * - Acknowledgement is received (to send any queued changes)
         * - Timeout occurs (retry)
         */
        fun updateCondition() {
            // We can't send the updated condition to a closed socket.
            if (socket.connected.state.getOrNull() != true) {
                log?.log("Can't update condition, socket is closed")
                return
            }
            // If we're waiting for an acknowledge of a past request, let's just wait.
            if (lastSent != null) {
                log?.log("Can't update condition, waiting on tentative send")
                return
            }

            // Build the total condition from all desired requirements
            val willSend = run {
                val r = desiredRequirements.value
                // Optimization: if requirements haven't changed by reference, skip
                if (r === listeningStatus.value.requirements) {
                    log?.log("No need to update condition; already satisfied by exact requirements")
                    return
                }
                // Combine all conditions with OR, remove duplicates, simplify
                ListeningStatus(Condition.Or(r.map { it.condition }.distinct()).simplify(), r)
            }

            // Optimization: if the combined condition is semantically equivalent, just update requirements
            // This handles cases where requirements changed but the OR'd condition is the same
            if (listeningStatus.value.fullCondition == willSend.fullCondition) {
                listeningStatus.value = willSend
                log?.log("No need to update condition; already satisfied by condition match")
                return
            }

            // Send the new condition to the server
            lastSent = willSend
            log?.log("Sending condition ${willSend.fullCondition}")
            socket.send(willSend.fullCondition)

            // Start acknowledgement timeout - retry after 4 seconds if no response
            scope.launch {
                delay(4.seconds)
                // Check if we're still waiting for this specific send
                if (lastSent == willSend) {
                    log?.log("Update condition to ${lastSent?.fullCondition} failed (timeout).")
                    lastSent = null
                    updateCondition() // Retry
                }
            }
        }

        // Socket event handlers

        // On open/reopen: resend current desired condition
        socket.onOpen {
            log?.log("Opened.")
            updateCondition()
        }

        // On message: distribute update and check for acknowledgement
        socket.onMessage {
            log?.log("Message: $it")
            // Always call onChange regardless of acknowledgement status
            onChange(it)

            // Check if this message is an acknowledgement of our sent condition
            val l = lastSent
            if (l != null && it.condition == l.fullCondition) {
                // Acknowledgement received!
                lastSent = null
                // Update listening status to reflect what we now know socket is monitoring
                listeningStatus.value = l

                // Check if requirements changed while we were waiting - send update if needed
                updateCondition()
            } else if(l != null && it.condition != null) {
                // We sent a condition and got a different one back - this might be a bug
                // Log detailed structure for debugging
                log?.log("Condition does not match, though it probably should. (${it.condition} == ${l.fullCondition}) -eval-> (${it.condition == l.fullCondition})")
                log?.log("--- Received condition structure:")
                it.condition!!.walk { log?.log("${it::class} ($it)") }
                log?.log("--- Expected condition structure:")
                l.fullCondition.walk { log?.log("${it::class} ($it)") }
                log?.log("---")
            }
        }

        // On close: clear all listening status
        // This forces refetches since we no longer have real-time updates
        socket.onClose {
            log?.log("Closed.")
            listeningStatus.value = ListeningStatus()
        }

        // Main reactive loop: manage socket lifecycle based on requirements
        // Debounce to avoid rapid connect/disconnect during startup when many requirements are added
        val debouncedRequirements = desiredRequirements.debounce(100.milliseconds, scope)
        scope.reactive {
            val requirements = debouncedRequirements()
            // Only keep socket open if we have active requirements
            if (requirements.isEmpty()) {
                listeningStatus.value = ListeningStatus()
                return@reactive
            }
            // Keep socket open and update condition to match requirements
            use(socket)
            updateCondition()
        }
    }
}

