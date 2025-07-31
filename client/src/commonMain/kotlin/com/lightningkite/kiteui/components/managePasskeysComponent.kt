package com.lightningkite.kiteui.components

import com.lightningkite.kiteui.locale.RenderSize
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.dp
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.eq
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningserver.auth.proof.WebAuthNCredential
import com.lightningkite.lightningserver.auth.proof.disabledAt
import com.lightningkite.lightningserver.auth.proof.subjectId
import com.lightningkite.lightningserver.db.LimitReadable
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.now
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember


fun ViewWriter.manageWebAuthNCredentialsComponent(
    webAuthNCCredentials: Reactive<ModelCache<WebAuthNCredential, String>?>,
    subjectName: Reactive<String>,
    subjectId: Reactive<String>,
) {
    val credentials: Reactive<LimitReadable<WebAuthNCredential>> = remember {
        webAuthNCCredentials.awaitNotNull()
            .query(Query(condition { it.subjectId.eq(subjectId()) }))
    }

    recyclerView {
        children (remember{ credentials()() }, { it._id }) { credential ->
            row {
                col {
                    spacing = 0.dp
                    text {
                        ::content { credential().displayName ?: "Passkey" }
                    }
                    subtext {
                        ::content { "Created on ${credential().establishedAt.renderToString(RenderSize.Numerical)}" }
                    }
                }
                expanding - space()
                centered - button {
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
