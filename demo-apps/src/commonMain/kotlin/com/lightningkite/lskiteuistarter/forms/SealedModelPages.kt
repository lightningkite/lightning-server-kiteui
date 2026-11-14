// The sealed-polymorphic renderer's whole reason to exist: SealedClassItem is a plain Kotlin
// `sealed class` (Paragraph/Image/Header/NestedArrayTest/NestedObject), so its serializer is
// polymorphic and NormalSealedRenderer picks it up. SealedPolymorhphicModel wraps that as both a
// List<SealedClassItem> and a nullable single field, so both shapes get exercised.
package com.lightningkite.lskiteuistarter.forms

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.kiteui.forms.ColumnInfo
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.defaultColumns
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.renderTable
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.lightningdb.modification
import com.lightningkite.lskiteuistarter.SealedPolymorhphicModel
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.Query
import kotlin.uuid.Uuid

private class SealedModelTableContext(val module: FormModule, val columns: Signal<List<ColumnInfo<SealedPolymorhphicModel>>>)

@Routable("/forms/sealed")
class SealedModelListPage : Page {
    override val title: Reactive<String> get() = Constant("Sealed Models")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val ctx = rememberSuspending {
            val module = session().formModule()
            SealedModelTableContext(
                module,
                Signal(SealedPolymorhphicModel.serializer().defaultColumns().map { ColumnInfo(it, module) })
            )
        }
        val items = remember { session().sealedPolymorhphicModels.list(Query(Condition.Always)) }

        val elementContext = this.context
        val createNew = Action("New") {
            val created = session().sealedPolymorhphicModels.add(SealedPolymorhphicModel())
            elementContext.pageNavigator.navigate(SealedModelEditPage(created._id.toString()))
        }

        col {
            row {
                expanding.col { h2("Sealed Polymorphic Models") }
                button {
                    text("New")
                    action = createNew
                }
            }
            subtext("SealedClassItem is a plain sealed class; NormalSealedRenderer renders both the single nullable `header` field and the `sealedClassItems` list of it.")

            expanding.frame {
                swapping(current = { ctx() }) { c ->
                    sizeConstraints(height = 30.rem).renderTable(
                        module = c.module,
                        innerSerializer = SealedPolymorhphicModel.serializer(),
                        items = items,
                        columns = c.columns,
                        linkTo = { model -> { SealedModelEditPage(model._id.toString()) } },
                    )
                }
            }
        }
    }
}

@Routable("/forms/sealed/{id}")
class SealedModelEditPage(val id: String) : Page {
    override val title: Reactive<String> get() = Constant("Edit Sealed Model")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val modelId = Uuid.parse(id)
        val module = rememberSuspending { session().formModule() }
        val item = remember { session().sealedPolymorhphicModels.item(modelId) }

        var seeded = false
        val draft = Signal(SealedPolymorhphicModel(_id = modelId))
        reactive {
            val loaded = item()() ?: return@reactive
            if (!seeded) {
                seeded = true
                draft.value = loaded
            }
        }

        val elementContext = this.context
        val save = Action("Save") {
            val old = item()() ?: throw PlainTextException("Model no longer exists", "Not found")
            val diff = modification(old, draft()) ?: return@Action
            item().modify(diff)
            elementContext.toast("Saved")
        }
        val delete = Action("Delete") {
            item().delete()
            elementContext.toast("Deleted")
            elementContext.pageNavigator.navigate(SealedModelListPage())
        }

        scrolling.col {
            h2("Edit Sealed Model")
            shownWhen { item()() == null }.text("Loading...")
            shownWhen { item()() != null }.col {
                swapping(current = { module() }) { m ->
                    card.col { form(m, draft) }
                }
                row {
                    important.button { text("Save"); action = save }
                    danger.button { text("Delete"); action = delete }
                }
            }
        }
    }
}
