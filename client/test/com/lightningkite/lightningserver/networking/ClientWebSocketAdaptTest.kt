package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [toClientWebSocket] adapts a use-counted [TypedWebSocket] to [ClientWebSocket]'s explicit
 * connect/close, so it has to hold the use between the two.  Dropping the disposer returned by
 * `beginUse()` did not merely leak: a [com.lightningkite.kiteui.retryWebSocket] only stays down
 * while its use count is zero, so `close()` on a still-held use dropped the connection and the
 * retry loop immediately redialled - an unclosable socket reconnecting for the life of the app.
 */
class ClientWebSocketAdaptTest {

    /** Tracks only what this test is about: how many uses are outstanding. */
    private class UseCountingSocket : TypedWebSocket<String, String> {
        var uses: Int = 0
            private set
        var closeCount: Int = 0
            private set

        override val connected: Reactive<Boolean> = Signal(false)

        override fun beginUse(): () -> Unit {
            uses++
            return { uses-- }
        }

        override fun close(code: Short, reason: String) { closeCount++ }
        override fun send(data: String) {}
        override fun onOpen(action: () -> Unit) {}
        override fun onMessage(action: (String) -> Unit) {}
        override fun onClose(action: (Short) -> Unit) {}
    }

    @Test
    fun closeReleasesTheUse() {
        val underlying = UseCountingSocket()
        val socket = underlying.toClientWebSocket()

        socket.connect()
        assertEquals(1, underlying.uses, "connect should take a use")

        socket.close(1000, "done")
        assertEquals(0, underlying.uses, "close must release the use, or the retry loop redials")
        assertTrue(underlying.closeCount > 0, "close should still reach the underlying socket")
    }

    @Test
    fun repeatedConnectsTakeOneUse() {
        val underlying = UseCountingSocket()
        val socket = underlying.toClientWebSocket()

        socket.connect()
        socket.connect()
        socket.connect()
        assertEquals(1, underlying.uses)

        // A single close has to balance them all; otherwise the socket can never be shut down.
        socket.close(1000, "done")
        assertEquals(0, underlying.uses)
    }

    @Test
    fun repeatedClosesDoNotUnderflowTheUseCount() {
        val underlying = UseCountingSocket()
        val socket = underlying.toClientWebSocket()

        socket.connect()
        socket.close(1000, "done")
        socket.close(1000, "again")
        assertEquals(0, underlying.uses, "a second close must not release a use it never took")
    }

    @Test
    fun canReconnectAfterClose() {
        val underlying = UseCountingSocket()
        val socket = underlying.toClientWebSocket()

        socket.connect()
        socket.close(1000, "done")
        socket.connect()
        assertEquals(1, underlying.uses, "a closed socket should be reusable")

        socket.close(1000, "done")
        assertEquals(0, underlying.uses)
    }
}
