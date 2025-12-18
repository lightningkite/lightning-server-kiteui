package com.lightningkite.lightningserver.networking

import kotlin.random.Random
import kotlin.time.Duration

/**
 * Manages exponential backoff for websocket channel reconnection.
 *
 * Backoff increases when connections fail quickly (< stableConnectionThreshold).
 * Backoff resets when connections stay open long enough to be considered stable,
 * or when the connection is closed intentionally.
 *
 * @param initialBackoff Starting backoff duration after first failure
 * @param maxBackoff Maximum backoff duration
 * @param stableConnectionThreshold How long a connection must stay open to be considered stable
 * @param jitterFactor Random variation factor (0.2 = +/- 20%)
 * @param timeSource Injectable time source for testing (returns epoch millis)
 * @param randomSource Injectable random source for testing (returns 0.0 to 1.0)
 */
class ChannelBackoff(
    private val initialBackoff: Duration,
    private val maxBackoff: Duration,
    private val stableConnectionThreshold: Duration,
    private val jitterFactor: Double = 0.2,
    private val timeSource: () -> Long = { com.lightningkite.now().toEpochMilliseconds() },
    private val randomSource: () -> Double = { Random.nextDouble() },
) {
    private var backoffMs: Long = 0L
    private var connectionOpenedAt: Long = 0L

    /** Call when a channel successfully connects */
    fun onConnectionOpened() {
        connectionOpenedAt = timeSource()
    }

    /**
     * Call when a channel closes.
     * @param wasIntentional true if the user closed the channel, false if server/network closed it
     */
    fun onConnectionClosed(wasIntentional: Boolean) {
        if (wasIntentional) {
            backoffMs = 0L
            return
        }

        val connectionDuration = timeSource() - connectionOpenedAt
        if (connectionDuration < stableConnectionThreshold.inWholeMilliseconds) {
            // Short-lived connection = failure, increase backoff
            backoffMs = if (backoffMs == 0L) {
                initialBackoff.inWholeMilliseconds
            } else {
                (backoffMs * 2).coerceAtMost(maxBackoff.inWholeMilliseconds)
            }
        } else {
            // Connection was stable, reset backoff
            backoffMs = 0L
        }
    }

    /** Returns current backoff with random jitter applied, or 0 if no backoff needed */
    fun getBackoffWithJitter(): Long {
        if (backoffMs == 0L) return 0L
        // Jitter: random value between -jitterFactor and +jitterFactor
        val jitterMultiplier = 1.0 + (randomSource() * 2 - 1) * jitterFactor
        return (backoffMs * jitterMultiplier).toLong().coerceAtLeast(1L)
    }

    /** Returns current base backoff in ms (without jitter), for logging */
    val currentBackoffMs: Long get() = backoffMs
}
