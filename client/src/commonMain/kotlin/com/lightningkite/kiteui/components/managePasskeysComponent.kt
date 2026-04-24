package com.lightningkite.kiteui.components

import com.lightningkite.kiteui.locale.RenderSize
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.dp
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.db.LimitReactiveList
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.services.database.*
import kotlin.time.Clock.System.now


public fun ViewWriter.manageWebAuthNCredentialsComponent(
    webAuthNCCredentials: Reactive<ModelCache<WebAuthNCredential, String>?>,
    subjectName: Reactive<String>,
    subjectId: Reactive<String>,
) {
    val credentials: Reactive<LimitReactiveList<WebAuthNCredential>> = remember {
        webAuthNCCredentials.awaitNotNull()
            .query(Query(condition { it.subjectId.eq(subjectId()) }))
    }

    recyclerView {
        children(remember { credentials()() }, { it._id }) { credential ->
            row {
                col {
                    gap = 0.dp
                    text {
                        ::content { credential().displayName }
                    }
                    subtext {
                        ::content { "Created on ${credential().establishedAt.renderToString(RenderSize.Numerical)}" }
                    }
                }
                expanding.space()
                centered.button {
                    icon(Icon.delete, "Delete")
                    onClick {
                        confirmDanger(
                            "Delete Passkey",
                            "Are you sure you want to delete this passkey? You won't be able to use it to sign in."
                        ) {
                            webAuthNCCredentials.await()?.get(credential.await()._id)?.modify(modification {
                                it.disabledAt assign now()
                            })
                        }
                    }
                }
            }
        }
    }
}
