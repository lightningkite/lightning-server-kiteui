package com.lightningkite.template.sdk

import com.lightningkite.UUID
import com.lightningkite.default
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.suppressConnectivityIssues
import com.lightningkite.readable.*
import com.lightningkite.lightningdb.CollectionUpdates
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.sort
import com.lightningkite.lightningserver.auth.accessToken
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.template.User
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes


class UserSession(
    val api: Api2,
    val userId: UUID,
) : CachedApi2(api) {

}

val sessionToken = PersistentProperty<String?>("sessionToken", null)

val currentSession: Readable<UserSession?> = sharedSuspending {
    val token = sessionToken() ?: return@sharedSuspending null
    val api = selectedApi().api

    val authApi = api.withHeaderCalculator(api.userAuth.accessToken(token))
    try {
        val self = authApi.userAuth.getSelf()

        UserSession(
            api = authApi,
            userId = self._id,
        ).also {
//            AppScope.launch { registerToken(authApi) }
        }
    } catch (e: Exception) {
        println("FAILED")
        e.printStackTrace()
        null
    }
}

//private suspend fun registerToken(authApi: Api2) {
//    suppressConnectivityIssues {
//        fcmToken()?.takeIf { it.isNotEmpty() }?.let { authApi.fcmToken.registerToken(it) }
//    }
//}
//private suspend fun deregisterToken() {
//    val api: Api2 = selectedApi().api
//    suppressConnectivityIssues {
//        fcmToken()?.takeIf { it.isNotEmpty() }?.let { api.fcmToken.clearToken(it) }
//    }
//}
