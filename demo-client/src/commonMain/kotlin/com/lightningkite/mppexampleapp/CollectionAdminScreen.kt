package com.lightningkite.mppexampleapp

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.forms.TableRenderer
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.schema.ExternalLightningServer
import com.lightningkite.lightningserver.schema.LightningServerKSchema
import com.lightningkite.serialization.VirtualInstance
import com.lightningkite.serialization.default
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.Serializable

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
                        text { ::content { it().key } }
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
        val endpoints = shared { externalLightningServer(adminUrl)().models[collectionName] as ClientModelRestEndpointsStandardImpl<VirtualInstance, Comparable<Comparable<*>>> }
        val mc = shared { ModelCache(endpoints(), endpoints().serializer) }
        val item = Draft(shared {
            val actualId = UrlProperties.decodeFromString(mc().serializer._id().serializer, itemId)
            mc()[actualId].notNull(mc().serializer.default().also {
                mc().serializer._id().setCopy(it, actualId)
            })
        }.flatten())
        col {
            reactive {
                clearChildren()
                card - form(mc().serializer, item)
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
    val queryString: Property<String?> = Property(null)

    override fun ViewWriter.render() {
        val endpoints = shared { externalLightningServer(adminUrl)().models[collectionName] as ClientModelRestEndpointsStandardImpl<VirtualInstance, Comparable<Comparable<*>>> }
        val mc = shared { ModelCache(endpoints(), endpoints().serializer) }
        col {
            reactive {
                clearChildren()
                val mc = mc()
                val query = queryString.lens(
                    get = {
                        it?.let {
                            try {
                                DefaultJson.decodeFromString(Query.serializer(mc.serializer), it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: Query()
                    },
                    set = { DefaultJson.encodeToString(Query.serializer(mc.serializer), it) }
                )
                form(Query.serializer(mc.serializer), query)
                expanding - TableRenderer.view(
                    writer = this@col,
                    innerSer = mc.serializer,
                    readable = shared { mc.watch(query.debounce(500)()) },
                    link = {
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, it._id)
                        return@view { DetailAdminScreen(adminUrl, collectionName, id) }
                    }
                )
            }
        }
    }
}
