// by Claude - adapted from ls-kiteui-starter LoginPage for AuthComponent2 API
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.auth.AuthComponent2
import com.lightningkite.kiteui.models.SizeConstraints
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lskiteuistarter.sdk.*
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*

@Routable("/login")
class LoginPage : Page, UseFullPage {
    override val title: Reactive<String> get() = Constant("Home")

    companion object {
        const val SECRET_FOR_API_SELECTOR = "i am a dev"
    }

    val backendSelectorEnabled = PersistentProperty("backendSelectorEnabled", false)

    override fun ViewWriter.render() {

        val authUI = remember {
            val api = selectedApi().api
            AuthComponent2(
                endpoints = AuthEndpoints(
                    subjects = mapOf("User" to api.userAuth),
                    emailProof = api.userAuth.email,
                    oneTimePasswordProof = api.userAuth.totp,
                    backupCodeProof = api.userAuth.backupCode,
                    passwordProof = api.userAuth.password,
                ),
                subjectType = "User",
                subject = api.userAuth,
                onAuthentication = { token ->
                    sessionToken set token
                    pageNavigator.reset(HomePage())
                }
            )
        }

        col {
            reactive {
                if (authUI().rawPrimaryInput() == SECRET_FOR_API_SELECTOR) backendSelectorEnabled.value = true
            }

            centered.sizedBox(SizeConstraints(maxWidth = 40.rem)).scrolling.col {
                centered.h4("Lightning Server and KiteUI Template")
                centered.text("This template is your bare bones starting point")
                centered.text("Sign in to get started")

                shownWhen { backendSelectorEnabled() }.field("Server") {
                    select {
                        bind(selectedApi, ApiOption.entries.toList().let(::Constant)) { it.apiName }
                    }
                }

                frame {
                    reactive {
                        clearChildren()
                        authUI().render(this@frame)
                    }
                }
            }
        }

    }
}
