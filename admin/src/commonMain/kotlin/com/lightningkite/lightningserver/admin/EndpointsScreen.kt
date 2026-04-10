//
// CODE REVIEW SUMMARY
// ===================
// EndpointsPage displays a filterable list of all API endpoints from the server schema.
// Each endpoint links to EndpointPage for testing/invocation.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. FILTER UX: Filter only matches path, not method. Consider allowing search by
//    method (GET, POST, etc.) or full endpoint description.
//
// 2. EMPTY STATE: No handling for when filter returns no results. Should show
//    "No endpoints match your filter" message.
//
// 3. GROUPING: Endpoints could be grouped by path prefix (e.g., /api/users/*)
//    for better organization in large schemas.
//
// 4. MISSING KDOC: No documentation explaining the page purpose or usage.
//
// 5. VISIBILITY: This page may need to be gated by adminSettings().showEndpoints
//    for consistency with the app navigation.
//
// 6. LOADING STATE: No indicator while serverSchema() is loading.
//
package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ElementWriter

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
    override fun ElementWriter.CanAddTheme.render() {
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