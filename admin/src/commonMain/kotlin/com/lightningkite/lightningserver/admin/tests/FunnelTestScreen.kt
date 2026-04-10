//
// CODE REVIEW SUMMARY
// ===================
// FunnelTestPage is a debug/testing page for validating the Funnels analytics system.
// It provides buttons to simulate funnel steps, errors, and completion.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. DEBUG PAGE WARNING: This appears to be a test/debug page. Consider:
//    - Hiding it from production (e.g., check adminSettings().showEndpoints)
//    - Adding a warning banner that it's for testing only
//
// 2. NULL AUTHENTICATION (Line 20): `adminAuthentication()` can return null.
//    The fetcher call should handle this gracefully.
//
// 3. HARDCODED FUNNEL NAME (Line 22): "test" is hardcoded. Consider making it
//    configurable or showing multiple test funnels.
//
// 4. NO FEEDBACK: Buttons don't show any feedback when clicked. Consider adding
//    visual confirmation (checkmark, toast, etc.) when steps are recorded.
//
// 5. MISSING KDOC: No documentation explaining the page's purpose or how funnels work.
//
// 6. LAZY DELEGATE (Line 22): Using `by lazy` inside render() may have lifecycle
//    implications. Consider using remember {} for consistency.
//
package com.lightningkite.lightningserver.admin.tests

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.monitoring.Funnels
import com.lightningkite.kiteui.monitoring.funnel
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ElementWriter

import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.lightningserver.admin.adminAuthentication
import com.lightningkite.lightningserver.admin.adminServer
import com.lightningkite.reactive.context.reactive

@Routable("funnel")
class FunnelTestPage: Page {
    override fun ElementWriter.CanAddTheme.render() {
        col {
            reactive {
                Funnels.fetcher = adminServer().fetcher(adminAuthentication())
            }
            val funnel by lazy { funnel("test") }
            button {
                text("Start")
                onClick { funnel }
            }
            button {
                text("Step 1")
                onClick { funnel.step(1) }
            }
            button {
                text("Error A")
                onClick { funnel.error("A") }
            }
            button {
                text("Complete")
                onClick { funnel.success() }
            }
        }
    }
}