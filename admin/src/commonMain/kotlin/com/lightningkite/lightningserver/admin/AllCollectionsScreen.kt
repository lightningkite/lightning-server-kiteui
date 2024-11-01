package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.bind
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.reactive.lens
import com.lightningkite.kiteui.reactive.shared
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme

@Routable("collections")
class AllCollectionsScreen() : Screen {
    override fun ViewWriter.render() {
        val models = shared { adminServer().models.entries.toList() }
        col {
            expanding - recyclerView {
                children(models) {
                    link {
                        text { ::content { it.invoke().value.serializer.displayName } }
                        ::to { it().key.let { { CollectionAdminScreen(it) } } }
                    }
                }
            }
        }
    }
}