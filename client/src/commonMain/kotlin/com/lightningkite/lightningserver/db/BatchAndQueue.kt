package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.identityHashCode
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Combines requests made within [batchWait] of each other into one call to [fulfill].
 *
 * Callers asking for an input that is already queued or in flight share its result instead of
 * adding it again, so [T] needs a meaningful `equals` and `hashCode`. A batch holds at most about
 * 500 inputs; once it is full, the next request starts a new one.
 *
 * If [fulfill] throws, every caller waiting on that batch receives the exception. A caller that is
 * cancelled stops waiting, but its batch still runs.
 *
 * @param fulfill receives the batch's inputs and returns one result per input, in the same order.
 */
public class BatchAndQueue<T, R>(
    public val scope: CoroutineScope,
    public val batchWait: Duration = 0.1.seconds,
    public val log: Log? = null,
    public val fulfill: suspend (List<T>) -> List<R>
) {
    /** Callers waiting on each input that is queued or in flight. */
    public val outgoing: HashMap<T, ArrayList<CompletableDeferred<R>>> = HashMap<T, ArrayList<CompletableDeferred<R>>>()

    /** The batch still accepting inputs, or `null` if none is. */
    private var multigetQueue: HashSet<T>? = null

    /**
     * Returns the result for [input], fetched together with whatever else is requested within [batchWait].
     *
     * @throws Exception whatever [fulfill] threw for this input's batch.
     */
    public suspend operator fun invoke(input: T): R {
        val deferred = CompletableDeferred<R>()
        log?.log("$input starting with deferred ${deferred.identityHashCode()}")

        outgoing[input]?.let {
            log?.log("$input already in progress")
            it.add(deferred)
            return deferred.await()
        } ?: run {
            outgoing[input] = arrayListOf(deferred)
        }

        multigetQueue?.takeUnless { it.size > 500 }?.let {
            log?.log("Adding $input to existing queue")
            it.add(input)
        } ?: run {
            log?.log("Starting new queue for $input")
            val queue = HashSet<T>()
            queue.add(input)
            this.multigetQueue = queue

            scope.launch {
                delay(batchWait)
                log?.log("Starting request")

                // Claude: A full batch may already have been replaced by a newer one, which must stay open.
                if (multigetQueue === queue) multigetQueue = null

                try {
                    val allInputs = queue.toList()
                    val allOutputs = allInputs.zip(fulfill(allInputs))

                    allOutputs.forEach { (key, value) ->
                        val out = outgoing.remove(key)
                        log?.log("Finished request. Sending results to ${out?.joinToString { it.identityHashCode().toString() }}")
                        out?.forEach {
                            it.complete(value)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    queue.forEach { key ->
                        val out = outgoing.remove(key)
                        log?.log("Failed to send request. Sending $e to ${out?.joinToString { it.identityHashCode().toString() }}")
                        out?.forEach { it.completeExceptionally(e) }
                    }
                }
            }
        }

        try {
            return deferred.await()
        } catch(e: CancellationException) {
            throw e
        } catch(t: Throwable) {
            log?.log("Failed to report. Error: ${t.message}")
            throw t
        }
    }
}

