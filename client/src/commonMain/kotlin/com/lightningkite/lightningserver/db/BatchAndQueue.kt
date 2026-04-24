package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.identityHashCode
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Batches concurrent requests together to minimize network calls and improve throughput.
 *
 * When multiple callers request different inputs concurrently, this class:
 * 1. Waits a short duration ([batchWait]) to collect more requests
 * 2. Executes all collected requests in a single batch via [fulfill]
 * 3. Distributes results back to individual callers
 *
 * ## Deduplication
 * If the same input is requested multiple times (even by different callers), only one request
 * is made and the result is shared with all callers. This is determined by [T]'s equals/hashCode.
 *
 * ## Batching Strategy
 * - First request for a new input: starts a new queue, waits [batchWait]
 * - Concurrent requests during [batchWait]: added to the same queue
 * - After [batchWait]: queue is sealed and fulfilled, new queue starts for subsequent requests
 * - Queue size limit: 500 items max to prevent unbounded memory growth
 *
 * ## Example Usage
 * ```kotlin
 * // In ModelCache, multiget batches individual item lookups
 * val multiget = BatchAndQueue<ID, T?>(scope) { ids ->
 *     val items = api.query(Query(condition = Condition.OnField(idProp, Condition.Inside(ids))))
 *     val map = items.associateBy { it._id }
 *     ids.map { map[it] } // Return in same order as requested
 * }
 *
 * // Multiple concurrent calls are batched
 * launch { val user1 = multiget(id1) } // \
 * launch { val user2 = multiget(id2) } //  } Single query: [id1, id2, id3]
 * launch { val user3 = multiget(id3) } // /
 * ```
 *
 * ## Important Gotchas
 * - **Ordering**: [fulfill] must return results in the same order as the input list
 * - **Exceptions**: If [fulfill] throws, ALL waiting callers receive the exception
 * - **Cancellation**: If a caller cancels, they stop waiting but the batch still executes
 * - **Queue Limit**: After 500 items, new requests start a fresh queue immediately
 *
 * @param T The input type (request type) - must have proper equals/hashCode
 * @param R The output type (response type)
 * @param scope Coroutine scope for launching batch execution
 * @param batchWait How long to wait for more requests before executing (default 100ms)
 * @param log Optional console for debugging batching behavior
 * @param fulfill Function that executes a batch: receives list of inputs, returns list of outputs in same order
 */
