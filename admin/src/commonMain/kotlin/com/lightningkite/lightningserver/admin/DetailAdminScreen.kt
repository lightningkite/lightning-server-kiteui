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
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.ConditionSerializer
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Draft
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.extensions.notNull
import com.lightningkite.serialization.SerializableAnnotationValue
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.default
import com.lightningkite.serialization.serializableProperties

@Routable("collections/{collectionName}/detail/{itemId}")
class DetailAdminPage(val collectionName: String, val itemId: String) : Page {
    override fun ViewWriter.render(): ViewModifiable {
        val mc = remember { adminServer().models[collectionName]?.cache(adminAuthentication()) as ModelCache<UnknownModel, UnknownId> }
        val item = Draft(remember {
            val mc = mc()
            val actualId = UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
            mc[actualId].notNull(mc.serializer.default().also {
                mc.serializer._id().setCopy(it, actualId)
            })
        }.flatten())
        return rowCollapsingToColumn(100.rem) {
            space { reactive { item() } }
            weight(2f) - scrolls - col {
                reactive {
                    clearChildren()
                    val forms = adminFormModule()
                    form(forms, mc().serializer, item)
                    atEnd - row {
                        danger - button {
                            text("Delete")
                            onClick {
                                confirmDanger("Delete", "Are you sure?") {
                                    val mc = mc()
                                    val actualId =
                                        UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
                                    mc[actualId].delete()
                                    toast {
                                        row {
                                            centered - icon(Icon.deleteForever, "Deleted")
                                            centered - text("Item has been deleted.")
                                        }
                                    }
                                    pageNavigator.goBack()
                                }
                            }
                        }
                        card - button {
                            text("Cancel")
                            ::enabled { item.changesMade() }
                            onClick {
                                confirmDanger("Cancel Changes", "Are you sure you want to undo your local changes?") {
                                    item set item.published()
                                }
                            }
                        }
                        shownWhen { item.published()._id != item()._id } - danger - button {
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
                                            centered - icon(Icon.done, "Done")
                                            centered - text("Your changes have been saved")
                                        }
                                    }
                                    pageNavigator.replace(DetailAdminPage(collectionName, UrlProperties.encodeToString(mc.serializer._id().serializer, newId)))
                                }
                            }
                        }
                        shownWhen { item.published()._id == item()._id } - important - button {
                            text("Save")
                            ::enabled { item.changesMade() }
                            onClick {
                                item.publish()
                                toast {
                                    row {
                                        centered - icon(Icon.done, "Done")
                                        centered - text("Your changes have been saved")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            weight(1f) - scrolls - col {
                val itemId = remember { item()._id }
                reactive {
                    clearChildren()
                    val mc = mc()
                    val itemId = itemId()
                    val myTypeName = mc.serializer.descriptor.serialName.substringBefore('/')
                    adminServer().models.entries.forEach { model ->
                        model.value.serializer.serializableProperties?.forEach {
                            val anno = it.serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.References" } ?: return@forEach
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