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
import com.lightningkite.lightningserver.sessions.proofs.IdentificationAndPassword
import com.lightningkite.lightningserver.sessions.proofs.Proof
import com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints
import com.lightningkite.lightningserver.sessions.proofs.ProofOption
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Signal

data class PasswordProofComponent(val p: ProofClientEndpoints.Password, val type: String) : ProofComponent {
    override val name: String = "Enter Password"
    override val icon: Icon = Icon.Companion.password
    override val via: String = p.via
    override val property: String? = p.property
    override fun render(
        to: ViewWriter,
        primaryIdentifier: UserIdentification?,
        option: ProofOption,
        onResult: (Proof?) -> Unit
    ): ViewModifiable = to.col {
        val code = Signal("")
        val provePasswordOwnership = Action("Submit", Icon.Companion.done) {
            onResult(
                p.provePasswordOwnership(
                    IdentificationAndPassword(
                        type = type,
                        property = option.method.property ?: primaryIdentifier?.property ?: "",
                        value = option.value ?: primaryIdentifier?.value ?: "",
                        password = code.await()
                    )
                )
            )
        }
        fieldNoErrorText("Password") {
            textInput {
                ::hint { "" }
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