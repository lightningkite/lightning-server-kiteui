package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.navigation.Page

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.link
import com.lightningkite.kiteui.views.direct.recyclerView
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.core.remember

@Routable("collections")
class AllCollectionsPage() : Page {
    override fun ViewWriter.render() {
        val models = remember { adminServer().models.entries.toList() }
        col {
            expanding.recyclerView {
                children(models, id = {it.key}) {
                    link {
                        text { ::content { it.invoke().value.serializer.displayName } }
                        ::to { it().key.let { { CollectionAdminPage(it) } } }
                    }
                }
            }
        }
    }
}