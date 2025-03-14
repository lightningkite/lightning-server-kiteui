package com.lightningkite.kiteui.components

import com.lightningkite.kiteui.locale.RenderSize
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.dp
import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.reactive.shared
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
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.and
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.eq
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningserver.auth.proof.PasskeyCredential
import com.lightningkite.lightningserver.auth.proof.disabledAt
import com.lightningkite.lightningserver.auth.proof.subjectId
import com.lightningkite.lightningserver.auth.proof.subjectName
import com.lightningkite.lightningserver.db.ModelCache
import kotlinx.datetime.Clock.System.now

fun ViewWriter.managePasskeysComponent(
    passkeyCredentials: Readable<ModelCache<PasskeyCredential, String>?>,
    subjectName: Readable<String>,
    subjectId: Readable<String>,
) {
    val passkeys = shared {
        passkeyCredentials()?.query(Query(condition {
            it.subjectName.eq(subjectName()) and it.subjectId.eq(subjectId()) and it.disabledAt.eq(null)
        }))?.invoke() ?: emptyList()
    }

    recyclerView {
        children(passkeys) { passkey ->
            row {
                col {
                    spacing = 0.dp
                    text {
                        ::content { passkey().friendlyName ?: "Passkey" }
                    }
                    subtext {
                        ::content { "Created on ${passkey().establishedAt.renderToString(RenderSize.Numerical)}" }
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
                            passkeyCredentials()?.get(passkey()._id)?.modify(modification {
                                it.disabledAt assign now()
                            })
                        }
                    }
                }
            }
        }
    }
}
