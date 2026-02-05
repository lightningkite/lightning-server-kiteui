// by Claude
package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.l2.Recycler2
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.services.database.HasId

/**
 * Overload of [children] for double-wrapped reactive lists (`Reactive<Reactive<List<T>>>`).
 * Handles unwrapping both layers and auto-pagination for [LimitReactiveList].
 */
// by Claude
fun <T, ID> Recycler2.children(
    items: Reactive<Reactive<List<T>>>,
    id: (T) -> ID,
    render: ViewWriter.(value: Reactive<T>) -> Unit
) {
    reactive {
        val inner = items()
        if (inner is LimitReactiveList<T>) {
            if (inner.limit < lastIndex() + 50) {
                inner.limit = lastIndex() + 100
            }
        }
    }
    children(items = remember { items()() }, id = id, render = render)
}

/**
 * [HasId] overload — `id` defaults to `{ it._id }`.
 */
// by Claude
fun <T : HasId<ID>, ID : Comparable<ID>> Recycler2.children(
    items: Reactive<Reactive<List<T>>>,
    render: ViewWriter.(value: Reactive<T>) -> Unit
) {
    children(items = items, id = { it._id }, render = render)
}
