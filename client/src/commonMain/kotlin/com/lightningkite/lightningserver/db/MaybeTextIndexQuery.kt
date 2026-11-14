package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.reactiveState
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.ScoredResult
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database.TextIndexQuery
import kotlin.time.Duration

public sealed interface MaybeTextIndexQuery<T> {
    public data class Search<T>(val query: TextIndexQuery<T>): MaybeTextIndexQuery<T>
    public data class Filter<T>(val query: Query<T>): MaybeTextIndexQuery<T>
}

/**
 * Makes a [TextIndexQuery] if [query] is not `null`. If `query = null` then a regular [Query] is created.
 *
 * **Note:** [orderBy] is only used when `query = null`, text index queries return entries by relevance score to the specified search string.
 *
 * See [TextIndexQuery] for query formatting specifications.
 *
 * @see TextIndexQuery
 * @see Query
 * */
public fun <T> MaybeTextIndexQuery(
    query: String?,
    condition: Condition<T>,
    orderBy: List<SortPart<T>> = emptyList(),
    skip: Int = 0,
    limit: Int = 50,
): MaybeTextIndexQuery<T> =
    if (query == null) MaybeTextIndexQuery.Filter(Query(condition, orderBy, skip, limit))
    else MaybeTextIndexQuery.Search(TextIndexQuery(query, condition, skip, limit))

/**
 * Performs a regular query or text-index query based on the provided [query].
 *
 * - When [query] is a regular [Query] this is equivalent to [list]. The result is ordered based on the [Query.orderBy] parameter and is a [LimitReactiveList].
 * - When [query] is a [TextIndexQuery] the cache is skipped to perform a [textQuery][com.lightningkite.lightningserver.typed.ClientModelRestEndpoints.textQuery].
 *   The result is ordered based on relevance score to the search string and is *not* a [LimitReactiveList]
 * */
public fun <T : HasId<ID>, ID : Comparable<ID>> ModelCache<T, ID>.listOrTextSearch(
    query: MaybeTextIndexQuery<T>,
    maximumAge: Duration,
    pullFrequency: Duration,
): Reactive<List<T>> {
    return when (query) {
        is MaybeTextIndexQuery.Filter<T> -> list(query.query, maximumAge, pullFrequency)
        is MaybeTextIndexQuery.Search<T> -> rememberSuspending { skipCache.textQuery(query.query).map(ScoredResult<T>::model) }
    }
}