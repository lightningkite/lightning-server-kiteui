@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.FormTypeInfo
import com.lightningkite.kiteui.forms.defaultTitleFields
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.readable.invoke
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.lightningserver.files.UploadInformation
import com.lightningkite.lightningserver.networking.BulkFetcher
import com.lightningkite.lightningserver.networking.ConnectivityFetcher
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.lightningserver.schema.LightningServerKSchema
import com.lightningkite.lightningserver.schema.LightningServerKSchemaEndpoint
import com.lightningkite.lightningserver.schema.LightningServerKSchemaInterface
import com.lightningkite.serialization.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties

fun SerializationRegistry.register(schema: LightningServerKSchema) {
    schema.structures.values.forEach { register(it) }
    schema.enums.values.forEach { register(it) }
    schema.aliases.values.forEach { register(it) }
}

private fun LightningServerKSchema.uploadEarlyEndpoint() = endpoints.find {
    it.output.serialName == "com.lightningkite.lightningserver.files.UploadInformation"
}
private fun LightningServerKSchema.uploadEarlyVerifyEndpoint(): LightningServerKSchemaEndpoint? {
    val expected = uploadEarlyEndpoint()?.path?.plus("/verify") ?: return null
    return endpoints.find {
        it.path == expected
    }
}
private fun LightningServerKSchema.healthEndpoint(): LightningServerKSchemaEndpoint? {
    return endpoints.find {
        it.output.serialName == "com.lightningkite.lightningserver.serverhealth.ServerHealth"
    }
}

private fun LightningServerKSchema.bulkEndpoint(): LightningServerKSchemaEndpoint? {
    return endpoints.find {
        it.path.contains("bulk") &&
                it.input.serialName == "kotlin.collections.LinkedHashMap" &&
                it.input.arguments.getOrNull(0)?.serialName == "kotlin.String" &&
                it.input.arguments.getOrNull(1)?.serialName == "com.lightningkite.lightningserver.typed.BulkRequest" &&
                it.output.serialName == "kotlin.collections.LinkedHashMap" &&
                it.output.arguments.getOrNull(0)?.serialName == "kotlin.String" &&
                it.output.arguments.getOrNull(1)?.serialName == "com.lightningkite.lightningserver.typed.BulkResponse"
    }
}

