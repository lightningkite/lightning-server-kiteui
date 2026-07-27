package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningserver.networking.toTypedWebsocket
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.ClientModelRestUpdatesWebsocket
import com.lightningkite.services.database.*
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.plus
import com.lightningkite.reactive.extensions.use
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.lensListenable
import kotlinx.coroutines.*
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * A sophisticated multi-layer caching system for model collections with real-time synchronization.
 *
 * ModelCache sits between your application and the REST API, providing:
 * - Intelligent caching of individual items and query results
 * - Automatic background polling with configurable staleness thresholds
 * - Optional WebSocket integration for real-time updates
 * - Request batching to minimize network calls
 * - Unified update pipeline that coordinates all data changes
 *
 * ## Architecture
 *
 * ### The Update Pipeline
 * All data changes flow through a single [newData] signal, which feeds into:
 * - [ListReconstructionCalculator] (via [cache]) for query result updates
 * - [lastIndividualValues] for individual item tracking
 *
 * This ensures consistency - when an item is mutated, both query caches and individual item
 * caches are updated atomically.
 *
 * ### Polling Strategy
 * Items and queries are fetched based on two parameters:
 * - **maximumAge**: How stale data can be before it's considered invalid
 * - **pullFrequency**: How often to poll for fresh data when not using sockets
 *
 * When WebSockets are available and [pullFrequency] < 30 seconds, the cache switches to
 * socket-based updates and only polls on initial fetch and as a fallback.
 *
 * ### Request Batching
 * Concurrent requests are batched using [BatchAndQueue], so that many items requested at once
 * become a single multi-ID query.
 *
 * ### WebSocket Integration
 * If [skipCache] implements [ClientModelRestUpdatesWebsocket], a [SharedCollectionUpdatesSocket]
 * is created to manage real-time updates. The socket:
 * - Tracks which conditions are actively being listened to
 * - Receives change notifications from the server
 * - Automatically reconnects and re-subscribes on disconnection
 * - Falls back to polling if socket fails to connect within 5 seconds
 *
 * ## Important Gotchas
 *
 * - **Socket Activation Timing**: If a socket is activated AFTER data is cached, changes might be
 *   missed. The code checks `activatedAt` timestamps to avoid applying stale socket updates.
 * - **Limit Handling**: When items are deleted from a limited query, the cache doesn't automatically
 *   fetch more items to fill the gap. See [ListReconstructionCalculator] for details.
 * - **Overload Clearing**: [CacheUpdate.SocketOverload] clears ALL caches when the socket can't keep up.
 *   This is intentionally aggressive to avoid serving stale data.
 * - **Minimum Pull Frequency**: Both polling loops enforce a minimum of 5 seconds between fetches
 *   to avoid overwhelming the server.
 *
 * @param T The model type with an ID field
 * @param ID The ID type, must be comparable
 * @param skipCache The REST API endpoints to fetch from (no caching)
 * @param serializer Serializer for the model type
 * @param scope Coroutine scope for background operations (defaults to AppScope)
 * @param log Optional logger for debugging
 */
