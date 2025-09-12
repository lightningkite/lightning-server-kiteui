package com.lightningkite.specialtest

import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsPlusWs

interface Api2 {
fun withHeaderCalculator(headerCalculator: suspend () -> List<Pair<String, String>>): Api2
suspend fun uploadFileForRequest(): com.lightningkite.lightningserver.files.UploadInformation
suspend fun verifyUploadedFile(input: kotlin.String): kotlin.String
suspend fun getServerHealth(): com.lightningkite.lightningserver.serverhealth.ServerHealth
suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse>
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
interface Api2TestModel : ClientModelRestEndpoints<com.lightningkite.lightningserver.demo.TestModel, com.lightningkite.Uuid>, ClientModelRestEndpointsPlusWs<com.lightningkite.lightningserver.demo.TestModel, com.lightningkite.Uuid>{
suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.demo.TestModel>): com.lightningkite.lightningserver.demo.TestModel
suspend fun dump(input: com.lightningkite.lightningserver.db.DumpRequest<com.lightningkite.lightningserver.demo.TestModel>): kotlin.String
}
val testModel: Api2TestModel
interface Api2UserAuth : UserAuthClientEndpoints<com.lightningkite.Uuid>, AuthenticatedUserAuthClientEndpoints<com.lightningkite.lightningserver.demo.User, com.lightningkite.Uuid>{
}
val userAuth: Api2UserAuth
interface Api2UserSession : ClientModelRestEndpoints<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningserver.demo.User, com.lightningkite.Uuid>, com.lightningkite.Uuid>{
suspend fun simplifiedModify(id: com.lightningkite.Uuid, input: com.lightningkite.serialization.Partial<com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningserver.demo.User, com.lightningkite.Uuid>>): com.lightningkite.lightningserver.auth.subject.Session<com.lightningkite.lightningserver.demo.User, com.lightningkite.Uuid>
}
val userSession: Api2UserSession
}
