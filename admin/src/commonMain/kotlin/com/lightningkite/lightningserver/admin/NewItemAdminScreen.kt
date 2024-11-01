package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.reactive.*
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
class NewItemAdminScreen(val collectionName: String) : Screen {

    @QueryParameter("condition")
    val conditionString: Property<String?> = Property(null)

    override fun ViewWriter.render() {
        val mc = shared { adminServer().models[collectionName] as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>> }
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
        scrolls - col {
            reactive {
                clearChildren()
                card - form(adminServer().context, mc().serializer, item)
                atEnd - important - button {
                    text("Save")
                    onClick {
                        val mc = mc()
                        val newItemId = mc.insert(item())()!!._id
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, newItemId)
                        screenNavigator.replace(DetailAdminScreen(collectionName, id))
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
//    is Condition.Exists<*> -> TODO()
//    is Condition.FullTextSearch<*> -> TODO()
//    is Condition.GeoDistance -> TODO()
//    is Condition.GreaterThan<*> -> TODO()
//    is Condition.GreaterThanOrEqual<*> -> TODO()
//    is Condition.IfNotNull<*> -> TODO()
//    is Condition.Inside<*> -> TODO()
//    is Condition.IntBitsAnyClear -> TODO()
//    is Condition.IntBitsAnySet -> TODO()
//    is Condition.IntBitsClear -> TODO()
//    is Condition.IntBitsSet -> TODO()
//    is Condition.LessThan<*> -> TODO()
//    is Condition.LessThanOrEqual<*> -> TODO()
//    is Condition.ListAllElements<*> -> TODO()
//    is Condition.ListAnyElements<*> -> TODO()
//    is Condition.ListSizesEquals<*> -> TODO()
//    Condition.Never -> TODO()
//    is Condition.Not<*> -> TODO()
//    is Condition.NotEqual<*> -> TODO()
//    is Condition.NotInside<*> -> TODO()
//    is Condition.OnField<*, *> -> TODO()
//    is Condition.OnKey<*> -> TODO()
//    is Condition.RawStringContains<*> -> TODO()
//    is Condition.RegexMatches -> TODO()
//    is Condition.SetAllElements<*> -> TODO()
//    is Condition.SetAnyElements<*> -> TODO()
//    is Condition.SetSizesEquals<*> -> TODO()
//    is Condition.StringContains -> TODO()
}