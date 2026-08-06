package com.lightningkite.lskiteuistarter.pressure

import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.ClientModelRestEndpointsAndUpdatesWebsocket
import com.lightningkite.lightningserver.typed.ClientWebSocket
import com.lightningkite.services.database.CollectionUpdates
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.Query

/**
 * Counts and optionally sabotages what a [com.lightningkite.lightningserver.db.ModelCache] sends to
 * the server.
 *
 * Only the operations the cache actually reaches for are overridden; everything else is delegated
 * untouched, so this stays a thin observer rather than a second implementation to keep in step.
 */
class InstrumentedRest<T : HasId<ID>, ID : Comparable<ID>>(
    private val wraps: ClientModelRestEndpoints<T, ID>,
    private val instruments: Instruments,
    private val label: String,
) : ClientModelRestEndpoints<T, ID> by wraps {

    override suspend fun query(input: Query<T>): List<T> {
        instruments.gate("$label.query", describe(input))
        return wraps.query(input)
    }

    override suspend fun detail(id: ID): T {
        instruments.gate("$label.detail", id.toString())
        return wraps.detail(id)
    }

    override suspend fun insert(input: T): T {
        instruments.gate("$label.insert")
        return wraps.insert(input)
    }

    override suspend fun insertBulk(input: List<T>): List<T> {
        instruments.gate("$label.insertBulk", "${input.size} items")
        return wraps.insertBulk(input)
    }

    override suspend fun modify(id: ID, input: Modification<T>): T {
        instruments.gate("$label.modify", id.toString())
        return wraps.modify(id, input)
    }

    override suspend fun delete(id: ID) {
        instruments.gate("$label.delete", id.toString())
        return wraps.delete(id)
    }

    override suspend fun bulkModify(input: MassModification<T>): Int {
        instruments.gate("$label.bulkModify", input.condition.toString().take(80))
        return wraps.bulkModify(input)
    }

    override suspend fun upsert(id: ID, input: T): T {
        instruments.gate("$label.upsert", id.toString())
        return wraps.upsert(id, input)
    }

    private fun describe(input: Query<T>): String =
        "${input.condition.toString().take(70)} limit=${input.limit}"
}

/**
 * The same, for a collection that also has an update socket.
 *
 * The REST half is delegated to an [InstrumentedRest] and the socket half straight through, because
 * the cache decides whether to open a socket at all by testing whether its endpoints implement
 * [ClientModelRestUpdatesWebsocket] - a wrapper that dropped the interface would silently turn the
 * real-time path off and look like a cache that just polls well.
 */
class InstrumentedRestWithUpdates<T : HasId<ID>, ID : Comparable<ID>>(
    private val wraps: ClientModelRestEndpointsAndUpdatesWebsocket<T, ID>,
    private val instruments: Instruments,
    label: String,
) : ClientModelRestEndpointsAndUpdatesWebsocket<T, ID>,
    ClientModelRestEndpoints<T, ID> by InstrumentedRest(wraps, instruments, label) {

    override fun updates(): ClientWebSocket<Condition<T>, CollectionUpdates<T, ID>> =
        CuttableWebSocket(wraps.updates(), instruments)
}

/**
 * A socket that can be cut and held down.
 *
 * Losing the update socket is the most dangerous thing that can happen to this cache: it goes on
 * believing what it last heard, and everything that changes while it is down is change it never saw.
 * Recovering correctly means noticing the gap and refetching, not quietly serving what it had - and
 * the only way to check that is to be able to cut the wire on demand.
 */
private class CuttableWebSocket<SEND, RECEIVE>(
    private val wraps: ClientWebSocket<SEND, RECEIVE>,
    private val instruments: Instruments,
) : ClientWebSocket<SEND, RECEIVE> by wraps {

    override fun connect() {
        // Refusing to connect, rather than connecting and closing, is what keeps the socket down
        // across the reconnect attempts the cache makes on its own.
        if (instruments.socketCut.value) {
            instruments.note("socket.connect refused (cut)")
            return
        }
        instruments.note("socket.connect")
        wraps.connect()
    }

    init {
        instruments.onSocketCut { cut ->
            if (cut) {
                instruments.note("socket.cut")
                wraps.close(1000, "pressure test")
            } else {
                instruments.note("socket.restored")
                wraps.connect()
            }
        }
    }
}
