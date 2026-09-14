package com.lightningkite.lightningserver.db

import kotlin.time.Instant

/**
 * Wraps a value with a timestamp indicating when it was last known to be accurate.
 *
 * Used throughout the caching system to track data freshness. The timestamp allows
 * determining if cached data is still within acceptable age ([ModelCache] maximumAge parameter).
 *
 * @param T The type of the wrapped value
 * @param item The cached value
 * @param at When this value was fetched or last confirmed fresh
 */
public data class WithTimestamp<T>(val item: T, val at: Instant)