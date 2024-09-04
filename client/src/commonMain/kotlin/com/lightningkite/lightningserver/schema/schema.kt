package com.lightningkite.lightningserver.schema

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsPlusUpdatesWebsocketStandardImpl
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsPlusWsStandardImpl
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.lightningserver.networking.BulkFetcher
import com.lightningkite.lightningserver.networking.ConnectivityOnlyFetcher
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.serialization.*
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlin.time.Duration.Companion.minutes

fun SerializationRegistry.register(schema: LightningServerKSchema) {
    schema.structures.values.forEach { register(it) }
    schema.enums.values.forEach { register(it) }
}

private fun LightningServerKSchema.uploadEarlyEndpoint() = endpoints.find {
    it.output.serialName == "com.lightningkite.lightningserver.files.UploadInformation"
}

private fun LightningServerKSchema.bulkEndpoint() = endpoints.find {
    it.path.contains("bulk") &&
            it.input.serialName == "com.lightningkite.lightningserver.typed.BulkRequest" &&
            it.output.serialName == "com.lightningkite.lightningserver.typed.BulkResponse"
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

class ExternalLightningServer(
    val schema: LightningServerKSchema,
    val registry: SerializationRegistry = SerializationRegistry.master.copy(),
    val json: Json = DefaultJson,
    val properties: Properties = UrlProperties
) {
    init {
        registry.register(schema)
    }
    val bulk = schema.bulkEndpoint()
    val file = schema.uploadEarlyEndpoint()

    fun fetcher(path: String): Fetcher = bulk?.let {
        BulkFetcher(path, json, this::accessToken)
    } ?: ConnectivityOnlyFetcher(path, json, this::accessToken)

    val auth: AuthClientEndpoints = AuthClientEndpoints(
        subjects = schema.interfaces.filter { it.matches.serialName == "UserAuthClientEndpoints" }.associate {
            it.path to UserAuthClientEndpoints.StandardImpl(
                fetchImplementation = fetcher(schema.baseUrl + "/" + it.path),
                idSerializer = it.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<Comparable<Any>>,
                json = json,
                properties = properties,
            )
        },
        authenticatedSubjects = schema.interfaces.filter { it.matches.serialName == "AuthenticatedUserAuthClientEndpoints" }.associate {
            it.path to AuthenticatedUserAuthClientEndpoints.StandardImpl(
                fetchImplementation = fetcher(schema.baseUrl + "/" + it.path),
                idSerializer = it.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<Comparable<Any>>,
                userSerializer = it.matches.arguments[1].serializer(registry, mapOf()) as KSerializer<HasId<Comparable<Any>>>,
                json = json,
                properties = properties,
            )
        },
        smsProof = schema.interfaces.find { it.matches.serialName == "SmsProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            SmsProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        emailProof = schema.interfaces.find { it.matches.serialName == "EmailProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            EmailProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        oneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "OneTimePasswordProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            OneTimePasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        passwordProof = schema.interfaces.find { it.matches.serialName == "PasswordProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            PasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        knownDeviceProof = schema.interfaces.find { it.matches.serialName == "KnownDeviceProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            KnownDeviceProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        authenticatedOneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedOneTimePasswordProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            AuthenticatedOneTimePasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        authenticatedPasswordProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedPasswordProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            AuthenticatedPasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        authenticatedKnownDeviceProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedKnownDeviceProofClientEndpoints" }?.let {
            val httpPath = it.path.substringBeforeLast('/')
            AuthenticatedKnownDeviceProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
    )
    var subject: String? = null
    var sessionToken: String? = null
    private var lastRefresh: Instant = now()
    private var token: Async<String> = asyncGlobal {
        auth.subjects[subject!!]!!.getTokenSimple(sessionToken!!)
    }
    private suspend fun accessToken(): String? {
        if (subject == null || sessionToken == null) return null
        if (now() - lastRefresh > 4.minutes) {
            lastRefresh = now()
            token = asyncGlobal {
                auth.subjects[subject!!]!!.getTokenSimple(sessionToken!!)
            }
        }
        return token.await()
    }
    val models: Map<String, ClientModelRestEndpointsStandardImpl<*, *>> = schema.interfaces.filter {
        it.matches.serialName == "ClientModelRestEndpoints"
    }.associate { inter ->
        val vserializer = inter.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<*>
        val vserializer2: KSerializer<HasId<Comparable<Comparable<*>>>> = if(vserializer is VirtualStruct.Concrete) VirtualStructConcreteWithId(vserializer) as KSerializer<HasId<Comparable<Comparable<*>>>>
        else vserializer as KSerializer<HasId<Comparable<Comparable<*>>>>
        val idserializer = vserializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<Comparable<Comparable<*>>>
        val httpPath = schema.baseUrl + inter.path
        val hasWs = schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Query" && it.output.serialName == "com.lightningkite.lightningdb.ListChange" }
        val hasUpdatesWs = schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Condition" && it.output.serialName == "com.lightningkite.lightningdb.CollectionUpdates" }
        inter.path to when {
            hasUpdatesWs -> ClientModelRestEndpointsPlusUpdatesWebsocketStandardImpl(
                fetchImplementation = fetcher(httpPath),
                wsImplementation = {
                    multiplexSocket(
                        url = schema.baseWsUrl + "?path=multiplex" + (sessionToken?.let { "?jwt=$it" } ?: ""),
                        path = inter.path,
                        params = emptyMap(),
                        json = json,
                        pingTime = 5_000
                    )
                },
                serializer = vserializer2,
                idSerializer = idserializer,
                json = json,
                properties = properties
            )
            hasWs -> ClientModelRestEndpointsPlusWsStandardImpl(
                fetchImplementation = fetcher(httpPath),
                wsImplementation = {
                    multiplexSocket(
                        url = schema.baseWsUrl + "?path=multiplex" + (sessionToken?.let { "?jwt=$it" } ?: ""),
                        path = inter.path,
                        params = emptyMap(),
                        json = json,
                        pingTime = 5_000
                    )
                },
                serializer = vserializer2,
                idSerializer = idserializer,
                json = json,
                properties = properties
            )
            else -> ClientModelRestEndpointsStandardImpl(
                fetchImplementation = fetcher(httpPath),
                wsImplementation = {
                    multiplexSocket(
                        url = schema.baseWsUrl + "?path=multiplex" + (sessionToken?.let { "?jwt=$it" } ?: ""),
                        path = inter.path,
                        params = emptyMap(),
                        json = json,
                        pingTime = 5_000
                    )
                },
                serializer = vserializer2,
                idSerializer = idserializer,
                json = json,
                properties = properties
            )
        }
    }
}