// Landing page for the forms-engine demo. Kept as plain links to separately-routable pages
// rather than tabs, per the kiteui skill's guidance against building tabs out of parallel
// shownWhen branches - each destination below is its own @Routable page.
package com.lightningkite.lskiteuistarter.forms

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive

@Routable("/forms")
class FormsIndexPage : Page {
    override val title: Reactive<String> get() = Constant("Forms Engine Demo")

    override fun ElementWriter.CanAddTheme.render() {
        col {
            h2("Forms Engine Demo")
            subtext("com.lightningkite.kiteui.forms renders and edits real, persisted models. Each link below drives a different renderer against the live server.")

            card.link {
                col {
                    h3("App Releases")
                    subtext("A plain data class: String, Boolean, LocalDate, nullable enum, nullable List<object>.")
                }
                ::to { { AppReleaseListPage() } }
            }
            card.link {
                col {
                    h3("Sealed Models")
                    subtext("SealedPolymorhphicModel - the sealed-class renderer's reason to exist.")
                }
                ::to { { SealedModelListPage() } }
            }
            card.link {
                col {
                    h3("Documents")
                    subtext("Set<String>, Map<String, String>, a foreign key to User, and real file uploads.")
                }
                ::to { { DocumentListPage() } }
            }
        }
    }
}
