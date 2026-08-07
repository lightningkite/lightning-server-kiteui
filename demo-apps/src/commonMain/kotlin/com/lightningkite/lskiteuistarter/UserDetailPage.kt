// Demonstrates editing through the ModelCache write API directly (no forms engine involved
// here - that's the `forms` package's job): ModelCacheItemReadable.modify()/.delete(), field
// binding via com.lightningkite.serialization.lensPath + .asString(), and computing a diff with
// lightningdb.modification(old, new) instead of hand-writing a Modification. Also the page a
// foreign key to User opens (see forms/FormsSupport.kt's typeInfo wiring).
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.lightningdb.modification
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.serialization.asString
import com.lightningkite.serialization.lensPath
import com.lightningkite.services.data.toEmailAddress
import kotlin.uuid.Uuid

@Routable("/users/{id}")
class UserDetailPage(val id: String) : Page {
    override val title: Reactive<String> get() = Constant("User")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull
        val userId = Uuid.parse(id)
        val item = remember { session().users.item(userId) }

        // Local edit buffer, seeded once from the loaded record - editing the live cache value
        // directly would fight every background refresh (see kiteui skill pitfall #8).
        var seeded = false
        val draft = Signal(User(_id = userId, email = "pending@example.com".toEmailAddress()))
        reactive {
            val loaded = item()() ?: return@reactive
            if (!seeded) {
                seeded = true
                draft.value = loaded
            }
        }

        val elementContext = this.context
        val save = Action("Save") {
            val old = item()() ?: throw PlainTextException("User no longer exists", "Not found")
            val diff = modification(old, draft()) ?: return@Action
            item().modify(diff)
            elementContext.toast("Saved")
        }
        val delete = Action("Delete") {
            item().delete()
            elementContext.toast("Deleted")
            elementContext.pageNavigator.goBack()
        }

        scrolling.col {
            h2("Edit User")
            subtext("Bound with lensPath()/.asString() straight to the loaded record's fields, saved with lightningdb.modification(old, new) through ModelCacheItemReadable.modify().")

            shownWhen { item()() == null }.text("Loading...")

            shownWhen { item()() != null }.card.col {
                field("Name") {
                    textInput {
                        keyboardHints = KeyboardHints.title
                        content bind draft.lensPath(User.path[User_name])
                    }
                }
                field("Email") {
                    textInput {
                        keyboardHints = KeyboardHints.email
                        content bind draft.lensPath(User.path.email).asString()
                    }
                }
                field("Role") {
                    select {
                        bind(draft.lensPath(User.path.role), Constant(UserRole.entries.toList())) { it.name }
                    }
                }

                row {
                    important.button {
                        text("Save")
                        action = save
                    }
                    danger.button {
                        text("Delete")
                        action = delete
                    }
                }
            }
        }
    }
}
