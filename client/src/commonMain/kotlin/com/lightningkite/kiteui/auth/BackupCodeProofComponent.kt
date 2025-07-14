package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textInput
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.lightningserver.auth.BackupCodeProofClientEndpoints
import com.lightningkite.lightningserver.auth.proof.IdentificationAndPassword
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.ProofOption
import com.lightningkite.readable.Property
import com.lightningkite.readable.await

data class BackupCodeProofComponent(val p: BackupCodeProofClientEndpoints, val type: String) : ProofComponent {
    override val name: String = "Enter Backup Code"
    override val icon: Icon = Icon.security
    override val via: String = p.via
    override val property: String? = p.property

    override fun render(
        to: ViewWriter,
        option: ProofOption,
        onResult: (Proof?) -> Unit
    ): ViewModifiable = to.col {
        val code = Property("")
        val provePasswordOwnership = Action("Submit", Icon.Companion.done) {
            onResult(
                p.proveBackupCode(
                    IdentificationAndPassword(
                        type = type,
                        property = option.method.property ?: "",
                        value = option.value ?: "",
                        password = code.await()
                    )
                )
            )
        }
        fieldNoErrorText("Backup Code") {
            textInput {
                ::hint { "xxxxx-xxxxx-xxxxx-xxxxx" }
                requestFocus()
                content bind code
                action = provePasswordOwnership
                keyboardHints = KeyboardHints.Companion.password
            }
        }
        SubtextSemantic.onNext - errorText()
        important - button {
            centered - text("Submit")
            action = provePasswordOwnership
        }
    }
}