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
import com.lightningkite.lightningserver.auth.OneTimePasswordProofClientEndpoints
import com.lightningkite.lightningserver.auth.proof.IdentificationAndPassword
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.ProofOption
import com.lightningkite.readable.Property
import com.lightningkite.readable.await

data class OtpProofComponent(val p: OneTimePasswordProofClientEndpoints, val type: String) : ProofComponent {
    override val name: String = "Use Authenticator App"
    override val icon: Icon = Icon.Companion.pinCode
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
                p.proveOTP(
                    IdentificationAndPassword(
                        type = type,
                        property = option.method.property ?: "",
                        value = option.value ?: "",
                        password = code.await()
                    )
                )
            )
        }
        fieldNoErrorText("One-time Password from App") {
            textInput {
                ::hint { "000000" }
                requestFocus()
                content bind code
                action = provePasswordOwnership
                keyboardHints = KeyboardHints.Companion.oneTimeCode
            }
        }
        SubtextSemantic.onNext - errorText()
        important - button {
            centered - text("Submit")
            action = provePasswordOwnership
        }
    }
}