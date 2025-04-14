package com.lightningkite.kiteui.components

import com.lightningkite.kiteui.locale.RenderSize
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.dp
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.confirmDanger
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.recyclerView
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.space
import com.lightningkite.kiteui.views.direct.subtext
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.and
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.eq
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningserver.auth.proof.WebAuthNCredential
import com.lightningkite.lightningserver.auth.proof.disabledAt
import com.lightningkite.lightningserver.auth.proof.subjectId
import com.lightningkite.lightningserver.auth.proof.subjectName
import com.lightningkite.lightningserver.db.LimitReadable
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.now
import com.lightningkite.readable.Readable
import com.lightningkite.readable.await
import com.lightningkite.readable.shared


fun ViewWriter.manageWebAuthNCredentialsComponent(
    webAuthNCCredentials: Readable<ModelCache<WebAuthNCredential, String>?>,
    subjectName: Readable<String>,
    subjectId: Readable<String>,
) {
    val credentials: Readable<LimitReadable<WebAuthNCredential>> = shared {
        webAuthNCCredentials.awaitNotNull()
            .query(Query(condition { it.subjectName.eq(subjectName()) and it.subjectId.eq(subjectId()) and it.disabledAt.eq(null) }))
    }

    recyclerView {
        children (shared{ credentials()() }, { it._id }) { credential ->
            row {
                col {
                    spacing = 0.dp
                    text {
                        ::content { credential().friendlyName ?: "Passkey" }
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
