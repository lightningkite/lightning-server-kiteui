//
// CODE REVIEW SUMMARY
// ===================
// DetailAdminPage is the item edit screen for a single record in a collection.
// Features: form editing, delete, cancel changes, save, and related records panel.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. FORCE UNWRAP (Line 107): `mc.insert(newItem)()!!._id` will crash if insert fails.
//    Should handle null/error gracefully with user feedback.
//
// 2. ERROR HANDLING: No try-catch around delete/save operations. Network errors
//    or validation failures should show user-friendly messages.
//
// 3. MAGIC NUMBER (Line 62): `100.rem` for rowCollapsingToColumn is hardcoded.
//    Consider extracting to a constant.
//
// 4. DELETE CONFIRMATION (Line 73): "Are you sure?" is generic. Consider showing
//    the item ID or name to confirm what's being deleted.
//
// 5. RELATED RECORDS PANEL (Lines 134-157): Iterates through ALL models and
//    properties to find references. Could be slow with many models. Consider
//    precomputing/caching reverse relationships in schema.
//
// 6. LOADING STATE: No loading indicator while fetching item data or saving.
//
// 7. MISSING KDOC: No documentation on the page's purpose or the Draft pattern.
//
// 8. ID DISPLAY: No display of the current item's ID for reference. Users may
//    need to know the ID for API calls or debugging.
//
// 9. NAVIGATION AFTER DELETE (Line 84): Goes back, but what if user came directly
//    to this page? Should have fallback to collection page.
//
package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.ConditionSerializer
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Draft
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.extensions.notNull
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.default
import com.lightningkite.services.database.serializableProperties

@Routable("collections/{collectionName}/detail/{itemId}")
class DetailAdminPage(val collectionName: String, val itemId: String) : Page {
    // by Claude - added null safety for missing collections
    private val mcOrNull = remember { adminServer().models[collectionName]?.cache(adminAuthentication()) as? ModelCache<UnknownModel, UnknownId> }
    private val mc = remember { mcOrNull()!! }

    override fun ViewWriter.render() {
        col {
            reactive {
                clearChildren()
                if (mcOrNull() == null) {
                    centered.col {
                        h2("Collection Not Found")
                        text("The collection '$collectionName' does not exist or is not accessible.")
                        button {
                            text("Go Home")
                            onClick { pageNavigator.reset(HomePage()) }
                        }
                    }
                    return@reactive
                }
                renderContent()
            }
        }
    }

    private fun RowOrCol.renderContent() {
        val item = Draft(remember {
            val mc = mc()
            val actualId = UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
            mc[actualId].notNull(mc.serializer.default().also {
                mc.serializer._id().setCopy(it, actualId)
            })
        }.flatten())
        rowCollapsingToColumn(100.rem) {
            space { reactive { item() } }
            weight(2f).scrolling.col {
                reactive {
                    clearChildren()
                    val forms = adminFormModule()
                    form(forms, mc().serializer, item)
                    atEnd.row {
                        danger.button {
                            text("Delete")
                            onClick {
                                confirmDanger("Delete", "Are you sure?") {
                                    val mc = mc()
                                    val actualId =
                                        UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
                                    mc[actualId].delete()
                                    toast {
                                        row {
                                            centered.icon(Icon.deleteForever, "Deleted")
                                            centered.text("Item has been deleted.")
                                        }
                                    }
                                    pageNavigator.goBack()
                                }
                            }
                        }
                        card.button {
                            text("Cancel")
                            ::enabled { item.changesMade() }
                            onClick {
                                confirmDanger("Cancel Changes", "Are you sure you want to undo your local changes?") {
                                    item set item.published()
                                }
                            }
                        }
                        shownWhen { item.published()._id != item()._id }.danger.button {
                            text("Delete and Re-create")
                            ::enabled { item.changesMade() }
                            onClick {
                                confirmDanger("Delete and Re-create", "Are you sure you want to delete this item then recreate it with a new ID?  This DOES COUNT as a deletion followed by a creation.") {
                                    val mc = mc()
                                    val actualId =
                                        UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
                                    val newItem = item()
                                    mc[actualId].delete()
                                    val newId = mc.insert(newItem)()!!._id
                                    toast {
                                        row {
                                            centered.icon(Icon.done, "Done")
                                            centered.text("Your changes have been saved")
                                        }
                                    }
                                    pageNavigator.replace(DetailAdminPage(collectionName, UrlProperties.encodeToString(mc.serializer._id().serializer, newId)))
                                }
                            }
                        }
                        shownWhen { item.published()._id == item()._id }.important.button {
                            text("Save")
                            ::enabled { item.changesMade() }
                            onClick {
                                item.publish()
                                toast {
                                    row {
                                        centered.icon(Icon.done, "Done")
                                        centered.text("Your changes have been saved")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            weight(1f).scrolls.col {
                val itemId = remember { item()._id }
                reactive {
                    clearChildren()
                    val mc = mc()
                    val itemId = itemId()
                    val myTypeName = mc.serializer.descriptor.serialName.substringBefore('/')
                    adminServer().models.entries.forEach { model ->
                        model.value.serializer.serializableProperties?.forEach {
                            val anno = it.serializableAnnotations.find { it.fqn == "com.lightningkite.services.data.References" } ?: return@forEach
                            val typeName = anno.values.get("references")?.let { it as? SerializableAnnotationValue.ClassValue }?.fqn ?: return@forEach
                            if (typeName != myTypeName) return@forEach
                            val reverseName = anno.values.get("reverseName")?.let { it as? SerializableAnnotationValue.StringValue }?.value?.takeUnless { it.isEmpty() }
                            val label = reverseName ?: "${model.value.serializer.displayName}'s ${it.displayName}"
                            link {
                                text(label)
                                to = { CollectionAdminPage(model.key).apply { conditionString.value = DefaultJson.encodeToString(
                                    ConditionSerializer(model.value.serializer),
                                    Condition.OnField(it as SerializableProperty<HasId<*>, UnknownId>, Condition.Equal(itemId))
                                ) } }
                            }
                        }
                    }
                }
            }
        }
    }
}