package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.navigation.*

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atEnd
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.important
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database._id
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.asyncReactive
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.default

@Routable("collections/{collectionName}/new-item")
class NewItemAdminPage(val collectionName: String) : Page {

    @QueryParameter("condition")
    val conditionString: Signal<String?> = Signal(null)

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
        val item = asyncReactive {
            val coerceCondition = conditionString.value?.let {
                try {
                    DefaultJson.decodeFromString(Condition.serializer(mc().serializer), it)
                } catch (e: Exception) {
                    null
                }
            } ?: Condition.Always
            Signal(
                try {
                    mc().skipCache.default().coerce(coerceCondition)
                } catch (e: Exception) {
                    mc().serializer.default().coerce(coerceCondition)
                }
            )
        }.flatten()
        scrolling.col {
            reactive {
                clearChildren()
                val forms = adminFormModule()
                form(forms, mc().serializer, item)
                atEnd.important.button {
                    text("Save")
                    onClick {
                        val mc = mc()
                        val newItemId = mc.insert(item())()!!._id
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, newItemId)
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