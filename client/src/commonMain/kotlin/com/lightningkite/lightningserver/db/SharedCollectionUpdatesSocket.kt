package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Console
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.lightningdb.CollectionUpdates
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.simplify
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.use
import com.lightningkite.reactive.lensing.lens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SharedCollectionUpdatesSocket<T : HasId<ID>, ID : Comparable<ID>>(
    val scope: CoroutineScope,
    val socket: TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>>,
    val onChange: (CollectionUpdates<T, ID>) -> Unit,
    val log: Console? = null,  // TODO: log this somewhere that I can access later
) {
    fun require(condition: Condition<T>) = Req(condition)

    /**
     * The conditions that we want the socket to inform us about.
     */
    val desiredRequirements = Signal<Set<Req>>(setOf()).also {
        it.addListener {
            log?.log("desiredRequirements is now ${it.value.joinToString { it.condition.toString() }}")
        }
    }
    inner class Req(override val condition: Condition<T>) : BaseResourceUse(), CacheUpdate.SocketChanges.ConditionAndTimestamp<T> {
        override var activatedAt: Instant? = null
        override fun activate() { desiredRequirements.value += this; activatedAt = now() }
        override fun deactivate() { desiredRequirements.value -= this; activatedAt = null }
        val satisfied = listeningStatus.lens { it.requirements.any { it.condition == condition } }
        suspend fun wait() = satisfied.waitFor { it }
    }

    /**
     * The conditions that the socket is currently listening for.
     */
    val listeningStatus = Signal<ListeningStatus<T>>(ListeningStatus()).also {
        it.addListener {
            log?.log("listeningStatus is now ${it.value.fullCondition} / ${it.value.requirements.joinToString { it.condition.toString() }}")
        }
    }

    inner class ListeningStatus<T>(
        val fullCondition: Condition<T> = Condition.Never,
        val requirements: Set<Req> = setOf()
    )

    init {
        // Keep the socket open as needed and fulfilling the desiredRequirements.
        var lastSent: ListeningStatus<T>? = null
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

            // Build the total condition
            val willSend = run {
                val r = desiredRequirements.value
                if (r === listeningStatus.value.requirements) {
                    log?.log("No need to update condition; already satisfied by exact requirements")
                    return
                }
                ListeningStatus(Condition.Or(r.map { it.condition }.distinct()).simplify(), r)
            }

            // Don't bother sending it if the net condition is equivalent.  Just mark it as fulfilled.
            if (listeningStatus.value.fullCondition == willSend.fullCondition) {
                listeningStatus.value = willSend
                log?.log("No need to update condition; already satisfied by condition match")
                return
            }
            lastSent = willSend
            log?.log("Sending condition ${willSend.fullCondition}")
            socket.send(willSend.fullCondition)

            // If we don't get the message in 5 seconds, we need to try again.
            scope.launch {
                // TODO: Is this retry logic actually working?
                delay(4.seconds)
                if (lastSent == willSend) {
                    log?.log("Update condition to ${lastSent.fullCondition} failed.")
                    lastSent = null
                    updateCondition()
                }
            }
        }
        // Whenever the socket re-opens, we have to send the condition we're interested again.
        socket.onOpen {
            log?.log("Opened.")
            updateCondition()
        }
        socket.onMessage {
            log?.log("Message: $it")
            onChange(it)

            // Check if we got an acknowledgement of a requested condition.
            val l = lastSent
            if (l != null && it.condition == l.fullCondition) {
                lastSent = null
                // Inform others that we're listening to this new set now.
                listeningStatus.value = l

                // Repeat if there are new differences
                updateCondition()
            }
        }

        // If the socket is closed, then we aren't listening for any requirement.
        // We need to inform others that we're no longer listening to a socket.
        socket.onClose {
            log?.log("Closed.")
            listeningStatus.value = ListeningStatus()
        }

        // We debounce this to stop it from requesting many times on page startup.
        val debouncedRequirements = desiredRequirements.debounce(scope, 100.milliseconds)
        scope.reactive {
            val requirements = debouncedRequirements()
            // Only keep the socket open if we have something to listen for.
            if (requirements.isEmpty()) {
                listeningStatus.value = ListeningStatus()
                return@reactive
            }
            use(socket)
            updateCondition()
        }
    }
}

