package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.kiteui.exceptions.*
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.suppressConnectivityIssues
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.accessToken
import com.lightningkite.lskiteuistarter.fcmToken
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.*
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException


val sessionToken = PersistentProperty<String?>("sessionToken", null)

val currentSession: Reactive<UserSession?> = rememberSuspending {
    val token = sessionToken() ?: return@rememberSuspending null
    val api = selectedApi().api

    val authApi = api.withHeaderCalculator(api.userAuth.accessToken(token))
    try {
        val self = authApi.userAuth.getSelf()

        UserSession(
            api = authApi,
            userId = self._id,
        )
    } catch (e: Exception) {
        println("FAILED")
        e.printStackTrace()
        null
    }
}.also { currentSession ->
    AppScope.reactiveSuspending {
        val s = currentSession() ?: run {
            println("Deregstering token logged out")
            deregisterToken()
            return@reactiveSuspending
        }
        suppressConnectivityIssues {
            println("Starting register")
            fcmToken()?.takeIf { it.isNotEmpty() }?.let {
                println("Registering token")
                s.api.fcmToken.registerToken(it)
                println("Registered token")
            }
        }
    }
}

val currentSessionFailed = BasicListenable()
val currentSessionNotNull = remember {
    val result = currentSession()
    if (result == null) {
        currentSessionFailed.invokeAll()
        launch { deregisterToken() }
        throw CancellationException("No session found")
    }
    result
}

suspend fun deregisterToken() {
    val api: Api = selectedApi().api
    suppressConnectivityIssues {
        fcmToken()?.takeIf { it.isNotEmpty() }?.let { api.fcmToken.clearToken(it) }
    }
}

fun ExceptionToMessages.installLoggedOutErrors() {
    this += ExceptionToMessage<LsErrorException>(
        priority = 3.0f,
        additionalCondition = { it.error.message == "Session has been terminated." }
    ) {
        currentSessionFailed.invokeAll()
        ExceptionMessage(
            "Logged Out",
            "You have been logged out, you will be redirected to the Landing page.",
        )
    }
}