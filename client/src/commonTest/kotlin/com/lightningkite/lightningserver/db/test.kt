
package com.lightningkite.lightningdb.test

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.services.database.*
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsLive
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.networking.Fetcher
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer


@GenerateDataClassPaths
@Serializable
data class ValidatedModel(
    @ExpectedPattern("[a-zA-Z ]+") @MaxLength(15) val name: String,
)
@GenerateDataClassPaths
@Serializable
data class NestedEnumTestModel(
    override val _id: Uuid = Uuid.random(),
    val thing: NestedEnumHolder = NestedEnumHolder()
) : HasId<Uuid> {
    companion object
}

@GenerateDataClassPaths
@Serializable
data class NestedEnumHolder(
    val enumValue: TestEnum = TestEnum.One
)
@GenerateDataClassPaths()
@Serializable
data class User(
    override val _id: Uuid = Uuid.random(),
    @Unique override var email: String,
    @Unique override val phoneNumber: String,
    var age: Long = 0,
    var friends: List<Uuid> = listOf()
) : HasId<Uuid>, HasEmail, HasPhoneNumber {
    companion object
}
@Serializable
enum class TestEnum { One, Two }



interface Api2 {
    fun withHeaderCalculator(headerCalculator: suspend () -> List<Pair<String, String>>): Api2
    suspend fun uploadFileForRequest(): com.lightningkite.lightningserver.files.UploadInformation
    suspend fun verifyUploadedFile(input: kotlin.String): kotlin.String
    suspend fun consumeFile(input: com.lightningkite.lightningserver.files.ServerFile): kotlin.String
    suspend fun test1(input: kotlin.Int): kotlin.Int
    suspend fun test2(input: kotlin.Int): kotlin.Int
    suspend fun test3(input: kotlin.Int): kotlin.Int
    suspend fun test4(input: com.lightningkite.lightningdb.test.ValidatedModel): com.lightningkite.lightningdb.test.ValidatedModel
    suspend fun test5(input: com.lightningkite.Uuid): com.lightningkite.Uuid
    suspend fun test7(input: com.lightningkite.lightningdb.test.NestedEnumTestModel): com.lightningkite.lightningdb.test.NestedEnumTestModel
    suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse>
    suspend fun getServerHealth(): com.lightningkite.lightningserver.serverhealth.ServerHealth
    fun sampleWebSocket(num: kotlin.Int): TypedWebSocket<kotlin.Int, kotlin.Int>
    interface Api2Auth {
        suspend fun refreshToken(): kotlin.String
        suspend fun getSelf(): com.lightningkite.lightningdb.test.User
        suspend fun anonymousToken(): kotlin.String
        suspend fun emailLoginLink(input: kotlin.String): kotlin.Unit
        suspend fun emailPINLogin(input: com.lightningkite.lightningserver.auth.old.EmailPinLogin): kotlin.String
    }
    val auth: Api2Auth
    interface Api2EmailProof : EmailProofClientEndpoints{
    }
    val emailProof: Api2EmailProof
    interface Api2FunnelInstance : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.Uuid>{
        suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelInstance>): com.lightningkite.lightningserver.monitoring.FunnelInstance
        suspend fun getFunnelHealth(date: kotlinx.datetime.LocalDate): List<com.lightningkite.lightningserver.monitoring.FunnelSummary>
        suspend fun summarizeFunnelsNow(input: kotlinx.datetime.LocalDate): kotlin.Unit
        suspend fun startFunnelInstance(input: com.lightningkite.lightningserver.monitoring.FunnelStart): com.lightningkite.Uuid
        suspend fun errorFunnelInstance(id: com.lightningkite.Uuid, input: kotlin.String): kotlin.Unit
        suspend fun setStepFunnelInstance(id: com.lightningkite.Uuid, input: kotlin.Int): kotlin.Unit
        suspend fun successFunnelInstance(id: com.lightningkite.Uuid): kotlin.Unit
    }
    val funnelInstance: Api2FunnelInstance
    interface Api2FunnelSummary : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.Uuid>{
        suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelSummary>): com.lightningkite.lightningserver.monitoring.FunnelSummary
    }
    val funnelSummary: Api2FunnelSummary
    interface Api2KnownDeviceProof : AuthenticatedKnownDeviceProofClientEndpoints, KnownDeviceProofClientEndpoints{
    }
    val knownDeviceProof: Api2KnownDeviceProof
    interface Api2KnownDeviceSecret : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret, com.lightningkite.Uuid>{
        suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret>): com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret
    }
    val knownDeviceSecret: Api2KnownDeviceSecret
    interface Api2OneTimePasswordProof : AuthenticatedOneTimePasswordProofClientEndpoints, OneTimePasswordProofClientEndpoints{
    }
    val oneTimePasswordProof: Api2OneTimePasswordProof
    interface Api2OtpSecret : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.OtpSecret, com.lightningkite.Uuid>{
        suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.OtpSecret>): com.lightningkite.lightningserver.auth.proof.OtpSecret
    }
    val otpSecret: Api2OtpSecret
    interface Api2PasswordProof : AuthenticatedPasswordProofClientEndpoints, PasswordProofClientEndpoints{
    }
    val passwordProof: Api2PasswordProof
    interface Api2PasswordSecret : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.PasswordSecret, com.lightningkite.Uuid>{
        suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.PasswordSecret>): com.lightningkite.lightningserver.auth.proof.PasswordSecret
    }
    val passwordSecret: Api2PasswordSecret
    interface Api2SmsProof : SmsProofClientEndpoints{
    }
    val smsProof: Api2SmsProof
    interface Api2UserAuth : UserAuthClientEndpoints<com.lightningkite.Uuid>, AuthenticatedUserAuthClientEndpoints<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>{
    }
    val userAuth: Api2UserAuth
    interface Api2UserSession : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>, com.lightningkite.Uuid>{
        suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>>): com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>
    }
    val userSession: Api2UserSession
}
class LiveApi2(val fetcher: Fetcher): Api2 {
    override fun withHeaderCalculator(headerCalculator: suspend () -> List<Pair<String, String>>): LiveApi2 = LiveApi2(fetcher.withHeaderCalculator(headerCalculator))
    override suspend fun uploadFileForRequest(): com.lightningkite.lightningserver.files.UploadInformation
            = fetcher("upload-early", HttpMethod.GET, kotlin.Unit.serializer(), Unit, com.lightningkite.lightningserver.files.UploadInformation.serializer())
    override suspend fun verifyUploadedFile(input: kotlin.String): kotlin.String
            = fetcher("upload-early/verify", HttpMethod.POST, kotlin.String.serializer(), input, kotlin.String.serializer())
    override suspend fun consumeFile(input: com.lightningkite.lightningserver.files.ServerFile): kotlin.String
            = fetcher("consume-file", HttpMethod.POST, ContextualSerializer(com.lightningkite.lightningserver.files.ServerFile::class, null, arrayOf()), input, kotlin.String.serializer())
    override suspend fun test1(input: kotlin.Int): kotlin.Int
            = fetcher("sample1", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Int.serializer())
    override suspend fun test2(input: kotlin.Int): kotlin.Int
            = fetcher("sample2", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Int.serializer())
    override suspend fun test3(input: kotlin.Int): kotlin.Int
            = fetcher("sample3", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Int.serializer())
    override suspend fun test4(input: com.lightningkite.lightningdb.test.ValidatedModel): com.lightningkite.lightningdb.test.ValidatedModel
            = fetcher("sample4", HttpMethod.POST, com.lightningkite.lightningdb.test.ValidatedModel.serializer(), input, com.lightningkite.lightningdb.test.ValidatedModel.serializer())
    override suspend fun test5(input: com.lightningkite.Uuid): com.lightningkite.Uuid
            = fetcher("sample5", HttpMethod.POST, ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf()), input, ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf()))
    override suspend fun test7(input: com.lightningkite.lightningdb.test.NestedEnumTestModel): com.lightningkite.lightningdb.test.NestedEnumTestModel
            = fetcher("sample6", HttpMethod.POST, com.lightningkite.lightningdb.test.NestedEnumTestModel.serializer(), input, com.lightningkite.lightningdb.test.NestedEnumTestModel.serializer())
    override suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse>
            = fetcher("bulk", HttpMethod.POST, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkRequest.serializer()), input, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkResponse.serializer()))
    override suspend fun getServerHealth(): com.lightningkite.lightningserver.serverhealth.ServerHealth
            = fetcher("meta/health", HttpMethod.GET, kotlin.Unit.serializer(), Unit, com.lightningkite.lightningserver.serverhealth.ServerHealth.serializer())
    override fun sampleWebSocket(num: kotlin.Int): TypedWebSocket<kotlin.Int, kotlin.Int>
            = fetcher.websocket("ws-test/${num}", kotlin.Int.serializer(), kotlin.Int.serializer())
    inner class Api2AuthLive : Api2.Api2Auth{
        override suspend fun refreshToken(): kotlin.String
                = fetcher("auth/refresh-token", HttpMethod.GET, kotlin.Unit.serializer(), Unit, kotlin.String.serializer())
        override suspend fun getSelf(): com.lightningkite.lightningdb.test.User
                = fetcher("auth/self", HttpMethod.GET, kotlin.Unit.serializer(), Unit, com.lightningkite.lightningdb.test.User.serializer())
        override suspend fun anonymousToken(): kotlin.String
                = fetcher("auth/anonymous", HttpMethod.GET, kotlin.Unit.serializer(), Unit, kotlin.String.serializer())
        override suspend fun emailLoginLink(input: kotlin.String): kotlin.Unit
                = fetcher("auth/login-email", HttpMethod.POST, kotlin.String.serializer(), input, kotlin.Unit.serializer())
        override suspend fun emailPINLogin(input: com.lightningkite.lightningserver.auth.old.EmailPinLogin): kotlin.String
                = fetcher("auth/login-email-pin", HttpMethod.POST, com.lightningkite.lightningserver.auth.old.EmailPinLogin.serializer(), input, kotlin.String.serializer())
    }
    override val auth: Api2AuthLive = Api2AuthLive()
    inner class Api2EmailProofLive : EmailProofClientEndpoints by EmailProofClientEndpointsLive(fetcher, "proofEmail", ), Api2.Api2EmailProof{
    }
    override val emailProof: Api2EmailProofLive = Api2EmailProofLive()
    inner class Api2FunnelInstanceLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.Uuid> by ClientModelRestEndpointsLive(fetcher, "meta/funnels/instance/rest", com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2FunnelInstance{
        override suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelInstance>): com.lightningkite.lightningserver.monitoring.FunnelInstance
                = fetcher("meta/funnels/instance/rest/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer()), input, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
        override suspend fun getFunnelHealth(date: kotlinx.datetime.LocalDate): List<com.lightningkite.lightningserver.monitoring.FunnelSummary>
                = fetcher("meta/funnels/summaries/${date}", HttpMethod.GET, kotlin.Unit.serializer(), Unit, ListSerializer(com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer()))
        override suspend fun summarizeFunnelsNow(input: kotlinx.datetime.LocalDate): kotlin.Unit
                = fetcher("meta/funnels/summarize-now", HttpMethod.POST, ContextualSerializer(kotlinx.datetime.LocalDate::class, null, arrayOf()), input, kotlin.Unit.serializer())
        override suspend fun startFunnelInstance(input: com.lightningkite.lightningserver.monitoring.FunnelStart): com.lightningkite.Uuid
                = fetcher("meta/funnels/start", HttpMethod.POST, com.lightningkite.lightningserver.monitoring.FunnelStart.serializer(), input, ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf()))
        override suspend fun errorFunnelInstance(id: com.lightningkite.Uuid, input: kotlin.String): kotlin.Unit
                = fetcher("meta/funnels/error/${id}", HttpMethod.POST, kotlin.String.serializer(), input, kotlin.Unit.serializer())
        override suspend fun setStepFunnelInstance(id: com.lightningkite.Uuid, input: kotlin.Int): kotlin.Unit
                = fetcher("meta/funnels/step/${id}", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Unit.serializer())
        override suspend fun successFunnelInstance(id: com.lightningkite.Uuid): kotlin.Unit
                = fetcher("meta/funnels/success/${id}", HttpMethod.POST, kotlin.Unit.serializer(), Unit, kotlin.Unit.serializer())
    }
    override val funnelInstance: Api2FunnelInstanceLive = Api2FunnelInstanceLive()
    inner class Api2FunnelSummaryLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.Uuid> by ClientModelRestEndpointsLive(fetcher, "meta/funnels/summary/rest", com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2FunnelSummary{
        override suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelSummary>): com.lightningkite.lightningserver.monitoring.FunnelSummary
                = fetcher("meta/funnels/summary/rest/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer()), input, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
    }
    override val funnelSummary: Api2FunnelSummaryLive = Api2FunnelSummaryLive()
    inner class Api2KnownDeviceProofLive : AuthenticatedKnownDeviceProofClientEndpoints by AuthenticatedKnownDeviceProofClientEndpointsLive(fetcher, "proofKnown", ), KnownDeviceProofClientEndpoints by KnownDeviceProofClientEndpointsLive(fetcher, "proofKnown", ), Api2.Api2KnownDeviceProof{
    }
    override val knownDeviceProof: Api2KnownDeviceProofLive = Api2KnownDeviceProofLive()
    inner class Api2KnownDeviceSecretLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret, com.lightningkite.Uuid> by ClientModelRestEndpointsLive(fetcher, "proofKnown/secrets", com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2KnownDeviceSecret{
        override suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret>): com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret
                = fetcher("proofKnown/secrets/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret.serializer()), input, com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret.serializer())
    }
    override val knownDeviceSecret: Api2KnownDeviceSecretLive = Api2KnownDeviceSecretLive()
    inner class Api2OneTimePasswordProofLive : AuthenticatedOneTimePasswordProofClientEndpoints by AuthenticatedOneTimePasswordProofClientEndpointsLive(fetcher, "proofOtp", ), OneTimePasswordProofClientEndpoints by OneTimePasswordProofClientEndpointsLive(fetcher, "proofOtp", ), Api2.Api2OneTimePasswordProof{
    }
    override val oneTimePasswordProof: Api2OneTimePasswordProofLive = Api2OneTimePasswordProofLive()
    inner class Api2OtpSecretLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.OtpSecret, com.lightningkite.Uuid> by ClientModelRestEndpointsLive(fetcher, "proofOtp/secrets", com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2OtpSecret{
        override suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.OtpSecret>): com.lightningkite.lightningserver.auth.proof.OtpSecret
                = fetcher("proofOtp/secrets/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer()), input, com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer())
    }
    override val otpSecret: Api2OtpSecretLive = Api2OtpSecretLive()
    inner class Api2PasswordProofLive : AuthenticatedPasswordProofClientEndpoints by AuthenticatedPasswordProofClientEndpointsLive(fetcher, "proofPassword", ), PasswordProofClientEndpoints by PasswordProofClientEndpointsLive(fetcher, "proofPassword", ), Api2.Api2PasswordProof{
    }
    override val passwordProof: Api2PasswordProofLive = Api2PasswordProofLive()
    inner class Api2PasswordSecretLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.proof.PasswordSecret, com.lightningkite.Uuid> by ClientModelRestEndpointsLive(fetcher, "proofPassword/secrets", com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2PasswordSecret{
        override suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.proof.PasswordSecret>): com.lightningkite.lightningserver.auth.proof.PasswordSecret
                = fetcher("proofPassword/secrets/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer()), input, com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer())
    }
    override val passwordSecret: Api2PasswordSecretLive = Api2PasswordSecretLive()
    inner class Api2SmsProofLive : SmsProofClientEndpoints by SmsProofClientEndpointsLive(fetcher, "proofSms", ), Api2.Api2SmsProof{
    }
    override val smsProof: Api2SmsProofLive = Api2SmsProofLive()
    inner class Api2UserAuthLive : UserAuthClientEndpoints<com.lightningkite.Uuid> by UserAuthClientEndpointsLive(fetcher, "UserSubject", ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), AuthenticatedUserAuthClientEndpoints<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid> by AuthenticatedUserAuthClientEndpointsLive(fetcher, "UserSubject", com.lightningkite.lightningdb.test.User.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2UserAuth{
    }
    override val userAuth: Api2UserAuthLive = Api2UserAuthLive()
    inner class Api2UserSessionLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>, com.lightningkite.Uuid> by ClientModelRestEndpointsLive(fetcher, "UserSubject/sessions", com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.lightningdb.test.User.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())), Api2.Api2UserSession{
        override suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>>): com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>
                = fetcher("UserSubject/sessions/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.lightningdb.test.User.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf()))), input, com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.lightningdb.test.User.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())))
    }
    override val userSession: Api2UserSessionLive = Api2UserSessionLive()
}
class CachedApi2(val uncached: Api2) {
    val funnelInstance: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.Uuid> = ModelCache(uncached.funnelInstance, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
    val funnelSummary: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.Uuid> = ModelCache(uncached.funnelSummary, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
    val knownDeviceSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret, com.lightningkite.Uuid> = ModelCache(uncached.knownDeviceSecret, com.lightningkite.lightningserver.auth.proof.KnownDeviceSecret.serializer())
    val otpSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.OtpSecret, com.lightningkite.Uuid> = ModelCache(uncached.otpSecret, com.lightningkite.lightningserver.auth.proof.OtpSecret.serializer())
    val passwordSecret: ModelCache<com.lightningkite.lightningserver.auth.proof.PasswordSecret, com.lightningkite.Uuid> = ModelCache(uncached.passwordSecret, com.lightningkite.lightningserver.auth.proof.PasswordSecret.serializer())
    val userSession: ModelCache<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningdb.test.User, com.lightningkite.Uuid>, com.lightningkite.Uuid> = ModelCache(uncached.userSession, com.lightningkite.lightningserver.auth.subject.Session.serializer(com.lightningkite.lightningdb.test.User.serializer(), ContextualSerializer(com.lightningkite.Uuid::class, null, arrayOf())))
}