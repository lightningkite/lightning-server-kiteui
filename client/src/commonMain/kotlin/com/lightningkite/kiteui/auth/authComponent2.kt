package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.ClientAuthenticator
import com.lightningkite.kiteui.WebAuthNMediationType
import com.lightningkite.kiteui.forms.KnownDeviceSecretInfoStuff
import com.lightningkite.kiteui.models.ErrorSemantic
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEachAnimated
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.auth.AuthClientEndpoints
import com.lightningkite.lightningserver.auth.AuthenticatedKnownDeviceProofClientEndpoints
import com.lightningkite.lightningserver.auth.KnownDeviceProofClientEndpoints
import com.lightningkite.lightningserver.auth.LightningServerAuthentication
import com.lightningkite.lightningserver.auth.UserAuthClientEndpoints
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.WebAuthN
import com.lightningkite.lightningserver.auth.subject.LogInRequest
import com.lightningkite.lightningserver.auth.subject.ProofsCheckResult
import com.lightningkite.lightningserver.db.debounce
import com.lightningkite.now
import com.lightningkite.readable.Action
import com.lightningkite.readable.AppScope
import com.lightningkite.readable.ImmediateWritable
import com.lightningkite.readable.Property
import com.lightningkite.readable.Readable
import com.lightningkite.readable.await
import com.lightningkite.readable.debounce
import com.lightningkite.readable.invoke
import com.lightningkite.readable.lens
import com.lightningkite.readable.reactive
import com.lightningkite.readable.shared
import com.lightningkite.readable.sharedSuspending
import com.lightningkite.toEmailAddress
import com.lightningkite.toPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.json.JsonNull.content
import kotlin.collections.plus
import kotlin.let
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

data class UserIdentification(val property: String, val value: String)

private val emailRegex = Regex("""[\w\-+._]+@(?:[\w\-]+\w\.)+[\w\-]+\w$""")
private val phoneRegex = Regex("""\+?[0-9-. ]+$""")

fun ViewWriter.authComponent2(
    endpoints: AuthClientEndpoints,
    subjectType: String = endpoints.subjects.keys.single(),
    subject: UserAuthClientEndpoints<*> = endpoints.subjects[subjectType]!!,
    supportUsernames: Boolean = false,
    knownDeviceLocalStorageName: String? = "known-device",
    filterMethods: suspend (UserIdentification?, List<ProofComponent>) -> List<ProofComponent> = { _, it -> it },
    onAuthentication: suspend (token: String) -> Unit,
): ViewModifiable = AuthComponent2(
    endpoints = endpoints,
    supportUsernames = supportUsernames,
    subjectType = subjectType,
    subject = subject,
    knownDeviceLocalStorageName = knownDeviceLocalStorageName,
    filterMethods = filterMethods,
    onAuthentication = onAuthentication,
).render(to = this)

