package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Console
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.comparator
import com.lightningkite.now
import com.lightningkite.readable.BasicListenable
import com.lightningkite.readable.Listenable
import kotlinx.serialization.KSerializer

class NaiveListReconstructionCalculator<T : HasId<ID>, ID : Comparable<ID>>(
    val serializer: KSerializer<T>,
    val log: Console? = null,
) : ListReconstructionCalculator<T, ID> {
    val byQuery = HashMap<Query<T>, WithTimestampAndLimit<List<T>>>()
    val all = BasicListenable()
    override fun updates(query: Query<T>): Listenable = all
    override fun update(update: CacheUpdate<T, ID>): Unit {
        log?.log("Handling update $update")
        when (update) {
            is CacheUpdate.DeletionResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, emptyList(), update.deletedIds)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.MutationResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, update.items)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.QueryResult -> byQuery[update.query] = WithTimestampAndLimit(update.result, requestedLimit = update.query.limit)
            is CacheUpdate.SocketChanges -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    val completeUpdate = update.fromRequirements.any {
                        it.condition == entry.key.condition && it.activatedAt?.let { old.at > it } == true
                    }
                    if (completeUpdate) {
                        val new = old.item.update(entry.key, update.changed, update.removed)
                        entry.setValue(old.copy(item = new, at = now()))
                    } else {
                        // If we can't guarantee this was a full knowledge update, we can't update the timestamp.
                        val new = old.item.update(entry.key, update.changed)
                        entry.setValue(old.copy(item = new))
                    }
                }
            }

            is CacheUpdate.SocketOverload -> byQuery.clear()
        }
        all.invokeAll()
    }

    override fun cached(query: Query<T>): WithTimestampAndLimit<List<T>>? = byQuery[query]
    override fun recommendQuery(query: Query<T>): Query<T> = query
    override fun clear() {
        byQuery.clear()
    }

    fun List<T>.update(query: Query<T>, edits: Collection<T>, removed: Collection<ID> = emptyList()): List<T> {
        //TODO: please make a better implementation
        // This has so many issues.  It doesn't account for totality at the moment, so it stupidly just adds the element
        // to the back regardless of if there should be items between.
        val rem = removed + edits.map { it._id }.toSet()
        return this
            .asSequence()
            .filter { it._id !in rem }
            .plus(edits)
            .filter { query.condition(it) }
            .distinctBy { it._id }
            .sortedWith(query.orderBy.ensureTotal(serializer).comparator!!)
            .toList()
    }
}