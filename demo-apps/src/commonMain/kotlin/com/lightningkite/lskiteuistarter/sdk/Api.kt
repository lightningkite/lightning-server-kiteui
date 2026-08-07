package com.lightningkite.lskiteuistarter.sdk



interface Api {
	fun withHeaderCalculator(calculator: suspend () -> List<Pair<String, String>>): Api
	/**
	 * Example Endpoint
	 * 
	 * **Auth Requirements:** No Requirements
	 * */
	suspend fun exampleEndpoint(): kotlin.Int
	/**
	 * Example Endpoint
	 * 
	 * **Auth Requirements:** User with root access
	 * */
	suspend fun exampleEndpoint(input: kotlin.Int): kotlin.Int

	val uploadEarlyEndpoint: com.lightningkite.lightningserver.files.ClientUploadEarlyEndpoints

	val appRelease: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.AppRelease, kotlin.uuid.Uuid>

	interface UserApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid> {

		val nestedTypeModel: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.User.NestedTypeModel, kotlin.uuid.Uuid>
	}
	val user: UserApi

	interface UserAuthApi : com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.Session<com.lightningkite.lskiteuistarter.User, kotlin.uuid.Uuid>, kotlin.uuid.Uuid> {

		interface EmailApi : com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Email {
			/**
			 * Verify New Email
			 * 
			 * Sends a verification passcode to a new email.
			 * 
			 * **Auth Requirements:** User with root access
			 * */
			suspend fun verifyNewEmail(input: com.lightningkite.services.data.EmailAddress): kotlin.String
		}
		val email: EmailApi

		interface TimeBasedOTPProof : com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.TimeBasedOTP, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.TotpSecret, kotlin.uuid.Uuid> {
		}
		val totp: TimeBasedOTPProof

		interface PasswordProof : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.PasswordSecret, kotlin.uuid.Uuid>, com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password {
		}
		val password: PasswordProof

		val backupCode: com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.BackupCode
	}
	val userAuth: UserAuthApi

	interface FcmTokenApi : com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.FcmToken, kotlin.String> {
		/**
		 * Register Token
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun registerToken(input: kotlin.String): com.lightningkite.services.database.EntryChange<com.lightningkite.lskiteuistarter.FcmToken>
		/**
		 * Test In-App Notifications
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun testInAppNotifications(id: kotlin.String): kotlin.String
		/**
		 * Clear Token
		 * 
		 * **Auth Requirements:** No Requirements
		 * */
		suspend fun clearToken(id: kotlin.String): kotlin.Boolean
	}
	val fcmToken: FcmTokenApi

	interface RaceApi {
		/**
		 * Start Storm
		 * 
		 * Mutates the race continuously, so a reader is never looking at a still target.
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun startStorm(input: com.lightningkite.lskiteuistarter.StormRequest): com.lightningkite.lskiteuistarter.StormState
		/**
		 * Stop Storm
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun stopStorm(): com.lightningkite.lskiteuistarter.StormState
		/**
		 * Storm State
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun stormState(): com.lightningkite.lskiteuistarter.StormState
		/**
		 * Seed Race
		 * 
		 * Wipes the race and lays out a fresh field of racers, all at the start line.
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun seedRace(input: com.lightningkite.lskiteuistarter.SeedRaceRequest): com.lightningkite.lskiteuistarter.RaceState
		/**
		 * Scratch Racer
		 * 
		 * Deletes one racer out of band.  Removals are the change a cache most easily misses.
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun scratchRacer(input: kotlin.uuid.Uuid?): com.lightningkite.lskiteuistarter.RaceState
		/**
		 * Race State
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun raceState(): com.lightningkite.lskiteuistarter.RaceState
		/**
		 * Advance Race
		 * 
		 * Moves randomly chosen racers up one checkpoint, reordering the leaderboard.
		 * 
		 * **Auth Requirements:** User with root access
		 * */
		suspend fun advanceRace(input: com.lightningkite.lskiteuistarter.AdvanceRaceRequest): com.lightningkite.lskiteuistarter.RaceState

		val club: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.Club, kotlin.uuid.Uuid>

		val racer: com.lightningkite.lightningserver.typed.ClientModelRestEndpointsAndUpdatesWebsocket<com.lightningkite.lskiteuistarter.Racer, kotlin.uuid.Uuid>
	}
	val race: RaceApi

	val document: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.Document, kotlin.uuid.Uuid>

	interface MetaApi {
		/**
		 * Get Server Health
		 * 
		 * Gets the current status of the server
		 * 
		 * **Auth Requirements:** No Requirements
		 * */
		suspend fun getServerHealth(): com.lightningkite.lightningserver.typed.ServerHealth
		/**
		 * Bulk Request
		 * 
		 * Performs multiple requests at once, returning the results in the same order.
		 * 
		 * **Auth Requirements:** No Requirements
		 * */
		suspend fun bulkRequest(input: Map<String, com.lightningkite.lightningserver.typed.BulkRequest>): Map<String, com.lightningkite.lightningserver.typed.BulkResponse>
	}
	val meta: MetaApi

	val sealedPolymorphicModel: com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lskiteuistarter.SealedPolymorhphicModel, kotlin.uuid.Uuid>
}
