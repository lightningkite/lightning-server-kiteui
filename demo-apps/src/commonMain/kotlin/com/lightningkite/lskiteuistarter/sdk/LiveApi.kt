package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.HttpMethod
import com.lightningkite.lightningserver.typed.Fetcher
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class LiveApi(val fetcher: Fetcher) : Api {
    override fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): LiveApi =
        LiveApi(fetcher.withHeaderCalculator(calculator))

    override suspend fun exampleEndpoint(): kotlin.Int =
        fetcher("example-endpoint", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, kotlin.Int.serializer())

    override suspend fun exampleEndpoint(input: kotlin.Int): kotlin.Int =
        fetcher("example-endpoint", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Int.serializer())

    inner class LiveUploadEarlyEndpointApi : Api.UploadEarlyEndpointApi {
        override suspend fun uploadFileForRequest(): com.lightningkite.lightningserver.files.UploadInformation =
            fetcher(
                "upload-early",
                HttpMethod.GET,
                kotlin.Unit.serializer(),
                kotlin.Unit,
                com.lightningkite.lightningserver.files.UploadInformation.serializer()
            )

        override suspend fun verifyUploadedFile(input: kotlin.String): kotlin.String =
            fetcher(
                "upload-early/verify",
                HttpMethod.POST,
                kotlin.String.serializer(),
                input,
                kotlin.String.serializer()
            )
    }

    override val uploadEarlyEndpoint = LiveUploadEarlyEndpointApi()

    override val appRelease = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(
        fetcher,
        "app-releases",
        com.lightningkite.lskiteuistarter.AppRelease.serializer(),
        kotlin.uuid.Uuid.serializer()
    )

    override val user = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(
        fetcher,
        "users",
        com.lightningkite.lskiteuistarter.User.serializer(),
        kotlin.uuid.Uuid.serializer()
    )

    inner class LiveUserAuthApi : Api.UserAuthApi,
        com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.sessions.proofs.LiveAuthClientEndpoints(
            fetcher,
            "auth/session",
            com.lightningkite.lskiteuistarter.User.serializer(),
            kotlin.uuid.Uuid.serializer()
        ),
        com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.Session<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(
            fetcher,
            "auth/session/sessions",
            com.lightningkite.lightningserver.sessions.Session.serializer(
                com.lightningkite.lskiteuistarter.User.serializer(),
                kotlin.uuid.Uuid.serializer()
            ),
            kotlin.uuid.Uuid.serializer()
        ) {

        inner class LiveEmailApi : Api.UserAuthApi.EmailApi,
            com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Email by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.Email(
                fetcher,
                "auth/proof/email",
            ) {
            override suspend fun verifyNewEmail(input: com.lightningkite.EmailAddress): kotlin.String =
                fetcher(
                    "auth/proof/email/verify-new-email",
                    HttpMethod.POST,
                    com.lightningkite.EmailAddress.serializer(),
                    input,
                    kotlin.String.serializer()
                )
        }

        override val email = LiveEmailApi()

        inner class LiveTimeBasedOTPProof : Api.UserAuthApi.TimeBasedOTPProof,
            com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.TimeBasedOTP by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.TimeBasedOTP(
                fetcher,
                "auth/proof/totp",
            ),
            com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.TotpSecret, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(
                fetcher,
                "auth/proof/totp/secrets",
                com.lightningkite.lightningserver.sessions.TotpSecret.serializer(),
                kotlin.uuid.Uuid.serializer()
            ) {
        }

        override val totp = LiveTimeBasedOTPProof()

        inner class LivePasswordProof : Api.UserAuthApi.PasswordProof,
            com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.PasswordSecret, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(
                fetcher,
                "auth/proof/password/secrets",
                com.lightningkite.lightningserver.sessions.PasswordSecret.serializer(),
                kotlin.uuid.Uuid.serializer()
            ),
            com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.Password(
                fetcher,
                "auth/proof/password",
            ) {
        }

        override val password = LivePasswordProof()

        override val backupCode = com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.BackupCode(
            fetcher,
            "auth/proof/backup-codes",
        )
    }

    override val userAuth = LiveUserAuthApi()

    inner class LiveFcmTokenApi : Api.FcmTokenApi,
        com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.FcmToken, kotlin.String> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(
            fetcher,
            "fcmTokens",
            com.lightningkite.lskiteuistarter.FcmToken.serializer(),
            kotlin.String.serializer()
        ) {
        override suspend fun registerToken(input: kotlin.String): com.lightningkite.services.database.EntryChange<com.lightningkite.lskiteuistarter.FcmToken> =
            fetcher(
                "fcmTokens/register",
                HttpMethod.POST,
                kotlin.String.serializer(),
                input,
                com.lightningkite.services.database.EntryChange.serializer(com.lightningkite.lskiteuistarter.FcmToken.serializer())
            )

        override suspend fun testInAppNotifications(id: kotlin.String): kotlin.String =
            fetcher(
                "fcmTokens/${fetcher.url(id, kotlin.String.serializer())}/test",
                HttpMethod.POST,
                kotlin.Unit.serializer(),
                kotlin.Unit,
                kotlin.String.serializer()
            )

        override suspend fun clearToken(id: kotlin.String): kotlin.Boolean =
            fetcher(
                "fcmTokens/${fetcher.url(id, kotlin.String.serializer())}/clear",
                HttpMethod.POST,
                kotlin.Unit.serializer(),
                kotlin.Unit,
                kotlin.Boolean.serializer()
            )
    }

    override val fcmToken = LiveFcmTokenApi()

    inner class LiveMetaApi : Api.MetaApi {
        override suspend fun getServerHealth(): com.lightningkite.lightningserver.typed.ServerHealth =
            fetcher(
                "meta/health",
                HttpMethod.GET,
                kotlin.Unit.serializer(),
                kotlin.Unit,
                com.lightningkite.lightningserver.typed.ServerHealth.serializer()
            )

        override suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse> =
            fetcher(
                "meta/bulk",
                HttpMethod.POST,
                MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkRequest.serializer()),
                input,
                MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkResponse.serializer())
            )
    }

    override val meta = LiveMetaApi()
}
