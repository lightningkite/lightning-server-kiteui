package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.navigation.DefaultUriFormat
import com.lightningkite.kiteui.models.ErrorSemantic
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.lightningserver.networking.lightningServer
import com.lightningkite.lightningserver.typed.LightningServerKSchema
import com.lightningkite.lightningserver.typed.LightningServerKSchemaEndpoint
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.remember
import com.lightningkite.services.database.default
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/**
 * An endpoint that acts on one item of a collection: it takes the item's ID, either as its only
 * route parameter or as its whole request body, and isn't one of the collection's standard REST
 * operations.  Found by [detectItemActions].
 */
data class ItemAction(
    val endpoint: LightningServerKSchemaEndpoint,
    /** The route parameter holding the ID, or null when the ID is the request body. */
    val idRoute: String?,
) {
    val title: String get() = endpoint.summary.ifBlank { "${endpoint.method} ${endpoint.path}" }

    /** Whether the user fills in anything beyond the ID. */
    val hasInput: Boolean get() = idRoute != null && endpoint.input.serialName != "kotlin.Unit"
}

/**
 * Finds the endpoints that act on a single item, keyed by the path of the collection they act on.
 *
 * Most models share an ID type (usually Uuid), so a matching type only narrows it down: the
 * endpoint must also point at one model specifically - by living under its path, by naming its
 * ID parameter after it (`sessionId`), or by naming it in its summary or path ("Scratch Racer").
 * Endpoints that point at two models equally are left out rather than guessed.
 */
fun LightningServerKSchema.detectItemActions(): Map<String, List<ItemAction>> {
    class Model(val path: String, val typeName: String, val idType: String)

    val models = interfaces
        .filter { it.matches.serialName == "com.lightningkite.lightningserver.typed.ClientModelRestEndpoints" }
        .map { Model(it.path, it.matches.arguments[0].serialName.substringAfterLast('.').lowercase(), it.matches.arguments[1].serialName) }
    val result = LinkedHashMap<String, MutableList<ItemAction>>()
    for (endpoint in endpoints) {
        if (endpoint.method == "WEBSOCKET") continue
        if (endpoint.belongsToInterface?.serialName?.startsWith("com.lightningkite.lightningserver.typed.ClientModelRest") == true) continue
        val (idType, idRoute) = when {
            endpoint.routes.size == 1 -> endpoint.routes.entries.single().let { it.value.serialName to it.key }
            endpoint.routes.isEmpty() && endpoint.input.arguments.isEmpty() -> endpoint.input.serialName to null
            else -> continue
        }
        val scored = models.filter { it.idType == idType }.map { model ->
            var score = 0
            if (endpoint.path.startsWith(model.path + "/")) score += 3
            if (idRoute != null && idRoute.lowercase().removeSuffix("_id").removeSuffix("id") == model.typeName) score += 3
            if (model.typeName in endpoint.summary.lowercase().replace(" ", "") || model.typeName in endpoint.path.lowercase()) score += 2
            model to score
        }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: continue
        val winner = scored.filter { it.second == best }.singleOrNull()?.first ?: continue
        result.getOrPut(winner.path) { mutableListOf() }.add(ItemAction(endpoint, idRoute))
    }
    return result
}

/** A card of buttons for the actions that apply to item [itemId] of [collectionName]; hidden when there are none. */
fun ViewWriter.itemActionsCard(collectionName: String, itemId: String) {
    val actions = remember { adminServer().itemActions[collectionName].orEmpty() }
    shownWhen { actions().isNotEmpty() }.card.col {
        h4("Actions")
        itemActionButtons(collectionName, itemId)
    }
}

/** One button per action that applies to item [itemId] of [collectionName], each opening its dialog. */
fun ViewWriter.itemActionButtons(collectionName: String, itemId: String) {
    val actions = remember { adminServer().itemActions[collectionName].orEmpty() }
    col {
        reactive {
            clearChildren()
            for (action in actions()) {
                button {
                    text(action.title)
                    onClick(action.title) {
                        itemActionDialog(collectionName, itemId, action, adminServer.await(), adminFormModule.await())
                    }
                }
            }
        }
    }
}

/**
 * Describes [itemAction], collects any values it needs beyond the ID, and runs it on demand - every
 * action goes through here, so nothing runs from a single misplaced click.
 */
@Suppress("UNCHECKED_CAST")
private fun ViewWriter.itemActionDialog(collectionName: String, itemId: String, itemAction: ItemAction, server: ExternalLightningServer, forms: FormModule) = dialog { close ->
    col {
        h3(itemAction.title)
        itemAction.endpoint.description.takeIf { it.isNotBlank() && it != itemAction.endpoint.summary }?.let { text(it) }
        subtext("${itemAction.endpoint.method} ${itemAction.endpoint.path}")

        val inSerializer = itemAction.endpoint.input.serializer(server.registry, mapOf()) as KSerializer<Any?>
        val outSerializer = itemAction.endpoint.output.serializer(server.registry, mapOf()) as KSerializer<Any?>
        val input = Signal(if (itemAction.hasInput) inSerializer.default() else null)
        if (itemAction.hasInput) form(forms, inSerializer, input)

        val result = Signal<ActionResult?>(null)
        // Shown here rather than left to the button, which otherwise just stops, saying nothing.
        val error = Signal<String?>(null)
        shownWhen { error() != null }.themed(ErrorSemantic).text { ::content { error() ?: "" } }
        shownWhen { result() != null }.col {
            subtext("Result")
            shownWhen { result()?.value == Unit }.text("Done.")
            shownWhen { result() != null && result()?.value != Unit }.card.view(
                forms,
                (if (outSerializer.descriptor.isNullable) outSerializer else (outSerializer as KSerializer<Any>).nullable) as KSerializer<Any?>,
                remember { result()?.value }
            )
        }

        atEnd.row {
            button {
                text { ::content { if (result() == null) "Cancel" else "Close" } }
                onClick { close() }
            }
            important.button {
                text("Run")
                action = Action("Run") {
                    error.value = null
                    try {
                        val model = server.models.getValue(collectionName)
                        val idSerializer = model.idserializer as KSerializer<Any?>
                        val id = DefaultUriFormat.decodeFromString(idSerializer, itemId)
                        val fetcher = server.fetcher(adminAuthentication.await())
                        // The fetcher encodes the ID the way its transport expects, as the REST endpoints do;
                        // encoding it ourselves broke IDs with characters like FCM tokens' ':'.
                        val path = itemAction.idRoute?.let { itemAction.endpoint.path.replace("{$it}", fetcher.url(id, idSerializer)) }
                            ?: itemAction.endpoint.path
                        val output = fetcher.invoke(
                            url = path,
                            method = HttpMethod.valueOf(itemAction.endpoint.method).lightningServer,
                            inSerializer = inSerializer,
                            body = if (itemAction.idRoute == null) id else input.value,
                            outSerializer = outSerializer,
                        )
                        result.value = ActionResult(output)
                        // The action may well have changed the item; show it as it is now.
                        model.cache(adminAuthentication.await()).totallyInvalidate()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error.value = context.exceptionMessage(e)?.let { listOf(it.title, it.body).filter { it.isNotBlank() }.joinToString(": ") }
                            ?: e.message ?: "The action failed."
                    }
                }
            }
        }
    }
}

/** Wraps an action's output so that a null output still counts as having run. */
private class ActionResult(val value: Any?)
