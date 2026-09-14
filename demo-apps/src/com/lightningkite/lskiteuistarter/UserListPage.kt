// by Claude - Demo page showing users with Recycler2.children(Reactive<Reactive<List<T>>>) overload
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.db.children
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.always
import com.lightningkite.services.database.condition

// by Claude
@Routable("/users")
class UserListPage : Page {
    override val title: Reactive<String> get() = Constant("Users")
    override fun ElementWriter.CanAddTheme.render() {
        // Double-wrapped: Reactive<Reactive<List<User>>>
        // Outer reactive tracks session changes, inner is the ModelCacheLimitReadable
        val users = remember {
            currentSessionNotNull().users.list(
                Query(condition<User> { it.always })
            )
        }

        col {
            h2("Users")
            expanding.recyclerView {
                // Uses the new children() overload for Reactive<Reactive<List<T>>>
                // Handles auto-pagination and id defaults via HasId
                children(items = users) { userReactive ->
                    card.row {
                        col {
                            h3 { ::content { userReactive().name } }
                            text { ::content { userReactive().email.raw } }
                            text { ::content { "Role: ${userReactive().role}" } }
                        }
                    }
                }
            }
        }
    }
}
