package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember

@Routable("endpoints")
class EndpointsPage() : Page {
    @QueryParameter val filter = Signal<String>("")
    override fun ViewWriter.render() {
        val endpoints = remember { serverSchema().endpoints }
        col {
            field("Filter") {
                textInput { content bind filter }
            }
            expanding.recyclerView {
                children(remember {
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