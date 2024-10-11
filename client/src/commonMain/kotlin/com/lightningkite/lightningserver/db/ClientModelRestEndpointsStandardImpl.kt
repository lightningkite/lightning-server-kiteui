@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.networking.Fetcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import com.lightningkite.serialization.*

open class ClientModelRestEndpointsStandardImpl<T: HasId<ID>, ID: Comparable<ID>>(
    val fetchImplementation: Fetcher,
    val wsImplementation: (path: String) -> RetryWebsocket,
    val serializer: KSerializer<T>,
    val idSerializer: KSerializer<ID>,
    val json: Json = DefaultJson,
    val properties: Properties = UrlProperties
): ClientModelRestEndpoints<T, ID> {
    override suspend fun default(): T = fetchImplementation(
        "_default_",
        HttpMethod.GET,
        null,
        serializer
    )
    override suspend fun query(input: Query<T>, ): List<T> = fetchImplementation(
        "query",
        HttpMethod.POST,
        json.encodeToString(Query.serializer(serializer), input),
        ListSerializer(serializer)
    )
    override suspend fun queryPartial(input: QueryPartial<T>, ): List<Partial<T>> = fetchImplementation(
        "query-partial",
        HttpMethod.POST,
        json.encodeToString(QueryPartial.serializer(serializer), input),
        ListSerializer(PartialSerializer(serializer))
    )
    override suspend fun detail(id: ID, ): T = fetchImplementation(
        "${id.urlify()}",
        HttpMethod.GET,
        null,
        serializer
    )
    override suspend fun insertBulk(input: List<T>, ): List<T> = fetchImplementation(
        "bulk",
        HttpMethod.POST,
        json.encodeToString(ListSerializer(serializer), input),
        ListSerializer(serializer)
    )
    override suspend fun insert(input: T, ): T = fetchImplementation(
        "",
        HttpMethod.POST,
        json.encodeToString(serializer, input),
        serializer
    )
    override suspend fun upsert(id: ID, input: T, ): T = fetchImplementation(
        "${id.urlify()}",
        HttpMethod.POST,
        json.encodeToString(serializer, input),
        serializer
    )
    override suspend fun bulkReplace(input: List<T>, ): List<T> = fetchImplementation(
        "",
        HttpMethod.PUT,
        json.encodeToString(ListSerializer(serializer), input),
        ListSerializer(serializer)
    )
    override suspend fun replace(id: ID, input: T, ): T = fetchImplementation(
        "${id.urlify()}",
        HttpMethod.PUT,
        json.encodeToString(serializer, input),
        serializer
    )
    override suspend fun bulkModify(input: MassModification<T>, ): Int = fetchImplementation(
        "bulk",
        HttpMethod.PATCH,
        json.encodeToString(MassModification.serializer(serializer), input),
        Int.serializer()
    )
    override suspend fun modifyWithDiff(id: ID, input: Modification<T>, ): EntryChange<T> = fetchImplementation(
        "${id.urlify()}/delta",
        HttpMethod.PATCH,
        json.encodeToString(Modification.serializer(serializer), input),
        EntryChange.serializer(serializer)
    )
    override suspend fun modify(id: ID, input: Modification<T>, ): T {
        return fetchImplementation(
            "${id.urlify()}",
            HttpMethod.PATCH,
            json.encodeToString(Modification.serializer(serializer), input),
            serializer
        )
    }
    override suspend fun bulkDelete(input: Condition<T>, ): Int = fetchImplementation(
        "bulk-delete",
        HttpMethod.POST,
        json.encodeToString(Condition.serializer(serializer), input),
        Int.serializer()
    )
    override suspend fun delete(id: ID, ): Unit = fetchImplementation(
        "${id.urlify()}",
        HttpMethod.DELETE,
        null,
        Unit.serializer(),
    )
    override suspend fun count(input: Condition<T>, ): Int = fetchImplementation(
        "count",
        HttpMethod.POST,
        json.encodeToString(Condition.serializer(serializer), input),
        Int.serializer()
    )
    override suspend fun groupCount(input: GroupCountQuery<T>, ): Map<String, Int> = fetchImplementation(
        "group-count",
        HttpMethod.POST,
        json.encodeToString(GroupCountQuery.serializer(serializer), input),
        MapSerializer(String.serializer(), Int.serializer())
    )
    override suspend fun aggregate(input: AggregateQuery<T>, ): Double? = fetchImplementation(
        "aggregate",
        HttpMethod.POST,
        json.encodeToString(AggregateQuery.serializer(serializer), input),
        Double.serializer().nullable
    )
    override suspend fun groupAggregate(input: GroupAggregateQuery<T>, ): Map<String, Double?> = fetchImplementation(
        "group-aggregate",
        HttpMethod.POST,
        json.encodeToString(GroupAggregateQuery.serializer(serializer), input),
        MapSerializer(String.serializer(), Double.serializer().nullable)
    )

    suspend fun RequestResponse.discard() = Unit
    fun ID.urlify(): String {
        return properties.encodeToString(idSerializer, this)
    }
    suspend fun <T> RequestResponse.readJson(serializer: KSerializer<T>): T {
        return json.decodeFromString(serializer, text())
    }
}

open class ClientModelRestEndpointsPlusWsStandardImpl<T: HasId<ID>, ID: Comparable<ID>>(
    fetchImplementation: Fetcher,
    wsImplementation: (path: String) -> RetryWebsocket,
    serializer: KSerializer<T>,
    idSerializer: KSerializer<ID>,
    json: Json = DefaultJson,
    properties: Properties = UrlProperties,
): ClientModelRestEndpointsStandardImpl<T, ID>(
    fetchImplementation,
    wsImplementation,
    serializer,
    idSerializer,
    json,
    properties,
), ClientModelRestEndpointsPlusWs<T, ID> {
    override fun watch(): TypedWebSocket<Query<T>, ListChange<T>> {
        return wsImplementation("").typed(json, Query.serializer(serializer), ListChange.serializer(serializer))
    }
}

open class ClientModelRestEndpointsPlusUpdatesWebsocketStandardImpl<T: HasId<ID>, ID: Comparable<ID>>(
    fetchImplementation: Fetcher,
    wsImplementation: (path: String) -> RetryWebsocket,
    serializer: KSerializer<T>,
    idSerializer: KSerializer<ID>,
    json: Json = DefaultJson,
    properties: Properties = UrlProperties,
): ClientModelRestEndpointsStandardImpl<T, ID>(
    fetchImplementation,
    wsImplementation,
    serializer,
    idSerializer,
    json,
    properties,
), ClientModelRestEndpointsPlusUpdatesWebsocket<T, ID> {
    override fun updates(): TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>> {
        return wsImplementation("").typed(json, Condition.serializer(serializer), CollectionUpdates.serializer(serializer, idSerializer))
    }
}
