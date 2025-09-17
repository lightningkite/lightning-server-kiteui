package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.lightningserver.networking.toClientWebSocket
import com.lightningkite.lightningserver.typed.ClientModelRestEndpointsAndUpdatesWebsocket
import com.lightningkite.lightningserver.typed.ClientWebSocket
import com.lightningkite.services.database.CollectionUpdates
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.EntryChange
import com.lightningkite.services.database.HasId
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.invokeAllSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

open class ClientModelRestEndpointsPlusUpdatesWebsocketMock<T : HasId<ID>, ID : Comparable<ID>>(
    scope: CoroutineScope,
    delayAmount: Duration = 0.1.seconds,
) :
    ClientModelRestEndpointsMock<T, ID>(scope, delayAmount), ClientModelRestEndpointsAndUpdatesWebsocket<T, ID> {

    override var connectivityFailure: Boolean
        get() = super.connectivityFailure
        set(value) {
            super.connectivityFailure = value
            if(value)
                updatesWs.onCloseList.forEach { it.invoke(1000) }
            else
                updatesWs.onOpenList.forEach { it.invoke() }
        }

    val entryChanges = Signal<List<EntryChange<T>>>(listOf())
    override fun change(collectionUpdates: CollectionUpdates<T, ID>) {
        val before = collectionUpdates.updates.associate { it._id to data[it._id] } + collectionUpdates.remove.associate { it to data[it] }
        super.change(collectionUpdates)
        val after = collectionUpdates.updates.associate { it._id to data[it._id] } + collectionUpdates.remove.associate { it to data[it] }
        val changes = (after.keys + before.keys).map {
            EntryChange(before[it], after[it])
        }
        log?.log("Sending entry changes ${changes}")
        entryChanges.value = changes
    }

    inner class UpdatesWs : ClientWebSocket<Condition<T>, CollectionUpdates<T, ID>> {
        var filter: Condition<T> = Condition.Never

        override val connected = MutableStateFlow(true)

        val listeners = ArrayList<(CollectionUpdates<T, ID>)->Unit>()

        override fun send(data: Condition<T>) {
            filter = data
            listeners.forEach { it(CollectionUpdates(condition = data)) }
        }

        val onOpenList = ArrayList<()->Unit>()
        override fun onOpen(action: () -> Unit) {
            onOpenList.add(action)
        }

        override fun onMessage(action: (CollectionUpdates<T, ID>) -> Unit) {
            listeners.add(action)
        }

        val onCloseList = ArrayList<(Short)->Unit>()
        override fun onClose(action: (Short) -> Unit) {
            onCloseList.add(action)
        }

        private var myListen: (()->Unit)? = null

        override fun connect() {
            myListen = entryChanges.addListener {
                if(connectivityFailure) return@addListener
                log?.log("Entry changes ${entryChanges.value}")

                val v = entryChanges.value.map { EntryChange(it.old?.takeIf { filter(it) }, it.new?.takeIf { filter(it) }) }
                val u = CollectionUpdates(
                    updates = v.mapNotNull { it.new }.toSet(),
                    remove = v.filter { it.new == null }.mapNotNull { it.old?._id }.toSet()
                )
                listeners.forEach { it(u) }
            }
            log?.log("Listening started")
            scope.launch {
                delay(10.milliseconds)
                if(connectivityFailure) {
//                        onOpenList.invokeAllSafe()
                    onCloseList.forEach { it.invoke(1000) }
                } else {
                    onOpenList.invokeAllSafe()
                }
            }
        }

        override fun close(code: Short, reason: String) {
            log?.log("Listening done")
            entryChanges.value = emptyList()
            filter = Condition.Never
            myListen?.invoke()
            myListen = null
            scope.launch {
                delay(10.milliseconds)
                onCloseList.forEach { it.invoke(1000) }
            }
        }
    }

    val updatesWs = UpdatesWs()
    override fun updates(): UpdatesWs = updatesWs
}