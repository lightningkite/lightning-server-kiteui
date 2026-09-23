package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.lightningserver.MultiplexMessage
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for [MultiplexedSocket] covering the bug classes behind the "second visit only
 * polls" report: channels must be reusable across close/connect, close must send exactly one "end"
 * frame, and a transport reconnect must re-subscribe active channels.
 *
 * The "start" frame is sent by a reactive scope, which recalculates asynchronously, so assertions
 * that depend on it [awaitTrue] before checking.  The "end" frame is sent synchronously by [close]
 * and is asserted directly.
 */
class MultiplexedSocketTest {

    /** A fake multiplex transport that captures sent frames and lets the test drive its state. */
    private class FakeMuxer : TypedWebSocket<MultiplexMessage, MultiplexMessage> {
        val sent = ArrayList<MultiplexMessage>()
        val connectedSignal = Signal(true)

        override val connected: Reactive<Boolean> get() = connectedSignal
        override fun beginUse(): () -> Unit = {}
        override fun close(code: Short, reason: String) {}
        override fun send(data: MultiplexMessage) { sent.add(data) }

        private val onOpenList = ArrayList<() -> Unit>()
        private val onMessageList = ArrayList<(MultiplexMessage) -> Unit>()
        private val onCloseList = ArrayList<(Short) -> Unit>()
        override fun onOpen(action: () -> Unit) { onOpenList.add(action) }
        override fun onMessage(action: (MultiplexMessage) -> Unit) { onMessageList.add(action) }
        override fun onClose(action: (Short) -> Unit) { onCloseList.add(action) }

        /** Simulate the server pushing a frame to whichever channel it targets. */
        fun deliver(message: MultiplexMessage) = onMessageList.toList().forEach { it(message) }
        /** Simulate the transport dropping. */
        fun dropTransport() {
            connectedSignal.value = false
            onCloseList.toList().forEach { it(1000) }
        }
        /** Simulate the transport coming back. */
        fun restoreTransport() {
            connectedSignal.value = true
            onOpenList.toList().forEach { it() }
        }

        fun starts(channel: String) = sent.count { it.channel == channel && it.start }
        fun ends(channel: String) = sent.count { it.channel == channel && it.end }
    }

    private fun awaitTrue(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(2)
    }

    @Test
    fun reconnectSendsStartAgain() {
        val muxer = FakeMuxer()
        val channel = MultiplexedSocket(muxer).channel("earned-credits/rest?x=1")

        channel.connect()
        awaitTrue { muxer.sent.any { it.start } }
        assertEquals(1, muxer.sent.count { it.start }, "connect() should send exactly one start frame")
        val id = muxer.sent.first { it.start }.channel

        // Server acknowledges the subscription, opening the channel.
        muxer.deliver(MultiplexMessage(channel = id, start = true))

        channel.close(1000, "done")
        assertEquals(1, muxer.ends(id), "close() should send exactly one end frame")
        val startsBeforeReconnect = muxer.starts(id)

        // Reuse the same channel instance - this is the case that silently failed before.
        channel.connect()
        awaitTrue { muxer.starts(id) > startsBeforeReconnect }
        assertTrue(
            muxer.starts(id) > startsBeforeReconnect,
            "reconnect must send another start frame (channel must be reusable)"
        )
    }

    @Test
    fun doubleCloseSendsSingleEnd() {
        val muxer = FakeMuxer()
        val channel = MultiplexedSocket(muxer).channel("x/rest")

        channel.connect()
        awaitTrue { muxer.sent.any { it.start } }
        val id = muxer.sent.first { it.start }.channel

        channel.close(1000, "a")
        channel.close(1000, "b")

        assertEquals(1, muxer.ends(id), "close() is idempotent: exactly one end frame total")
    }

    /**
     * A "start" the server never answers has to be asked for again.
     *
     * It is the one failure with no other way back: the transport stays connected, so nothing
     * reconnects, and no other code path ever re-sends.  One channel stays shut for the whole
     * session while every other channel on the same socket carries on, which presents as a single
     * screen frozen rather than as anything to do with the connection.
     */
    @Test
    fun anUnansweredStartIsAskedForAgain() {
        val muxer = FakeMuxer()
        val channel = MultiplexedSocket(muxer).channel("z/rest")

        channel.connect()
        awaitTrue { muxer.sent.any { it.start } }
        val id = muxer.sent.first { it.start }.channel

        // The server never answers.  Nothing else about the transport changes.
        awaitTrue(timeoutMs = 15_000) { muxer.starts(id) >= 2 }
        assertTrue(muxer.starts(id) >= 2, "an unanswered start must be retried, sent ${muxer.starts(id)}")
    }

    /** ...and once it is answered, asking has to stop. */
    @Test
    fun anAnsweredStartIsNotAskedForAgain() {
        val muxer = FakeMuxer()
        val channel = MultiplexedSocket(muxer).channel("w/rest")

        channel.connect()
        awaitTrue { muxer.sent.any { it.start } }
        val id = muxer.sent.first { it.start }.channel
        muxer.deliver(MultiplexMessage(channel = id, start = true))

        // Well past the retry interval, with the channel open the whole time.
        Thread.sleep(6_000)
        assertEquals(1, muxer.starts(id), "an open channel must not keep asking to be opened")
    }

    /** A closed channel stops asking, however unanswered it was left. */
    @Test
    fun closingStopsAnUnansweredStartFromBeingRetried() {
        val muxer = FakeMuxer()
        val channel = MultiplexedSocket(muxer).channel("v/rest")

        channel.connect()
        awaitTrue { muxer.sent.any { it.start } }
        val id = muxer.sent.first { it.start }.channel

        channel.close(1000, "done")
        val startsAtClose = muxer.starts(id)

        Thread.sleep(6_000)
        assertEquals(startsAtClose, muxer.starts(id), "a closed channel must stop asking")
    }

    @Test
    fun transportReconnectResubscribes() {
        val muxer = FakeMuxer()
        val channel = MultiplexedSocket(muxer).channel("y/rest")

        channel.connect()
        awaitTrue { muxer.sent.any { it.start } }
        val id = muxer.sent.first { it.start }.channel
        muxer.deliver(MultiplexMessage(channel = id, start = true))

        muxer.dropTransport()
        muxer.restoreTransport()

        awaitTrue { muxer.starts(id) >= 2 }
        assertTrue(muxer.starts(id) >= 2, "channel must re-subscribe after a transport reconnect")
    }
}
