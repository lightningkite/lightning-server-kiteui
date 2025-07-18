package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.ClientAuthenticator
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.WebAuthNMediationType
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.activityIndicator
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.launch
import com.lightningkite.lightningserver.auth.WebAuthNProofEndpoints
import com.lightningkite.lightningserver.auth.proof.Identification
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.ProofOption
import com.lightningkite.lightningserver.auth.proof.WebAuthN
import com.lightningkite.lightningserver.auth.subject.ProofsCheckResult
import com.lightningkite.readable.Readable
import com.lightningkite.readable.invoke
import com.lightningkite.readable.sharedSuspending
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class WebAuthNProofComponent(
    val p: WebAuthNProofEndpoints,
    val type: String,
    val usePasskeyUI: Boolean,
) : ProofComponent {
    override val name: String = "Use Passkey"
    override val icon: Icon = Icon.Companion.passkey
    override val via: String = p.via
    override val property: String? = p.property
    override val primaryIdentifierRequired: Boolean get() = true
    override suspend fun supported(): Boolean = ClientAuthenticator.getClientAuthenticator().webAuthNAvailable()

    override val earlyProof: (suspend (ViewWriter) -> Proof?)? = if (usePasskeyUI) {
        label@{
            if (!ClientAuthenticator.getClientAuthenticator().autofillAvailable()) return@label null

            val response = p.start(Identification(type, null, null))
            val signedChallenge = ClientAuthenticator.getClientAuthenticator()
                .getWebAuthNCredentials(response.options, WebAuthNMediationType.Conditional)

            p.prove(WebAuthN.Authentication.ProveRequest(response.challengeId, signedChallenge))
        }
    } else null

    override fun render(
        to: ViewWriter,
        primaryIdentifier: UserIdentification?,
        checks: ProofsCheckResult<*>?,
        onResult: (Proof?) -> Unit,
    ): ViewModifiable = to.frame {
        centered - activityIndicator()
        launch {
            try {
                val (key, getOptions) = p.start(
                    Identification(
                        type = type,
                        property = primaryIdentifier?.property,
                        value = primaryIdentifier?.value,
                    )
                )
                val signedChallenge =
                    ClientAuthenticator.Companion.getClientAuthenticator().getWebAuthNCredentials(
                        getOptions,
                        WebAuthNMediationType.Optional
                    )
                val result = p.prove(
                    WebAuthN.Authentication.ProveRequest(
                        key,
                        signedChallenge
                    )
                )
                onResult(result)
            } catch (e: Exception) {
                e.printStackTrace2()
                onResult(null)
            }
        }
    }
}