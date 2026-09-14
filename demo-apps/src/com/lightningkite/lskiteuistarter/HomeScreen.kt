package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lskiteuistarter.sdk.currentSession
import com.lightningkite.lskiteuistarter.sdk.sessionToken
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Routable("/dashboard")
class HomePage : Page {
    override val title: Reactive<String> get() = Constant("Home")
    override fun ElementWriter.CanAddTheme.render() {

        // `reentrancyLimit` does NOT fix this (verified empirically: raising it to 20 changes
        // nothing, and no ReactiveReentrancyException is ever thrown here - the exception theory
        // was a misdiagnosis). The real problem: resetting the nav stack on this page's own first
        // render races the swapView behind navigatorView, which hasn't finished subscribing to
        // navigator.currentPage() yet at that exact moment. A same-tick reset() lands in
        // PageNavigator.stack (the write itself succeeds) but never reaches the view - the app
        // silently hangs on this screen instead of swapping to LandingPage. LandingPage's own
        // redirect avoids this only by accident, because its currentSession.await() is a real
        // suspension that happens to outlast the race window. Deferring past a real suspension
        // point does the same thing deliberately; delay(1) was measured insufficient, delay(50)
        // reliable across repeated runs under load. See ls-kiteui-starter's
        // integration-tests SmokeTest.loggedOutUserRedirectedFromHome for the reproduction.
        reactive {
            if (currentSession() == null)
                launch { delay(50); context.pageNavigator.reset(LandingPage()) }
        }

        col {
            centered.h2("Welcome to your home page")

            expanding.space()

            important.buttonTheme.button {
                centered.text("Test Notifications")
                ::enabled { fcmToken() != null }
                onClick {
                    currentSession()?.api?.fcmToken?.testInAppNotifications(fcmToken()!!)
                }
            }

            important.buttonTheme.button {
                centered.text("Logout")
                onClick {
                    try {
                        currentSession()?.api?.userAuth?.terminateSession()
                    } catch (e: Exception) {

                    } finally {
                        sessionToken set null
                        context.pageNavigator.reset(LoginPage())
                    }
                }
            }
        }
    }
}