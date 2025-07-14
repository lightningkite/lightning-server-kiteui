package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.auth.authComponent2
import com.lightningkite.kiteui.forms.login
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.reactive
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.rawImage
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.auth.AuthClientEndpoints

@Routable("/auth")
class AuthTestPage : Page {
    override fun ViewWriter.render(): ViewModifiable {
        return frame {
//            val schema = asyncReadable { fetch("https://jivie.lightningkite.com/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
//                val server = ExternalLightningServer(schema().also { println("SCHEMA: $it") })
                centered - sizeConstraints(width = 20.rem, height = 25.rem) - card - login(AuthClientEndpoints.dummy) {
                    mainPageNavigator.navigate(HomePage())
                }
            }
        }
    }
}
@Routable("/auth2")
class Auth2TestPage : Page {
    override fun ViewWriter.render(): ViewModifiable {
        return frame {
//            val schema = asyncReadable { fetch("https://jivie.lightningkite.com/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
//                val server = ExternalLightningServer(schema().also { println("SCHEMA: $it") })
                centered - sizeConstraints(width = 20.rem) - card - col {
                    centered - icon(Icon.passkey.copy(width = 5.rem, height = 5.rem), "My System")
                    authComponent2(AuthClientEndpoints.dummy) {
                        mainPageNavigator.navigate(HomePage())
                    }
                }
            }
        }
    }
}