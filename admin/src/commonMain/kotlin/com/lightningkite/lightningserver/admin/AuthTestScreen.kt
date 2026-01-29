//
// CODE REVIEW SUMMARY
// ===================
// AuthTestPage and Auth2TestPage are test pages for verifying authentication components.
// They use AuthEndpoints.dummy for testing without a real server connection.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. DEBUG PAGE WARNING: These are test pages using dummy endpoints. Consider:
//    - Hiding from production navigation
//    - Adding visible warning that these are for testing only
//
// 2. COMMENTED CODE (Lines 25-28, 40-43): Remove or document the commented-out
//    schema fetching code. If it's kept for reference, explain why.
//
// 3. MISSING KDOC: No documentation explaining the purpose of these test pages
//    or the difference between AuthTestPage and Auth2TestPage.
//
// 4. MAGIC NUMBERS (Lines 29, 44-45): Hardcoded size constraints (20.rem, 25.rem,
//    5.rem). Extract to named constants or use theme values.
//
// 5. PAGE DUPLICATION: The two pages are very similar. Consider a parameterized
//    approach or documenting why both variants are needed.
//
// 6. UNUSED IMPORT: Icon is only used in Auth2TestPage. Consider organizing imports.
//
package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.auth.authComponent2
import com.lightningkite.kiteui.forms.login
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.reactive.context.reactive

@Routable("/auth")
class AuthTestPage : Page {
    override fun ViewWriter.render() {
        frame {
//            val schema = asyncReactive { fetch("https://jivie.lightningkite.com/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
//                val server = ExternalLightningServer(schema().also { println("SCHEMA: $it") })
                centered.sizeConstraints(width = 20.rem, height = 25.rem).card.login(AuthEndpoints.dummy) {
                    mainPageNavigator.navigate(HomePage())
                }
            }
        }
    }
}
@Routable("/auth2")
class Auth2TestPage : Page {
    override fun ViewWriter.render() {
        frame {
//            val schema = asyncReactive { fetch("https://jivie.lightningkite.com/meta/kschema").text().let { DefaultJson.decodeFromString(LightningServerKSchema.serializer(), it) } }
            reactive {
                clearChildren()
//                val server = ExternalLightningServer(schema().also { println("SCHEMA: $it") })
                centered.sizeConstraints(width = 20.rem).card.col {
                    centered.icon(Icon.passkey.copy(width = 5.rem, height = 5.rem), "My System")
                    authComponent2(AuthEndpoints.dummy) {
                        mainPageNavigator.navigate(HomePage())
                    }
                }
            }
        }
    }
}