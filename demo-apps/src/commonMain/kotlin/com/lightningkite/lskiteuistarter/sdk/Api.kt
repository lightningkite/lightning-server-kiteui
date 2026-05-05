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

		interface PasswordProof : com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints.Password, com.lightningkite.lightningserver.typed.ClientModelRestEndpoints<com.lightningkite.lightningserver.sessions.PasswordSecret, kotlin.uuid.Uuid> {
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
}
