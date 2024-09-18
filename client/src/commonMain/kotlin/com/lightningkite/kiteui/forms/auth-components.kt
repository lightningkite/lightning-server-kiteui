package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.kiteui.models.InvalidSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.subject.IdAndAuthMethods

private object Regexes {
    val email = Regex("""[\w\-+._]+@(?:[\w\-]+\w\.)+[\w\-]+\w$""")
    val phoneNumber = Regex("^\\d{3}-?\\d{3}-?\\d{4}$")
}

private fun RView.validate(condition: ReactiveContext.() -> Boolean) {
    dynamicTheme {
        if (condition()) null else InvalidSemantic
    }
}

class AuthRenderer(val endpoints: AuthClientEndpoints): Screen {
    private val log = ConsoleRoot.tag("AuthRenderer")

    private val identifier = Property("")
    private fun identification(): Identification {
        val id = identifier.value
        return Identification(
            type = endpoints.subjects.keys.first(),     // TODO: Support multiple subjects?
            property = if (id.matches(Regexes.email)) "email"
                else if (id.matches(Regexes.phoneNumber)) "phoneNumber"
                else throw IllegalArgumentException("identifier property not recognized"),
            value = id
        )
    }

    override val title = Property("Login")

    private val _message = Property("Enter your email or phone number below to log in.")
    var message by _message

    private var _onLogin: suspend (IdAndAuthMethods<*>)->Unit = { println("WARN: On Login not implemented") }
    fun onLogin(action: suspend (IdAndAuthMethods<*>)->Unit) {
        _onLogin = action
    }

    private val auth = Property<ProofEndpoints.AuthComponent?>(null)

    private fun ViewWriter.stageOne() {
        col {
            bold - h2 { ::content { title() } }
            text { ::content { _message() } }

            val identifierValid = shared {
                val id = identifier()
                id.matches(Regexes.email) or id.matches(Regexes.phoneNumber)
            }

            fieldTheme - textField {
                hint = "Email or Phone Number"
                content bind identifier
                validate { identifierValid() }
            }

            if (endpoints.emailProof != null || endpoints.smsProof != null) important - button {
                centered - text("Send Code")
                ::enabled { identifierValid() }
                onClick {
                    val id = identifier()
                    val endpoint = if (id.matches(Regexes.email)) endpoints.emailProof
                        ?: throw IllegalStateException("Email identifier was provided but no email endpoint was provided")
                    else if (id.matches(Regexes.phoneNumber)) endpoints.smsProof
                        ?: throw IllegalStateException("Phone number identifier was provided but no phone number endpoint was provided")
                    else throw IllegalStateException("Provided identifier doesn't match any known properties")

                    val component = endpoint.authComponent
                    component.start(identification())
                    auth.value = component
                }
            }

            val otherMethods = listOfNotNull(
                endpoints.passwordProof,
                endpoints.oneTimePasswordProof
            )
            if (otherMethods.isNotEmpty()) row {
                spacing = 0.25.rem

                for (method in otherMethods) {
                    val comp = method.authComponent
                    expanding - important - button {
                        ::enabled { identifierValid() }
                        centered - text(comp.name)
                        onClick {
                            comp.start(identification())
                            auth.value = comp
                        }
                    }
                }
            }
        }
    }

    private val proofs = HashMap<String, Proof>()
    private val knownDeviceToken = PersistentProperty<String?>("known-device-token", null)

    private val password = Property("")
    private fun ViewWriter.stageTwo() {
        col {
            h2 { ::content { auth()?.name ?: "" } }
            text { ::content { auth()?.message ?: ""} }

            fieldTheme - textField {
                ::hint { auth()?.hint ?: "" }
                content bind password
                validate { password().isNotBlank() }
            }

            important - button {
                ::enabled { password().isNotBlank() }
                centered - text("Log In")
                onClick {
                    val auth = auth() ?: return@onClick
                    proofs[auth.name] = auth.submit(password())

                    endpoints.knownDeviceProof?.let {
                        val token = knownDeviceToken() ?: return@let
                        val key = it.authComponent.name
                        if (!proofs.containsKey(key)) proofs[key] = it.proveKnownDevice(token)
                    }

                    val result = endpoints.subjects.values.first().logIn(
                        proofs.values.toList()
                    )

                    endpoints.authenticatedKnownDeviceProof?.let {
                        knownDeviceToken.value = it.establishKnownDevice()
                    }

                    _onLogin(result)
                }
            }
        }
    }

    override fun ViewWriter.render() {
        sizeConstraints(minWidth = 30.rem) - stack {
            onlyWhen { auth() == null } - stageOne()
            onlyWhen { auth() != null } - stageTwo()
        }
    }
}

@ViewDsl
fun ViewWriter.login(endpoints: AuthClientEndpoints, setup: AuthRenderer.() -> Unit = {}) {
    AuthRenderer(endpoints).apply {
        setup()
        render()
    }
}