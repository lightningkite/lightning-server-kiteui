package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.WebSocket
import com.lightningkite.kiteui.retryWebSocket
import com.lightningkite.kiteui.typed
import com.lightningkite.lightningserver.MultiplexMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for the "muxer never connects at all" report: [MultiplexedSocketTest] exercises
 * [MultiplexedSocket]'s channel logic against a [TypedWebSocket] stub whose `connected` starts out
 * `true`, so it never exercises the real [retryWebSocket] transition from disconnected to connected.
 * This test wires the real [retryWebSocket] (as production does in `BulkFetcher.wsMuxer`) to a fake
 * low-level [WebSocket], starting everything cold - exactly the real app's initial-activation path -
 * to confirm the "start" frame is actually sent once the transport opens.
 */
class MultiplexedSocketColdStartTest {

    // AppScope runs on Dispatchers.Main, which a plain JVM test process lacks.
    @BeforeTest
    fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun resetMainDispatcher() { Dispatchers.resetMain() }

    /** A fake low-level transport that captures raw frames and lets the test drive open/close. */
    private class FakeWebSocket : WebSocket {
        val sent = ArrayList<String>()
        @Volatile var hasOpenHandler = false
        private val onOpenList = ArrayList<() -> Unit>()
        private val onMessageList = ArrayList<(String) -> Unit>()
        private val onCloseList = ArrayList<(Short) -> Unit>()

        override fun close(code: Short, reason: String) {}
        override fun send(data: String) { sent.add(data) }
        override fun send(data: Blob) {}
        override fun onOpen(action: () -> Unit) { onOpenList.add(action); hasOpenHandler = true }
        override fun onMessage(action: (String) -> Unit) { onMessageList.add(action) }
        override fun onBinaryMessage(action: (Blob) -> Unit) {}
        override fun onClose(action: (Short) -> Unit) { onCloseList.add(action) }

        /** Simulate the server accepting the connection. */
        fun open() = onOpenList.toList().forEach { it() }
    }

    // A generous timeout: this test drives real threads/wall-clock time (unlike the
    // virtual-time ModelCacheTest suite), so it needs slack under CPU contention when
    // the full test suite runs concurrently rather than in isolation.
    private fun awaitTrue(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(2)
    }

    @Test
    fun coldChannelConnectsThroughRealRetryWebSocket() {
        val fakeTransport = FakeWebSocket()
        // Mirrors BulkFetcher.wsMuxer: a real RetryWebSocket over a real MultiplexedSocket, but
        // with the low-level WebSocket faked out so the test controls when the transport opens.
        val retryWs = retryWebSocket(underlyingSocket = { fakeTransport }, pingTime = 10_000)
        val muxer = retryWs.typed(Json, MultiplexMessage.serializer(), MultiplexMessage.serializer())
        val channel = MultiplexedSocket(muxer).channel("earned-credits/rest?x=1")

        // Cold start: neither the channel nor the underlying transport is connected yet - this is
        // the real app's initial activation path (ModelCache.list -> ... -> WebSocketChannel.connect()).
        channel.connect()
        try {
            // retryWebSocket reacts to shouldBeOn becoming true by launching reset() asynchronously
            // (Dispatchers.Default, since retryWebSocket's own scope has no confined dispatcher) - wait
            // for that launch to actually register handlers on the transport before simulating open(),
            // just as a real network handshake would only complete after the browser's WebSocket
            // object and its onopen handler already exist.
            awaitTrue { fakeTransport.hasOpenHandler }
            assertTrue(fakeTransport.hasOpenHandler, "retryWebSocket should have started connecting the transport")
            fakeTransport.open()

            awaitTrue { fakeTransport.sent.any { it.contains("\"start\":true") } }
            assertTrue(
                fakeTransport.sent.any { it.contains("\"start\":true") },
                "channel.connect() should eventually send a multiplex 'start' frame once the real " +
                        "retryWebSocket transport opens, starting from a fully cold (disconnected) state"
            )
        } finally {
            // Left open, the channel would go on asking for a start frame long after this test.
            channel.close(1000, "test over")
        }
    }
}
