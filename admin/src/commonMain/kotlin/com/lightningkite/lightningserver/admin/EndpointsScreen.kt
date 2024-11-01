package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.field

@Routable("endpoints")
class EndpointsScreen() : Screen {
    @QueryParameter val filter = Property<String>("")
    override fun ViewWriter.render() {
        val endpoints = shared { serverSchema().endpoints }
        col {
            field("Filter") {
                textInput { content bind filter }
            }
            expanding - recyclerView {
                children(shared {
                    val f = filter()
                    endpoints().filter { it.path.contains(f) }
                }) {
                    link {
                        text { ::content { it().method + " " + it().path } }
                        ::to { it().let { { EndpointScreen(it.path, it.method) } } }
                    }
                }
            }
        }
    }
}