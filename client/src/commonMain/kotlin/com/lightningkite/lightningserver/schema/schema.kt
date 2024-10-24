package com.lightningkite.lightningserver.schema

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.FormTypeInfo
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.lightningserver.files.UploadInformation
import com.lightningkite.lightningserver.networking.BulkFetcher
import com.lightningkite.lightningserver.networking.ConnectivityOnlyFetcher
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.serialization.*
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
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

    fun authlessFetcher(path: String): Fetcher = bulk?.let {
        BulkFetcher(path, json, { null })
    } ?: ConnectivityOnlyFetcher(path, json, { null })

    fun fetcher(path: String): Fetcher = bulk?.let {
        BulkFetcher(path, json, this::accessToken)
    } ?: ConnectivityOnlyFetcher(path, json, this::accessToken)

    val auth: AuthClientEndpoints = AuthClientEndpoints(
        subjects = schema.interfaces.filter { it.matches.serialName == "UserAuthClientEndpoints" }.associate {
            it.path to UserAuthClientEndpoints.StandardImpl(
                fetchImplementation = authlessFetcher(schema.baseUrl + "/" + it.path),
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
            val httpPath = schema.baseUrl + "/" + it.path
            SmsProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        emailProof = schema.interfaces.find { it.matches.serialName == "EmailProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            EmailProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        oneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "OneTimePasswordProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            OneTimePasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        passwordProof = schema.interfaces.find { it.matches.serialName == "PasswordProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            PasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        knownDeviceProof = schema.interfaces.find { it.matches.serialName == "KnownDeviceProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            KnownDeviceProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        authenticatedOneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedOneTimePasswordProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            AuthenticatedOneTimePasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        authenticatedPasswordProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedPasswordProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            AuthenticatedPasswordProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
        authenticatedKnownDeviceProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedKnownDeviceProofClientEndpoints" }?.let {
            val httpPath = schema.baseUrl + "/" + it.path
            AuthenticatedKnownDeviceProofClientEndpoints.StandardImpl(fetchImplementation = fetcher(httpPath), json = json, properties = properties)
        },
    )
    private var lastRefresh: Instant = Instant.DISTANT_PAST
    var subject: String? = null
        set(value) {
            lastRefresh = Instant.DISTANT_PAST
            field = value
        }
    var sessionToken: String? = null
        set(value) {
            lastRefresh = Instant.DISTANT_PAST
            field = value
        }
    private var token: Async<String?>? = null
    private suspend fun accessToken(): String? {
        val subject = subject ?: return null
        val sessionToken = sessionToken ?: return null
        if (now() - lastRefresh > 4.minutes || token == null) {
            lastRefresh = now()
            token = asyncGlobal {
                (auth.subjects[subject] ?: throw IllegalArgumentException("No such subject; available subjects: ${auth.subjects.keys}")).getTokenSimple(sessionToken)
            }
        }
        return token!!.await()
    }

    val models: Map<String, ModelCache<*, *>> = schema.interfaces.filter {
        it.matches.serialName == "ClientModelRestEndpoints"
    }.associate { inter ->
        val vserializer = inter.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<HasId<Comparable<Comparable<*>>>>
        val idserializer = vserializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<Comparable<Comparable<*>>>
        val httpPath = schema.baseUrl + inter.path
        val hasWs =
            schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Query" && it.output.serialName == "com.lightningkite.lightningdb.ListChange" }
        val hasUpdatesWs =
            schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Condition" && it.output.serialName == "com.lightningkite.lightningdb.CollectionUpdates" }
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
                serializer = vserializer,
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
                serializer = vserializer,
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
                serializer = vserializer,
                idSerializer = idserializer,
                json = json,
                properties = properties
            )
        }.let { ModelCache(it, it.serializer) }
    }

    val context = FormModule().apply {
        fileUpload = file?.let {
            { file ->
                val req = fetcher(schema.baseUrl).invoke(it.path, HttpMethod.GET, null, UploadInformation.serializer())
                val r = connectivityFetch(req.uploadUrl, HttpMethod.PUT, body = RequestBodyFile(file))
                if(!r.ok) throw IllegalStateException("File upload to ${req.uploadUrl.substringBefore('?')} failed")
                ServerFile(req.futureCallToken)
            }
        }
        typeInfo = label@{ name ->
            val m = models.values.find { it.serializer.descriptor.serialName == name } ?: return@label null
            FormTypeInfo(
                cache = m,
                screen = { id -> screen(m, id) }
            )
        }
    }

    var screen: (type: ModelCache<*, *>, id: Comparable<*>?) -> (() -> Screen)? = { _, _ -> null}
}

