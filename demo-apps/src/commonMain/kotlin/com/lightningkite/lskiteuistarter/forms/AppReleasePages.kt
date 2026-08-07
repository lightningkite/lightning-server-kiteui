// The forms engine's plain-data-class showcase: AppRelease mixes a String, a Boolean, a
// LocalDate, a nullable enum, and a nullable List<ObjectWithArray> - one field of each shape
// DataClassRenderer has to route to a different sub-renderer. The list page also exercises
// renderTable() over a live ModelCache collection.
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
import com.lightningkite.lskiteuistarter.AppRelease
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

private class AppReleaseTableContext(val module: FormModule, val columns: Signal<List<ColumnInfo<AppRelease>>>)

@Routable("/forms/app-releases")
class AppReleaseListPage : Page {
    override val title: Reactive<String> get() = Constant("App Releases")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val ctx = rememberSuspending {
            val module = session().formModule()
            AppReleaseTableContext(module, Signal(AppRelease.serializer().defaultColumns().map { ColumnInfo(it, module) }))
        }
        val releases = remember { session().appReleases.list(Query(Condition.Always)) }

        val elementContext = this.context
        val createNew = Action("New Release") {
            val created = session().appReleases.add(
                AppRelease(version = "1.0.0", platform = null, requiredUpdate = false)
            )
            elementContext.pageNavigator.navigate(AppReleaseEditPage(created._id.toString()))
        }

        col {
            row {
                expanding.col { h2("App Releases") }
                button {
                    text("New Release")
                    action = createNew
                }
            }
            subtext("A plain data class - String, Boolean, LocalDate, a nullable enum, and a nullable List<ObjectWithArray> - edited through form() and listed here with renderTable().")

            expanding.frame {
                swapping(current = { ctx() }) { c ->
                    sizeConstraints(height = 30.rem).renderTable(
                        module = c.module,
                        innerSerializer = AppRelease.serializer(),
                        items = releases,
                        columns = c.columns,
                        linkTo = { release -> { AppReleaseEditPage(release._id.toString()) } },
                    )
                }
            }
        }
    }
}

@Routable("/forms/app-releases/{id}")
class AppReleaseEditPage(val id: String) : Page {
    override val title: Reactive<String> get() = Constant("Edit App Release")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val releaseId = Uuid.parse(id)
        val module = rememberSuspending { session().formModule() }
        val item = remember { session().appReleases.item(releaseId) }

        var seeded = false
        val draft = Signal(AppRelease(_id = releaseId, version = "", requiredUpdate = false, platform = null))
        reactive {
            val loaded = item()() ?: return@reactive
            if (!seeded) {
                seeded = true
                draft.value = loaded
            }
        }

        val elementContext = this.context
        val save = Action("Save") {
            val old = item()() ?: throw PlainTextException("Release no longer exists", "Not found")
            val diff = modification(old, draft()) ?: return@Action
            item().modify(diff)
            elementContext.toast("Saved")
        }
        val delete = Action("Delete") {
            item().delete()
            elementContext.toast("Deleted")
            elementContext.pageNavigator.navigate(AppReleaseListPage())
        }

        scrolling.col {
            h2("Edit App Release")
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
