package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.WebSocket
import com.lightningkite.kiteui.retryWebSocket
import com.lightningkite.kiteui.typed
import com.lightningkite.services.database.CollectionUpdates
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.gt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [SharedCollectionUpdatesSocket] sends its condition from `onOpen` only if the socket already reports itself
 * connected. [SharedCollectionUpdatesSocketTest]'s mock socket is connected from the start, so only a real
 * [retryWebSocket] opening from cold shows whether the subscription goes out when the transport opens - rather
 * than never, leaving every ModelCache reader to wait out its socket timeout and fall back to polling.
 */
class SharedCollectionUpdatesSocketColdStartTest {

    // retryWebSocket's ping loop runs on AppScope, which needs a Main dispatcher a plain JVM test lacks.
    private val mainThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @BeforeTest
    fun setMainDispatcher() { Dispatchers.setMain(mainThread) }

    @AfterTest
    fun resetMainDispatcher() { Dispatchers.resetMain(); mainThread.close() }

    private class FakeWebSocket : WebSocket {
        val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
        @Volatile var hasOpenHandler = false
        private val onOpenList = ArrayList<() -> Unit>()

        override fun close(code: Short, reason: String) {}
        override fun send(data: String) { sent.add(data) }
        override fun send(data: Blob) {}
        override fun onOpen(action: () -> Unit) { onOpenList.add(action); hasOpenHandler = true }
        override fun onMessage(action: (String) -> Unit) {}
        override fun onBinaryMessage(action: (Blob) -> Unit) {}
        override fun onClose(action: (Short) -> Unit) {}

        /** Simulates the server accepting the connection. */
        fun open() = onOpenList.toList().forEach { it() }
    }

    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(2)
    }

    @Test
    fun sendsTheConditionWhenTheSocketOpens() {
        val transport = FakeWebSocket()
        val retrying = retryWebSocket(underlyingSocket = { transport }, pingTime = 10_000)
        val typed = retrying.typed(
            Json,
            Condition.serializer(LargeTestModel.serializer()),
            CollectionUpdates.serializer(LargeTestModel.serializer(), Uuid.serializer()),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val updates = SharedCollectionUpdatesSocket(scope = scope, socket = typed, onChange = {})
            val release = updates.require(Condition { it.int gt 4 }).beginUse()

            // The requirement starts the connection; the handshake completes only once handlers exist, as in a browser.
            awaitTrue(5_000) { transport.hasOpenHandler }
            assertTrue(transport.hasOpenHandler, "requiring a condition should start connecting the transport")
            transport.open()

            // Well inside the 4 s acknowledgement retry and ModelCache's 5 s socket wait: the send must come from onOpen.
            awaitTrue(1_000) { transport.sent.isNotEmpty() }
            assertTrue(transport.sent.isNotEmpty(), "the condition was never sent after the socket opened")
            release()
        } finally {
            scope.cancel()
        }
    }
}
