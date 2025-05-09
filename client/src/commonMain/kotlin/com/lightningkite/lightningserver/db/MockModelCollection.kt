package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.*
import com.lightningkite.readable.Constant
import com.lightningkite.readable.LateInitProperty
import com.lightningkite.readable.Readable
import com.lightningkite.readable.ReadableState
import kotlinx.serialization.KSerializer
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlin.time.Duration

class MockModelCollection<T : HasId<ID>, ID : Comparable<ID>>(val serializer: KSerializer<T>) : ModelCacheLike<T, ID> {
    val models = HashMap<ID, MockWritableModel>()

    fun populate(item: T) {
        val id = item._id
        models.getOrPut(id) { MockWritableModel(id) }.property.value = item
    }

    inner class MockWritableModel(val id: ID) : ModelCacheItemReadable<T> {
        override val lastUpdatedAt: Readable<Instant?> = Constant(now())
        val property = LateInitProperty<T?>()
        val value: T? get() = property.state.let { if(it.success) it.raw else null }

        override suspend fun modify(modification: Modification<T>): T? {
            property.value = property.value?.let { modification(it) }
            actionPerformed()
            return property.value
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
        override val state: ReadableState<T?>
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
        override val state: ReadableState<List<T>>
            get() = ReadableState(models.values.asSequence()
            .mapNotNull { if (it.property.state.ready) it.property.value else null }
            .filter { query.condition(it) }
            .let {
                query.orderBy.comparator?.let { c ->
                    it.sortedWith(c)
                } ?: it.sortedBy { it._id }
            }
            .take(limit)
            .toList())
        override val lastUpdatedAt: Readable<Instant?> = Constant(now())
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