package com.lightningkite.template

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.sort
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.*
import com.lightningkite.reactive.lensing.*
import com.lightningkite.readable.*
import com.lightningkite.template.sdk.currentSession
import com.lightningkite.template.sdk.sessionToken
import kotlinx.coroutines.launch

@Routable("/dashboard")
class HomePage: Page {
    override val title: Reactive<String> get() = Constant("Home")
    override fun ViewWriter.render(): ViewModifiable {

        reactive {
            if(currentSession() == null)
                pageNavigator.reset(LandingPage())
        }

        return col {
            centered - h2("Welcome to your home page")

            card - button {
                text("Start spam")
                onClick { currentSession.invoke()?.api?.spammyMessage?.spamalot() }
            }

            expanding - recyclerView {
                placer = RecyclerViewPlacerVerticalGrid(1, sizeDoesNotChange = true)
                children(remember { currentSession()?.spammyMessage?.watch(Query(orderBy = sort { it.sentAt.descending() }))?.invoke() ?: listOf() }, { it._id }){
                    card - text { ::content { it().content }}
                }
            }

            important - buttonTheme - button {
                centered - text("Logout")
                onClick {
                    try {
                        currentSession.await()?.api?.userAuth?.terminateSession()
                    } catch (e:Exception){

                    } finally {
                        sessionToken set null
                        pageNavigator.reset(LoginPage())
                    }
                }
            }
        }
    }
}