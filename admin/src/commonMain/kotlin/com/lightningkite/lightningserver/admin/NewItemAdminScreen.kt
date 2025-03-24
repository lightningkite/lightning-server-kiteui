package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atEnd
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.important
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.default

@Routable("collections/{collectionName}/new-item")
class NewItemAdminPage(val collectionName: String) : Page {

    @QueryParameter("condition")
    val conditionString: Property<String?> = Property(null)

    override fun ViewWriter.render(): ViewModifiable {
        val mc = shared { adminServer().models[collectionName]?.cache(adminAuthentication()) as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>> }
        val item = asyncReadable {
            val coerceCondition = conditionString.value?.let {
                try {
                    DefaultJson.decodeFromString(Condition.serializer(mc().serializer), it)
                } catch (e: Exception) {
                    null
                }
            } ?: Condition.Always
            Property(
                try {
                    mc().skipCache.default().coerce(coerceCondition)
                } catch (e: Exception) {
                    mc().serializer.default().coerce(coerceCondition)
                }
            )
        }.flatten()
        return scrolling - col {
            reactive {
                clearChildren()
                val forms = adminFormModule()
                form(forms, mc().serializer, item)
                atEnd - important - button {
                    text("Save")
                    onClick {
                        println("New item: ${item()}")
                        val mc = mc()
                        println("MC $mc")
                        val newItemId = mc.insert(item())()!!._id
                        println("MC $newItemId")
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, newItemId)
                        println("id $id")
                        pageNavigator.replace(DetailAdminPage(collectionName, id))
                    }
                }
            }
        }
    }
}

fun <T> T.coerce(condition: Condition<T>): T = when(condition) {
    is Condition.And<T> -> condition.conditions.fold(this) { a, b -> a.coerce(b) }
    is Condition.Or<T> -> condition.conditions.fold(this) { a, b -> a.coerce(b) }
    is Condition.Equal<T> -> condition.value
    is Condition.OnField<T, *> -> {
        val key = condition.key as SerializableProperty<T, Any?>
        val sub: Any? = key.get(this).coerce(condition.condition as Condition<Any?>)
        key.setCopy(this, sub)
    }
    else -> this
}