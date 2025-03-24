package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.monitoring.Funnels
import com.lightningkite.kiteui.monitoring.funnel
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.reactive
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.text

@Routable("funnel")
class FunnelTestPage: Page {
    override fun ViewWriter.render(): ViewModifiable = col {
        reactive {
            Funnels.fetcher = adminServer().fetcher("", adminAuthentication())
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