public class ModelCache<T : HasId<ID>, ID : Comparable<ID>>(
    public val skipCache: ClientModelRestEndpoints<T, ID>,
    override val serializer: KSerializer<T>,
    public val scope: CoroutineScope = AppScope,
    public val log: Log? = null
) : ModelCacheLike<T, ID> {
    private val idProp = serializer._id()

    /**
     * The central update pipeline - ALL data changes flow through this signal, whether they came
     * from a query, a mutation, or the socket.  Starts as [CacheUpdate.SocketOverload], meaning
     * "nothing is known".
     */
    public val newData: Signal<CacheUpdate<T, ID>> = Signal<CacheUpdate<T, ID>>(CacheUpdate.SocketOverload())

    /** Interrupts pending polling delays.  Each tracked item/query takes a child of this. */
    private val interrupt: InterruptibleDelay = InterruptibleDelay()

    /** Real-time updates, if [skipCache] supports them. */
    @Suppress("UNCHECKED_CAST")
    public val sockets: SharedCollectionUpdatesSocket<T, ID>? =
        (skipCache as? ClientModelRestUpdatesWebsocket<T, ID>)?.let {
            SharedCollectionUpdatesSocket(
                scope = scope,
                socket = it.updates().toTypedWebsocket(),
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


    /**
     * Looks up items by ID, coalescing concurrent requests into a single `_id inside [...]` query.
     *
     * @return For each requested ID, either the found item or null if missing
     */
    private val multiget: BatchAndQueue<ID, T?> = BatchAndQueue<ID, T?>(scope, log = log?.tag("multiget")) {
        val r = skipCache.query(
            Query(
                condition = Condition.OnField(idProp, Condition.Inside(it))
            )
        )
        newData.value = CacheUpdate.MultiGetResult(it.toSet() - r.mapTo(HashSet()) {it._id}, r)
        val map = r.associateBy { it._id }
        it.map { map[it] }
    }

    /**
     * Runs queries.  Unlike [multiget] these are not merged - each executes separately, just
     * concurrently, so the batching only coordinates timing.
     */
    private val queryInternal: BatchAndQueue<Query<T>, List<T>> = BatchAndQueue<Query<T>, List<T>>(scope, log = log?.tag("queryInternal")) {
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

    /**
     * Individual item states, keyed by ID.  A null [WithTimestamp.item] means confirmed missing or
     * deleted, as opposed to a not-ready signal, which means nothing is known yet.
     */
    private val lastIndividualValues: HashMap<ID, LateInitSignal<WithTimestamp<T?>>> = HashMap<ID, LateInitSignal<WithTimestamp<T?>>>()

    init {
        newData.addListener {
            when (val update = newData.value) {
                is CacheUpdate.DeletionResult -> update.deletedIds.forEach { id ->
                    lastIndividualValues.getOrPut(id, ::LateInitSignal).value = WithTimestamp(null, scope.now())
                }
                is CacheUpdate.MultiGetResult -> {
                    update.items.forEach { item ->
                        lastIndividualValues.getOrPut(item._id, ::LateInitSignal).value = WithTimestamp(item, scope.now())
                    }
                    update.missing.forEach { id ->
                        lastIndividualValues.getOrPut(id, ::LateInitSignal).value = WithTimestamp(null, scope.now())
                    }
                }
                is CacheUpdate.SocketOverload -> lastIndividualValues.values.forEach { it.unset() }
                else -> update.items?.forEach { item ->
                    lastIndividualValues.getOrPut(item._id, ::LateInitSignal).value = WithTimestamp(item, scope.now())
                }
            }
        }
    }


    /**
     * Whether the update socket has been covering [timestamp] for anything matching [matches].
     *
     * The `timestamp > activatedAt` comparison is the important part: data cached before the socket
     * connected may have missed changes that happened during that gap, so the socket's guarantee
     * does not extend back over it.  When it does, the data can be treated as fresh indefinitely,
     * because the socket will tell us about any change.
     */
    private fun socketCoveredSince(timestamp: Instant, matches: (Condition<T>) -> Boolean): Boolean =
        sockets?.listeningStatus?.value?.requirements
            ?.asSequence()
            ?.filter { matches(it.condition) }
            ?.minOfOrNull { it.activatedAt ?: Instant.DISTANT_FUTURE }
            ?.let { timestamp > it } == true

    /**
     * The list reconstruction calculator for query result caching.
     *
     * This handles caching and incremental updating of query results. All query-related
     * updates from [newData] are fed into this cache, which intelligently merges them
     * with existing query results without requiring full refetches.
     */
    public val cache: ListReconstructionCalculator<T, ID> = OptimizedListReconstructionCalculator<T, ID>(
        serializer,
        log = log?.tag("CollectionCache"),
        clock = scope.coroutineContext[ClockContextElement]?.clock ?: Clock.System
    )

    init {
        newData.addListener { cache.update(newData.value) }
    }

    /**
     * A cached value that keeps itself fresh for as long as anything is listening to it.
     *
     * Subclasses say *what* to watch and how to retrieve it; everything about *when* to retrieve,
     * and how freshness and failure turn into a [ReactiveState], lives here.
     *
     * ## Lifecycle
     * The background work starts when the first listener is added and stops when the last one is
     * removed.  It registers a socket requirement (when sockets are in use and [pullFrequency] is
     * short enough to make polling wasteful), does an initial retrieve if nothing acceptable is
     * cached, then polls until nobody is listening.
     */
    public abstract inner class SelfRefreshing<V> internal constructor(
        protected val maximumAge: Duration,
        protected val pullFrequency: Duration,
    ) : ModelCacheReadable<V> {
        /** The condition to subscribe the update socket to. */
        protected abstract val socketCondition: Condition<T>

        /** Fires whenever [cached] may have changed. */
        protected abstract val changes: Listenable

        /** The currently cached value, or null if there is nothing cached. */
        protected abstract fun cached(): WithTimestamp<V>?

        /** Whether the socket is currently keeping [cached] up to date on its own. */
        protected abstract fun isLive(cached: WithTimestamp<V>): Boolean

        /** Whether more must be retrieved even though [cached] is fresh - e.g. a raised limit. */
        protected open fun needsMore(): Boolean = false

        /** Retrieves from the server; results reach [cached] by way of [newData]. */
        protected abstract suspend fun retrieve()

        protected abstract val log: Log?

        private val interrupt: InterruptibleDelay = this@ModelCache.interrupt.child()

        /**
         * The failure from the most recent retrieve attempt, or null if it succeeded.
         *
         * Kept separate from the cached value, which only ever holds data that was successfully
         * retrieved - a failure belongs to the reader that attempted it.
         */
        private val fetchError: Signal<Exception?> = Signal(null)

        private fun WithTimestamp<V>.freshWithin(window: Duration): Boolean =
            isLive(this) || scope.now() - at < window

        /**
         * Retrieves and records the outcome, so [state] can report a failure instead of sitting in
         * a loading state forever.  Rethrows, so callers that can react to it themselves still see it.
         */
        protected suspend fun fetch() {
            try {
                retrieve()
                fetchError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fetchError.value = e
                throw e
            }
        }

        /** [fetch] for the background loop, which has no caller to report a failure to. */
        private suspend fun fetchInBackground() {
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.log("Fetch failed: $e")
            }
        }

        private val processWhileRunning: ResourceUse = ResourceUse(scope) {
            onRemove { log?.log("No longer needed") }

            // Sockets are preferred whenever we would otherwise poll often, since frequent polling
            // is wasteful when the server can just tell us.
            val socket = if (pullFrequency < 30.seconds && sockets != null) {
                log?.log("Using socket")
                sockets.require(socketCondition).also { r ->
                    use(r)
                    // Anything that changed while we were disconnected was missed, so refetch.
                    onRemove(r.satisfied.addListener {
                        if (r.satisfied.value) launch {
                            log?.log("Fetching after socket connected")
                            fetchInBackground()
                        }
                    })
                }
            } else null

            val acceptable = cached()?.let { it.freshWithin(maximumAge) && !needsMore() } == true
            if (acceptable) log?.log("Found existing value")
            else {
                if (socket != null) {
                    // Give the socket a moment to connect first, or we fetch and then immediately
                    // refetch when it connects milliseconds later.
                    log?.log("Waiting for socket to connect...")
                    withTimeoutOrNull<Unit>(5.seconds) { socket.wait() } ?: log?.log("Socket did not connect")
                }
                log?.log("Initial fetch")
                fetchInBackground()
            }

            // Never poll faster than this, no matter what was asked for.
            val pull = maxOf(5.seconds, pullFrequency)
            while (true) {
                val untilStale = cached()
                    ?.takeIf { !needsMore() }
                    ?.let { if (isLive(it)) pull else pull - (scope.now() - it.at) }
                    ?: Duration.ZERO
                if (untilStale > Duration.ZERO) {
                    log?.log("No need to pull for $untilStale")
                    interrupt.delay(untilStale)
                } else {
                    log?.log("Needs pull, starting")
                    fetchInBackground()
                    // Wait a full interval before retrying, so failures don't spin.
                    interrupt.delay(pull)
                }
            }
        }

        /**
         * The current value.
         *
         * Fresh cached data wins over a failed refresh - it is still within the [maximumAge] the
         * caller asked for.  Use [showPreviousOnLoadOrError] to keep displaying data past that point.
         *
         * Reading this does not start the background work; adding a listener does.
         */
        override val state: ReactiveState<V>
            get() {
                cached()?.let { if (it.freshWithin(maximumAge)) return ReactiveState(it.item) }
                return fetchError.value?.let { ReactiveState.exception(it) } ?: ReactiveState.notReady
            }

        /**
         * [state] as a plain [Reactive]: keeps [processWhileRunning] alive while anything is
         * listening, and fires only when the state actually changes.
         *
         * The `lens { it }` is what deduplicates.  It matters that this compares *states* rather
         * than values, so that "nothing loaded yet" and "loaded, and the item is null" are told
         * apart - the latter is how a confirmed-missing item is reported.
         */
        private val observed: Reactive<V> by lazy {
            object : Reactive<V> {
                override val state: ReactiveState<V> get() = this@SelfRefreshing.state
                override fun addListener(listener: () -> Unit): Release = changes.addListener(listener)
            }.lens { it }.uses(processWhileRunning)
        }

        // [state] also changes when a retrieve starts failing or recovers, which [observed] can't
        // see, so both have to be listened to.
        override fun addListener(listener: () -> Unit): Release = (observed + fetchError).addListener(listener)

        override val lastUpdatedAt: Reactive<Instant?> by lazy { changes.lensListenable { cached()?.at } }

        /** Discards any pending delay so the polling loop reconsiders immediately. */
        protected fun interruptPolling(): Unit = interrupt.interrupt()
    }

    override fun item(
        id: ID,
        maximumAge: Duration,
        pullFrequency: Duration,
    ): ModelCacheItemReadable<T> = ModelCacheItemReadableImpl(id, maximumAge, pullFrequency)

    /** Tracks a single item by ID.  See [SelfRefreshing] for the lifecycle. */
    public inner class ModelCacheItemReadableImpl(
        public val id: ID,
        maximumAge: Duration,
        pullFrequency: Duration,
    ) : SelfRefreshing<T?>(maximumAge, pullFrequency), ModelCacheItemReadable<T> {
        override val log: Log? = this@ModelCache.log?.tag("$id")

        /** The shared signal holding this ID's cached value; every reader of the ID sees the same one. */
        private val basis: LateInitSignal<WithTimestamp<T?>> = lastIndividualValues.getOrPut(id, ::LateInitSignal)

        override val socketCondition: Condition<T> get() = Condition.OnField(idProp, Condition.Equal(id))
        override val changes: Listenable get() = basis
        override fun cached(): WithTimestamp<T?>? = basis.state.getOrNull()
        override suspend fun retrieve() { multiget(id) }

        override fun isLive(cached: WithTimestamp<T?>): Boolean {
            val item = cached.item ?: return false
            return socketCoveredSince(cached.at) { it(item) }
        }

        /** Discards the cached value and retrieves fresh data immediately. */
        override suspend fun invalidate() {
            basis.unset()
            fetch()
            interruptPolling()
        }

        override suspend fun modify(modification: Modification<T>): T? =
            skipCache.modify(id, modification).also { newData.value = CacheUpdate.MutationResult(items = listOf(it)) }

        override suspend fun delete() {
            skipCache.delete(id)
            newData.value = CacheUpdate.DeletionResult(setOf(id))
        }

        /**
         * Deletes when [value] is null, inserts when the item does not exist yet, and otherwise
         * sends only the fields that actually differ.
         */
        override suspend fun set(value: T?) {
            if (value == null) return delete()
            val existing = awaitOnce()
            val updated = if (existing == null) skipCache.insert(value)
            else modification(serializer, existing, value)?.let { skipCache.modify(id, it) }
            updated?.let { newData.value = CacheUpdate.MutationResult(items = listOf(it)) }
        }

        override fun equals(other: Any?): Boolean = other is ModelCache<T, ID>.ModelCacheItemReadableImpl
                && id == other.id
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = id.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }

    override fun list(
        query: Query<T>,
        maximumAge: Duration,
        pullFrequency: Duration,
    ): ModelCacheLimitReadable<T> = ModelCacheLimitReadableImpl(query, maximumAge, pullFrequency)

    /** Tracks the results of a query.  See [SelfRefreshing] for the lifecycle. */
    public inner class ModelCacheLimitReadableImpl(
        public val query: Query<T>,
        maximumAge: Duration,
        pullFrequency: Duration,
    ) : SelfRefreshing<List<T>>(maximumAge, pullFrequency), ModelCacheLimitReadable<T> {
        override val log: Log? = this@ModelCache.log?.tag("${query.condition} ${query.orderBy}")

        /** [query] with the current [limit] applied. */
        private var currentQuery: Query<T> = query

        override val socketCondition: Condition<T> get() = currentQuery.condition
        override val changes: Listenable get() = cache.updates
        override fun cached(): WithTimestamp<List<T>>? =
            cache.cached(currentQuery)?.let { WithTimestamp(it.item, it.at) }

        override fun isLive(cached: WithTimestamp<List<T>>): Boolean =
            socketCoveredSince(cached.at) { it == currentQuery.condition }

        /** A cached result fetched under a smaller limit doesn't cover what is being asked for now. */
        override fun needsMore(): Boolean =
            cache.cached(currentQuery)?.let { it.requestedLimit < currentQuery.limit } == true

        override suspend fun retrieve() { queryInternal(cache.recommendQuery(currentQuery)) }

        override var limit: Int
            get() = currentQuery.limit
            @Deprecated("Use limit(n) instead, which reports when the new items have arrived.")
            set(value) {
                currentQuery = currentQuery.copy(limit = value)
                // Nobody is awaiting the extra items, so let the polling loop pick them up.
                interruptPolling()
            }

        override suspend fun limit(count: Int) {
            if (count == currentQuery.limit) return
            currentQuery = currentQuery.copy(limit = count)
            fetch()
        }

        override fun equals(other: Any?): Boolean = other is ModelCache<T, ID>.ModelCacheLimitReadableImpl
                && query == other.query
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = query.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }


    // =============================================================================
    // Mutation operations - all propagate through newData to update all caches
    // =============================================================================

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

    /**
     * Performs a bulk modification (e.g., update all items matching a condition).
     *
     * Clears ALL caches, because the server does not tell us which items the modification actually
     * touched.  Intentionally aggressive - serving stale data is worse than refetching.  Polling
     * loops are interrupted too, or observers would sit in a loading state until their next poll.
     *
     * @return Number of items modified
     */
    override suspend fun bulkModify(bulkUpdate: MassModification<T>): Int {
        return skipCache.bulkModify(bulkUpdate).also {
            newData.value = CacheUpdate.SocketOverload()
            interrupt.interrupt()
        }
    }

    // =============================================================================
    // Local/testing utilities - manipulate cache without API calls
    // =============================================================================

    /**
     * Completely invalidates all caches and interrupts all polling loops.
     * Useful for forcing a full refresh or handling auth changes.
     */
    public suspend fun totallyInvalidate() {
        newData.value = CacheUpdate.SocketOverload()
        interrupt.interrupt()
    }

    /**
     * Applies [modify] to every cached item matching [matching], marking an item deleted when
     * [modify] returns null.
     *
     * Bypasses the API, so local and server state diverge until the next poll or socket update.
     * For optimistic updates and testing.
     */
    public fun localSignalUpdate(matching: (T) -> Boolean, modify: (T) -> T?) {
        val updates = HashSet<T>()
        val removals = HashSet<ID>()
        lastIndividualValues.values.asSequence()
            .mapNotNull { it.state.getOrNull()?.item }
            .filter(matching)
            .forEach {
                modify(it)?.let { updates.add(it) }
                    ?: removals.add(it._id)
            }
        newData.value = CacheUpdate.MutationResult(items = updates)
        newData.value = CacheUpdate.DeletionResult(deletedIds = removals)
    }

    /**
     * Inserts into the cache without an API call; the item exists only locally until the server
     * confirms it.  For optimistic updates and testing.
     */
    public fun localInsert(item: T): T {
        newData.value = CacheUpdate.MutationResult(items = setOf(item))
        return item
    }
}

