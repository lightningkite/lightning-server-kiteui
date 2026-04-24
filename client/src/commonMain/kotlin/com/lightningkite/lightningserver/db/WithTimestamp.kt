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

/**
 * Extends [WithTimestamp] with information about the requested limit for query results.
 *
 * Used by [ListReconstructionCalculator] to track query result caches. The [requestedLimit]
 * helps determine if a cached result is sufficient for a new request:
 * - If new request has limit <= requestedLimit: can use cached result (just take fewer items)
 * - If new request has limit > requestedLimit: need to refetch (cached result might be incomplete)
 *
 * ## Example
 * ```kotlin
 * // Cached: Query(limit=10) -> 10 items at 2024-01-01 12:00
 * val cached = WithTimestampAndLimit(items, requestedLimit = 10, at = timestamp)
 *
 * // Request 1: Query(limit=5) -> can use cached (take first 5)
 * // Request 2: Query(limit=20) -> must refetch (cache might be missing items 11-20)
 * ```
 *
 * @param T The type of the wrapped value (typically List<T>)
 * @param item The cached value
 * @param requestedLimit The limit that was requested when fetching this data
 * @param at When this value was fetched or last confirmed fresh
 */
public data class WithTimestampAndLimit<T>(val item: T, val requestedLimit: Int, val at: Instant)