open class AuthComponent2(
    val endpoints: AuthClientEndpoints,
    val subjectType: String = endpoints.subjects.keys.single(),
    val subject: UserAuthClientEndpoints<*> = endpoints.subjects[subjectType]!!,
    val supportUsernames: Boolean = false,
    val knownDeviceLocalStorageName: String? = "known-device",
    val filterMethods: suspend (UserIdentification?, List<ProofComponent>) -> List<ProofComponent> = { _, it -> it },
    val onAuthentication: suspend (token: String) -> Unit,
) {
    val primaryIdentifier = Property<UserIdentification?>(null)
    val proofs = Property(listOf<Proof>())
    val currentProof = Property<ProofComponent?>(null)

    val authResult: Readable<ProofsCheckResult<out Comparable<*>>?> = sharedSuspending {
        subject.checkProofs(proofs().also { if (it.isEmpty()) return@sharedSuspending null })
    }

    val proofOptions = sharedSuspending {
        val primaryIdentifier = primaryIdentifier()
        val result = authResult()
        val solved = proofs.value.mapTo(HashSet()) { it.via }
        val available = result?.options?.map { it.method.via }
        endpoints.components(subjectType)
            .filter { it.via !in solved }
            .filter { it.supported() }
            .filter {
                if(primaryIdentifier == null)
                    !it.primaryIdentifierRequired
                else if (proofs.value.isEmpty())
                    (it.property == null || it.property == primaryIdentifier.property) &&
                        (supportUsernames || primaryIdentifier.property != "username")
                else
                    true
            }
            .filter { available == null || it.via in available }
            .let { filterMethods(primaryIdentifier, it) }
    }
    val selectFirstAction = Action("Select First", Icon.done) {
        if (currentProof.value == null) {
            proofOptions().firstOrNull()?.let {
                currentProof.value = it
            }
        }
    }

    open fun render(to: ViewWriter): ViewModifiable = to.col {

        renderPrimaryIdentifier(this)

        card - progressBar {
            ::ratio {
                authResult()?.let { proofs().sumOf { it.strength } / it.strengthRequired.toFloat() } ?: 0.00f
            }
        }

        centered - shownWhen { !authResult.state().ready } - activityIndicator()
        shownWhen { authResult.state().exception != null } - ErrorSemantic.onNext - col {
            val msg = shared { authResult.state().exception?.let { exceptionToMessage(it) }}
            text { ::content { msg()?.title ?: "Error" } }
            subtext { ::content { msg()?.body ?: "" } }
        }
        shownWhen { authResult()?.readyToLogIn != true && currentProof() == null } - pickProof(this@col)
        shownWhen { currentProof() != null } - col {
            forEachAnimated(shared { listOfNotNull(currentProof()).map { it to authResult() } }.debounce(10.milliseconds)) { (it, authResult) ->
                it.render(this@forEachAnimated, primaryIdentifier.value, authResult) {
                    if (it != null) {
                        proofs.value += it
                    }
                    currentProof.value = null
                }
            }
        }
        shownWhen { authResult()?.readyToLogIn == true } - renderFinalize(this@col)
    }

    open fun renderPrimaryIdentifier(to: RowOrCol): Unit = with(to) {

        shownWhen { proofs().isEmpty() && currentProof() == null } - field(
            when {
                endpoints.emailProof != null && endpoints.smsProof != null -> "Email or Phone Number"
                endpoints.emailProof != null -> "Email"
                endpoints.smsProof != null -> "Phone Number"
                else -> "Username"
            }
        ) {
            val autoFillAvailable =
                sharedSuspending { ClientAuthenticator.getClientAuthenticator().autofillAvailable() }
            textInput {
                hint = when {
                    endpoints.emailProof != null && endpoints.smsProof != null -> "me@email.com OR 800-123-4567"
                    endpoints.emailProof != null -> "me@email.com"
                    endpoints.smsProof != null -> "800-123-4567"
                    else -> "MyUsername"
                }
                ::keyboardHints {
                    when {
                        endpoints.emailProof != null && endpoints.smsProof != null -> KeyboardHints.email
                        endpoints.emailProof != null -> KeyboardHints.email
                        endpoints.smsProof != null -> KeyboardHints.phone
                        else -> KeyboardHints.id
                    }.let {

                        if (endpoints.webAuthNProof != null && autoFillAvailable()
                        )
                            it.copy(includePasskeys = true)
                        else it
                    }
                }
                val primaryIdentifier2 = Property("")
                content bind primaryIdentifier2
                reactive {
                    primaryIdentifier()?.value?.let { primaryIdentifier2.value = it }
                }
                reactive {
                    val it = primaryIdentifier2()
                    primaryIdentifier.value = run lens@{
                        if (it.isBlank()) return@lens null
                        try {
                            if (emailRegex.matches(it)) {
                                it.trim().toEmailAddress()
                                UserIdentification("email", it.trim())
                            } else null
                        } catch (e: Exception) {
                            null
                        } ?: try {
                            if (phoneRegex.matches(it)) {
                                it.trim().toPhoneNumber()
                                UserIdentification("phone", it.trim())
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            e.printStackTrace2()
                            null
                        } ?: if (supportUsernames) UserIdentification("username", it.trim()) else null
                    }
                }
                reactive {
                    if (currentProof() == null) requestFocus()
                }
                action = selectFirstAction
            }
        }

        shownWhen { proofs().isNotEmpty() || currentProof() != null } - row {
            centered - button {
                padding = 0.2.rem
                icon(Icon.arrowBack, "Cancel")
                onClick {
                    currentProof.value = null
                    primaryIdentifier.value = null
                    proofs.value = emptyList()
                }
            }
            expanding - centered - text {
                ::content{ primaryIdentifier()?.value?.takeIf { it.isNotEmpty() } ?: "Using Passkey" }
            }
        }
    }

    open fun renderFinalize(to: ViewWriter): ViewModifiable = to.col {
        val desiredSessionLength = Property<Duration?>(1.days)
        val rememberDevice = Property(false)
        val knownDevice =
            knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }
        val knownDeviceOptions = sharedSuspending {
            endpoints.knownDeviceProof?.knownDeviceOptions()
        }
        centered - h5("Ready to login")
        shownWhen { knownDeviceOptions() != null } - row {
            centered - checkbox { checked bind rememberDevice }
            centered - text {
                content = "This is my device"
//                        ::content { "Remember this device for ${knownDeviceOptions()?.duration?.inWholeDays} days" }
            }
        }
        shownWhen { rememberDevice() || knownDeviceOptions() == null } - row {
            centered - checkbox {
                checked bind desiredSessionLength.lens(
                    get = { it != 1.days },
                    set = { if (it) null else 1.days }
                )
            }
            centered - text {
                ::content {
                    val days = authResult()?.maxExpiration?.let { it - now() }?.toDouble(DurationUnit.DAYS)
                        ?.roundToInt()
                    if (days != null) "Keep me logged in for $days days" else "Keep me logged in"
                }
            }
        }


        important - buttonTheme - button {
            centered - text("Login")
            onClick {
                val result = subject.logInV2(
                    LogInRequest(
                        proofs = proofs(),
                        expires = desiredSessionLength.await()?.let { now() + it }
                    ))

                result.session?.let {
                    onAuthentication(it)
                    (AppScope + Dispatchers.Main).launch {
                        if (rememberDevice.await()) {
                            endpoints.authenticatedKnownDeviceProof?.invoke(
                                LightningServerAuthentication(
                                    subject,
                                    subjectType,
                                    it
                                )
                            )?.establishKnownDeviceV2()?.let {
                                knownDevice?.value = KnownDeviceSecretInfoStuff(
                                    info = it,
                                    primaryIdentifier = primaryIdentifier.value?.value ?: ""
                                )
                            }
                        } else {
                            knownDevice?.value = null
                        }
                    }
                }
            }
        }
    }
    open fun pickProof(to: ViewWriter): ViewModifiable = to.col {
        val cancelIfSelected = launch {
            proofOptions()
                .mapNotNull {
                    it.earlyProof?.let { task ->
                        launch {
                            task(this@col)?.let { proofs.value += it }
                        }
                    }
                }
        }
        forEachAnimated(proofOptions) {
            card - buttonTheme - button {
                centered - sizeConstraints(width = 16.rem) - row {
                    icon(it.icon, "")
                    text(it.name)
                }
                onClick {
                    cancelIfSelected.cancel()
                    currentProof.value = it
                }
            }
        }
    }
}
