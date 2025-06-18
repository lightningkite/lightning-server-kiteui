package com.lightningkite.template.sdk

import com.lightningkite.*
import com.lightningkite.lightningdb.*
import com.lightningkite.kiteui.*
import kotlinx.datetime.*
import com.lightningkite.serialization.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.networking.Fetcher
import kotlinx.serialization.builtins.*
import kotlinx.serialization.*

class LiveApi2(val fetcher: Fetcher): Api2 {
override fun withHeaderCalculator(headerCalculator: suspend () -> List<Pair<String, String>>): LiveApi2 = LiveApi2(fetcher.withHeaderCalculator(headerCalculator))
override suspend fun exampleEndpoint(): kotlin.Int
    = fetcher("example-endpoint", HttpMethod.GET, kotlin.Unit.serializer(), Unit, kotlin.Int.serializer())
override suspend fun getServerHealth(): com.lightningkite.lightningserver.serverhealth.ServerHealth
    = fetcher("meta/health", HttpMethod.GET, kotlin.Unit.serializer(), Unit, com.lightningkite.lightningserver.serverhealth.ServerHealth.serializer())
override suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse>
    = fetcher("meta/bulk", HttpMethod.POST, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkRequest.serializer()), input, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkResponse.serializer()))
inner class Api2EmailProofLive : EmailProofClientEndpoints by EmailProofClientEndpointsLive(fetcher, "auth/proof/email", ), Api2.Api2EmailProof{
}
override val emailProof: Api2EmailProofLive = Api2EmailProofLive()
inner class Api2FcmTokenLive : ClientModelRestEndpoints<com.lightningkite.template.FcmToken, kotlin.String> by ClientModelRestEndpointsLive(fetcher, "fcmTokens", com.lightningkite.template.FcmToken.serializer(), kotlin.String.serializer()), Api2.Api2FcmToken{
override suspend fun simplifiedModify(id: kotlin.String, input: com.lightningkite.serialization.Partial<com.lightningkite.template.FcmToken>): com.lightningkite.template.FcmToken
    = fetcher("fcmTokens/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.template.FcmToken.serializer()), input, com.lightningkite.template.FcmToken.serializer())
override suspend fun registerToken(input: kotlin.String): com.lightningkite.lightningdb.EntryChange<com.lightningkite.template.FcmToken>
    = fetcher("fcmTokens/register", HttpMethod.POST, kotlin.String.serializer(), input, com.lightningkite.lightningdb.EntryChange.serializer(com.lightningkite.template.FcmToken.serializer()))
override suspend fun clearToken(id: kotlin.String): kotlin.Boolean
    = fetcher("fcmTokens/${id.urlifyToCommaString()}/clear", HttpMethod.POST, kotlin.Unit.serializer(), Unit, kotlin.Boolean.serializer())
override suspend fun testInAppNotifications(id: kotlin.String): kotlin.String
    = fetcher("fcmTokens/${id.urlifyToCommaString()}/test", HttpMethod.POST, kotlin.Unit.serializer(), Unit, kotlin.String.serializer())
}
override val fcmToken: Api2FcmTokenLive = Api2FcmTokenLive()
inner class Api2FunnelInstanceLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "meta/funnels/instance/rest", com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2FunnelInstance{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelInstance>): com.lightningkite.lightningserver.monitoring.FunnelInstance
    = fetcher("meta/funnels/instance/rest/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer()), input, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
override suspend fun getFunnelHealth(date: kotlinx.datetime.LocalDate): List<com.lightningkite.lightningserver.monitoring.FunnelSummary>
    = fetcher("meta/funnels/summaries/${date.urlifyToCommaString()}", HttpMethod.GET, kotlin.Unit.serializer(), Unit, ListSerializer(com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer()))
override suspend fun summarizeFunnelsNow(input: kotlinx.datetime.LocalDate): kotlin.Unit
    = fetcher("meta/funnels/summarize-now", HttpMethod.POST, ContextualSerializer(kotlinx.datetime.LocalDate::class, null, arrayOf()).nullable, input, kotlin.Unit.serializer())
override suspend fun startFunnelInstance(input: com.lightningkite.lightningserver.monitoring.FunnelStart): com.lightningkite.UUID
    = fetcher("meta/funnels/start", HttpMethod.POST, com.lightningkite.lightningserver.monitoring.FunnelStart.serializer(), input, ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf()))
override suspend fun errorFunnelInstance(id: com.lightningkite.UUID, input: kotlin.String): kotlin.Unit
    = fetcher("meta/funnels/error/${id.urlifyToCommaString()}", HttpMethod.POST, kotlin.String.serializer(), input, kotlin.Unit.serializer())
override suspend fun setStepFunnelInstance(id: com.lightningkite.UUID, input: kotlin.Int): kotlin.Unit
    = fetcher("meta/funnels/step/${id.urlifyToCommaString()}", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Unit.serializer())
override suspend fun successFunnelInstance(id: com.lightningkite.UUID): kotlin.Unit
    = fetcher("meta/funnels/success/${id.urlifyToCommaString()}", HttpMethod.POST, kotlin.Unit.serializer(), Unit, kotlin.Unit.serializer())
}
override val funnelInstance: Api2FunnelInstanceLive = Api2FunnelInstanceLive()
inner class Api2FunnelSummaryLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "meta/funnels/summary/rest", com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2FunnelSummary{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelSummary>): com.lightningkite.lightningserver.monitoring.FunnelSummary
    = fetcher("meta/funnels/summary/rest/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer()), input, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
}
override val funnelSummary: Api2FunnelSummaryLive = Api2FunnelSummaryLive()
inner class Api2OneTimePasswordProofLive : AuthenticatedOneTimePasswordProofClientEndpoints by AuthenticatedOneTimePasswordProofClientEndpointsLive(fetcher, "auth/proof/otp", ), OneTimePasswordProofClientEndpoints by OneTimePasswordProofClientEndpointsLive(fetcher, "auth/proof/otp", ), Api2.Api2OneTimePasswordProof{
}
override val oneTimePasswordProof: Api2OneTimePasswordProofLive = Api2OneTimePasswordProofLive()
inner class Api2OtpSecretLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.OtpSecret, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "auth/proof/otp/secrets", com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2OtpSecret{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.OtpSecret>): com.lightningkite.lightningserver.auth.proof.OtpSecret
    = fetcher("auth/proof/otp/secrets/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer()), input, com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer())
}
override val otpSecret: Api2OtpSecretLive = Api2OtpSecretLive()
inner class Api2PasswordProofLive : AuthenticatedPasswordProofClientEndpoints by AuthenticatedPasswordProofClientEndpointsLive(fetcher, "auth/proof/password", ), PasswordProofClientEndpoints by PasswordProofClientEndpointsLive(fetcher, "auth/proof/password", ), Api2.Api2PasswordProof{
}
override val passwordProof: Api2PasswordProofLive = Api2PasswordProofLive()
inner class Api2PasswordSecretLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.PasswordSecret, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "auth/proof/password/secrets", com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2PasswordSecret{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.PasswordSecret>): com.lightningkite.lightningserver.auth.proof.PasswordSecret
    = fetcher("auth/proof/password/secrets/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer()), input, com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer())
}
override val passwordSecret: Api2PasswordSecretLive = Api2PasswordSecretLive()
inner class Api2SpammyMessageLive : ClientModelRestEndpoints<com.lightningkite.template.SpammyMessage, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "spammyMessages", com.lightningkite.template.SpammyMessage.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), ClientModelRestEndpointsPlusUpdatesWebsocket<com.lightningkite.template.SpammyMessage, com.lightningkite.UUID> by ClientModelRestEndpointsPlusUpdatesWebsocketLive(fetcher, "spammyMessages", com.lightningkite.template.SpammyMessage.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2SpammyMessage{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.template.SpammyMessage>): com.lightningkite.template.SpammyMessage
    = fetcher("spammyMessages/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.template.SpammyMessage.serializer()), input, com.lightningkite.template.SpammyMessage.serializer())
override suspend fun spamalot(): kotlin.Unit
    = fetcher("spammyMessages/spam", HttpMethod.POST, kotlin.Unit.serializer(), Unit, kotlin.Unit.serializer())
}
override val spammyMessage: Api2SpammyMessageLive = Api2SpammyMessageLive()
inner class Api2UserLive : ClientModelRestEndpoints<com.lightningkite.template.User, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "users", com.lightningkite.template.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2User{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.template.User>): com.lightningkite.template.User
    = fetcher("users/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.template.User.serializer()), input, com.lightningkite.template.User.serializer())
}
override val user: Api2UserLive = Api2UserLive()
inner class Api2UserAuthLive : UserAuthClientEndpoints<com.lightningkite.UUID> by UserAuthClientEndpointsLive(fetcher, "auth/user", ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), AuthenticatedUserAuthClientEndpoints<com.lightningkite.template.User, com.lightningkite.UUID> by AuthenticatedUserAuthClientEndpointsLive(fetcher, "auth/user", com.lightningkite.template.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2UserAuth{
}
override val userAuth: Api2UserAuthLive = Api2UserAuthLive()
inner class Api2UserSessionLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.template.User, com.lightningkite.UUID>, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "auth/user/sessions", com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.template.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2UserSession{
override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.template.User, com.lightningkite.UUID>>): com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.template.User, com.lightningkite.UUID>
    = fetcher("auth/user/sessions/${id.urlifyToCommaString()}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.template.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf()))), input, com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.template.User.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())))
}
override val userSession: Api2UserSessionLive = Api2UserSessionLive()
}
