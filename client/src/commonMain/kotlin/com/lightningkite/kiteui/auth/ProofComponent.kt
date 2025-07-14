package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.ProofMethodInfo
import com.lightningkite.lightningserver.auth.proof.ProofOption
import com.lightningkite.lightningserver.auth.subject.ProofsCheckResult
import com.lightningkite.readable.Constant
import com.lightningkite.readable.Readable
import com.lightningkite.toEmailAddress
import com.lightningkite.toPhoneNumber
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

interface ProofComponent {
    val property: String? get() = null
    val via: String
    val name: String
    val icon: Icon

    val primaryIdentifierRequired: Boolean get() = true
    suspend fun supported(): Boolean = true

    /**
     * Returns an asynchronous task that's attempting to authenticate using this method.
     * The only current use of this is for [WebAuthNProofComponent].
     */
    val earlyProof: (suspend (ViewWriter) -> Proof?)? get() = null

    fun render(to: ViewWriter, primaryIdentifier: UserIdentification?, checks: ProofsCheckResult<*>?, onResult: (Proof?) -> Unit): ViewModifiable {
        val primaryIdentifier = primaryIdentifier ?: return to.frame {
            text("How... how did you get here?")
        }
        val option = checks?.options?.firstOrNull { it.method.via == via } ?: ProofOption(
            ProofMethodInfo(
                via,
                primaryIdentifier.property
            ), primaryIdentifier.value)
        return render(to, option, onResult)
    }
    fun render(to: ViewWriter, option: ProofOption, onResult: (Proof?) -> Unit): ViewModifiable = TODO()
}