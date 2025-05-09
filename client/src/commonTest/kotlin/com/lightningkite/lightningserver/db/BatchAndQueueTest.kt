package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchAndQueueTest {
    val testLog = if(Platform.current == Platform.Desktop) ConsoleRoot else null
    @Test
    fun batch() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(1000)
            it
        }
        launch { b(1) }
        launch { b(2) }
        launch { b(3) }
        advanceUntilIdle()
        assertEquals(1, activations)
    }

    @Test
    fun noOverlappingRequests() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(1000)
            it
        }
        launch { b(1) }
        delay(200)
        launch { b(1) }
        delay(200)
        launch { b(1) }
        advanceUntilIdle()
        assertEquals(1, activations)
    }
}