class ExternalLightningServer(
    val schema: LightningServerKSchema,
    val useLiveData: Boolean = true,
    val registry: SerializationRegistry = SerializationRegistry.master.copy(),
    val json: Json = DefaultJson,
    val properties: Properties = UrlProperties,
) {
    init {
        registry.register(schema)
    }

    val bulk = schema.bulkEndpoint()
    val file = schema.uploadEarlyEndpoint()
    val fileVerify = schema.uploadEarlyVerifyEndpoint()
    val health = schema.healthEndpoint()
    
    private val nullToken: suspend () -> List<Pair<String, String>> = { listOf() }
    fun authlessFetcher(): Fetcher = fetcher(null)
    fun fetcher(auth: LightningServerAuthentication?): Fetcher {
        return bulk?.let {
            BulkFetcher(schema.baseUrl + it.path, schema.baseWsUrl + "/multiplex", json, calculator = auth?.accessToken ?: nullToken)
        } ?: ConnectivityFetcher(schema.baseUrl, schema.baseWsUrl, json, calculator = auth?.accessToken ?: nullToken)
    }

    val auth: AuthClientEndpoints = AuthClientEndpoints(
        subjects = schema.interfaces.filter { it.matches.serialName == "UserAuthClientEndpoints" }.associate {
            it.path to UserAuthClientEndpointsLive(
                fetcher = authlessFetcher(),
                subpath = it.path,
                idSerializer = it.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<Comparable<Any>>,
            )
        },
        authenticatedSubjects = schema.interfaces.filter { it.matches.serialName == "AuthenticatedUserAuthClientEndpoints" }.associate {
            it.path to { auth ->
                AuthenticatedUserAuthClientEndpointsLive(
                    fetcher = fetcher(auth),
                    subpath = it.path,
                    idSerializer = it.matches.arguments[1].serializer(registry, mapOf()) as KSerializer<Comparable<Any>>,
                    userSerializer = it.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<HasId<Comparable<Any>>>,
                )
            }
        },
        smsProof = schema.interfaces.find { it.matches.serialName == "SmsProofClientEndpoints" }?.let {
            val httpPath = it.path
            SmsProofClientEndpointsLive(fetcher = authlessFetcher(), subpath = httpPath,)
        },
        emailProof = schema.interfaces.find { it.matches.serialName == "EmailProofClientEndpoints" }?.let {
            val httpPath = it.path
            EmailProofClientEndpointsLive(fetcher = authlessFetcher(), subpath = httpPath,)
        },
        oneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "OneTimePasswordProofClientEndpoints" }?.let {
            val httpPath = it.path
            OneTimePasswordProofClientEndpointsLive(fetcher = authlessFetcher(), subpath = httpPath,)
        },
        passwordProof = schema.interfaces.find { it.matches.serialName == "PasswordProofClientEndpoints" }?.let {
            val httpPath = it.path
            PasswordProofClientEndpointsLive(fetcher = authlessFetcher(), subpath = httpPath,)
        },
        webAuthNProof = schema.interfaces.find { it.matches.serialName == "WebAuthNProofEndpoints" }?.let {
            val httpPath = it.path
            WebAuthNProofEndpointsLive(fetcher = authlessFetcher(), subpath = httpPath,)
        },
        knownDeviceProof = schema.interfaces.find { it.matches.serialName == "KnownDeviceProofClientEndpoints" }?.let {
            val httpPath = it.path
            KnownDeviceProofClientEndpointsLive(fetcher = authlessFetcher(), subpath = httpPath,)
        },
        authenticatedOneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedOneTimePasswordProofClientEndpoints" }?.let {
            { auth ->
                val httpPath = it.path
                AuthenticatedOneTimePasswordProofClientEndpointsLive(fetcher = fetcher(auth), subpath = httpPath,)
            }
        },
        authenticatedPasswordProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedPasswordProofClientEndpoints" }?.let {
            { auth ->
                val httpPath = it.path
                AuthenticatedPasswordProofClientEndpointsLive(fetcher = fetcher(auth), subpath = httpPath,)
            }
        },
        webAuthNRegistration = schema.interfaces.find { it.matches.serialName == "WebAuthNRegistrationEndpoints" }?.let {
            { auth ->
                val httpPath = it.path
                WebAuthNRegistrationEndpointsLive(fetcher = fetcher(auth), subpath = httpPath,)
            }
        },
        authenticatedKnownDeviceProof = schema.interfaces.find { it.matches.serialName == "AuthenticatedKnownDeviceProofClientEndpoints" }?.let {
            { auth ->
                val httpPath = it.path
                AuthenticatedKnownDeviceProofClientEndpointsLive(fetcher = fetcher(auth), subpath = httpPath,)
            }
        },
    )

    inner class ModelInfo<T : HasId<ID>, ID : Comparable<ID>>(val inter: LightningServerKSchemaInterface) {
        val docGroup = inter.docGroup
        val serializer = inter.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<T>
        val idserializer = serializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<ID>
        val vserializer = inter.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<UnknownModel>
        val vidserializer = serializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<UnknownId>
        val httpPath = inter.path
        val hasWs =
            schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Query" && it.output.serialName == "com.lightningkite.lightningdb.ListChange" }
        val hasUpdatesWs =
            schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Condition" && it.output.serialName == "com.lightningkite.lightningdb.CollectionUpdates" }

        init {
            println("${inter.path} uses ${serializer::class}")
        }

        private var cacheCache = PerAuthCache { auth ->
            when {
                !useLiveData -> object: ClientModelRestEndpoints<T, ID> by ClientModelRestEndpointsLive<T, ID>(
                    fetcher = auth?.let { fetcher(it) } ?: authlessFetcher(),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                ) {}
                hasUpdatesWs -> object: ClientModelRestEndpoints<T, ID> by ClientModelRestEndpointsLive<T, ID>(
                    fetcher = auth?.let { fetcher(it) } ?: authlessFetcher(),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                ), ClientModelRestEndpointsPlusUpdatesWebsocket<T, ID> by ClientModelRestEndpointsPlusUpdatesWebsocketLive<T, ID>(
                    fetcher = auth?.let { fetcher(it) } ?: authlessFetcher(),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                ) {}
                hasWs -> object: ClientModelRestEndpoints<T, ID> by ClientModelRestEndpointsLive<T, ID>(
                    fetcher = auth?.let { fetcher(it) } ?: authlessFetcher(),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                ), ClientModelRestEndpointsPlusWs<T, ID> by ClientModelRestEndpointsPlusWsLive<T, ID>(
                    fetcher = auth?.let { fetcher(it) } ?: authlessFetcher(),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                ) {}
                else -> object: ClientModelRestEndpoints<T, ID> by ClientModelRestEndpointsLive<T, ID>(
                    fetcher = auth?.let { fetcher(it) } ?: authlessFetcher(),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                ) {}
            }.let { ModelCache(it, serializer) } as ModelCache<T, ID>
        }

        @Suppress("UNCHECKED_CAST")
        fun cache(auth: LightningServerAuthentication?): ModelCache<T, ID> = cacheCache(auth)
    }

    val models: Map<String, ModelInfo<*, *>> = schema.interfaces.filter {
        it.matches.serialName == "ClientModelRestEndpoints"
    }.associate { inter ->
        inter.path to ModelInfo<UnknownModel, UnknownId>(inter)
    }

    fun formModule(auth: LightningServerAuthentication?) = FormModule().apply {
        fileUpload = file?.let {
            { file ->
                val req = fetcher(auth).invoke(it.path, HttpMethod.GET, Unit.serializer(), Unit, UploadInformation.serializer())
                val r = connectivityFetch(req.uploadUrl, HttpMethod.PUT, body = RequestBodyFile(file))
                if (!r.ok) throw IllegalStateException("File upload to ${req.uploadUrl.substringBefore('?')} failed")
                val safe = fileVerify?.let { verify ->
                    fetcher(auth).invoke(
                        verify.path,
                        HttpMethod.POST,
                        String.serializer(),
                        req.futureCallToken,
                        String.serializer()
                    )
                } ?: req.futureCallToken
                ServerFile(safe)
            }
        }
        typeInfo = label@{ name ->
            val m = models.values.find { it.serializer.descriptor.serialName == name } as? ModelInfo<UnknownModel, UnknownId> ?: return@label null
            FormTypeInfo(
                serializer = m.serializer,
                cache = { m.cache(auth) },
                page = { id -> page(m, id) },
                renderToString = {
                    val c = m.cache(auth)
                    c.get(it)()?.let {
                        c.serializer.defaultTitleFields().joinToString(" ") { f ->
                            f.get(it).toString()
                        }
                    } ?: "?"
                }
            )
        }
    }

    var page: (type: ModelInfo<*, *>, id: Comparable<*>?) -> (() -> Page)? = { _, _ -> null }

}

class PerAuthCache<T>(val calculate: (LightningServerAuthentication?) -> T) {
    val forNull by lazy { calculate(null) }
    var lastKnown: LightningServerAuthentication? = null
    var lastValue: T? = null
    operator fun invoke(auth: LightningServerAuthentication?): T {
        if(auth == null) return forNull
        val lastValue = lastValue
        if (lastValue == null || auth != lastKnown) {
            val v = calculate(auth)
            this.lastKnown = auth
            this.lastValue = v
            return v
        } else {
            return lastValue
        }
    }
}
