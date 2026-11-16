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

public fun <T : HasId<ID>?, ID : Comparable<ID>> Reactive<ModelStore.Reference<T & Any, ID>>.draftOrElse(
    notFound: (ModelStore.Reference<T & Any, ID>) -> T,
): Draft<T> = ModelDraft(this, notFound)

public fun <T : HasId<ID>, ID : Comparable<ID>> Reactive<ModelStore.Reference<T, ID>>.draftOrNull(): Draft<T?> =
    draftOrElse { null }

public fun <T : HasId<ID>, ID : Comparable<ID>> Reactive<ModelStore.Reference<T, ID>>.draftOrThrow(): Draft<T> =
    draftOrElse { throw NoSuchElementException("${it.store.serializer.descriptor.serialName} ${it.id} does not exist") }

// Claude: Holds only the user's edits, so live changes to other fields keep showing and publish sends just the edits.
internal class ModelDraft<T : HasId<ID>?, ID : Comparable<ID>>(
    private val ref: Reactive<ModelStore.Reference<T & Any, ID>>,
    private val notFound: (ModelStore.Reference<T & Any, ID>) -> T,
) : Draft<T>, ReactiveWithMutableValue<T> {
    private val edits = Signal<Modification<T & Any>?>(null)

    private val live: Reactive<T> = remember {
        val ref = ref()
        ref() ?: notFound(ref)
    }

    private val shown: Reactive<T> = remember {
        val live = live()
        val edits = edits()
        if (live != null && edits != null) edits(live) else live
    }

    override val state: ReactiveState<T> get() = shown.state
    override fun addListener(listener: () -> Unit): Release = shown.addListener(listener)

    override fun valueSet(value: T) {
        val ref = ref.state.getOrNull()
        val live = live.state.getOrNull()
        check(ref != null && live != null && value != null) { "A draft can only be edited while it holds a record" }
        edits.value = modification(ref.store.serializer, live, value)
    }

    override val published: MutableReactive<T> = live.withWrite { ref.awaitOnce().set(it) }

    override val changesMade: Reactive<Boolean> = remember { edits() != null }

    override suspend fun publish(): T {
        val ref = ref.awaitOnce()
        // Claude: A missing record is shown via notFound, so publishing creates it.
        if (ref.awaitOnce() == null) awaitOnce()?.let { ref.upsert(it) }
        else edits.value?.let { ref.modify(it) }
        edits.value = null
        return awaitOnce()
    }

    override fun cancel() {
        edits.value = null
    }
}
