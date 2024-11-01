package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.Property
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.scrolls
import com.lightningkite.kiteui.views.direct.text

@Routable("/")
class HomeScreen : Screen {
    override fun ViewWriter.render() {
        text("HELLO WORLD")
    }
}