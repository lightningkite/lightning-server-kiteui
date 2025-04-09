package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.field

@Routable("endpoints")
class EndpointsPage() : Page {
    @QueryParameter val filter = Property<String>("")
    override fun ViewWriter.render(): ViewModifiable {
        val endpoints = shared { serverSchema().endpoints }
        return col {
            field("Filter") {
                textInput { content bind filter }
            }
            expanding - recyclerView {
                children(shared {
                    val f = filter()
                    endpoints().filter { it.path.contains(f) }
                }, id = { it.method + it.path }) {
                    link {
                        text { ::content { it().method + " " + it().path } }
                        ::to { it().let { { EndpointPage(it.path, it.method) } } }
                    }
                }
            }
        }
    }
}