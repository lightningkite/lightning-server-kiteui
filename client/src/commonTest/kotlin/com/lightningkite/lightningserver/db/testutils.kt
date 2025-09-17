package com.lightningkite.lightningserver.db

import com.lightningkite.services.ClockContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds


fun runTest2(action: suspend TestScope.() -> Unit) {
    lateinit var scope: TestScope
    runTest(ClockContextElement(
        object: Clock {
            val start = Clock.System.now()
            override fun now(): Instant = start + scope.currentTime.milliseconds
        }
    )) {
        scope = this
        action()
    }
}