package com.lightningkite.template

import com.lightningkite.UUID
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.auth.AuthType
import com.lightningkite.lightningserver.auth.Authentication
import com.lightningkite.lightningserver.auth.RequestAuth
import com.lightningkite.lightningserver.auth.proof.EmailProofEndpoints
import com.lightningkite.lightningserver.auth.proof.OneTimePasswordProofEndpoints
import com.lightningkite.lightningserver.auth.proof.PasswordProofEndpoints
import com.lightningkite.lightningserver.auth.proof.PinHandler
import com.lightningkite.lightningserver.auth.subject.AuthEndpointsForSubject
import com.lightningkite.lightningserver.core.ServerPath
import com.lightningkite.lightningserver.core.ServerPathGroup
import com.lightningkite.lightningserver.email.Email
import com.lightningkite.lightningserver.email.EmailLabeledValue
import com.lightningkite.lightningserver.exceptions.NotFoundException
import com.lightningkite.lightningserver.settings.generalSettings
import com.lightningkite.toEmailAddress
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.serialization.KSerializer


class AuthenticationEndpoints(path: ServerPath): ServerPathGroup(path){

    // Base for pins that are used in email and phone proofs
    val pins = PinHandler(Server.cache, "pins")

    // Endpoints for proofing you own a specific email for authentication
    val proofEmail = EmailProofEndpoints(
        path = path("proof/email"),
        pin = pins,
        email = Server.email,
        emailTemplate = { to, pin ->
            Email(
                subject = "${generalSettings().projectName} Log In",
                to = listOf(EmailLabeledValue(to)),
                html = createHTML(true).let {
                    it.html {
                        emailBase {
                            header("Log In Code")
                            paragraph("Your log in code is:")
                            code(pin)
                            paragraph("If you did not request this code, you can safely ignore this email.")
                        }
                    }
                }
            )
        },
        verifyEmail = { it.toEmailAddress(); true }
    )

    // Endpoints for establishing and verifying otp for a user
    val proofOtp = OneTimePasswordProofEndpoints(path("proof/otp"), Server.database, Server.cache)

    // Endpoints for establishing and validating passwords
    val proofPassword = PasswordProofEndpoints(path("proof/password"), Server.database, Server.cache)

    // Endpoints for establishing a session for a user after generating proofs
    val userAuth = AuthEndpointsForSubject(
        path("user"),
        object : Authentication.SubjectHandler<User, UUID> {
            override val name: String get() = "User"
            override val authType: AuthType get() = AuthType<User>()
            override val idSerializer: KSerializer<UUID>
                get() = Server.users.info.serialization.idSerializer
            override val subjectSerializer: KSerializer<User>
                get() = Server.users.info.serialization.serializer

            override suspend fun fetch(id: UUID): User = Server.users.info.collection().get(id) ?: throw NotFoundException()
            override suspend fun findUser(property: String, value: String): User? = when (property) {
                "email" -> Server.users.info.collection().findOne(condition { it.email eq value.toEmailAddress() }) ?: run {
                    Server.users.info.collection().insertOne(User(email = value.toEmailAddress(), name = ""))!!
                }

//                "phone" -> users.info.collection().findOne(condition { it.phone eq value })
                "_id" -> Server.users.info.collection().get(UUID.parse(value))
                else -> null
            }

            override val knownCacheTypes: List<RequestAuth.CacheKey<User, UUID, *>> = listOf(RoleCacheKey)

            override suspend fun desiredStrengthFor(result: User): Int =
                if (result.role >= UserRole.Admin) Int.MAX_VALUE else 5
        },
        database = Server.database
    )


}