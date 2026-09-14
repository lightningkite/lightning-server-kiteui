package com.lightningkite.lightningserver.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Whether [InterruptibleDelay.interrupt] can be lost.
 *
 * It notifies whoever is listening at the instant it is called and keeps no record, so a wake that
 * arrives before the delay has established its listener has nowhere to land.  [ModelCache] relies on
 * exactly this to tell a sleeping reader that what it holds has been thrown away, and a reader that
 * misses that sleeps out its whole poll interval holding nothing.
 */
class InterruptibleDelayTest {

    /** The control: an interrupt ends a delay that is established and waiting on it. */
    @Test fun anEstablishedDelayIsEndedByAnInterrupt() = runTest2 {
        val interrupt = InterruptibleDelay()
        var woke = false
        val job = backgroundScope.launch { interrupt.delay(10.minutes); woke = true }
        repeat(20) { yield() }
        assertFalse(woke, "nothing has interrupted it yet, and the duration has not passed")

        interrupt.interrupt()
        repeat(20) { yield() }
        assertTrue(woke, "an interrupt ends a delay that is waiting on it")
        job.cancel()
    }

    /**
     * An interrupt that arrives while the delay is still starting up must still end it.
     *
     * [delay] does not establish its listener synchronously: it goes through [race], which collects a
     * `channelFlow` and `launch`es each racer, so registration lands three scheduler turns after the
     * call.  An [interrupt] inside that window reaches nobody and is gone, and the caller then sleeps
     * out the full duration - for a [ModelCache] reader whose held value was just invalidated, that
     * means a whole poll interval showing nothing.
     *
     * Measured window, by turns of head start given before interrupting:
     *   0 lost, 1 lost, 2 lost, 3 heard, 4+ heard.
     */
    @Test fun anInterruptArrivingWhileTheDelayIsStartingIsNotLost() = runTest2 {
        for (turns in 0..2) {
            val interrupt = InterruptibleDelay()
            var woke = false
            // Entry has to be guaranteed before interrupting.  An interrupt fired before anyone is
            // delaying is spent, not stored - otherwise it would end whatever delay happened to start
            // next - so a test that interrupts before the call is asserting the wrong thing.
            val entered = CompletableDeferred<Unit>()
            val job = backgroundScope.launch {
                entered.complete(Unit)
                interrupt.delay(10.minutes)
                woke = true
            }
            entered.await()
            repeat(turns) { yield() }

            interrupt.interrupt()

            repeat(40) { yield() }
            assertTrue(
                woke,
                "an interrupt $turns turn(s) into the delay's startup was dropped; a wake must not " +
                        "depend on the delay having finished registering"
            )
            job.cancel()
        }
    }

    /**
     * The same window, but interrupted through the parent - which is how [ModelCache] actually uses
     * this.  Every reader delays on its own `interrupt.child()`, while invalidation fires the shared
     * parent, so a fix that only remembers interrupts on the instance being delayed upon does not
     * cover the case it was written for.
     */
    @Test fun aParentInterruptArrivingWhileTheDelayIsStartingIsNotLost() = runTest2 {
        for (turns in 0..2) {
            val parent = InterruptibleDelay()
            val child = parent.child()
            var woke = false
            val entered = CompletableDeferred<Unit>()
            val job = backgroundScope.launch {
                entered.complete(Unit)
                child.delay(10.minutes)
                woke = true
            }
            entered.await()
            repeat(turns) { yield() }

            parent.interrupt()

            repeat(40) { yield() }
            assertTrue(
                woke,
                "a parent interrupt $turns turn(s) into the child's startup was dropped"
            )
            job.cancel()
        }
    }

    /**
     * A delay that ends on its own timer must leave nothing registered.
     *
     * The interrupt racers lose that race and are cancelled part-way through their suspension, so
     * releasing their listeners cannot be left to the statement after the suspend - cancellation
     * skips it.  [ModelCache]'s poll loop delays for as long as anything is listening, so a listener
     * leaked per cycle accumulates for the life of the page and is re-invoked on every interrupt.
     */
    @Test fun aDelayThatEndsOnItsTimerLeavesNoListenersBehind() = runTest2 {
        val parent = InterruptibleDelay()
        val child = parent.child()

        repeat(5) { child.delay(10.milliseconds) }

        assertTrue(
            child.pendingListenerCountForTesting == 0,
            "the child kept ${child.pendingListenerCountForTesting} listener(s) from delays that timed out"
        )
        assertTrue(
            parent.pendingListenerCountForTesting == 0,
            "the parent kept ${parent.pendingListenerCountForTesting} listener(s) from delays that timed out"
        )
    }

    /** And a delay ended by an interrupt must clean up just the same. */
    @Test fun aDelayEndedByAnInterruptLeavesNoListenersBehind() = runTest2 {
        val parent = InterruptibleDelay()
        val child = parent.child()
        repeat(3) {
            val job = backgroundScope.launch { child.delay(10.minutes) }
            repeat(20) { yield() }
            parent.interrupt()
            repeat(20) { yield() }
            job.cancel()
        }

        assertTrue(
            child.pendingListenerCountForTesting == 0 && parent.pendingListenerCountForTesting == 0,
            "after interrupts, child=${child.pendingListenerCountForTesting} parent=${parent.pendingListenerCountForTesting}"
        )
    }

    /** Interrupting more than once while a delay is established must not resume it twice. */
    @Test fun repeatedInterruptsDoNotResumeTheSameDelayTwice() = runTest2 {
        val interrupt = InterruptibleDelay()
        var woke = 0
        val job = backgroundScope.launch { interrupt.delay(10.minutes); woke++ }
        repeat(20) { yield() }

        interrupt.interrupt()
        interrupt.interrupt()
        interrupt.interrupt()

        repeat(40) { yield() }
        assertTrue(woke == 1, "the delay ended exactly once, but woke=$woke")
        job.cancel()
    }
}
