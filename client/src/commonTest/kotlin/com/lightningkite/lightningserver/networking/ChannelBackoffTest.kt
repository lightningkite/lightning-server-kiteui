package com.lightningkite.lightningserver.networking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ChannelBackoffTest {

    @Test
    fun noBackoffOnFirstConnection() {
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { 0L },
            randomSource = { 0.5 }, // Middle value = no jitter effect
        )

        assertEquals(0L, backoff.getBackoffWithJitter())
    }

    @Test
    fun backoffIncreasesOnQuickDisconnect() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Connect
        backoff.onConnectionOpened()

        // Disconnect after only 100ms (< 5s threshold)
        currentTime = 100L
        backoff.onConnectionClosed(wasIntentional = false)

        assertEquals(100L, backoff.currentBackoffMs)
    }

    @Test
    fun backoffDoublesOnRepeatedQuickDisconnects() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // First quick disconnect
        backoff.onConnectionOpened()
        currentTime = 100L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(100L, backoff.currentBackoffMs)

        // Second quick disconnect
        currentTime = 200L
        backoff.onConnectionOpened()
        currentTime = 300L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(200L, backoff.currentBackoffMs)

        // Third quick disconnect
        currentTime = 400L
        backoff.onConnectionOpened()
        currentTime = 500L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(400L, backoff.currentBackoffMs)
    }

    @Test
    fun backoffCapsAtMaximum() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 500.milliseconds, // Low max for testing
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Simulate many quick disconnects
        repeat(10) { i ->
            backoff.onConnectionOpened()
            currentTime += 100L
            backoff.onConnectionClosed(wasIntentional = false)
        }

        // Should cap at 500ms
        assertEquals(500L, backoff.currentBackoffMs)
    }

    @Test
    fun backoffResetsOnStableConnection() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Build up backoff with quick disconnects
        backoff.onConnectionOpened()
        currentTime = 100L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(100L, backoff.currentBackoffMs)

        backoff.onConnectionOpened()
        currentTime = 200L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(200L, backoff.currentBackoffMs)

        // Now have a stable connection (>= 5 seconds)
        currentTime = 1000L
        backoff.onConnectionOpened()
        currentTime = 6000L // 5 seconds later
        backoff.onConnectionClosed(wasIntentional = false)

        // Backoff should be reset
        assertEquals(0L, backoff.currentBackoffMs)
    }

    @Test
    fun backoffResetsOnIntentionalClose() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Build up backoff
        backoff.onConnectionOpened()
        currentTime = 100L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(100L, backoff.currentBackoffMs)

        // Intentional close (even if quick) resets backoff
        currentTime = 200L
        backoff.onConnectionOpened()
        currentTime = 300L
        backoff.onConnectionClosed(wasIntentional = true)

        assertEquals(0L, backoff.currentBackoffMs)
    }

    @Test
    fun jitterAddsRandomness() {
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            jitterFactor = 0.2, // +/- 20%
            timeSource = { 0L },
            randomSource = { 0.0 }, // Minimum random = -20% jitter
        )

        // Build up some backoff
        backoff.onConnectionOpened()
        backoff.onConnectionClosed(wasIntentional = false)

        // With randomSource = 0.0 and jitterFactor = 0.2:
        // jitterMultiplier = 1.0 + (0.0 * 2 - 1) * 0.2 = 1.0 + (-1) * 0.2 = 0.8
        // Result = 100 * 0.8 = 80
        assertEquals(80L, backoff.getBackoffWithJitter())
    }

    @Test
    fun jitterMaximum() {
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            jitterFactor = 0.2,
            timeSource = { 0L },
            randomSource = { 1.0 }, // Maximum random = +20% jitter
        )

        backoff.onConnectionOpened()
        backoff.onConnectionClosed(wasIntentional = false)

        // With randomSource = 1.0 and jitterFactor = 0.2:
        // jitterMultiplier = 1.0 + (1.0 * 2 - 1) * 0.2 = 1.0 + 1 * 0.2 = 1.2
        // Result = 100 * 1.2 = 120
        assertEquals(120L, backoff.getBackoffWithJitter())
    }

    @Test
    fun jitterMiddleValueNoChange() {
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            jitterFactor = 0.2,
            timeSource = { 0L },
            randomSource = { 0.5 }, // Middle random = no jitter
        )

        backoff.onConnectionOpened()
        backoff.onConnectionClosed(wasIntentional = false)

        // With randomSource = 0.5 and jitterFactor = 0.2:
        // jitterMultiplier = 1.0 + (0.5 * 2 - 1) * 0.2 = 1.0 + 0 * 0.2 = 1.0
        // Result = 100 * 1.0 = 100
        assertEquals(100L, backoff.getBackoffWithJitter())
    }

    @Test
    fun noJitterWhenNoBackoff() {
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { 0L },
            randomSource = { 0.0 },
        )

        // No backoff = no jitter applied
        assertEquals(0L, backoff.getBackoffWithJitter())
    }

    @Test
    fun fullScenarioRapidReconnectLoop() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Simulate the problematic scenario:
        // connect -> message -> immediate fail -> reconnect -> repeat

        // First attempt - no backoff initially
        assertEquals(0L, backoff.getBackoffWithJitter())
        backoff.onConnectionOpened()
        currentTime += 50 // Connection lasts 50ms
        backoff.onConnectionClosed(wasIntentional = false)

        // Second attempt - should have 100ms backoff
        assertEquals(100L, backoff.getBackoffWithJitter())
        currentTime += 100 // Wait for backoff
        backoff.onConnectionOpened()
        currentTime += 50
        backoff.onConnectionClosed(wasIntentional = false)

        // Third attempt - should have 200ms backoff
        assertEquals(200L, backoff.getBackoffWithJitter())
        currentTime += 200
        backoff.onConnectionOpened()
        currentTime += 50
        backoff.onConnectionClosed(wasIntentional = false)

        // Fourth attempt - should have 400ms backoff
        assertEquals(400L, backoff.getBackoffWithJitter())
        currentTime += 400
        backoff.onConnectionOpened()
        currentTime += 50
        backoff.onConnectionClosed(wasIntentional = false)

        // Fifth attempt - should have 800ms backoff
        assertEquals(800L, backoff.getBackoffWithJitter())
    }

    @Test
    fun connectionExactlyAtThresholdIsStable() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Build up backoff first
        backoff.onConnectionOpened()
        currentTime = 100L
        backoff.onConnectionClosed(wasIntentional = false)
        assertEquals(100L, backoff.currentBackoffMs)

        // Connection lasting exactly 5000ms (threshold)
        currentTime = 1000L
        backoff.onConnectionOpened()
        currentTime = 6000L // Exactly 5000ms duration
        backoff.onConnectionClosed(wasIntentional = false)

        // Should NOT reset (< threshold, not <=)
        // Actually, let me check the code... it uses `<` so exactly at threshold is stable
        // connectionDuration < stableConnectionThreshold means 5000 < 5000 is false
        // So exactly at threshold is considered stable
        assertEquals(0L, backoff.currentBackoffMs)
    }

    @Test
    fun connectionJustUnderThresholdIsUnstable() {
        var currentTime = 0L
        val backoff = ChannelBackoff(
            initialBackoff = 100.milliseconds,
            maxBackoff = 30.seconds,
            stableConnectionThreshold = 5.seconds,
            timeSource = { currentTime },
            randomSource = { 0.5 },
        )

        // Connection lasting 4999ms (just under threshold)
        backoff.onConnectionOpened()
        currentTime = 4999L
        backoff.onConnectionClosed(wasIntentional = false)

        // Should increase backoff (4999 < 5000)
        assertEquals(100L, backoff.currentBackoffMs)
    }
}
