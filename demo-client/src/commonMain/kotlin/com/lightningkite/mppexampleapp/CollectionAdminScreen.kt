package com.lightningkite.mppexampleapp

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.forms.*
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.lightningserver.files.UploadInformation
import com.lightningkite.lightningserver.schema.ExternalLightningServer
import com.lightningkite.lightningserver.schema.LightningServerKSchema
import com.lightningkite.serialization.VirtualInstance
import com.lightningkite.serialization.default
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass

@Serializable
data class AdminCredentials(
    val session: String? = null,
    val userType: String? = null,
)

val admins = PersistentProperty("credentials", mapOf<String, AdminCredentials>())

val schemaCache = HashMap<String, Readable<LightningServerKSchema>>()
fun schema(url: String): Readable<LightningServerKSchema> = schemaCache.getOrPut(url) {
    GlobalScope.asyncReadable {
        fetch(url)
            .text()
            .let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) }
    }
}

fun ReactiveContext.externalLightningServer(url: String) = shared {
    println("Refetching ")
    val loadable = admins()[url]
    val s = ExternalLightningServer(schema("$url/meta/kschema")())
    s.screen = label@{ type, id ->
        type as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        val idAsString = UrlProperties.encodeToString(type.serializer._id().serializer, id as Comparable<Comparable<*>>)
        return@label {
            DetailAdminScreen(
                adminUrl = url,
                collectionName = s.models.entries.single { (_, it) -> it.serializer.descriptor.serialName == type.serializer.descriptor.serialName }.key,
                itemId = idAsString
            )
        }
    }
    s.sessionToken = loadable?.session
    s.subject = loadable?.userType
    s
}

@Routable("admin/{adminUrl}/collections")
class AllCollectionsScreen(val adminUrl: String) : Screen {
    override fun ViewWriter.render() {
        val endpoints = shared { externalLightningServer(adminUrl)().models.entries.toList() }
        endpoints.addListener { println("Values: ${endpoints.state}") }
        col {
            row {
                text(adminUrl)
                sizeConstraints(20.rem) - fieldTheme - select {
                    bind(
                        admins.lens(
                            get = { it[adminUrl]?.userType },
                            modify = { o, v -> o + (adminUrl to (o[adminUrl]?.copy(userType = v) ?: AdminCredentials(userType = v))) }
                        ),
                        shared { listOf(null) + externalLightningServer(adminUrl)().auth.subjects.keys.toList() },
                        { it ?: "None" }
                    )
                }
                expanding - fieldTheme - textField {
                    content bind admins.lens(
                        get = { it[adminUrl]?.session ?: "" },
                        modify = { o, v -> o + (adminUrl to (o[adminUrl]?.copy(session = v) ?: AdminCredentials(session = v))) }
                    )
                }
            }
            expanding - recyclerView {
                children(endpoints) {
                    link {
                        text { ::content { it().value.serializer.displayName } }
                        ::to { it().key.let { { CollectionAdminScreen(adminUrl, it) } } }
                    }
                }
            }
        }
    }
}

@Routable("admin/{adminUrl}/collections/{collectionName}/{itemId}")
class DetailAdminScreen(val adminUrl: String, val collectionName: String, val itemId: String) : Screen {
    override fun ViewWriter.render() {
        val server = shared { externalLightningServer(adminUrl)() }
        val mc = shared { externalLightningServer(adminUrl)().models[collectionName] as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>> }
        val item = Draft(shared {
            val mc = mc()
            val actualId = UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
            mc[actualId].notNull(mc.serializer.default().also {
                mc.serializer._id().setCopy(it, actualId)
            })
        }.flatten())
        scrolls - col {
            reactive {
                clearChildren()
                card - form(server().context, mc().serializer, item)
                atEnd - important - button {
                    text("Save")
                    ::enabled { item.changesMade() }
                    onClick {
                        item.publish()
                    }
                }
            }
        }
    }
}

@Routable("admin/{adminUrl}/collections/{collectionName}")
class CollectionAdminScreen(val adminUrl: String, val collectionName: String) : Screen {
    @QueryParameter
    val conditionString: Property<String?> = Property(null)

    @QueryParameter
    val sortString: Property<String?> = Property(null)

    override fun ViewWriter.render() {
        val server = shared { externalLightningServer(adminUrl)() }
        val mc = shared { externalLightningServer(adminUrl)().models[collectionName]!! }
        col {
            reactive {
                clearChildren()
                val mc = mc() as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
                val condition = conditionString.lens(
                    get = {
                        it?.let {
                            try {
                                DefaultJson.decodeFromString(Condition.serializer(mc.serializer), it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: Condition.Always
                    },
                    set = { DefaultJson.encodeToString(Condition.serializer(mc.serializer), it) }
                )
                form(server().context, Condition.serializer(mc.serializer), condition)
                val sort = sortString.lens(
                    get = {
                        it?.let {
                            try {
                                DefaultJson.decodeFromString(ListSerializer(SortPartSerializer(mc.serializer)), it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: listOf()
                    },
                    set = { DefaultJson.encodeToString(ListSerializer(SortPartSerializer(mc.serializer)), it) }
                )
                form(server().context, ListSerializer(SortPartSerializer(mc.serializer)), sort)
                expanding - TableRenderer.view(
                    formContext = server().context,
                    writer = this@col,
                    innerSer = mc.serializer,
                    readable = shared { mc.watch(Query(condition.debounce(500)(), sort.debounce(500)())) },
                    link = {
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, it._id)
                        return@view { DetailAdminScreen(adminUrl, collectionName, id) }
                    }
                )
            }
        }
    }
}
