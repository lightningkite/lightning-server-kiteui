
package com.lightningkite.lightningdb.test

import com.lightningkite.*
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.*
import com.lightningkite.lightningserver.monitoring.FunnelInstance
import com.lightningkite.lightningserver.networking.Fetcher
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.*
import kotlinx.serialization.encodeToString





@GenerateDataClassPaths
@Serializable
data class ValidatedModel(
    @ExpectedPattern("[a-zA-Z ]+") @MaxLength(15) val name: String,
)
@GenerateDataClassPaths
@Serializable
data class NestedEnumTestModel(
    override val _id: UUID = UUID.random(),
    val thing: NestedEnumHolder = NestedEnumHolder()
) : HasId<UUID> {
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
    override val _id: UUID = UUID.random(),
    @Unique override var email: String,
    @Unique override val phoneNumber: String,
    var age: Long = 0,
    var friends: List<UUID> = listOf()
) : HasId<UUID>, HasEmail, HasPhoneNumber {
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
    suspend fun test5(input: com.lightningkite.UUID): com.lightningkite.UUID
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
    interface Api2FunnelInstance : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.UUID>{
        suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelInstance>): com.lightningkite.lightningserver.monitoring.FunnelInstance
        suspend fun getFunnelHealth(date: kotlinx.datetime.LocalDate): List<com.lightningkite.lightningserver.monitoring.FunnelSummary>
        suspend fun summarizeFunnelsNow(input: kotlinx.datetime.LocalDate): kotlin.Unit
        suspend fun startFunnelInstance(input: com.lightningkite.lightningserver.monitoring.FunnelStart): com.lightningkite.UUID
        suspend fun errorFunnelInstance(id: com.lightningkite.UUID, input: kotlin.String): kotlin.Unit
        suspend fun setStepFunnelInstance(id: com.lightningkite.UUID, input: kotlin.Int): kotlin.Unit
        suspend fun successFunnelInstance(id: com.lightningkite.UUID): kotlin.Unit
    }
    val funnelInstance: Api2FunnelInstance
    interface Api2FunnelSummary : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.UUID>{
        suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelSummary>): com.lightningkite.lightningserver.monitoring.FunnelSummary
    }
    val funnelSummary: Api2FunnelSummary
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
    override suspend fun test5(input: com.lightningkite.UUID): com.lightningkite.UUID
            = fetcher("sample5", HttpMethod.POST, ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf()), input, ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf()))
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
    inner class Api2FunnelInstanceLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "meta/funnels/instance/rest", com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2FunnelInstance{
        override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelInstance>): com.lightningkite.lightningserver.monitoring.FunnelInstance
                = fetcher("meta/funnels/instance/rest/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer()), input, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
        override suspend fun getFunnelHealth(date: kotlinx.datetime.LocalDate): List<com.lightningkite.lightningserver.monitoring.FunnelSummary>
                = fetcher("meta/funnels/summaries/${date}", HttpMethod.GET, kotlin.Unit.serializer(), Unit, ListSerializer(com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer()))
        override suspend fun summarizeFunnelsNow(input: kotlinx.datetime.LocalDate): kotlin.Unit
                = fetcher("meta/funnels/summarize-now", HttpMethod.POST, ContextualSerializer(kotlinx.datetime.LocalDate::class, null, arrayOf()), input, kotlin.Unit.serializer())
        override suspend fun startFunnelInstance(input: com.lightningkite.lightningserver.monitoring.FunnelStart): com.lightningkite.UUID
                = fetcher("meta/funnels/start", HttpMethod.POST, com.lightningkite.lightningserver.monitoring.FunnelStart.serializer(), input, ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf()))
        override suspend fun errorFunnelInstance(id: com.lightningkite.UUID, input: kotlin.String): kotlin.Unit
                = fetcher("meta/funnels/error/${id}", HttpMethod.POST, kotlin.String.serializer(), input, kotlin.Unit.serializer())
        override suspend fun setStepFunnelInstance(id: com.lightningkite.UUID, input: kotlin.Int): kotlin.Unit
                = fetcher("meta/funnels/step/${id}", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Unit.serializer())
        override suspend fun successFunnelInstance(id: com.lightningkite.UUID): kotlin.Unit
                = fetcher("meta/funnels/success/${id}", HttpMethod.POST, kotlin.Unit.serializer(), Unit, kotlin.Unit.serializer())
    }
    override val funnelInstance: Api2FunnelInstanceLive = Api2FunnelInstanceLive()
    inner class Api2FunnelSummaryLive : ClientModelRestEndpoints<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.UUID> by ClientModelRestEndpointsLive(fetcher, "meta/funnels/summary/rest", com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer(), ContextualSerializer(com.lightningkite.UUID::class, null, arrayOf())), Api2.Api2FunnelSummary{
        override suspend fun simplifiedModify(id: com.lightningkite.UUID, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.monitoring.FunnelSummary>): com.lightningkite.lightningserver.monitoring.FunnelSummary
                = fetcher("meta/funnels/summary/rest/${id}/simplified", HttpMethod.PATCH, com.lightningkite.serialization.Partial.serializer(com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer()), input, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
    }
    override val funnelSummary: Api2FunnelSummaryLive = Api2FunnelSummaryLive()
}

class CachedApi2(val uncached: Api2) {
    val funnelInstance: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelInstance, com.lightningkite.UUID> = ModelCache(uncached.funnelInstance, com.lightningkite.lightningserver.monitoring.FunnelInstance.serializer())
    val funnelSummary: ModelCache<com.lightningkite.lightningserver.monitoring.FunnelSummary, com.lightningkite.UUID> = ModelCache(uncached.funnelSummary, com.lightningkite.lightningserver.monitoring.FunnelSummary.serializer())
}