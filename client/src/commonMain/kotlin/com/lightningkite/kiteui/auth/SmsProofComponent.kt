package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.shownWhen
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textInput
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.auth.SmsProofClientEndpoints
import com.lightningkite.lightningserver.auth.proof.FinishProof
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.ProofOption
import com.lightningkite.readable.BasicListenable
import com.lightningkite.readable.Property
import com.lightningkite.readable.await
import com.lightningkite.readable.invoke
import com.lightningkite.readable.reactive
import com.lightningkite.readable.rerunOn
import com.lightningkite.readable.sharedSuspending
import com.lightningkite.toPhoneNumber
import kotlin.time.Duration.Companion.seconds

data class SmsProofComponent(val p: SmsProofClientEndpoints) : ProofComponent {
    override val name: String = "Text Code"
    override val icon: Icon = Icon.Companion.chat
    override val via: String = p.via
    override val property: String? = p.property

    val code = Property("")
    val resendTime = 15.seconds
    override fun render(
        to: ViewWriter,
        option: ProofOption,
        onResult: (Proof?) -> Unit
    ): ViewModifiable = to.col {
        val resend = BasicListenable()
        val challenge = sharedSuspending {
            rerunOn(resend)
            p.beginSmsOwnershipProof(option.value ?: "").withTimestamp()
        }
        val proveEmailOwnership = Action("Submit", Icon.Companion.done) {
            onResult(p.provePhoneOwnership(FinishProof(challenge().value, code.await())))
        }

        centered - shownWhen { !challenge.state().ready } - text("Sending text...")
        shownWhen { challenge.state().ready } - col {
            fieldNoErrorText("Login code texted to ${option.value ?: ""}") {
                textInput {
                    ::hint { "ABCDEF" }
                    reactive { if (challenge.state().ready) requestFocus() }
                    content bind code
                    action = proveEmailOwnership
                    keyboardHints = KeyboardHints.Companion.oneTimeCodeLetters
                }
            }
            SubtextSemantic.onNext - errorText()
            important - buttonTheme - button {
                centered - text("Submit")
                action = proveEmailOwnership
            }
            button {
                ::enabled { nowBySecond() !in challenge().timestamp + 3.seconds..challenge().timestamp + resendTime }
                centered - shownWhen { nowBySecond() > challenge().timestamp + resendTime } - text("Send new code")
                centered - shownWhen { nowBySecond() < challenge().timestamp + 3.seconds } - row {
                    centered - icon(Icon.Companion.done.copy(1.rem, 1.rem), "")
                    centered - text("Sent!")
                }
                centered - shownWhen { nowBySecond() in challenge().timestamp + 3.seconds..challenge().timestamp + resendTime } - text {
                    ::content { "Can send new code in ${(challenge().timestamp + resendTime - nowBySecond()).inWholeSeconds}" }
                }
                onClick {
                    if (nowBySecond() > challenge().timestamp + resendTime)
                        resend.invokeAll()
                }
            }
        }
    }
}