@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.RequestBodyFile
import com.lightningkite.kiteui.connectivityFetch
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.FormTypeInfo
import com.lightningkite.kiteui.forms.defaultTitleFields
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.services.database.HasId
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.files.UploadInformation
import com.lightningkite.services.files.ServerFile
import com.lightningkite.lightningserver.networking.BulkFetcher
import com.lightningkite.lightningserver.networking.ConnectivityFetcher
import com.lightningkite.lightningserver.networking.lightningServer
import com.lightningkite.lightningserver.sessions.proofs.LiveAuthClientEndpoints
import com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.ClientModelRestEndpointsAndUpdatesWebsocket
import com.lightningkite.lightningserver.typed.ClientModelRestUpdatesWebsocket
import com.lightningkite.lightningserver.typed.Fetcher
import com.lightningkite.lightningserver.typed.LightningServerKSchema
import com.lightningkite.lightningserver.typed.LightningServerKSchemaEndpoint
import com.lightningkite.lightningserver.typed.LightningServerKSchemaInterface
import com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.LiveClientModelRestEndpointsAndUpdatesWebsocket
import com.lightningkite.reactive.context.invoke
import com.lightningkite.services.database.SerializationRegistry
import com.lightningkite.services.database.serializableProperties
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties

fun SerializationRegistry.register(schema: LightningServerKSchema) {
    schema.structures.values.forEach { register(it) }
    schema.enums.values.forEach { register(it) }
    schema.aliases.values.forEach { register(it) }
    if(schema.aliases.containsKey("com.lightningkite.services.files.ServerFile")) throw IllegalStateException()
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
    val registry: SerializationRegistry = SerializationRegistry.master,
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

    private val fetcherAuthCache = HashMap<LightningServerAuthentication?, Fetcher>()
    fun fetcher(auth: LightningServerAuthentication?): Fetcher = fetcherAuthCache.getOrPut(auth) {
        bulk?.let {
            BulkFetcher(
                schema.baseUrl + it.path,
                schema.baseWsUrl + "/multiplex",
                json,
                calculator = auth?.accessToken ?: nullToken
            )
        } ?: ConnectivityFetcher(schema.baseUrl, schema.baseWsUrl, json, calculator = auth?.accessToken ?: nullToken)
    }

    private val endpointsAuthCache = HashMap<LightningServerAuthentication?, AuthEndpoints>()
    fun authEndpoints(auth: LightningServerAuthentication?): AuthEndpoints = endpointsAuthCache.getOrPut(auth) {
        val fetcher = fetcher(auth)

        return AuthEndpoints(
            subjects = schema.interfaces.filter { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints" }
                .associate {
                    it.path to LiveAuthClientEndpoints(
                        fetcher = fetcher,
                        subpath = it.path,
                        subjectSerializer = it.matches.arguments[0].serializer(
                            registry,
                            mapOf()
                        ) as KSerializer<HasId<Comparable<Any>>>,
                        idSerializer = it.matches.arguments[1].serializer(
                            registry,
                            mapOf()
                        ) as KSerializer<Comparable<Any>>,
                    )
                },
            authentication = auth,
            smsProof = schema.interfaces.find { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Sms" }
                ?.let {
                    val httpPath = it.path
                    LiveProofClientEndpoints.Sms(fetcher = fetcher, subpath = httpPath)
                },
            emailProof = schema.interfaces.find { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Email" }
                ?.let {
                    val httpPath = it.path
                    LiveProofClientEndpoints.Email(fetcher = fetcher, subpath = httpPath)
                },
            oneTimePasswordProof = schema.interfaces.find { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.TimeBasedOTP" }
                ?.let {
                    val httpPath = it.path
                    LiveProofClientEndpoints.TimeBasedOTP(fetcher = fetcher, subpath = httpPath)
                },
            passwordProof = schema.interfaces.find { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password" }
                ?.let {
                    val httpPath = it.path
                    LiveProofClientEndpoints.Password(fetcher = fetcher, subpath = httpPath)
                },
            webAuthNProof = schema.interfaces.find { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.WebAuthN" }
                ?.let {
                    val httpPath = it.path
                    LiveProofClientEndpoints.WebAuthNEndpoints(fetcher = fetcher, subpath = httpPath)
                },
            knownDeviceProof = schema.interfaces.find { it.matches.serialName == "com.lightningkite.lightningserver.sessions.proofs.KnownDeviceProofClientEndpoints" }
                ?.let {
                    val httpPath = it.path
                    LiveProofClientEndpoints.KnownDevice(fetcher = fetcher, subpath = httpPath)
                },
            withAuthentication = { authEndpoints(it) }
        )
    }

    inner class ModelInfo<T : HasId<ID>, ID : Comparable<ID>>(val inter: LightningServerKSchemaInterface) {
        val docGroup = inter.docGroup
        val serializer = inter.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<T>
        val idserializer = serializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<ID>
        val vserializer = inter.matches.arguments[0].serializer(registry, mapOf()) as KSerializer<UnknownModel>
        val vidserializer =
            serializer.serializableProperties!!.find { it.name == "_id" }!!.serializer as KSerializer<UnknownId>
        val httpPath = inter.path

        val hasUpdatesWs =
            schema.endpoints.any { it.path == inter.path && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Condition" && it.output.serialName == "com.lightningkite.lightningdb.CollectionUpdates" }

        private var cacheCache = PerAuthCache { auth ->
            when {
                !useLiveData -> LiveClientModelRestEndpoints(
                    fetcher = fetcher(auth),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                )

                hasUpdatesWs -> LiveClientModelRestEndpointsAndUpdatesWebsocket(
                    fetcher = fetcher(auth),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                )

                else -> LiveClientModelRestEndpoints(
                    fetcher = fetcher(auth),
                    subpath = httpPath,
                    serializer = serializer,
                    idSerializer = idserializer,
                )
            }.let { ModelCache(it, serializer) } as ModelCache<T, ID>
        }

        @Suppress("UNCHECKED_CAST")
        fun cache(auth: LightningServerAuthentication?): ModelCache<T, ID> = cacheCache(auth)
    }

    val models: Map<String, ModelInfo<*, *>> = schema.interfaces.filter {
        it.matches.serialName == "com.lightningkite.lightningserver.typed.ClientModelRestEndpoints"
    }.associate { inter ->
        inter.path to ModelInfo<UnknownModel, UnknownId>(inter)
    }

    fun formModule(auth: LightningServerAuthentication?) = FormModule().apply {
        fileUpload = file?.let {
            { file ->
                val req = fetcher(auth).invoke(
                    it.path,
                    HttpMethod.GET.lightningServer,
                    Unit.serializer(),
                    Unit,
                    UploadInformation.serializer()
                )
                val r = connectivityFetch(req.uploadUrl, HttpMethod.PUT, body = RequestBodyFile(file))
                if (!r.ok) throw IllegalStateException("File upload to ${req.uploadUrl.substringBefore('?')} failed")
                val safe = fileVerify?.let { verify ->
                    fetcher(auth).invoke(
                        verify.path,
                        HttpMethod.POST.lightningServer,
                        String.serializer(),
                        req.futureCallToken,
                        String.serializer()
                    )
                } ?: req.futureCallToken
                ServerFile(safe)
            }
        }
        typeInfo = label@{ name ->
            val m =
                models.values.find { it.serializer.descriptor.serialName == name } as? ModelInfo<UnknownModel, UnknownId>
                    ?: return@label null
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
        if (auth == null) return forNull
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
