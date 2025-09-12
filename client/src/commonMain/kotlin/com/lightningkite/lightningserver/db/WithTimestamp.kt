package com.lightningkite.lightningserver.db

import kotlin.time.Clock.System.now
import kotlin.time.Instant

data class WithTimestamp<T>(val item: T, val at: Instant = now())
data class WithTimestampAndLimit<T>(val item: T, val requestedLimit: Int, val at: Instant = now())