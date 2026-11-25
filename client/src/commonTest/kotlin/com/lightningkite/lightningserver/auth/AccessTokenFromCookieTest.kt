package com.lightningkite.lightningserver.auth

import com.lightningkite.lightningserver.db.runTest2
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.HasId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AccessTokenFromCookieTest {

    private class CookieAuth : AuthClientEndpoints<HasId<String>, String> {
        var cookieFetches = 0
        override suspend fun getTokenFromCookie(): String = "token${++cookieFetches}"

        override suspend fun logIn(input: List<Proof>): IdAndAuthMethods<String> = TODO()
        override suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<String> = TODO()
        override suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<String> = TODO()
        override suspend fun getTokenSimple(input: String): String = TODO()
        override suspend fun setRefreshTokenCookie(input: String) = TODO()
        override suspend fun getSelf(): HasId<String> = TODO()
        override suspend fun authRequirements(): AuthRequirements = TODO()
        override suspend fun terminateSession() = TODO()
        override suspend fun terminateSession(sessionId: Uuid) = TODO()
    }

    @Test
    fun reusesTokenWithinMaxAge() = runTest2 {
        val auth = CookieAuth()
        val accessToken = auth.accessTokenFromCookie(maxAge = 4.minutes)
        assertEquals("token1", accessToken())
        delay(3.minutes)
        assertEquals("token1", accessToken())
        assertEquals(1, auth.cookieFetches)
    }

    @Test
    fun refetchesAfterMaxAge() = runTest2 {
        val auth = CookieAuth()
        val accessToken = auth.accessTokenFromCookie(maxAge = 4.minutes)
        assertEquals("token1", accessToken())
        delay(4.minutes + 1.seconds)
        assertEquals("token2", accessToken())
        assertEquals(2, auth.cookieFetches)
    }

    @Test
    fun forceInvalidateRefetches() = runTest2 {
        val auth = CookieAuth()
        val invalidate = Signal(0)
        val accessToken = auth.accessTokenFromCookie(forceInvalidate = invalidate, maxAge = 4.minutes)
        assertEquals("token1", accessToken())
        invalidate.value++
        assertEquals("token2", accessToken())
        assertEquals(2, auth.cookieFetches)
    }
}
