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
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.condition
import com.lightningkite.services.database.eq
import com.lightningkite.services.database.modification
import com.lightningkite.lightningserver.sessions.proofs.WebAuthNCredential
import com.lightningkite.lightningserver.sessions.proofs.disabledAt
import com.lightningkite.lightningserver.sessions.proofs.subjectId
import com.lightningkite.lightningserver.db.LimitReadable
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlin.time.Clock.System.now


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
                    gap = 0.dp
                    text {
                        ::content { credential().displayName ?: "Passkey" }
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
