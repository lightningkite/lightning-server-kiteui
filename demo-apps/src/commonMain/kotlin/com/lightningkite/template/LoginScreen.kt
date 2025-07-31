package com.lightningkite.template

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.AuthComponent
import com.lightningkite.kiteui.models.SizeConstraints
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.auth.AuthClientEndpoints
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.*
import com.lightningkite.reactive.lensing.*
import com.lightningkite.readable.*
import com.lightningkite.template.sdk.currentSession
import com.lightningkite.template.sdk.selectedApi
import com.lightningkite.template.sdk.sessionToken
import kotlinx.coroutines.launch

@Routable("/login")
class LoginPage : Page, UseFullPage {
    override val title: Reactive<String> get() = Constant("Home")
    override fun ViewWriter.render(): ViewModifiable {

        val authUI = remember {
            val api = selectedApi().api
            val anon = AuthComponent(
                endpoints = AuthClientEndpoints(
                    subjects = mapOf("user" to api.userAuth),
                    authenticatedSubjects = emptyMap(),
                    emailProof = api.emailProof,
                    oneTimePasswordProof = api.oneTimePasswordProof,
                    passwordProof = api.passwordProof,
                ),
                subjectPath = "user",
                subject = api.userAuth
            ) { token ->
                sessionToken set token
                pageNavigator.reset(HomePage())
            }
            anon
        }

        return frame {
            centered - sizedBox(SizeConstraints(maxWidth = 40.rem)) - scrolls - col {

                centered - h4("Lightning Server and KiteUI Template")

                centered - text("This template is your bare bones starting point")

                centered - text("Sign in to get started")


                stack {
                    reactive {
                        clearChildren()
                        with(authUI()) {
                            render()
                        }
                    }
                }
            }
        }

    }
}