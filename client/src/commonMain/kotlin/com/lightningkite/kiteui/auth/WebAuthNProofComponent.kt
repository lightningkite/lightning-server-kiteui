package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.activityIndicator
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.lightningserver.sessions.ProofsCheckResult
import com.lightningkite.lightningserver.sessions.proofs.*
import kotlinx.coroutines.launch

public data class WebAuthNProofComponent(
    val p: ProofClientEndpoints.WebAuthN,
    val type: String,
    val usePasskeyUI: Boolean,
) : ProofComponent {
    override val icon: Icon = Icon.passkey
    override val via: String = p.via
    override val property: String? = p.property
    override val primaryIdentifierRequired: Boolean get() = true
    override suspend fun supported(): Boolean = ClientAuthenticator.getClientAuthenticator().webAuthNAvailable()
    override fun name(isPrimary: Boolean): String = if (usePasskeyUI && isPrimary) "Use Passkey" else "Use Security Key"

    override val earlyProof: (suspend () -> Proof?)? = if (usePasskeyUI) {
        label@{
            if (!ClientAuthenticator.getClientAuthenticator().autofillAvailable()) return@label null

            val response = p.start(Identification(type, null, null))
            val signedChallenge = ClientAuthenticator.getClientAuthenticator()
                .getWebAuthNCredentials(response.options, WebAuthNMediationType.Conditional)

            p.prove(WebAuthN.Authentication.ProveRequest(response.challengeId, signedChallenge))
        }
    } else null

    override fun render(
        to: ElementWriter.CanAddTheme,
        primaryIdentifier: UserIdentification?,
        checks: ProofsCheckResult<*>?,
        onResult: (Proof?) -> Unit,
    ) {
        to.frame {
            centered.activityIndicator()
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
                        ClientAuthenticator.getClientAuthenticator().getWebAuthNCredentials(
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

    override fun render(
        to: ElementWriter.CanAddTheme,
        primaryIdentifier: UserIdentification?,
        option: ProofOption,
        onResult: (Proof?) -> Unit,
    ): Unit = render(to, primaryIdentifier, null, onResult)

}