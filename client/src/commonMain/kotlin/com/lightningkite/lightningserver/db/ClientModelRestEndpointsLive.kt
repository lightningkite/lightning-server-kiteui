package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.lightningdb.AggregateQuery
import com.lightningkite.lightningdb.CollectionUpdates
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.EntryChange
import com.lightningkite.lightningdb.GroupAggregateQuery
import com.lightningkite.lightningdb.GroupCountQuery
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.ListChange
import com.lightningkite.lightningdb.MassModification
import com.lightningkite.lightningdb.ModelPermissions
import com.lightningkite.lightningdb.Modification
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.QueryPartial
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.serialization.Partial
import com.lightningkite.serialization.PartialSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

open class ClientModelRestEndpointsLive<T : HasId<ID>, ID : Comparable<ID>>(
    val fetcher: Fetcher,
    val subpath: String,
    val serializer: KSerializer<T>,
    val idSerializer: KSerializer<ID>,
): ClientModelRestEndpoints<T, ID> {

    override suspend fun default(): T = fetcher(
        "$subpath/_default_",
        HttpMethod.GET,
        Unit.serializer(),
        Unit,
        serializer
    )

    override suspend fun permissions(): ModelPermissions<T> = fetcher(
        "$subpath/_permissions_",
        HttpMethod.GET,
        Unit.serializer(),
        Unit,
        ModelPermissions.Companion.serializer(serializer)
    )

    override suspend fun query(input: Query<T>): List<T> = fetcher(
        "$subpath/query",
        HttpMethod.POST,
        Query.Companion.serializer(serializer),
        input,
        ListSerializer(serializer)
    )

    override suspend fun queryPartial(input: QueryPartial<T>): List<Partial<T>> = fetcher(
        "$subpath/query-partial",
        HttpMethod.POST,
        QueryPartial.Companion.serializer(serializer),
        input,
        ListSerializer(PartialSerializer(serializer))
    )

    override suspend fun detail(id: ID): T = fetcher(
        "$subpath/${id.urlify()}",
        HttpMethod.GET,
        Unit.serializer(),
        Unit,
        serializer
    )

    override suspend fun insertBulk(input: List<T>): List<T> = fetcher(
        "$subpath/bulk",
        HttpMethod.POST,
        ListSerializer(serializer),
        input,
        ListSerializer(serializer)
    )

    override suspend fun insert(input: T): T = fetcher(
        "$subpath",
        HttpMethod.POST,
        serializer,
        input,
        serializer
    )

    override suspend fun upsert(id: ID, input: T): T = fetcher(
        "$subpath/${id.urlify()}",
        HttpMethod.POST,
        serializer,
        input,
        serializer
    )

    override suspend fun bulkReplace(input: List<T>): List<T> = fetcher(
        "$subpath",
        HttpMethod.PUT,
        ListSerializer(serializer),
        input,
        ListSerializer(serializer)
    )

    override suspend fun replace(id: ID, input: T): T = fetcher(
        "$subpath/${id.urlify()}",
        HttpMethod.PUT,
        serializer,
        input,
        serializer
    )

    override suspend fun bulkModify(input: MassModification<T>): Int = fetcher(
        "$subpath/bulk",
        HttpMethod.PATCH,
        MassModification.Companion.serializer(serializer),
        input,
        Int.serializer()
    )

    override suspend fun modifyWithDiff(id: ID, input: Modification<T>): EntryChange<T> = fetcher(
        "$subpath/${id.urlify()}/delta",
        HttpMethod.PATCH,
        Modification.Companion.serializer(serializer),
        input,
        EntryChange.Companion.serializer(serializer)
    )

    override suspend fun modify(id: ID, input: Modification<T>): T {
        return fetcher(
            "$subpath/${id.urlify()}",
            HttpMethod.PATCH,
            Modification.Companion.serializer(serializer),
            input,
            serializer
        )
    }

    override suspend fun bulkDelete(input: Condition<T>): Int = fetcher(
        "$subpath/bulk-delete",
        HttpMethod.POST,
        Condition.Companion.serializer(serializer),
        input,
        Int.serializer()
    )

    override suspend fun delete(id: ID): Unit = fetcher(
        "$subpath/${id.urlify()}",
        HttpMethod.DELETE,
        Unit.serializer(),
        Unit,
        Unit.serializer(),
    )

    override suspend fun count(input: Condition<T>): Int = fetcher(
        "$subpath/count",
        HttpMethod.POST,
        Condition.Companion.serializer(serializer),
        input,
        Int.serializer()
    )

    override suspend fun groupCount(input: GroupCountQuery<T>): Map<String, Int> = fetcher(
        "$subpath/group-count",
        HttpMethod.POST,
        GroupCountQuery.Companion.serializer(serializer),
        input,
        MapSerializer(String.serializer(), Int.serializer())
    )

    override suspend fun aggregate(input: AggregateQuery<T>): Double? = fetcher(
        "$subpath/aggregate",
        HttpMethod.POST,
        AggregateQuery.Companion.serializer(serializer),
        input,
        Double.serializer().nullable
    )

    override suspend fun groupAggregate(input: GroupAggregateQuery<T>): Map<String, Double?> = fetcher(
        "$subpath/group-aggregate",
        HttpMethod.POST,
        GroupAggregateQuery.Companion.serializer(serializer),
        input,
        MapSerializer(String.serializer(), Double.serializer().nullable)
    )

    private fun ID.urlify(): String {
        return UrlProperties.encodeToString(idSerializer, this)
    }
}

class ClientModelRestEndpointsPlusWsLive<T : HasId<ID>, ID : Comparable<ID>>(
    val fetcher: Fetcher,
    val subpath: String,
    val serializer: KSerializer<T>,
    val idSerializer: KSerializer<ID>,
): ClientModelRestEndpointsPlusWs<T, ID> {
    override fun watch(): TypedWebSocket<Query<T>, ListChange<T>> {
        return fetcher.websocket(subpath, Query.serializer(serializer), ListChange.serializer(serializer))
    }
}
class ClientModelRestEndpointsPlusUpdatesWebsocketLive<T : HasId<ID>, ID : Comparable<ID>>(
    val fetcher: Fetcher,
    val subpath: String,
    val serializer: KSerializer<T>,
    val idSerializer: KSerializer<ID>,
): ClientModelRestEndpointsPlusUpdatesWebsocket<T, ID> {
    override fun updates(): TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>> {
        return fetcher.websocket(subpath, Condition.serializer(serializer), CollectionUpdates.serializer(serializer, idSerializer))
    }

}