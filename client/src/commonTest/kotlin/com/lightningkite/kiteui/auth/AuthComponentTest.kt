package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lightningserver.sessions.IdAndAuthMethods
import com.lightningkite.lightningserver.sessions.LogInRequest
import com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints
import com.lightningkite.lightningserver.sessions.proofs.Proof
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class AuthComponentTest {
    private class LogInOnly(val refreshToken: String?) : AuthClientEndpoints<Nothing, Uuid> {
        override suspend fun logInV2(input: LogInRequest) = IdAndAuthMethods(Uuid.random(), refreshToken = refreshToken)
        override suspend fun logIn(input: List<Proof>) = TODO()
        override suspend fun checkProofs(input: List<Proof>) = TODO()
        override suspend fun getTokenSimple(input: String) = TODO()
        override suspend fun getTokenFromCookie() = TODO()
        override suspend fun setRefreshTokenCookie(input: String) = TODO()
        override suspend fun getSelf() = TODO()
        override suspend fun authRequirements() = TODO()
        override suspend fun terminateSession() = TODO()
        override suspend fun terminateSession(sessionId: Uuid) = TODO()
    }

    private fun component(subject: LogInOnly, onAuthentication: suspend (String) -> Unit) = AuthComponent(
        endpoints = AuthEndpoints(subjects = mapOf("User" to subject)),
        knownDeviceLocalStorageName = null,
        onAuthentication = onAuthentication,
    )

    @Test
    fun logInPassesTheRefreshTokenOn() = runTest {
        val received = ArrayList<String>()
        val returned = component(LogInOnly("token")) { received += it }.logIn(1.days)
        assertEquals(listOf("token"), received)
        assertEquals("token", returned)
    }

    @Test
    fun logInWithoutARefreshTokenFailsVisibly() = runTest {
        val received = ArrayList<String>()
        assertFailsWith<PlainTextException> {
            component(LogInOnly(null)) { received += it }.logIn(1.days)
        }
        assertEquals(listOf(), received)
    }
}
