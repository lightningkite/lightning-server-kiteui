package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.forms.FieldVisibility
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.ModelPermissions
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.LightningServerAuthentication
import com.lightningkite.lightningserver.schema.ExternalLightningServer
import com.lightningkite.lightningserver.schema.LightningServerKSchema
import com.lightningkite.now
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes


@Serializable
data class AdminCredentials(
    val session: String? = null,
    val userType: String? = null,
)

val serverUrl = PersistentProperty("url", "http://localhost:8080")
val adminCredentials = PersistentProperty<AdminCredentials?>("credentials", null)
val adminAuthentication = shared {
    val c = adminCredentials() ?: return@shared null
    LightningServerAuthentication(
        subject = adminServer().auth.subjects[c.userType ?: return@shared null]!!,
        subjectPath = c.userType ?: return@shared null,
        c.session ?: return@shared null
    )
}
val serverSchema = sharedSuspending {
    fetch(serverUrl() + "/meta/kschema")
        .text()
        .let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) }
}
@Serializable
data class AdminSettings(
    val showHiddenFields: Boolean = false,
    val editAllFields: Boolean = false,
    val showAlternativeEditOptions: Boolean = false,
    val showEndpoints: Boolean = false,
    val unlockDestructiveActions: Instant? = null,
)
val adminSettings = PersistentProperty("adminSettings", AdminSettings())
val nowByMinute = sharedProcess {
    while(true) {
        emit(now())
        delay(1.minutes)
    }
}
val unlockDestructiveActions = shared {
    nowByMinute() < (adminSettings().unlockDestructiveActions ?: Instant.DISTANT_PAST) + 30.minutes
}
val loadedPermissions: Readable<Map<String, ModelPermissions<out HasId<out Comparable<*>>>>> = sharedSuspending {
    val auth = adminAuthentication()
    adminServer().models.entries.map {
        async {
            it.key to try {
                it.value.cache(auth).skipCache.permissions()
            } catch(e: LsErrorException) {
                if(e.status == 403.toShort()) ModelPermissions()
                else if(e.status == 401.toShort()) ModelPermissions()
                else ModelPermissions.allowAll()
            }
        }
    }.awaitAll().associate { it }
//    mapOf()
}
val adminServer = shared {
    println("Refetching ")
    val s = ExternalLightningServer(serverSchema())
    s.screen = label@{ type, id ->
        type as ExternalLightningServer.ModelInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        val idAsString = UrlProperties.encodeToString(type.idserializer, id as Comparable<Comparable<*>>)
        return@label {
            DetailAdminScreen(
                collectionName = s.models.entries.single { (_, it) -> it.serializer.descriptor.serialName == type.serializer.descriptor.serialName }.key,
                itemId = idAsString
            )
        }
    }
    s
}
val adminFormModule = shared {
    adminServer().formModule(adminAuthentication()).also {
        val settings = adminSettings()
        if(settings.showHiddenFields) {
            it.visibilitySettings.keys.forEach { k ->
                it.visibilitySettings[k] = it.visibilitySettings[k]!!.coerceAtLeast(FieldVisibility.READ)
            }
        }
        if(settings.editAllFields) {
            it.visibilitySettings.keys.forEach { k ->
                if(it.visibilitySettings[k]!! >= FieldVisibility.READ)
                    it.visibilitySettings[k] = FieldVisibility.EDIT
            }
        }
        if(settings.showAlternativeEditOptions) {
            it.showTypePicker = true
        }
    }
}