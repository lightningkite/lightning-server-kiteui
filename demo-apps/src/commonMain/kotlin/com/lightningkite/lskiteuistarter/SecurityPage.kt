package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.auth.SecurityComponent
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lightningserver.auth.accessToken
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember

@Routable("/security")
class SecurityPage : Page {
    override val title: Reactive<String> get() = Constant("Security")

    override fun ElementWriter.CanAddTheme.render() {
        val security = remember {
            val session = currentSession() ?: return@remember null
            val api = session.api
            SecurityComponent(
                endpoints = AuthEndpoints(
                    subjects = mapOf("User" to api.userAuth),
                    emailProof = api.userAuth.email,
                    oneTimePasswordProof = api.userAuth.totp,
                    backupCodeProof = api.userAuth.backupCode,
                    passwordProof = api.userAuth.password,
                ),
                subjectId = session.userId.toString(),
                elevate = { token ->
                    val auth = api.withHeaderCalculator(api.userAuth.accessToken(token)).userAuth
                    SecurityComponent.Elevated(
                        password = auth.password,
                        totp = SecurityComponent.TotpEndpoints(auth.totp),
                        backupCode = auth.backupCode,
                    )
                },
            )
        }
        scrolling.col {
            centered.sizeConstraints(maxWidth = 40.rem).col {
                h1("Security")
                card.frame {
                    reactive {
                        clearChildren()
                        security()?.render(this@frame)
                    }
                }
            }
        }
    }
}
