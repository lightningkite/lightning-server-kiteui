package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.lightningserver.HttpMethod
import com.lightningkite.lightningserver.typed.Fetcher
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.builtins.*
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class LiveApi(val fetcher: Fetcher) : Api {
	override fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): LiveApi = 
		LiveApi(fetcher.withHeaderCalculator(calculator))
	override suspend fun exampleEndpoint(): kotlin.Int =
		fetcher("example-endpoint", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, kotlin.Int.serializer())
	override suspend fun exampleEndpoint(input: kotlin.Int): kotlin.Int =
		fetcher("example-endpoint", HttpMethod.POST, kotlin.Int.serializer(), input, kotlin.Int.serializer())

	override val uploadEarlyEndpoint = com.lightningkite.lightningserver.files.LiveClientUploadEarlyEndpoints(fetcher, "upload-early", )

	override val appRelease = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "app-releases", com.lightningkite.lskiteuistarter.AppRelease.serializer(), kotlin.uuid.Uuid.serializer())

	inner class LiveUserApi : Api.UserApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "users", com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()) {

		override val nestedTypeModel = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "users/nested/rest", com.lightningkite.lskiteuistarter.User.NestedTypeModel.serializer(), kotlin.uuid.Uuid.serializer())
	}
	override val user = LiveUserApi()

	inner class LiveUserAuthApi : Api.UserAuthApi, com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.sessions.proofs.LiveAuthClientEndpoints(fetcher, "auth/session", com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()), com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.Session<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "auth/session/sessions", com.lightningkite.lightningserver.sessions.Session.serializer(com.lightningkite.lskiteuistarter.User.serializer(), kotlin.uuid.Uuid.serializer()), kotlin.uuid.Uuid.serializer()) {

		inner class LiveEmailApi : Api.UserAuthApi.EmailApi, com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Email by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.Email(fetcher, "auth/proof/email", ) {
			override suspend fun verifyNewEmail(input: com.lightningkite.services.data.EmailAddress): kotlin.String =
				fetcher("auth/proof/email/verify-new-email", HttpMethod.POST, com.lightningkite.services.data.EmailAddress.serializer(), input, kotlin.String.serializer())
		}
		override val email = LiveEmailApi()

		inner class LiveTimeBasedOTPProof : Api.UserAuthApi.TimeBasedOTPProof, com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.TimeBasedOTP by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.TimeBasedOTP(fetcher, "auth/proof/totp", ), com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.TotpSecret, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "auth/proof/totp/secrets", com.lightningkite.lightningserver.sessions.TotpSecret.serializer(), kotlin.uuid.Uuid.serializer()) {
		}
		override val totp = LiveTimeBasedOTPProof()

		inner class LivePasswordProof : Api.UserAuthApi.PasswordProof, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.PasswordSecret, kotlin.uuid.Uuid> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "auth/proof/password/secrets", com.lightningkite.lightningserver.sessions.PasswordSecret.serializer(), kotlin.uuid.Uuid.serializer()), com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password by com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.Password(fetcher, "auth/proof/password", ) {
		}
		override val password = LivePasswordProof()

		override val backupCode = com.lightningkite.lightningserver.sessions.proofs.LiveProofClientEndpoints.BackupCode(fetcher, "auth/proof/backup-codes", )
	}
	override val userAuth = LiveUserAuthApi()

	inner class LiveFcmTokenApi : Api.FcmTokenApi, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.FcmToken, kotlin.String> by com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "fcmTokens", com.lightningkite.lskiteuistarter.FcmToken.serializer(), kotlin.String.serializer()) {
		override suspend fun registerToken(input: kotlin.String): com.lightningkite.services.database.EntryChange<com.lightningkite.lskiteuistarter.FcmToken> =
			fetcher("fcmTokens/register", HttpMethod.POST, kotlin.String.serializer(), input, com.lightningkite.services.database.EntryChange.serializer(com.lightningkite.lskiteuistarter.FcmToken.serializer()))
		override suspend fun testInAppNotifications(id: kotlin.String): kotlin.String =
			fetcher("fcmTokens/${fetcher.url(id, kotlin.String.serializer())}/test", HttpMethod.POST, kotlin.Unit.serializer(), kotlin.Unit, kotlin.String.serializer())
		override suspend fun clearToken(id: kotlin.String): kotlin.Boolean =
			fetcher("fcmTokens/${fetcher.url(id, kotlin.String.serializer())}/clear", HttpMethod.POST, kotlin.Unit.serializer(), kotlin.Unit, kotlin.Boolean.serializer())
	}
	override val fcmToken = LiveFcmTokenApi()

	inner class LiveRaceApi : Api.RaceApi {
		override suspend fun startStorm(input: com.lightningkite.lskiteuistarter.StormRequest): com.lightningkite.lskiteuistarter.StormState =
			fetcher("race/storm", HttpMethod.POST, com.lightningkite.lskiteuistarter.StormRequest.serializer(), input, com.lightningkite.lskiteuistarter.StormState.serializer())
		override suspend fun stopStorm(): com.lightningkite.lskiteuistarter.StormState =
			fetcher("race/storm/stop", HttpMethod.POST, kotlin.Unit.serializer(), kotlin.Unit, com.lightningkite.lskiteuistarter.StormState.serializer())
		override suspend fun stormState(): com.lightningkite.lskiteuistarter.StormState =
			fetcher("race/storm/state", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, com.lightningkite.lskiteuistarter.StormState.serializer())
		override suspend fun seedRace(input: com.lightningkite.lskiteuistarter.SeedRaceRequest): com.lightningkite.lskiteuistarter.RaceState =
			fetcher("race/seed", HttpMethod.POST, com.lightningkite.lskiteuistarter.SeedRaceRequest.serializer(), input, com.lightningkite.lskiteuistarter.RaceState.serializer())
		override suspend fun scratchRacer(input: kotlin.uuid.Uuid?): com.lightningkite.lskiteuistarter.RaceState =
			fetcher("race/scratch", HttpMethod.POST, kotlin.uuid.Uuid.serializer().nullable, input, com.lightningkite.lskiteuistarter.RaceState.serializer())
		override suspend fun raceState(): com.lightningkite.lskiteuistarter.RaceState =
			fetcher("race/state", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, com.lightningkite.lskiteuistarter.RaceState.serializer())
		override suspend fun advanceRace(input: com.lightningkite.lskiteuistarter.AdvanceRaceRequest): com.lightningkite.lskiteuistarter.RaceState =
			fetcher("race/advance", HttpMethod.POST, com.lightningkite.lskiteuistarter.AdvanceRaceRequest.serializer(), input, com.lightningkite.lskiteuistarter.RaceState.serializer())

		override val club = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "race/club", com.lightningkite.lskiteuistarter.Club.serializer(), kotlin.uuid.Uuid.serializer())

		override val racer = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpointsAndUpdatesWebSocket(fetcher, "race/racer", com.lightningkite.lskiteuistarter.Racer.serializer(), kotlin.uuid.Uuid.serializer())
	}
	override val race = LiveRaceApi()

	override val document = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "documents", com.lightningkite.lskiteuistarter.Document.serializer(), kotlin.uuid.Uuid.serializer())

	inner class LiveMetaApi : Api.MetaApi {
		override suspend fun getServerHealth(): com.lightningkite.lightningserver.typed.ServerHealth =
			fetcher("meta/health", HttpMethod.GET, kotlin.Unit.serializer(), kotlin.Unit, com.lightningkite.lightningserver.typed.ServerHealth.serializer())
		override suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse> =
			fetcher("meta/bulk", HttpMethod.POST, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkRequest.serializer()), input, MapSerializer(String.serializer(), com.lightningkite.lightningserver.typed.BulkResponse.serializer()))
	}
	override val meta = LiveMetaApi()

	override val sealedPolymorphicModel = com.lightningkite.lightningserver.typed.LiveClientModelRestEndpoints(fetcher, "test-sealed-classes", com.lightningkite.lskiteuistarter.SealedPolymorhphicModel.serializer(), kotlin.uuid.Uuid.serializer())
}