public class BatchAndQueue<T, R>(
    public val scope: CoroutineScope,
    public val batchWait: Duration = 0.1.seconds,
    public val log: Log? = null,
    public val fulfill: suspend (List<T>) -> List<R>
) {
    /**
     * Maps inputs to all callers waiting for that input's result.
     * Multiple callers requesting the same input share the same deferred result.
     */
    public val outgoing: HashMap<T, ArrayList<CompletableDeferred<R>>> = HashMap<T, ArrayList<CompletableDeferred<R>>>()

    /**
     * The current queue collecting requests. Null when no queue is active (all requests have been fulfilled).
     * Set to null after [batchWait] to seal the queue and start fulfillment.
     */
    public var multigetQueue: HashSet<T>? = null

    /**
     * Requests a result for the given input, batching with other concurrent requests.
     *
     * ## Behavior
     * 1. If this input is already in progress: joins the existing request
     * 2. If a queue exists and isn't full (< 500 items): adds to queue
     * 3. Otherwise: starts a new queue
     *
     * After [batchWait], the queue is sealed and [fulfill] is called with all queued inputs.
     * The result is distributed to all callers who requested this input.
     *
     * @param input The request input
     * @return The result for this input
     * @throws Exception If [fulfill] throws, all waiting callers receive the exception
     */
    public suspend operator fun invoke(input: T): R {
        // Create a deferred result for this caller
        val deferred = CompletableDeferred<R>()
        log?.log("$input starting with deferred ${deferred.identityHashCode()}")

        // CASE 1: Same input is already in progress - join the existing request
        // This handles deduplication: multiple callers for the same input share one request
        outgoing[input]?.let {
            log?.log("$input already in progress")
            it.add(deferred)
            return deferred.await()
        } ?: run {
            // First request for this input - register it
            outgoing[input] = arrayListOf(deferred)
        }

        // CASE 2: Queue exists and has room - add to existing queue
        // The 500-item limit prevents unbounded memory growth
        multigetQueue?.takeUnless { it.size > 500 }?.let {
            log?.log("Adding $input to existing queue")
            it.add(input)
        } ?: run {
            // CASE 3: No queue or queue is full - start a new queue
            log?.log("Starting new queue for $input")
            val queue = HashSet<T>()
            queue.add(input)
            this.multigetQueue = queue

            // Launch coroutine to execute the batch after delay
            scope.launch {
                // Wait for more requests to accumulate
                delay(batchWait)
                log?.log("Starting request")

                // Seal the queue: don't let more items be added to this batch
                // Use identity check (===) to handle race where queue was already replaced
                if (multigetQueue === queue) multigetQueue = null

                try {
                    // Execute the batch: fulfill all inputs at once
                    val allInputs = queue.toList()
                    val allOutputs = allInputs.zip(fulfill(allInputs))

                    // Distribute results to all waiting callers
                    allOutputs.forEach { (key, value) ->
                        val out = outgoing.remove(key)
                        log?.log("Finished request. Sending results to ${out?.joinToString { it.identityHashCode().toString() }}")
                        out?.forEach {
                            it.complete(value)
                        }
                    }
                } catch (e: Exception) {
                    // If fulfill throws, propagate the error to ALL waiting callers in this batch
                    // Note: CancellationException is not special-cased here, all callers get the error
                    e.printStackTrace()
                    queue.forEach { key ->
                        val out = outgoing.remove(key)
                        log?.log("Failed to send request. Sending $e to ${out?.joinToString { it.identityHashCode().toString() }}")
                        out?.forEach { it.completeExceptionally(e) }
                    }
                }
            }
        }

        // Wait for the result (from either an existing or new batch)
        try {
            return deferred.await()
        } catch(e: CancellationException) {
            // Caller cancelled - just propagate the cancellation
            // The batch will still complete, but this caller stops waiting
            throw e
        } catch(t: Throwable) {
            // Other exceptions from fulfill are propagated here
            log?.log("Failed to report. Error: ${t.message}")
            throw t
        }
    }
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Configurable Queue Limit
 *    Current: Hardcoded 500-item limit
 *    Suggestion: Make configurable per-instance
 *
 * 2. Batch Size Metrics
 *    Current: No visibility into batch sizes, wait times, or deduplication rate
 *    Suggestion: Track metrics: average batch size, max batch size, deduplication hit rate
 *
 * 3. Adaptive Batch Wait
 *    Current: Fixed batchWait duration
 *    Suggestion: Dynamically adjust based on request rate or batch size
 *
 * 4. Partial Failure Handling
 *    Current: One failure fails the entire batch
 *    Problem: Single bad input can fail 500 requests
 *    Suggestion: Support partial results, individual error handling
 *
 * 5. Cancellation Cleanup
 *    Current: Cancelled callers leave their deferred in outgoing until batch completes
 *    Problem: Minor memory waste if many callers cancel
 *    Suggestion: Remove cancelled deferreds from outgoing map
 *
 * 6. Queue Prioritization
 *    Current: FIFO processing, no prioritization
 *    Suggestion: Support priority levels for time-sensitive vs. background requests
 *
 * 7. Batch Timeout
 *    Current: Only batchWait controls timing, no overall timeout
 *    Problem: Fulfill could hang indefinitely
 *    Suggestion: Add timeout for fulfill execution
 *
 * 8. Ordering Validation
 *    Current: Assumes fulfill returns results in correct order, no validation
 *    Problem: Silent bugs if fulfill breaks the contract
 *    Suggestion: Validate output list size matches input list size
 *
 * 9. Thread Safety
 *    Current: Concurrent access to outgoing/multigetQueue from multiple coroutines
 *    Problem: Potential race conditions without explicit synchronization
 *    Suggestion: Document thread-safety guarantees or add synchronization
 */