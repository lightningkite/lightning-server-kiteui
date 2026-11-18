package com.lightningkite.lightningserver.auth

import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.services.database.HasId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AccessTokenTest {
    // accessToken() fetches on AppScope, which runs on Dispatchers.Main.
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class TokenServer(val answers: MutableList<() -> String>) : AuthClientEndpoints<HasId<String>, String> {
        var calls = 0
        override suspend fun getTokenSimple(input: String): String { calls++; return answers.removeFirst()() }
        override suspend fun logIn(input: List<Proof>): IdAndAuthMethods<String> = TODO()
        override suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<String> = TODO()
        override suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<String> = TODO()
        override suspend fun getSelf(): HasId<String> = TODO()
        override suspend fun subsession(input: SubSessionRequest): String = TODO()
        override suspend fun authRequirements(): AuthRequirements = TODO()
        override suspend fun terminateSession() = TODO()
        override suspend fun terminateSession(sessionId: Uuid) = TODO()
    }

    @Test fun reusesASuccessfulToken() = runTest {
        val server = TokenServer(mutableListOf({ "first" }, { "second" }))
        val accessToken = server.accessToken("session")
        assertEquals("first", accessToken())
        assertEquals("first", accessToken())
        assertEquals(1, server.calls)
    }

    @Test fun retriesAfterAFailedFetch() = runTest {
        val server = TokenServer(mutableListOf({ throw IllegalStateException("offline") }, { "recovered" }))
        val accessToken = server.accessToken("session")
        assertFailsWith<IllegalStateException> { accessToken() }
        assertEquals("recovered", accessToken())
        assertEquals(2, server.calls)
    }
}
