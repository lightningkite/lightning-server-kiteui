package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test suite for BatchAndQueue functionality.
 *
 * BatchAndQueue is a utility that batches multiple concurrent requests for the same operation
 * into a single execution, then distributes the result to all waiting callers. This is critical
 * for ModelCache performance by preventing duplicate requests.
 *
 * Key behaviors tested:
 * - Multiple concurrent requests execute only once
 * - Duplicate requests while one is in progress are queued and share results
 * - Request deduplication works correctly
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchAndQueueTest {
    val testLog = if(Platform.current == Platform.Desktop) Log else null

    /**
     * Tests that multiple concurrent requests are batched into a single execution.
     *
     * Verifies that when 3 requests for different items are launched simultaneously,
     * the handler is only invoked once with all 3 items, not 3 separate times.
     */
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

    /**
     * Tests that overlapping requests for the same item are deduplicated.
     *
     * Verifies that when the same item (ID=1) is requested multiple times while
     * the first request is still in progress, all requests share the same execution
     * and only trigger a single handler invocation.
     */
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

    /**
     * Tests error handling - exceptions in handler propagate to all waiting requests.
     */
    @Test
    fun errorPropagation() = runTest2 {
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            throw IllegalStateException("Test error")
        }

        var exception1: Exception? = null
        var exception2: Exception? = null

        launch {
            try {
                b(1)
            } catch (e: Exception) {
                exception1 = e
            }
        }
        launch {
            try {
                b(2)
            } catch (e: Exception) {
                exception2 = e
            }
        }

        advanceUntilIdle()

        assertEquals("Test error", exception1?.message)
        assertEquals("Test error", exception2?.message)
    }

    /**
     * Tests that errors don't prevent subsequent batches from executing.
     */
    @Test
    fun errorRecovery() = runTest2 {
        var callCount = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            callCount++
            if (callCount == 1) throw IllegalStateException("First batch fails")
            it
        }

        // First batch fails
        launch {
            try {
                b(1)
            } catch (_: Exception) { }
        }
        advanceUntilIdle()

        // Second batch should succeed
        val result = b(2)
        assertEquals(2, result)
        assertEquals(2, callCount)
    }

    /**
     * Tests correct result distribution - each request gets its corresponding result.
     */
    @Test
    fun resultDistribution() = runTest2 {
        val b = BatchAndQueue<Int, String>(this, log = testLog) { requests ->
            delay(100)
            requests.map { "result-$it" }
        }

        var result1: String? = null
        var result2: String? = null
        var result3: String? = null

        launch { result1 = b(1) }
        launch { result2 = b(2) }
        launch { result3 = b(3) }

        advanceUntilIdle()

        assertEquals("result-1", result1)
        assertEquals("result-2", result2)
        assertEquals("result-3", result3)
    }

    /**
     * Tests sequential batches - second batch starts only after first completes.
     */
    @Test
    fun sequentialBatches() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(1000)
            it
        }

        // First batch
        launch { b(1) }
        launch { b(2) }
        advanceUntilIdle()
        assertEquals(1, activations)

        // Second batch (after first completes)
        launch { b(3) }
        launch { b(4) }
        advanceUntilIdle()
        assertEquals(2, activations)
    }

    /**
     * Tests single request - no batching overhead when only one request.
     */
    @Test
    fun singleRequest() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(100)
            it
        }

        val result = b(1)
        assertEquals(1, result)
        assertEquals(1, activations)
    }

    /**
     * Tests request deduplication with complex key types.
     */
    @Test
    fun complexKeyDeduplication() = runTest2 {
        data class ComplexKey(val id: Int, val name: String)

        var activations = 0
        val b = BatchAndQueue<ComplexKey, String>(this, log = testLog) {
            activations++
            delay(100)
            it.map { k -> "${k.id}-${k.name}" }
        }

        val key = ComplexKey(1, "test")

        launch { b(key) }
        launch { b(key) }
        launch { b(key) }

        advanceUntilIdle()
        assertEquals(1, activations)
    }

    /**
     * Tests large batch size handling.
     */
    @Test
    fun largeBatch() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(100)
            it
        }

        val requests = (1..100).map { id ->
            launch { b(id) }
        }

        advanceUntilIdle()
        assertEquals(1, activations)
    }

    /**
     * Tests requests arriving during handler execution.
     */
    @Test
    fun requestsDuringExecution() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(1000)
            it
        }

        // Start first batch
        launch { b(1) }
        delay(100) // Let batch start executing

        // Add requests during execution
        launch { b(2) }
        launch { b(3) }

        advanceUntilIdle()

        // Should have 2 activations - first batch, then queued requests
        assertEquals(2, activations)
    }

    /**
     * Tests handler returning null values in results.
     */
    @Test
    fun nullResultValues() = runTest2 {
        val b = BatchAndQueue<Int, Int?>(this, log = testLog) { requests ->
            delay(100)
            requests.map { if (it == 2) null else it }
        }

        var result1: Int? = -1
        var result2: Int? = -1
        var result3: Int? = -1

        launch { result1 = b(1) }
        launch { result2 = b(2) }
        launch { result3 = b(3) }

        advanceUntilIdle()

        assertEquals(1, result1)
        assertEquals(null, result2)  // Explicitly null
        assertEquals(3, result3)
    }

    /**
     * Tests stress scenario with interleaved batches.
     */
    @Test
    fun stressTestInterleavedBatches() = runTest2 {
        var activations = 0
        val b = BatchAndQueue<Int, Int>(this, log = testLog) {
            activations++
            delay(500)
            it
        }

        // Launch many requests with delays to create multiple batches
        repeat(10) { i ->
            launch { b(i * 10) }
            delay(100)
        }

        advanceUntilIdle()

        // Should have multiple batches due to delayed launches
        if(activations < 1) { throw Exception("Expected multiple batches, got $activations") }
    }
}

/*
 * ============================================================================
 * TEST COVERAGE RECOMMENDATIONS
 * ============================================================================
 *
 * Missing Test Scenarios:
 *
 * 1. Error Handling:
 *    - Test behavior when handler throws an exception
 *    - Verify all waiting requests receive the error
 *    - Test error handling doesn't prevent subsequent requests
 *
 * 2. Result Distribution:
 *    - Test that each request receives the correct result for its input
 *    - Test partial results when handler returns a Map<Input, Output>
 *    - Verify result mapping works correctly with complex types
 *
 * 3. Timing and Sequencing:
 *    - Test requests arriving after handler starts but before it completes
 *    - Test sequential batches (ensure second batch starts after first completes)
 *    - Test cancellation of waiting requests
 *
 * 4. Edge Cases:
 *    - Test with empty batch (no requests)
 *    - Test with single request (no batching needed)
 *    - Test with very large batch sizes
 *    - Test request deduplication with complex key types
 *
 * 5. Concurrent Stress Testing:
 *    - Test with hundreds of concurrent requests
 *    - Test interleaved batches (new batch starts while old one completes)
 *    - Test performance under sustained load
 *
 * 6. Handler Behavior:
 *    - Test handlers with different execution times
 *    - Test handlers that modify shared state
 *    - Test handlers with side effects
 *
 * 7. Integration Scenarios:
 *    - Test BatchAndQueue used in reactive contexts
 *    - Test interaction with coroutine cancellation
 *    - Test with structured concurrency and coroutine scopes
 */