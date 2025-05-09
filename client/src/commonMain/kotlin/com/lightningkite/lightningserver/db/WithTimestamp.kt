package com.lightningkite.lightningserver.db

import com.lightningkite.now
import kotlinx.datetime.Instant

data class WithTimestamp<T>(val item: T, val at: Instant = now())
data class WithTimestampAndLimit<T>(val item: T, val requestedLimit: Int, val at: Instant = now())