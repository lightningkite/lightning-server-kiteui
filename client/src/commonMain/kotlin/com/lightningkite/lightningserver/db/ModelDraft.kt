package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.modification
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.Draft
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import com.lightningkite.reactive.core.ReactiveWithMutableValue
import com.lightningkite.reactive.core.Release
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.withWrite
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Modification

public fun <T : HasId<ID>, ID : Comparable<ID>> Reactive<ModelReference<T, ID>>.draft(
    notFound: (ModelReference<T, ID>) -> T = {
        ref: ModelReference<*, *> -> throw NoSuchElementException("${ref.store.serializer.descriptor.serialName} ${ref.id} does not exist")
    },
): Draft<T> = ModelDraft(this, notFound)

// Claude: Holds only the user's edits, so live changes to other fields keep showing and publish sends just the edits.
internal class ModelDraft<T : HasId<ID>, ID : Comparable<ID>>(
    private val ref: Reactive<ModelReference<T, ID>>,
    private val notFound: (ModelReference<T, ID>) -> T,
) : Draft<T>, ReactiveWithMutableValue<T> {
    private val edits = Signal<Modification<T>?>(null)

    private val live: Reactive<T> = remember {
        val ref = ref()
        ref() ?: notFound(ref)
    }

    private val shown: Reactive<T> = remember {
        val live = live()
        edits()?.invoke(live) ?: live
    }

    override val state: ReactiveState<T> get() = shown.state
    override fun addListener(listener: () -> Unit): Release = shown.addListener(listener)

    override fun valueSet(value: T) {
        val ref = ref.state.getOrNull()
        val live = live.state.getOrNull()
        check(ref != null && live != null) { "A draft cannot be edited before its record has loaded" }
        edits.value = modification(ref.store.serializer, live, value)
    }

    override val published: MutableReactive<T> = live.withWrite { ref.awaitOnce().set(it) }

    override val changesMade: Reactive<Boolean> = remember { edits() != null }

    override suspend fun publish(): T {
        val ref = ref.awaitOnce()
        // Claude: A missing record is shown via notFound, so publishing creates it.
        if (ref.awaitOnce() == null) ref.upsert(awaitOnce())
        else edits.value?.let { ref.modify(it) }
        edits.value = null
        return awaitOnce()
    }

    override fun cancel() {
        edits.value = null
    }
}
