package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Console
import com.lightningkite.kiteui.identityHashCode
import com.lightningkite.lightningdb.CollectionUpdates
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.inside
import com.lightningkite.now
import com.lightningkite.serialization.DataClassPathAccess
import com.lightningkite.serialization.DataClassPathSelf
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BatchAndQueue<T, R>(
    val scope: CoroutineScope,
    val batchWait: Duration = 0.1.seconds,
    val log: Console? = null,
    val fulfill: suspend (List<T>) -> List<R>
) {
    val outgoing = HashMap<T, ArrayList<CompletableDeferred<R>>>()
    var multigetQueue: HashSet<T>? = null
    suspend operator fun invoke(input: T): R {
        val deferred = CompletableDeferred<R>()
        log?.log("$input starting with deferred ${deferred.identityHashCode()}")

        // If the request is already going, there is no need
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
            // Start a new queue.
            val queue = HashSet<T>()
            queue.add(input)
            this.multigetQueue = queue

            scope.launch {
                delay(batchWait)
                log?.log("Starting request")
                // stop letting things get added to this queue.
                if (multigetQueue === queue) multigetQueue = null
                try {
                    // get all the items in one big request
                    val allInputs = queue.toList()
                    val allOutputs = allInputs.zip(fulfill(allInputs))
                    // send the results
                    allOutputs.forEach { (key, value) ->
                        val out = outgoing.remove(key)
                        log?.log("Finished request.  Sending results to ${out?.joinToString { it.identityHashCode().toString() }}")
                        out?.forEach {
                            it.complete(value)
                        }
                    }
                } catch (e: Exception) {
//                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    // let everyone know of our failure
                    queue.forEach { key ->
                        val out = outgoing.remove(key)
                        log?.log("Failed to send request.  Sending ", e, " to ${out?.joinToString { it.identityHashCode().toString() }}")
                        out?.forEach { it.completeExceptionally(e) }
                    }
                }

            }
        }
        try {
            return deferred.await()
        } catch(e: CancellationException) {
            // Cool.  We don't care.
            throw e
        }catch(t: Throwable) {
            log?.log("Failed to report.  Error: ${t.message}")
            throw t
        }
    }
}