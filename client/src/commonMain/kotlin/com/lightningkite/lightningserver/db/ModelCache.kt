package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Console
import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.lightningdb.CollectionUpdates
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.MassModification
import com.lightningkite.lightningdb.Modification
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb._id
import com.lightningkite.lightningdb.modification
import com.lightningkite.now
import com.lightningkite.readable.AppScope
import com.lightningkite.readable.BasicListenable
import com.lightningkite.readable.LateInitProperty
import com.lightningkite.readable.Property
import com.lightningkite.readable.Readable
import com.lightningkite.readable.ReadableState
import com.lightningkite.readable.awaitOnce
import com.lightningkite.readable.lens
import com.lightningkite.readable.lensListenable
import com.lightningkite.readable.onRemove
import com.lightningkite.readable.shared
import com.lightningkite.readable.sharedProcess
import com.lightningkite.readable.sharedProcessRaw
import com.lightningkite.readable.use
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlin.Unit
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class ModelCache<T : HasId<ID>, ID : Comparable<ID>>(
    val skipCache: ClientModelRestEndpoints<T, ID>,
    val serializer: KSerializer<T>,
//    val newest: (T?, T?) -> T? = { _, it -> it },
    val onUpdate: ((CollectionUpdates<T, ID>) -> Unit)? = null,
    val scope: CoroutineScope = AppScope,
    val log: Console? = null
) : ModelCacheLike<T, ID> {
    private val idProp = serializer._id()

    // The main pipeline for all data changes in the system
    val newData = Property<CacheUpdate<T, ID>>(CacheUpdate.SocketOverload())
    val interrupt = InterruptibleDelay()

    // sockets
    val sockets: SharedCollectionUpdatesSocket<T, ID>? =
        (skipCache as? ClientModelRestEndpointsPlusUpdatesWebsocket<T, ID>)?.let {
            SharedCollectionUpdatesSocket(
                scope = scope,
                socket = it.updates(),
                log = log?.tag("Sockets"),
                onChange = { it ->
                    if (it.overload)
                        newData.value = CacheUpdate.SocketOverload()
                    else
                        newData.value = CacheUpdate.SocketChanges(
                            changed = it.updates,
                            removed = it.remove,
                            fromCondition = sockets!!.listeningStatus.value.fullCondition,
                            fromRequirements = sockets.listeningStatus.value.requirements
                        )
                }
            )
        }


    val multiget = BatchAndQueue<ID, T?>(scope, log = log?.tag("multiget")) {
        val r = skipCache.query(
            Query(
                condition = Condition.OnField(idProp, Condition.Inside(it))
            )
        )
        newData.value = CacheUpdate.MutationResult(r)
        val map = r.associateBy { it._id }
        it.map { map[it] }
    }
    val queryInternal = BatchAndQueue<Query<T>, List<T>>(scope, log = log?.tag("queryInternal")) {
        coroutineScope {
            it.map {
                async {
                    val r = skipCache.query(it)
                    newData.value = CacheUpdate.QueryResult(it, r)
                    r
                }
            }.awaitAll()
        }
    }

    val lastIndividualValues = HashMap<ID, LateInitProperty<WithTimestamp<T?>>>()

    init {
        newData.addListener {
            when (val update = newData.value) {
                is CacheUpdate.DeletionResult -> update.deletedIds.forEach { id ->
                    lastIndividualValues.getOrPut(id, ::LateInitProperty).value = WithTimestamp(null)
                }

                is CacheUpdate.SocketOverload -> lastIndividualValues.values.forEach { it.unset() }
                else -> update.items?.forEach { item ->
                    lastIndividualValues.getOrPut(item._id, ::LateInitProperty).value = WithTimestamp(item)
                }
            }
        }
    }

    fun idIs(id: ID) = Condition.OnField(idProp, Condition.Equal(id))
    val WithTimestamp<T?>.isLive
        get() = item != null && sockets?.listeningStatus?.value?.requirements
            ?.asSequence()
            ?.filter { it.condition.invoke(item) }
            ?.minOfOrNull { it.activatedAt ?: Instant.DISTANT_FUTURE }
            ?.let { at > it } == true

    fun Condition<T>.isLiveAt(timestamp: Instant) = sockets?.listeningStatus?.value?.requirements
        ?.asSequence()
        ?.filter { it.condition == this }
        ?.minOfOrNull { it.activatedAt ?: Instant.DISTANT_FUTURE }
        ?.let { timestamp > it } == true

    fun WithTimestamp<T?>.couldExpireAt(recencyRequirement: Duration): Duration = when {
        isLive -> recencyRequirement
        else -> recencyRequirement - (now() - at)
    }

    override fun item(
        id: ID,
        maximumAge: Duration,
        pullFrequency: Duration,
    ): ModelCacheItemReadable<T> = ModelCacheItemReadableImpl(id, maximumAge, pullFrequency)
    inner class ModelCacheItemReadableImpl(
        val id: ID,
        val maximumAge: Duration,
        val pullFrequency: Duration,
    ) : ModelCacheItemReadable<T> {
        val log = this@ModelCache.log?.tag("$id")
        val interrupt = this@ModelCache.interrupt.child()
        val basis = lastIndividualValues.getOrPut(id, ::LateInitProperty)
        val processWhileRunning = ResourceUse(scope) {
            onRemove { log?.log("No longer needed") }

            // Use a live socket if pullFrequency is very low.
            val socketToWaitFor = if (pullFrequency < 30.seconds && sockets != null) {
                log?.log("Using socket")
                val r = sockets.require(Condition.OnField(idProp, Condition.Equal(id)))
                use(r)
                onRemove(r.satisfied.addListener {
                    if (r.satisfied.value) {
                        launch {
                            log?.log("Fetching after socket connected")
                            multiget(id)
                        }
                    }
                })
                r
            } else null

            // Automatically emit our best known value, if it matches the requirements.
            basis.state.getOrNull()?.let { lastKnown ->
                if (lastKnown.isLive || now() - lastKnown.at < maximumAge) {
                    log?.log("Found existing value ${lastKnown.at}")
                } else null
            } ?: run {
                // Otherwise, pull immediately
                if (socketToWaitFor != null) {
                    log?.log("Waiting for socket to connect...")
                    withTimeoutOrNull<Unit>(5.seconds) {
                        // Wait for the socket to connect first though, if we have one.
                        socketToWaitFor.wait()
                        log?.log("Socket connected.")
                    } ?: log?.log("Failed to connect to socket")
                }
                log?.log("Initial fetch")
                multiget(id)
                log?.log("Initial fetch complete.")
            }

            // Pull regularly
            val pullFrequency = maxOf(5.seconds, pullFrequency)
            while (true) {
                val next = basis.state.getOrNull()?.couldExpireAt(pullFrequency) ?: (-1).seconds
                if (next > 0.seconds) {
                    log?.log("No need to pull for $next")
                    interrupt.delay(next)
                } else {
                    log?.log("Needs pull, starting")
                    multiget(id)
                    interrupt.delay(pullFrequency)
                }
            }
        }

        override val state: ReadableState<T?>
            get() {
                return basis.state.handle(
                    success = {
                        if (it.isLive || now() - it.at < maximumAge) ReadableState(it.item)
                        else ReadableState.notReady
                    },
                    exception = { ReadableState.exception(it) },
                    notReady = { ReadableState.notReady }
                )
            }

        val diff = basis.lens { it.item }.uses(processWhileRunning)
        override fun addListener(listener: () -> Unit): () -> Unit = diff.addListener(listener)
        override val lastUpdatedAt: Readable<Instant?> = basis.lens { it.at }
        val live = shared(coroutineContext = scope.coroutineContext) {
            sockets?.listeningStatus?.let(::rerunOn)
            basis().isLive
        }

        override suspend fun invalidate() {
            basis.unset()
            multiget(id)
            interrupt.interrupt()
        }

        override suspend fun modify(modification: Modification<T>): T? {
            return skipCache.modify(id, modification).also {
                run {
                    newData.value = CacheUpdate.MutationResult(items = listOf(it))
                }
            }
        }

        override suspend fun delete() {
            return skipCache.delete(id)
                .also { run { newData.value = CacheUpdate.DeletionResult(setOf(id)) } }
        }

        override suspend fun set(value: T?) {
            if (value == null) delete()
            else {
                val existing = awaitOnce()
                if (existing == null)
                    skipCache.insert(value).also {
                        run {
                            newData.value = CacheUpdate.MutationResult(items = listOf(it))
                        }
                    }
                else
                    modification(serializer, existing, value)?.let {
                        skipCache.modify(id, it)
                    }?.also { run { newData.value = CacheUpdate.MutationResult(items = listOf(it)) } }
            }
        }

        override fun equals(other: Any?): Boolean = other is ModelCache<T, ID>.ModelCacheItemReadableImpl
                && id == other.id
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = id.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }

    val cache: ListReconstructionCalculator<T, ID> = NaiveListReconstructionCalculator<T, ID>(serializer, log = log?.tag("CollectionCache"))

    init {
        newData.addListener { cache.update(newData.value) }
    }

    override fun list(
        query: Query<T>,
        maximumAge: Duration,
        pullFrequency: Duration,
    ): ModelCacheLimitReadable<T> = ModelCacheLimitReadableImpl(query, maximumAge, pullFrequency)
    inner class ModelCacheLimitReadableImpl(
        val query: Query<T>,
        val maximumAge: Duration,
        val pullFrequency: Duration,
    ) : ModelCacheLimitReadable<T> {
        val interrupt = this@ModelCache.interrupt.child()
        var currentQuery = query
        val processWhileRunning = ResourceUse(scope) {
            val log = log?.tag("${currentQuery.condition} ${currentQuery.orderBy}")

            // Use a live socket if pullFrequency is very low.
            val socketToWaitFor = if (pullFrequency < 30.seconds && sockets != null) {
                log?.log("Using socket")
                val r = sockets.require(currentQuery.condition)
                use(r)
                onRemove(r.satisfied.addListener {
                    if (r.satisfied.value) {
                        launch {
                            log?.log("Fetching after socket connected")
                            queryInternal(currentQuery)
                        }
                    }
                })
                r
            } else null

            // Automatically emit our best known value, if it matches the requirements.
            cache.cached(currentQuery)?.let { lastKnown ->
                if (currentQuery.condition.isLiveAt(lastKnown.at) || now() - lastKnown.at < maximumAge) {
                    log?.log("Found existing value ${lastKnown.at} / ${lastKnown.requestedLimit}")
                } else null
            } ?: run {
                // Otherwise, pull immediately
                if (socketToWaitFor != null) {
                    withTimeoutOrNull<Unit>(5.seconds) {
                        // Wait for the socket to connect first though, if we have one.
                        log?.log("Waiting for socket to connect...")
                        socketToWaitFor.wait()
                        log?.log("Socket connected.")
                    } ?: log?.log("Failed to connect to socket")
                }
                log?.log("Initial fetch")
                queryInternal(cache.recommendQuery(currentQuery))
                log?.log("Initial fetch complete.")
            }

            // Pull regularly
            val pullFrequency = maxOf(5.seconds, pullFrequency)
            while (true) {
                val mostRecent = cache.cached(currentQuery)
                val next = when {
                    mostRecent == null -> (-1).seconds
                    mostRecent.requestedLimit < currentQuery.limit -> (-1).seconds
                    currentQuery.condition.isLiveAt(mostRecent.at) -> pullFrequency
                    else -> pullFrequency - (now() - mostRecent.at)
                }
                // TODO: if we're up to date otherwise, can we do limit extension to request more items?
                if (next > 0.seconds) {
                    log?.log("No need to pull for $next")
                    interrupt.delay(next)
                } else {
                    //TODO: Harden against exceptions
                    log?.log("Needs pull, starting because most recent is ${mostRecent?.at} / ${mostRecent?.requestedLimit}")
                    queryInternal(cache.recommendQuery(currentQuery))
                    interrupt.delay(pullFrequency)
                }
            }
        }

        override val state: ReadableState<List<T>>
            get() {
                return cache.cached(currentQuery)?.let { lastKnown ->
                    if (currentQuery.condition.isLiveAt(lastKnown.at) || now() - lastKnown.at < maximumAge) {
                        ReadableState(lastKnown.item)
                    } else ReadableState.notReady
                } ?: ReadableState.notReady
            }

        val diff = cache.updates(query).lensListenable {
            cache.cached(currentQuery)?.let { lastKnown ->
                if (currentQuery.condition.isLiveAt(lastKnown.at) || now() - lastKnown.at < maximumAge) {
                    lastKnown.item
                } else null
            }
        }.uses(processWhileRunning)
        val diffWithTs = cache.updates(query).lensListenable {
            cache.cached(currentQuery)?.let { lastKnown ->
                if (currentQuery.condition.isLiveAt(lastKnown.at) || now() - lastKnown.at < maximumAge) {
                    lastKnown
                } else null
            }
        }.uses(processWhileRunning)

        override fun addListener(listener: () -> Unit): () -> Unit = diff.addListener(listener)
        override val lastUpdatedAt: Readable<Instant?> = diffWithTs.lens { it?.at }
        val live = shared(coroutineContext = scope.coroutineContext) {
            sockets?.listeningStatus?.let(::rerunOn)
            cache.cached(currentQuery)?.at?.let { time ->
                currentQuery.condition.isLiveAt(time)
            } == true
        }

        override var limit: Int = query.limit
            set(value) {
                field = value
                currentQuery = currentQuery.copy(limit = value)
                interrupt.interrupt()
            }

        override fun equals(other: Any?): Boolean = other is ModelCache<T, ID>.ModelCacheLimitReadableImpl
                && query == other.query
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = query.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }


    // Other operations
    override suspend fun add(item: T): T {
        return skipCache.insert(item).also {
            newData.value = CacheUpdate.MutationResult(items = setOf(it))
        }
    }

    override suspend fun addAll(items: List<T>): List<T> {
        return skipCache.insertBulk(items).also {
            newData.value = CacheUpdate.MutationResult(items = it.toSet())
        }
    }

    override suspend fun upsert(item: T): ModelCacheItemReadable<T> {
        skipCache.upsert(item._id, item).let {
            newData.value = CacheUpdate.MutationResult(items = setOf(it))
        }
        return this[item._id]
    }

    override suspend fun bulkModify(bulkUpdate: MassModification<T>): Int {
        return skipCache.bulkModify(bulkUpdate).also {
            newData.value = CacheUpdate.SocketOverload()
        }
    }

// Local shenanigans

    val totalInvalidation =
        MutableSharedFlow<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)

    suspend fun totallyInvalidate() {
        totalInvalidation.tryEmit(Unit)
        interrupt.interrupt()
    }

    fun localSignalUpdate(matching: (T) -> Boolean, modify: (T) -> T?) {
        val updates = HashSet<T>()
        val removals = HashSet<ID>()
        lastIndividualValues.values.asSequence()
            .mapNotNull { it.value.item }
            .filter(matching)
            .forEach {
                modify(it)?.let { updates.add(it) }
                    ?: removals.add(it._id)
            }
        newData.value = CacheUpdate.MutationResult(items = updates)
        newData.value = CacheUpdate.DeletionResult(deletedIds = removals)
    }

    fun localInsert(item: T): T {
        newData.value = CacheUpdate.MutationResult(items = setOf(item))
        return item
    }
}

