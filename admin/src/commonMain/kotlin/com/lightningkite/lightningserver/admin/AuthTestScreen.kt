package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.login
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.mainScreenNavigator
import com.lightningkite.kiteui.reactive.reactive
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.lightningserver.auth.AuthClientEndpoints

@Routable("/auth")
class AuthTestScreen : Screen {
    override fun ViewWriter.render() {
        stack {
//            val schema = asyncReadable { fetch("https://jivie.lightningkite.com/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
//                val server = ExternalLightningServer(schema().also { println("SCHEMA: $it") })
                centered - sizeConstraints(width = 20.rem, height = 25.rem) - card - login(AuthClientEndpoints.dummy) {
                    mainScreenNavigator.navigate(HomeScreen())
                }
            }
        }
    }
}