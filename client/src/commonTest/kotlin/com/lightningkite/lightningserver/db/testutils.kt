package com.lightningkite.lightningserver.db

import com.lightningkite.default
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds


fun runTest2(action: suspend TestScope.() -> Unit) {
    runTest {
        val oldClock = Clock.default
        try {
            Clock.default = object: Clock {
                val start = Clock.System.now()
                override fun now(): Instant = start + currentTime.milliseconds
            }
            action()
        } finally {
            Clock.default = oldClock
        }
    }
}