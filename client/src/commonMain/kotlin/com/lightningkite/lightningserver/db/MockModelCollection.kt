package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.*
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.LateInitSignal
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.extensions.value
import kotlinx.serialization.KSerializer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class MockModelCollection<T : HasId<ID>, ID : Comparable<ID>>(override val serializer: KSerializer<T>) : ModelCacheLike<T, ID> {
    val models = HashMap<ID, MockWritableModel>()

    fun populate(item: T) {
        val id = item._id
        models.getOrPut(id) { MockWritableModel(id) }.property.value = item
    }

    inner class MockWritableModel(val id: ID) : ModelCacheItemReadable<T> {
        override val lastUpdatedAt: Reactive<Instant?> = Constant(Clock.System.now())
        val property = LateInitSignal<T?>()
        val value: T? get() = property.state.let { if(it.success) it.raw else null }

        override suspend fun modify(modification: Modification<T>): T? {
            property.value = property.state.getOrNull()?.let { modification(it) }
            actionPerformed()
            return property.state.getOrNull()
        }

        override suspend fun delete() {
            property.unset()
            models.remove(id)
            actionPerformed()
        }

        override suspend fun invalidate() {
            actionPerformed()
        }

        override fun addListener(listener: () -> Unit): () -> Unit = property.addListener(listener)
        override val state: ReactiveState<T?>
            get() = property.state
        override suspend fun set(value: T?) {
            if(value == null) delete()
            else {
                property.set(value)
                actionPerformed()
            }
        }
    }

    private val listeners = ArrayList<() -> Unit>()
    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return {
            val pos = listeners.indexOfFirst { it === listener }
            if (pos != -1) {
                listeners.removeAt(pos)
            }
        }
    }

    fun actionPerformed() {
        listeners.toList().forEach { it() }
    }

    override fun item(id: ID, maximumAge: Duration, pullFrequency: Duration): ModelCacheItemReadable<T>  = models.getOrPut(id) { MockWritableModel(id) }
    override fun list(query: Query<T>, maximumAge: Duration, pullFrequency: Duration): ModelCacheLimitReadable<T> = object : ModelCacheLimitReadable<T> {
        override fun addListener(listener: () -> Unit): () -> Unit = this@MockModelCollection.addListener(listener)
        override var limit: Int = query.limit
        override val state: ReactiveState<List<T>>
            get() = ReactiveState(models.values.asSequence()
            .mapNotNull { if (it.property.state.ready) it.property.state.getOrNull() else null }
            .filter { query.condition(it) }
            .let {
                query.orderBy.comparator?.let { c ->
                    it.sortedWith(c)
                } ?: it.sortedBy { it._id }
            }
            .take(limit)
            .toList())
        override val lastUpdatedAt: Reactive<Instant?> = Constant(Clock.System.now())
    }

    override suspend fun add(item: T): T {
        return models.getOrPut(item._id) { MockWritableModel(item._id) }.also { it.property.value = item }.also {
            actionPerformed()
        }.value!!
    }

    override suspend fun addAll(items: List<T>): List<T> {
        return items.map { item ->
            models.getOrPut(item._id) { MockWritableModel(item._id) }.let { it.property.value = item; item }
        }.also { actionPerformed() }
    }

    override suspend fun upsert(item: T): ModelCacheItemReadable<T> {
        return models.getOrPut(item._id) { MockWritableModel(item._id) }.also { it.property.value = item }.also { actionPerformed() }
    }

    override suspend fun bulkModify(bulkUpdate: MassModification<T>): Int {
        var count = 0
        models.values.forEach {
            val v = it.value
            if (v != null && bulkUpdate.condition(v)) {
                it.modify(bulkUpdate.modification)
                count++
            }
        }
        actionPerformed()
        return count
    }
}