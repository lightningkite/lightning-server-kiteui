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
import com.lightningkite.reactive.extensions.use
import com.lightningkite.reactive.extensions.value
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.lensListenable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * - External listeners via [onUpdate]
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
 * Multiple concurrent requests for different items/queries are batched using [BatchAndQueue]:
 * - [multiget]: Batches individual item lookups into single multi-ID queries
 * - [queryInternal]: Batches multiple query requests (though each query still executes separately)
 *
 * ### WebSocket Integration
 * If [skipCache] implements [ClientModelRestUpdatesWebsocket], a [SharedCollectionUpdatesSocket]
 * is created to manage real-time updates. The socket:
 * - Tracks which conditions are actively being listened to
 * - Receives change notifications from the server
 * - Automatically reconnects and re-subscribes on disconnection
 * - Falls back to polling if socket fails to connect within 5 seconds
 *
 * ## Usage Examples
 *
 * ### Track a single item
 * ```kotlin
 * val userCache = ModelCache(api.users, User.serializer())
 * val user = userCache.item(
 *     id = userId,
 *     maximumAge = 30.seconds,  // Data valid for 30 seconds
 *     pullFrequency = 60.seconds // Poll every minute
 * )
 * // user.state is a Reactive<User?> that auto-updates
 * ```
 *
 * ### Track a query
 * ```kotlin
 * val activeUsers = userCache.list(
 *     query = Query(condition { it.active eq true }),
 *     maximumAge = 10.seconds,
 *     pullFrequency = 30.seconds
 * )
 * // activeUsers.state is a Reactive<List<User>> that auto-updates
 * ```
 *
 * ### Mutations propagate automatically
 * ```kotlin
 * userCache[userId].modify(Modification.assign(User::name, "New Name"))
 * // Both individual item caches and all query results are updated automatically
 * ```
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
 * @param onUpdate Optional callback for external update notifications (deprecated)
 * @param scope Coroutine scope for background operations (defaults to AppScope)
 * @param log Optional logger for debugging
 */
