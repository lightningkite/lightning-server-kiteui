package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormTypeInfo
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.decodeFromString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.ConditionSerializer
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.serialization.SerializableAnnotationValue
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.default
import com.lightningkite.serialization.serializableProperties
import kotlinx.serialization.encodeToString

@Routable("collections/{collectionName}/{itemId}")
class DetailAdminScreen(val collectionName: String, val itemId: String) : Screen {
    override fun ViewWriter.render() {
        val mc = shared { adminServer().models[collectionName] as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>> }
        val item = Draft(shared {
            val mc = mc()
            val actualId = UrlProperties.decodeFromString(mc.serializer._id().serializer, itemId)
            mc[actualId].notNull(mc.serializer.default().also {
                mc.serializer._id().setCopy(it, actualId)
            })
        }.flatten())
        rowCollapsingToColumn(100.rem) {
            weight(2f) - scrolls - col {
                reactive {
                    clearChildren()
                    card - form(adminServer().context, mc().serializer, item)
                    atEnd - important - button {
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
            weight(1f) - scrolls - col {
                val itemId = shared { item()._id }
                reactive {
                    clearChildren()
                    val mc = mc()
                    val itemId = itemId()
                    adminServer().models.entries.forEach { model ->
                        model.value.serializer.serializableProperties?.forEach {
                            val anno = it.serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.References" } ?: return@forEach
                            val typeName = anno.values.get("references")?.let { it as? SerializableAnnotationValue.ClassValue }?.fqn ?: return@forEach
                            val reverseName = anno.values.get("reverseName")?.let { it as? SerializableAnnotationValue.StringValue }?.value?.takeUnless { it.isEmpty() }
                            if (typeName != mc.serializer.descriptor.serialName.substringBefore('/')) return@forEach
                            val label = reverseName ?: "${model.value.serializer.displayName}'s ${it.displayName}"
                            link {
                                text(label)
                                to = { CollectionAdminScreen(model.key).apply { conditionString.value = DefaultJson.encodeToString(
                                    ConditionSerializer(model.value.serializer),
                                    Condition.OnField(it as SerializableProperty<HasId<*>, Comparable<Comparable<*>>>, Condition.Equal(itemId))
                                ) } }
                            }
                        }
                    }
                }
            }
        }
    }
}