package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lightningserver.sessions.IdAndAuthMethods
import com.lightningkite.lightningserver.sessions.LogInRequest
import com.lightningkite.lightningserver.sessions.proofs.AuthClientEndpoints
import com.lightningkite.lightningserver.sessions.proofs.Proof
import com.lightningkite.lightningserver.db.runTest2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class ReAuthComponentTest {
    private class LogInOnly(val refreshToken: String?) : AuthClientEndpoints<Nothing, Uuid> {
        val id = Uuid.random()
        override suspend fun logInV2(input: LogInRequest) = IdAndAuthMethods(id, refreshToken = refreshToken)
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

    private fun component(subject: LogInOnly, onAuthentication: suspend (String) -> Unit) = ReAuthComponent(
        endpoints = AuthEndpoints(subjects = mapOf("User" to subject)),
        subjectId = subject.id.toString(),
        onAuthentication = onAuthentication,
    )

    @Test
    fun logInPassesTheRefreshTokenOn() = runTest2 {
        val received = ArrayList<String>()
        component(LogInOnly("token")) { received += it }.logIn()
        assertEquals(listOf("token"), received)
    }

    @Test
    fun logInWithoutARefreshTokenFailsVisibly() = runTest2 {
        val received = ArrayList<String>()
        assertFailsWith<PlainTextException> {
            component(LogInOnly(null)) { received += it }.logIn()
        }
        assertEquals(listOf(), received)
    }
}
