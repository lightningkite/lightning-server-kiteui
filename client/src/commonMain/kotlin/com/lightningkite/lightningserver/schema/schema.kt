package com.lightningkite.lightningserver.schema

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.AuthClientEndpoints
import com.lightningkite.lightningserver.auth.UserAuthClientEndpoints
import com.lightningkite.lightningserver.batchFetch
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.lightningserver.networking.BulkFetcher
import com.lightningkite.lightningserver.networking.ConnectivityOnlyFetcher
import com.lightningkite.serialization.*
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

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

fun LightningServerKSchema.clientModelRestEndpoints(
    registry: SerializationRegistry,
    json: Json = DefaultJson,
    wsToken: () -> String?,
    token: (suspend () -> String?)
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
                        url = this.baseWsUrl + "?path=multiplex" + (wsToken()?.let { "?jwt=$it" } ?: ""),
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

//fun LightningServerKSchema.authEndpoints(
//    registry: SerializationRegistry,
//    json: Json = DefaultJson,
//    wsToken: () -> String?,
//    token: (suspend () -> String?)
//): AuthClientEndpoints {
//    val bulk = bulkEndpoint()
//    return AuthClientEndpoints(
//        subjects = endpoints.filter {
//            it.path.endsWith("login") && it.input.serialName == ListSerializer(Unit.serializer()).descriptor.serialName && it.input.arguments.firstOrNull()?.serialName == "com.lightningkite.lightningserver.auth.proof.Proof"
//        }.map {
//            val httpPath = it.path.substringBeforeLast('/')
//            UserAuthClientEndpoints.StandardImpl(
//                fetchImplementation = bulk?.let {
//                    BulkFetcher(httpPath, json, token)
//                } ?: ConnectivityOnlyFetcher(httpPath, json, token),,
//                idSerializer = re
//            )
//        }
//    )
//}

class ExternalServer(
    val schema: LightningServerKSchema,
    val registry: SerializationRegistry = SerializationRegistry.master,
) {
    val auth: AuthClientEndpoints = TODO()
    var subjectIndex = 0
    var sessionToken: String? = null
    private var lastRefresh: Instant = now()
    private var token: Async<String> = asyncGlobal {
        auth.authenticatedSubjects[subjectIndex]!!.getTokenSimple(sessionToken!!)
    }
    private suspend fun accessToken(): String? {
        if (sessionToken == null) return null
        if (now() - lastRefresh > 4.minutes) {
            lastRefresh = now()
            token = asyncGlobal {
                auth.authenticatedSubjects[subjectIndex]!!.getTokenSimple(sessionToken!!)
            }
        }
        return token.await()
    }
//    val models: Map<String, ClientModelRestEndpoints<*, *>> = schema.clientModelRestEndpoints(registry)
}