public class ModelCache<T : HasId<ID>, ID : Comparable<ID>>(
    public val skipCache: ClientModelRestEndpoints<T, ID>,
    public val serializer: KSerializer<T>,
//    val newest: (T?, T?) -> T? = { _, it -> it },
    public val onUpdate: ((CollectionUpdates<T, ID>) -> Unit)? = null,
    public val scope: CoroutineScope = AppScope,
    public val log: Log? = null
) : ModelCacheLike<T, ID> {
    /** Property accessor for the ID field of type T, extracted from serializer metadata */
    private val idProp = serializer._id()

    /**
     * The central update pipeline - ALL data changes flow through this signal.
     *
     * When this signal fires, it triggers updates to:
     * - [cache] (ListReconstructionCalculator) for query results
     * - [lastIndividualValues] for individual item tracking
     * - Any external listeners
     *
     * Initialized with [CacheUpdate.SocketOverload] to indicate empty state.
     */
    public val newData: Signal<CacheUpdate<T, ID>> = Signal<CacheUpdate<T, ID>>(CacheUpdate.SocketOverload())

    /**
     * Global interrupt mechanism for canceling pending delays in polling loops.
     * Child interrupts are created for each item/query to allow selective interruption.
     */
    public val interrupt: InterruptibleDelay = InterruptibleDelay()

    /**
     * WebSocket manager for real-time updates, if the API supports it.
     *
     * Created only if [skipCache] implements [ClientModelRestUpdatesWebsocket].
     * When available, provides:
     * - Real-time change notifications via [onChange]
     * - Condition-based subscriptions via [SharedCollectionUpdatesSocket.require]
     * - Automatic reconnection and resubscription
     *
     * The [onChange] callback feeds updates directly into the [newData] pipeline,
     * ensuring consistency with polling-based updates.
     */
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
     * Batching system for individual item lookups by ID.
     *
     * When multiple items are requested concurrently, this batches them into a single
     * query using `_id inside [id1, id2, ...]` to minimize network round trips.
     *
     * After fetching, emits a [CacheUpdate.MultiGetResult] into the [newData] pipeline
     * with both found items and missing IDs.
     *
     * @return For each requested ID, either the found item or null if missing
     */
    public val multiget: BatchAndQueue<ID, T?> = BatchAndQueue<ID, T?>(scope, log = log?.tag("multiget")) {
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
     * Batching system for query execution.
     *
     * Multiple query requests are collected and executed in parallel using [async]/[awaitAll].
     * Note: Unlike [multiget], queries are NOT merged - each query executes separately,
     * but concurrently. The batching only helps coordinate timing.
     *
     * After each query completes, emits a [CacheUpdate.QueryResult] into the [newData] pipeline.
     *
     * @return For each requested query, the list of matching items
     */
    public val queryInternal: BatchAndQueue<Query<T>, List<T>> = BatchAndQueue<Query<T>, List<T>>(scope, log = log?.tag("queryInternal")) {
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
     * Cache of individual item states, keyed by ID.
     *
     * Each entry is a [LateInitSignal] that holds [WithTimestamp] wrapping either:
     * - The item (T) if it exists
     * - null if confirmed missing or deleted
     *
     * Signals are created lazily on first access and persist for the lifetime of the cache.
     * They are updated by the [newData] listener below.
     */
    public val lastIndividualValues: HashMap<ID, LateInitSignal<WithTimestamp<T?>>> = HashMap<ID, LateInitSignal<WithTimestamp<T?>>>()

    init {
        /**
         * Listener that updates [lastIndividualValues] when [newData] fires.
         *
         * This keeps individual item caches in sync with all data changes:
         * - Deletions mark items as null
         * - MultiGet results update both found items and confirm missing items as null
         * - Socket overload unsets all signals (marking them as not-ready)
         * - All other updates (mutations, queries, socket changes) update found items
         */
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

    /** Helper to create a condition that matches a specific ID */
    public fun idIs(id: ID): Condition.OnField<T, ID> = Condition.OnField(idProp, Condition.Equal(id))

    /**
     * Extension property to check if a cached item value is "live" (actively monitored by WebSocket).
     *
     * An item is live if:
     * 1. It exists (not null)
     * 2. A WebSocket is active
     * 3. The socket is listening to a condition that matches this item
     * 4. The socket was activated BEFORE this data was cached ([at] > [activatedAt])
     *
     * When data is live, it can be considered fresh indefinitely because the socket will
     * notify us of any changes. This allows skipping polling.
     *
     * The `at > activatedAt` check is critical: if we cached data before the socket connected,
     * we might have missed changes that occurred during that gap.
     */
    public val WithTimestamp<T?>.isLive: Boolean
        get() = item != null && sockets?.listeningStatus?.value?.requirements
            ?.asSequence()
            ?.filter { it.condition.invoke(item) }
            ?.minOfOrNull { it.activatedAt ?: Instant.DISTANT_FUTURE }
            ?.let { at > it } == true

    /**
     * Checks if a query condition is "live" at a given timestamp.
     *
     * Similar to [isLive] but for query conditions rather than individual items.
     * A condition is live at a timestamp if the socket was listening to that exact condition
     * before the timestamp.
     *
     * @param timestamp The timestamp of the cached data
     * @return true if the socket has been watching this condition since before [timestamp]
     */
    public fun Condition<T>.isLiveAt(timestamp: Instant): Boolean = sockets?.listeningStatus?.value?.requirements
        ?.asSequence()
        ?.filter { it.condition == this }
        ?.minOfOrNull { it.activatedAt ?: Instant.DISTANT_FUTURE }
        ?.let { timestamp > it } == true

    /**
     * Calculates how long until this cached value expires based on recency requirements.
     *
     * @param recencyRequirement The maximum age before data is considered stale
     * @return How long until expiration (negative if already expired, full [recencyRequirement] if live)
     */
    public suspend fun WithTimestamp<T?>.couldExpireAt(recencyRequirement: Duration): Duration = when {
        isLive -> recencyRequirement
        else -> recencyRequirement - (Clock.default().now() - at)
    }

    /**
     * Creates a reactive handle for tracking a single item by ID.
     *
     * The returned [ModelCacheItemReadable] automatically:
     * - Fetches the item initially
     * - Polls for updates based on [pullFrequency] (or uses WebSocket if available)
     * - Exposes a reactive [state] that updates automatically
     * - Provides mutation methods that update the cache
     *
     * @param id The ID of the item to track
     * @param maximumAge How stale the cached data can be before it's considered invalid
     * @param pullFrequency How often to poll for fresh data (minimum 5 seconds enforced)
     * @return A reactive readable that manages fetching and caching for this item
     */
    override fun item(
        id: ID,
        maximumAge: Duration,
        pullFrequency: Duration,
    ): ModelCacheItemReadable<T> = ModelCacheItemReadableImpl(id, maximumAge, pullFrequency)

    /**
     * Implementation of [ModelCacheItemReadable] for tracking a single item with automatic updates.
     *
     * ## Lifecycle
     * Background operations start when the [state] property is accessed (via [processWhileRunning]).
     * This includes:
     * 1. Initial fetch (possibly waiting for WebSocket connection first)
     * 2. Polling loop that runs until the item is no longer observed
     *
     * ## Polling Strategy
     * - If [pullFrequency] < 30 seconds AND WebSocket is available: use socket, poll only initially
     * - Otherwise: poll regularly at [pullFrequency] intervals (minimum 5 seconds)
     * - Skip polling if data is "live" (socket is actively monitoring)
     * - Calculate next poll based on how long until data expires
     *
     * ## State Management
     * The [state] property returns:
     * - `ReactiveState(item)` if data is fresh (live or within [maximumAge])
     * - `ReactiveState.notReady` if data is stale or hasn't been fetched yet
     * - `ReactiveState.exception(e)` if an error occurred
     *
     * ## Gotchas
     * - The item is fetched via [multiget], which batches concurrent requests
     * - Socket connections wait up to 5 seconds before timing out and falling back to immediate fetch
     * - Mutations go directly to the API and then update the cache via [newData]
     * - [set] uses modification diffing to avoid unnecessary field updates
     */
    public inner class ModelCacheItemReadableImpl(
        public val id: ID,
        public val maximumAge: Duration,
        public val pullFrequency: Duration,
    ) : ModelCacheItemReadable<T> {
        /** Tagged logger for this specific item */
        public val log: Log? = this@ModelCache.log?.tag("$id")

        /** Child interrupt for canceling this item's polling loop without affecting others */
        public val interrupt: InterruptibleDelay = this@ModelCache.interrupt.child()

        /** The underlying signal holding the cached item value with timestamp */
        public val basis: LateInitSignal<WithTimestamp<T?>> = lastIndividualValues.getOrPut(id, ::LateInitSignal)

        /**
         * Background coroutine that manages fetching and polling.
         * Started lazily when [state] or [diff] is accessed for the first time.
         * Canceled automatically when all observers are removed.
         */
        public val processWhileRunning: ResourceUse = ResourceUse(scope) {
            onRemove { log?.log("No longer needed") }

            // Decide whether to use WebSocket for real-time updates
            // Socket is preferred when pullFrequency is low (< 30 seconds) since frequent polling is wasteful
            val socketToWaitFor = if (pullFrequency < 30.seconds && sockets != null) {
                log?.log("Using socket")
                // Register this item's condition with the socket manager
                val r = sockets.require(Condition.OnField(idProp, Condition.Equal(id)))
                use(r)
                // When socket connects, fetch immediately to ensure we have the latest data
                onRemove(r.satisfied.addListener {
                    if (r.satisfied.value) {
                        launch {
                            log?.log("Fetching after socket connected")
                            try {
                                multiget(id)
                            } catch(e: Exception) {
                                // TODO: Better error handling - should this propagate to state?
                                println("WARN: $e")
                            }
                        }
                    }
                })
                r
            } else null

            // Check if we already have acceptable cached data
            basis.state.getOrNull()?.let { lastKnown ->
                if (lastKnown.isLive || Clock.default().now() - lastKnown.at < maximumAge) {
                    log?.log("Found existing value ${lastKnown.at}")
                } else null
            } ?: run {
                // No acceptable cached data - need to fetch
                if (socketToWaitFor != null) {
                    log?.log("Waiting for socket to connect...")
                    // Give socket 5 seconds to connect before falling back to immediate fetch
                    // This avoids the race where we fetch, then socket connects and refetches unnecessarily
                    withTimeoutOrNull<Unit>(5.seconds) {
                        socketToWaitFor.wait()
                        log?.log("Socket connected.")
                    } ?: log?.log("Failed to connect to socket")
                }
                log?.log("Initial fetch")
                try {
                    multiget(id)
                } catch(e: Exception) {
                    // TODO: Better error handling - should this propagate to state?
                    println("WARN: $e")
                }
                log?.log("Initial fetch complete.")
            }

            // Polling loop - runs until this item is no longer observed
            // Minimum interval is 5 seconds to avoid overwhelming the server
            val pullFrequency = maxOf(5.seconds, pullFrequency)
            while (true) {
                // Calculate when we need to fetch next based on when data expires
                // Returns negative if data is already expired, full pullFrequency if data is live
                val next = basis.state.getOrNull()?.couldExpireAt(pullFrequency) ?: (-1).seconds
                if (next > 0.seconds) {
                    // Data is still fresh - wait before next poll
                    log?.log("No need to pull for $next")
                    interrupt.delay(next)
                } else {
                    // Data is stale or not present - fetch now
                    log?.log("Needs pull, starting")
                    try {
                        multiget(id)
                    } catch(e: Exception) {
                        // TODO: Better error handling - should this propagate to state?
                        println("WARN: $e")
                    }
                    // Always wait pullFrequency before next poll to avoid rapid retries
                    interrupt.delay(pullFrequency)
                }
            }
        }

        /**
         * The current state of the item.
         *
         * Returns:
         * - `ReactiveState(item)` if data is fresh (live or within [maximumAge])
         * - `ReactiveState.notReady` if data is stale, not yet fetched, or item doesn't exist
         * - `ReactiveState.exception(e)` if an error occurred during fetch
         *
         * Accessing this property triggers [processWhileRunning] if not already started.
         */
        override val state: ReactiveState<T?>
            get() {
                return basis.state.handle(
                    success = {
                        if (it.isLive || scope.now() - it.at < maximumAge) ReactiveState(it.item)
                        else ReactiveState.notReady
                    },
                    exception = { ReactiveState.exception(it) },
                    notReady = { ReactiveState.notReady }
                )
            }

        /**
         * A listenable view of just the item (without timestamp/state wrapper).
         * Ensures [processWhileRunning] stays active while there are listeners.
         */
        public val diff: Reactive<T?> = basis.lens { it.item }.uses(processWhileRunning)

        override fun addListener(listener: () -> Unit): () -> Unit = diff.addListener(listener)

        /** Reactive view of when the item was last updated */
        override val lastUpdatedAt: Reactive<Instant?> = basis.lens { it.at }

        /**
         * Computed property indicating whether this item is currently "live" (monitored by WebSocket).
         * Recomputes when socket listening status changes.
         */
        public val live: Reactive<Boolean> = remember(coroutineContext = scope.coroutineContext) {
            sockets?.listeningStatus?.let(::rerunOn)
            basis().isLive
        }

        /**
         * Forcefully invalidates the cached item and fetches fresh data immediately.
         * Also interrupts any pending delays in the polling loop.
         */
        override suspend fun invalidate() {
            basis.unset()
            multiget(id)
            interrupt.interrupt()
        }

        /**
         * Applies a modification to the item on the server, then updates the cache.
         * The update propagates through [newData] to all other caches (queries, etc.).
         */
        override suspend fun modify(modification: Modification<T>): T? {
            return skipCache.modify(id, modification).also {
                run {
                    newData.value = CacheUpdate.MutationResult(items = listOf(it))
                }
            }
        }

        /**
         * Deletes the item on the server, then marks it as deleted in the cache.
         * The deletion propagates through [newData] to all other caches.
         */
        override suspend fun delete() {
            return skipCache.delete(id)
                .also { run { newData.value = CacheUpdate.DeletionResult(setOf(id)) } }
        }

        /**
         * Sets the item to a new value, intelligently choosing insert vs. modify.
         *
         * - If [value] is null: deletes the item
         * - If item doesn't exist: inserts [value]
         * - If item exists: diffs the changes and sends only modified fields via [modification]
         *
         * The diff-based update minimizes network payload and reduces server load.
         */
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
        /**
         * Wire up the list reconstruction calculator to receive all updates from [newData].
         * This ensures query caches stay in sync with mutations, deletions, and socket changes.
         */
        newData.addListener { cache.update(newData.value) }
    }

    /**
     * Creates a reactive handle for tracking a query result.
     *
     * The returned [ModelCacheLimitReadable] automatically:
     * - Fetches the query results initially
     * - Polls for updates based on [pullFrequency] (or uses WebSocket if available)
     * - Exposes a reactive [state] that updates automatically
     * - Allows dynamic [limit] changes
     *
     * @param query The query to track (condition, sort, limit)
     * @param maximumAge How stale the cached data can be before it's considered invalid
     * @param pullFrequency How often to poll for fresh data (minimum 5 seconds enforced)
     * @return A reactive readable that manages fetching and caching for this query
     */
    override fun list(
        query: Query<T>,
        maximumAge: Duration,
        pullFrequency: Duration,
    ): ModelCacheLimitReadable<T> = ModelCacheLimitReadableImpl(query, maximumAge, pullFrequency)

    /**
     * Implementation of [ModelCacheLimitReadable] for tracking query results with automatic updates.
     *
     * Very similar to [ModelCacheItemReadableImpl] but for queries instead of individual items.
     * Key differences:
     * - Uses [cache] (ListReconstructionCalculator) instead of [lastIndividualValues]
     * - Supports dynamic [limit] changes via [currentQuery]
     * - Checks [requestedLimit] to determine if cached results are sufficient
     * - Socket requirement uses query's condition instead of ID equality
     *
     * See [ModelCacheItemReadableImpl] for detailed lifecycle and polling documentation.
     */
    public inner class ModelCacheLimitReadableImpl(
        public val query: Query<T>,
        public val maximumAge: Duration,
        public val pullFrequency: Duration,
    ) : ModelCacheLimitReadable<T> {
        /** Child interrupt for canceling this query's polling loop */
        public val interrupt: InterruptibleDelay = this@ModelCache.interrupt.child()

        /**
         * The active query being tracked. Can be modified dynamically via [limit] setter.
         * When changed, triggers a new fetch by interrupting the polling loop.
         */
        public var currentQuery: Query<T> = query

        /**
         * Background coroutine managing fetching and polling for this query.
         * Similar to [ModelCacheItemReadableImpl.processWhileRunning] but with query-specific logic.
         */
        public val processWhileRunning: ResourceUse = ResourceUse(scope) {
            val log = log?.tag("${currentQuery.condition} ${currentQuery.orderBy}")

            // Use WebSocket if pullFrequency is low (< 30 seconds)
            val socketToWaitFor = if (pullFrequency < 30.seconds && sockets != null) {
                log?.log("Using socket")
                val r = sockets.require(currentQuery.condition)
                use(r)
                onRemove(r.satisfied.addListener {
                    if (r.satisfied.value) {
                        launch {
                            log?.log("Fetching after socket connected")
                            try {
                                queryInternal(currentQuery)
                            } catch(e: Exception) {
                                println("WARN: $e")
                            }
                        }
                    }
                })
                r
            } else null

            // Automatically emit our best known value, if it matches the requirements.
            cache.cached(currentQuery)?.let { lastKnown ->
                if (currentQuery.condition.isLiveAt(lastKnown.at) || Clock.default().now() - lastKnown.at < maximumAge) {
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
                try {
                    queryInternal(cache.recommendQuery(currentQuery))
                } catch(e: Exception) {
                    println("WARN: $e")
                }
                log?.log("Initial fetch complete.")
            }

            // Polling loop - similar to item polling but with query-specific logic
            val pullFrequency = maxOf(5.seconds, pullFrequency)
            while (true) {
                val mostRecent = cache.cached(currentQuery)
                val next = when {
                    // No cached data - fetch immediately
                    mostRecent == null -> (-1).seconds
                    // Cached limit is smaller than requested - need to fetch more
                    // This happens when limit is increased dynamically
                    mostRecent.requestedLimit < currentQuery.limit -> (-1).seconds
                    // Data is live (socket is watching) - no need to poll
                    currentQuery.condition.isLiveAt(mostRecent.at) -> pullFrequency
                    // Calculate time until data expires
                    else -> pullFrequency - (Clock.default().now() - mostRecent.at)
                }
                // TODO: if we're up to date otherwise, can we do limit extension to request more items?
                // This would allow fetching additional items for a limited query without refetching all items.
                if (next > 0.seconds) {
                    log?.log("No need to pull for $next")
                    interrupt.delay(next)
                } else {
                    //TODO: Harden against exceptions - should errors propagate to state?
                    log?.log("Needs pull, starting because most recent is ${mostRecent?.at} / ${mostRecent?.requestedLimit}")
                    try {
                        queryInternal(cache.recommendQuery(currentQuery))
                    } catch(e: Exception) {
                        println("WARN: $e")
                    }
                    interrupt.delay(pullFrequency)
                }
            }
        }

        /**
         * The current state of the query result.
         *
         * Returns:
         * - `ReactiveState(list)` if data is fresh (live or within [maximumAge])
         * - `ReactiveState.notReady` if data is stale or not yet fetched
         *
         * Accessing this property triggers [processWhileRunning] if not already started.
         */
        override val state: ReactiveState<List<T>>
            get() {
                return cache.cached(currentQuery)?.let { lastKnown ->
                    if (currentQuery.condition.isLiveAt(lastKnown.at) || scope.now() - lastKnown.at < maximumAge) {
                        ReactiveState(lastKnown.item)
                    } else ReactiveState.notReady
                } ?: ReactiveState.notReady
            }

        /**
         * A listenable view of the query result list.
         * Fires when [cache] updates and data freshness changes.
         * Ensures [processWhileRunning] stays active while there are listeners.
         */
        public val diff: Reactive<List<T>?> = cache.updates(query).lensListenable {
            cache.cached(currentQuery)?.let { lastKnown ->
                if (currentQuery.condition.isLiveAt(lastKnown.at) || scope.now() - lastKnown.at < maximumAge) {
                    lastKnown.item
                } else null
            }
        }.uses(processWhileRunning)

        /** Like [diff] but includes timestamp information */
        public val diffWithTs: Reactive<WithTimestampAndLimit<List<T>>?> = cache.updates(query).lensListenable {
            cache.cached(currentQuery)?.let { lastKnown ->
                if (currentQuery.condition.isLiveAt(lastKnown.at) || scope.now() - lastKnown.at < maximumAge) {
                    lastKnown
                } else null
            }
        }.uses(processWhileRunning)

        override fun addListener(listener: () -> Unit): () -> Unit = diff.addListener(listener)

        /** Reactive view of when the query was last updated */
        override val lastUpdatedAt: Reactive<Instant?> = diffWithTs.lens { it?.at }

        /**
         * Computed property indicating whether this query is currently "live" (monitored by WebSocket).
         * Recomputes when socket listening status changes.
         */
        public val live: Reactive<Boolean> = remember(coroutineContext = scope.coroutineContext) {
            sockets?.listeningStatus?.let(::rerunOn)
            cache.cached(currentQuery)?.at?.let { time ->
                currentQuery.condition.isLiveAt(time)
            } == true
        }

        /**
         * Mutable limit for the query.
         * Changing this updates [currentQuery] and interrupts the polling loop to fetch more/fewer items.
         */
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


    // =============================================================================
    // Mutation operations - all propagate through newData to update all caches
    // =============================================================================

    /**
     * Inserts a new item via the API, then updates all caches.
     * @return The inserted item (may have server-assigned ID or defaults)
     */
    override suspend fun add(item: T): T {
        return skipCache.insert(item).also {
            newData.value = CacheUpdate.MutationResult(items = setOf(it))
        }
    }

    /**
     * Bulk inserts multiple items via the API, then updates all caches.
     * @return The list of inserted items (may have server-assigned IDs or defaults)
     */
    override suspend fun addAll(items: List<T>): List<T> {
        return skipCache.insertBulk(items).also {
            newData.value = CacheUpdate.MutationResult(items = it.toSet())
        }
    }

    /**
     * Upserts an item (insert if new, update if exists) via the API, then updates caches.
     * @return A reactive handle for the upserted item
     */
    override suspend fun upsert(item: T): ModelCacheItemReadable<T> {
        skipCache.upsert(item._id, item).let {
            newData.value = CacheUpdate.MutationResult(items = setOf(it))
        }
        return this[item._id]
    }

    /**
     * Performs a bulk modification (e.g., update all items matching a condition).
     *
     * **WARNING**: This clears ALL caches via [CacheUpdate.SocketOverload] because
     * we don't know which specific items were affected by the bulk operation.
     * This is intentionally aggressive to avoid serving stale data.
     *
     * @return Number of items modified
     */
    override suspend fun bulkModify(bulkUpdate: MassModification<T>): Int {
        return skipCache.bulkModify(bulkUpdate).also {
            newData.value = CacheUpdate.SocketOverload()
        }
    }

    // =============================================================================
    // Local/testing utilities - manipulate cache without API calls
    // =============================================================================

    /**
     * Flow for total cache invalidation events.
     * Emits Unit when [totallyInvalidate] is called.
     */
    public val totalInvalidation: MutableSharedFlow<Unit> =
        MutableSharedFlow<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)

    /**
     * Completely invalidates all caches and interrupts all polling loops.
     * Useful for forcing a full refresh or handling auth changes.
     */
    public fun totallyInvalidate() {
        totalInvalidation.tryEmit(Unit)
        newData.value = CacheUpdate.SocketOverload()  // by Claude - clear all caches
        interrupt.interrupt()
    }

    /**
     * Locally updates cached items without making API calls.
     *
     * Finds all cached items matching [matching], applies [modify] to each, and:
     * - If [modify] returns non-null: updates the cache with the new value
     * - If [modify] returns null: marks the item as deleted
     *
     * **Use with caution**: This bypasses the API, so local and server state will diverge
     * until the next poll or socket update. Primarily useful for optimistic updates or testing.
     *
     * @param matching Predicate to filter items
     * @param modify Transformation function (return null to mark as deleted)
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
     * Locally inserts an item into the cache without making an API call.
     *
     * **Use with caution**: The item only exists in the local cache until confirmed by the server.
     * Primarily useful for optimistic UI updates or testing.
     *
     * @param item The item to insert locally
     * @return The same item (unchanged)
     */
    public fun localInsert(item: T): T {
        newData.value = CacheUpdate.MutationResult(items = setOf(item))
        return item
    }
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Error Handling and Propagation
 *    Current: Exceptions during fetch are logged with println("WARN: $e")
 *    Problem: Errors don't propagate to the state, so UI has no way to show error messages
 *    Suggestion: Add error state to ReactiveState, expose fetch failures in state.exception
 *
 * 2. Batch Query Optimization
 *    Current: queryInternal executes queries in parallel but doesn't merge overlapping queries
 *    Problem: Query(condition=always) and Query(condition=always, limit=10) fetch separately
 *    Suggestion: Add query merging logic to detect when one query can satisfy another
 *
 * 3. Configurable Socket Threshold
 *    Current: Hardcoded 30-second threshold for socket vs polling
 *    Suggestion: Make this configurable per-cache or per-query
 *
 * 4. Limit Extension Strategy
 *    Current: TODO comment notes that limit extension isn't implemented
 *    Problem: When items are deleted from a limited query, no automatic refetch to fill the gap
 *    Suggestion: Add smart refetching when cached item count drops below limit
 *
 * 5. Cache Eviction Policy
 *    Current: lastIndividualValues grows unbounded
 *    Problem: Memory leaks in long-running apps with many accessed items
 *    Suggestion: Implement LRU eviction or weak references for unused items
 *
 * 6. Structured Logging
 *    Current: Mix of log?.log() and println()
 *    Suggestion: Consolidate on log everywhere, add structured log levels (debug, info, warn, error)
 *
 * 7. Partial Fetch Detection
 *    Current: No indication to consumers when cached data is incomplete (e.g., limited query missing items)
 *    Suggestion: Expose completeness metadata in state or as separate flag
 *
 * 8. Socket Reconnection Strategy
 *    Current: 5-second timeout for initial socket connection, no retry logic documented
 *    Suggestion: Expose socket connection state, add configurable retry policy
 *
 * 9. Testing API
 *    Current: localInsert/localSignalUpdate exist but aren't well-integrated
 *    Suggestion: Create a dedicated testing API with mock modes, time control, etc.
 *
 * 10. Deprecation Cleanup
 *     Current: onUpdate parameter is marked with comment but still present
 *     Suggestion: Add @Deprecated annotation if truly deprecated, or remove comment if still needed
 *
 * 11. Metrics and Observability
 *     Current: No visibility into cache performance (hit rate, fetch count, etc.)
 *     Suggestion: Add metrics API: cache hit/miss rate, active queries count, WebSocket status
 *
 * 12. Concurrent Access Safety
 *     Current: No apparent synchronization on shared state (lastIndividualValues, etc.)
 *     Problem: Potential races if accessed from multiple coroutine contexts
 *     Suggestion: Document thread-safety guarantees or add synchronization
 */

