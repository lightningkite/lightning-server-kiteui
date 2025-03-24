package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.bind
import com.lightningkite.readable.invoke
import com.lightningkite.readable.lens
import com.lightningkite.readable.shared
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.children

@Routable("collections")
class AllCollectionsPage() : Page {
    override fun ViewWriter.render(): ViewModifiable {
        val models = shared { adminServer().models.entries.toList() }
        return col {
            expanding - recyclerView {
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