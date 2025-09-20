package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.forms.FieldVisibility
import com.lightningkite.kiteui.forms.tryChildSerializers
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.LightningServerAuthentication
import com.lightningkite.lightningserver.typed.LightningServerKSchema
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.reactiveProcess
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.ModelPermissions
import com.lightningkite.services.database.WrappingSerializer
import com.lightningkite.services.database.childSerializersOrNull
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.services.database.typeParametersSerializersOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import com.lightningkite.services.database.LazyRenamedSerialDescriptor


@Serializable
data class AdminCredentials(
    val session: String? = null,
    val userType: String? = null,
)

val serverUrl = PersistentProperty("url", "http://localhost:8080")
val adminCredentials = PersistentProperty<AdminCredentials?>("credentials", null)
val adminAuthentication = remember {
    val c = adminCredentials() ?: return@remember null
    if(c.session == null) return@remember null
    LightningServerAuthentication(
        subject = adminServer().authEndpoints(null).subjects[c.userType ?: return@remember null]!!,
        subjectPath = c.userType,
        c.session
    )
}
val serverSchema = rememberSuspending {
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
    val liveData: Boolean = true,
)

val adminSettings = PersistentProperty("adminSettings", AdminSettings())
val nowByMinute = reactiveProcess {
    while (true) {
        emit(Clock.System.now())
        delay(1.minutes)
    }
}
val unlockDestructiveActions = remember {
    nowByMinute() < (adminSettings().unlockDestructiveActions ?: Instant.DISTANT_PAST) + 30.minutes
}
val loadedPermissions: Reactive<Map<String, ModelPermissions<out HasId<out Comparable<*>>>>> = rememberSuspending {
    val auth = adminAuthentication()
    adminServer().models.entries.map {
        async {
            it.key to try {
                it.value.cache(auth).skipCache.permissions().also { v -> println("Permissions for ${it.key}: $v") }
            } catch (e: LsErrorException) {
                if (e.status == 403) ModelPermissions()
                else if (e.status == 401) ModelPermissions()
                else ModelPermissions.allowAll()
            } catch (e: Exception) {
                println("WARN: Could not read permissions for ${it.key}.")
                e.printStackTrace()
                ModelPermissions.allowAll()
            }
        }
    }.awaitAll().associate { it }
//    mapOf()
}
private operator fun SerialDescriptor.get(name: String): SerialDescriptor? {
    val i = this.getElementIndex(name)
    if(i >= 0) return this.getElementDescriptor(i)
    return null
}
private fun KSerializer<*>.printDescriptorBetter(): String = descriptor.serialName + typeParametersSerializersOrNull()?.joinToString(prefix = "<", postfix = ">") { it.printDescriptorBetter() }.orEmpty()
private fun KSerializer<*>.printDescriptorNested(label: String, tab: Int = 0) {
    println("${"  ".repeat(tab)}$label: ${if(this is WrappingSerializer<*, *>) this.to.descriptor.serialName else descriptor.serialName}")
    if(tab > 3) return
    serializableProperties?.forEach { prop ->
        prop.serializer.printDescriptorNested(prop.name, tab + 1)
    }
}
val adminServer = remember {
    println("Refetching ")
    try {
        val s = ExternalLightningServer(serverSchema(), adminSettings().liveData)
        s.page = label@{ type, id ->
            type as ExternalLightningServer.ModelInfo<UnknownModel, UnknownId>
            val idAsString = UrlProperties.encodeToString(type.idserializer, id as UnknownId)
            return@label {
                DetailAdminPage(
                    collectionName = s.models.entries.single { (_, it) -> it.serializer.descriptor.serialName == type.serializer.descriptor.serialName }.key,
                    itemId = idAsString
                )
            }
        }
        s
    } catch (e: Exception) {
        println("Exception in admin server")
        throw e
    }
}
val adminFormModule = remember {
    adminServer().formModule(adminAuthentication()).also {
        val settings = adminSettings()
        if (settings.showHiddenFields) {
            it.visibilitySettings.forEach { (fqn, visibility) ->
                it.visibilitySettings[fqn] = visibility.coerceAtLeast(FieldVisibility.READ)
            }
        }
        if (settings.editAllFields) {
            it.visibilitySettings.keys.forEach { fqn ->
                it.visibilitySettings[fqn] = FieldVisibility.EDIT
            }
        }
        if (settings.showAlternativeEditOptions) {
            it.showTypePicker = true
        }
    }
}