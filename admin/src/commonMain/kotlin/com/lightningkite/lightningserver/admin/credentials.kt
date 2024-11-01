package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.schema.ExternalLightningServer
import com.lightningkite.lightningserver.schema.LightningServerKSchema
import kotlinx.serialization.Serializable


@Serializable
data class AdminCredentials(
    val session: String? = null,
    val userType: String? = null,
)

val serverUrl = PersistentProperty("url", "http://localhost:8080")
val adminCredentials = PersistentProperty<AdminCredentials?>("credentials", null)
val serverSchema = sharedSuspending {
    fetch(serverUrl() + "/meta/kschema")
        .text()
        .let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) }
}
val adminServer = shared {
    println("Refetching ")
    val loadable = adminCredentials()
    val s = ExternalLightningServer(serverSchema())
    s.screen = label@{ type, id ->
        type as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        val idAsString = UrlProperties.encodeToString(type.serializer._id().serializer, id as Comparable<Comparable<*>>)
        return@label {
            DetailAdminScreen(
                collectionName = s.models.entries.single { (_, it) -> it.serializer.descriptor.serialName == type.serializer.descriptor.serialName }.key,
                itemId = idAsString
            )
        }
    }
    s.sessionToken = loadable?.session
    s.subject = loadable?.userType
    s
}
