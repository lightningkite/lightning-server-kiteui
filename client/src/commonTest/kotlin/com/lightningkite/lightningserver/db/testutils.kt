package com.lightningkite.lightningserver.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds


/**
 * [runTest] with a test Dispatchers.Main, which AppScope and `remember` run on, and a [Clock] on the test's virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTest2(action: suspend TestScope.() -> Unit): TestResult {
    // Installed before runTest, so runTest's scheduler is this one's and work on main follows virtual time.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    lateinit var scope: TestScope
    return runTest(ClockContextElement(
        object: Clock {
            val start = Clock.System.now()
            override fun now(): Instant = start + scope.currentTime.milliseconds
        }
    )) {
        scope = this
        try {
            action()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
