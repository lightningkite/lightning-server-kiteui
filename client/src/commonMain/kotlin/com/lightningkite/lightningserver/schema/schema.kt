package com.lightningkite.lightningserver.schema

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.batchFetch
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.lightningserver.db.Fetcher
import com.lightningkite.serialization.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

fun SerializationRegistry.register(schema: LightningServerKSchema) {
    schema.structures.values.forEach { register(it) }
    schema.enums.values.forEach { register(it) }
}

fun LightningServerKSchema.uploadEarlyEndpoint() = endpoints.find {
    it.output.serialName == "com.lightningkite.lightningserver.files.UploadInformation"
}

fun LightningServerKSchema.bulkEndpoint() = endpoints.find {
    it.path.contains("bulk") &&
            it.input.serialName == "com.lightningkite.lightningserver.typed.BulkRequest" &&
            it.output.serialName == "com.lightningkite.lightningserver.typed.BulkResponse"
}

private class BulkFetcher(val base: String, val json: Json, val token: (suspend () -> String)?) : Fetcher {
    override suspend fun <T> invoke(
        url: String,
        method: HttpMethod,
        jsonBody: String?,
        outSerializer: KSerializer<T>
    ): T {
        return batchFetch("$base/$url", method, token, jsonBody, outSerializer, json)
    }
}

private class ConnectivityOnlyFetcher(val base: String, val json: Json, val token: (suspend () -> String)?) : Fetcher {
    override suspend fun <T> invoke(
        url: String,
        method: HttpMethod,
        jsonBody: String?,
        outSerializer: KSerializer<T>
    ): T {
        return connectivityFetch("$base/$url", method, {
            if (token != null) httpHeaders(
                "Authorization" to token.invoke(),
                "Accept" to "application/json"
            ) else httpHeaders("Accept" to "application/json")
        }, RequestBodyText(jsonBody ?: "{}", "application/json")).let {
            if (!it.ok) {
                val text = it.text()
                try {
                    val e = json.decodeFromString(LSError.serializer(), text)
                    throw LsErrorException(it.status, e)
                } catch (e: Exception) {
                    throw LsErrorException(it.status, LSError(it.status.toInt(), "Unknown", message = text))
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                if (outSerializer.descriptor.serialName == "kotlin.Unit") return Unit as T
                json.decodeFromString(outSerializer, it.text())
            }
        }
    }
}

fun LightningServerKSchema.clientModelRestEndpoints(
    registry: SerializationRegistry,
    json: Json = DefaultJson,
    wsToken: String?,
    token: (suspend () -> String)?
): Map<String, ClientModelRestEndpoints<*, *>> {
    val bulk = bulkEndpoint()
    return this.models.mapValues { entry ->
        val vserializer = entry.value.type.serializer(registry, mapOf()) as KSerializer<*>
        val vserializer2: KSerializer<HasId<Comparable<Comparable<*>>>> = if(vserializer is VirtualStruct.Concrete) VirtualStructConcreteWithId(vserializer) as KSerializer<HasId<Comparable<Comparable<*>>>>
        else vserializer as KSerializer<HasId<Comparable<Comparable<*>>>>
        val idserializer = vserializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<Comparable<Comparable<*>>>
        val httpPath = this.baseUrl + entry.value.path
        val wsPath = this.baseUrl + entry.value.path
        val hasWs =
            endpoints.find { it.path == entry.value.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Query" && it.output.serialName == "com.lightningkite.lightningdb.ListChange" }
        val hasUpdatesWs =
            endpoints.find { it.path == entry.value.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Condition" && it.output.serialName == "com.lightningkite.lightningdb.CollectionUpdates" }
        when {
            else -> ClientModelRestEndpointsStandardImpl<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>(
                fetchImplementation = bulk?.let {
                    BulkFetcher(httpPath, json, token)
                } ?: ConnectivityOnlyFetcher(httpPath, json, token),
                wsImplementation = {
                    multiplexSocket(
                        url = this.baseWsUrl + "?path=multiplex" + (wsToken?.let { "?jwt=$it" } ?: ""),
                        path = entry.value.path,
                        params = emptyMap(),
                        json = json,
                        pingTime = 5_000
                    )
                },
                serializer = vserializer2,
                idSerializer = idserializer,
            )
        }
    }
}

class VirtualStructConcreteWithId(val wraps: VirtualStruct.Concrete): WrappingSerializer<VirtualInstanceWithId, VirtualInstance>(wraps.descriptor.serialName + "_ID") {
    override fun getDeferred(): KSerializer<VirtualInstance> = wraps
    override fun inner(it: VirtualInstanceWithId): VirtualInstance = it.virtualInstance
    override fun outer(it: VirtualInstance): VirtualInstanceWithId = VirtualInstanceWithId(it)
}

class VirtualInstanceWithId(val virtualInstance: VirtualInstance): HasId<Comparable<Comparable<*>>> {
    @Suppress("UNCHECKED_CAST")
    override val _id: Comparable<Comparable<*>> get() = virtualInstance.values[0] as Comparable<Comparable<*>>
    val type: VirtualStruct.Concrete get() = virtualInstance.type
    val values: List<Any?> get() = virtualInstance.values
    override fun toString(): String = "${type.struct.serialName}(${values.zip(type.struct.fields).joinToString { "${it.second.name}=${it.first}" }})"
}

private interface ignoreClientModelRestEndpointsPlusWs<T : HasId<ID>, ID : Comparable<ID>> :
    ClientModelRestEndpoints<T, ID> {
    fun watch(): TypedWebSocket<Query<T>, ListChange<T>>
}

private interface ignoreClientModelRestEndpointsPlusUpdatesWebsocket<T : HasId<ID>, ID : Comparable<ID>> :
    ClientModelRestEndpoints<T, ID> {
    fun updates(): TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>>
}