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
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.lensListenable
import kotlinx.coroutines.*
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * A multi-layer caching system for model collections with real-time synchronization.
 *
 * ModelCache sits between your application and the REST API, providing:
 * - Caching of both individual items and query results in a single [CoverageStore]
 * - Automatic background polling with configurable staleness thresholds
 * - Optional WebSocket integration for real-time updates
 * - Request batching, so many items requested at once become a single multi-ID query
 *
 * ## What is cached
 *
 * Everything - items and lists alike - lives in one [store], which knows not only the values it
 * holds but which queries it holds *completely*.  Reads are answered from it whenever a completeness
 * claim backs them; otherwise this class fetches.  Looking an item up by ID is not a special case:
 * it is [CoverageStore.idQuery], a query for one row.
 *
 * ## Polling strategy
 *
 * Items and queries are fetched based on two parameters:
 * - **maximumAge**: how stale an answer may be before a reader stops accepting it
 * - **pullFrequency**: how often to poll when not relying on the socket
 *
 * When WebSockets are available and [pullFrequency] is under 30 seconds, the cache subscribes rather
 * than polling, and data covered by the subscription stays fresh indefinitely - the socket will say
 * if it changes.
 *
 * ## Important gotchas
 *
 * - **Socket activation timing**: data cached before the socket connected may have missed changes
 *   during the gap, so the socket's guarantee does not extend back over it.  See [socketCoveredSince].
 * - **Overload clearing**: an overloaded socket, and any bulk modification, clears everything.
 *   Intentionally aggressive - serving stale data is worse than refetching.
 * - **Minimum pull frequency**: polling never runs faster than every 5 seconds.
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
    public val log: Log? = null,
    /**
     * Reads a row's version, for models that carry one, so that conflicting accounts of the same row
     * are settled by which is genuinely later rather than by clocks.  See [CoverageStore].
     */
    versionOf: ((T) -> Long?)? = null,
) : ModelCacheLike<T, ID> {
    private val idProp = serializer._id()

    /** Everything known about the collection.  See [CoverageStore]. */
    public val store: CoverageStore<T, ID> = CoverageStore(serializer, versionOf)

    /**
     * Fetches the read mask, so the store can tell what the server actually filtered by.
     *
     * Until this lands the store assumes nothing about masks, which costs some reuse but is never
     * wrong; see [CoverageStore.useMasking].  A failure here is therefore not worth retrying or
     * surfacing - it leaves the cache exactly as correct as it was, only less clever.
     */
    init {
        scope.launch {
            try {
                store.useMasking(skipCache.permissions().readMask)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.log("Could not read permissions, so masked sorts stay conservative: $e")
            }
        }
    }

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
                onChange = { update ->
                    if (update.overload) {
                        store.invalidate()
                        interrupt.interrupt()
                    } else {
                        store.socketDelta(
                            changed = update.updates,
                            removed = update.remove,
                            at = scope.now(),
                            live = { condition, since -> socketCoveredSince(since) { it == condition } },
                        )
                        // A list that lost a row is now short of the limit its reader asked for.
                        if (update.remove.isNotEmpty()) interrupt.interrupt()
                    }
                }
            )
        }

    /**
     * Looks up items by ID, coalescing concurrent requests into a single `_id inside [...]` query.
     *
     * This batching is the reason [ModelCacheItemReadableImpl] does not simply run its own one-row
     * query: without it, a screen showing fifty referenced items would issue fifty requests.
     *
     * @return For each requested ID, either the found item or null if missing
     */
    private val multiget: BatchAndQueue<ID, T?> = BatchAndQueue<ID, T?>(scope, log = log?.tag("multiget")) { ids ->
        val at = scope.now()
        val found = skipCache.query(
            Query(
                condition = Condition.OnField(idProp, Condition.Inside(ids)),
                // Must cover the whole batch.  Anything the query doesn't return is treated as
                // confirmed-missing below, so a limit shorter than the batch would report existing
                // items as deleted.  Query's default limit is far smaller than a full batch.
                limit = ids.size,
            )
        )
        store.identified(found, ids.toSet() - found.mapTo(HashSet()) { it._id }, at)
        val map = found.associateBy { it._id }
        ids.map { map[it] }
    }

    /**
     * Runs queries.  Unlike [multiget] these are not merged - each executes separately, just
     * concurrently, so the batching only coordinates timing.
     */
    private val queryInternal: BatchAndQueue<Query<T>, List<T>> =
        BatchAndQueue<Query<T>, List<T>>(scope, log = log?.tag("queryInternal")) { queries ->
            coroutineScope {
                queries.map { query ->
                    async {
                        // Stamped before the request goes out, not after it returns, so a fetch that
                        // raced a change is never authoritative for longer than it was in flight.
                        val at = scope.now()
                        skipCache.query(query).also { store.queried(query, it, at) }
                    }
                }.awaitAll()
            }
        }

    /**
     * Whether the update socket has been covering [timestamp] for anything matching [matches].
     *
     * The `timestamp > activatedAt` comparison is the important part: data cached before the socket
     * connected may have missed changes that happened during that gap, so the socket's guarantee
     * does not extend back over it.  When it does, the data can be treated as fresh indefinitely,
     * because the socket will tell us about any change.
     *
     * Derived on every read rather than stored, because sockets close asynchronously and a
     * remembered "this is live" flag would be wrong the instant one does.
     */
    private fun socketCoveredSince(timestamp: Instant, matches: (Condition<T>) -> Boolean): Boolean =
        sockets?.listeningStatus?.value?.requirements
            ?.asSequence()
            ?.filter { matches(it.condition) }
            ?.minOfOrNull { it.activatedAt ?: Instant.DISTANT_FUTURE }
            ?.let { timestamp > it } == true

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

        /** The currently cached value, or null if nothing in [store] backs it. */
        protected abstract fun cached(): WithTimestamp<V>?

        /** Whether the socket is currently keeping [cached] up to date on its own. */
        protected abstract fun isLive(cached: WithTimestamp<V>): Boolean

        /** Whether more must be retrieved even though [cached] is fresh - e.g. a raised limit. */
        protected open fun needsMore(): Boolean = false

        /** Retrieves from the server; results reach [cached] by way of [store]. */
        protected abstract suspend fun retrieve()

        protected abstract val log: Log?

        /** Fires whenever [cached] may have changed. */
        protected val changes: Listenable get() = store.updates

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
        protected suspend fun fetch(): Unit = recordingOutcome { retrieve() }

        /** [fetch] for retrievals other than [retrieve], such as a pagination extension. */
        protected suspend fun <R> recordingOutcome(action: suspend () -> R): R {
            try {
                return action().also { fetchError.value = null }
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
         * apart - the latter is how a confirmed-missing item is reported.  It is also what keeps
         * [store]'s single change signal from waking every reader on every unrelated update.
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

    /**
     * Tracks a single item by ID.  See [SelfRefreshing] for the lifecycle.
     *
     * This is a one-row query against the same [store] as any list, so a list that already covered
     * the item answers this without a request, and vice versa.  It stays a separate class only
     * because retrieval batches through [multiget] and because a socket subscription on any
     * condition the item matches keeps it live, not just a subscription to its ID.
     */
    public inner class ModelCacheItemReadableImpl(
        public val id: ID,
        maximumAge: Duration,
        pullFrequency: Duration,
    ) : SelfRefreshing<T?>(maximumAge, pullFrequency), ModelCacheItemReadable<T> {
        override val log: Log? = this@ModelCache.log?.tag("$id")

        private val query: Query<T> = store.idQuery(id)

        override val socketCondition: Condition<T> get() = query.condition
        override fun cached(): WithTimestamp<T?>? =
            store.known(query)?.let { WithTimestamp(it.items.firstOrNull(), it.at) }

        override suspend fun retrieve() { multiget(id) }

        override fun isLive(cached: WithTimestamp<T?>): Boolean {
            val item = cached.item ?: return false
            return socketCoveredSince(cached.at) { it(item) }
        }

        /** Discards what is known about this ID and retrieves fresh data immediately. */
        override suspend fun invalidate() {
            store.invalidate(id)
            fetch()
            interruptPolling()
        }

        override suspend fun modify(modification: Modification<T>): T? =
            skipCache.modify(id, modification).also { store.mutated(listOf(it), scope.now()) }

        override suspend fun delete() {
            skipCache.delete(id)
            deletionRecorded(setOf(id))
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
            updated?.let { store.mutated(listOf(it), scope.now()) }
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
    ): ModelCacheLimitReadable<T> {
        // A skipped query says nothing about the rows before it, so "everything from the start of
        // the order through here" - the only claim this cache knows how to make - is unavailable.
        require(query.skip == 0) { "ModelCache cannot cache a query with skip; page with limit() instead." }
        require(query.limit > 0) { "ModelCache needs a positive limit; a limit of ${query.limit} asks the server for nothing." }
        return ModelCacheLimitReadableImpl(
            // Make the sort total before it ever reaches the server.  The cache already materializes
            // lists with `ensureTotal` applied, so without this the server and the client can disagree
            // about which tied rows fall inside the limit; it is also what makes a pagination cursor
            // (`orderBy.after`) well defined.  Note this changes the ORDER BY sent to the server.
            query.copy(orderBy = query.orderBy.ensureTotal(serializer)),
            maximumAge,
            pullFrequency,
        )
    }

    /** Tracks the results of a query.  See [SelfRefreshing] for the lifecycle. */
    public inner class ModelCacheLimitReadableImpl(
        public val query: Query<T>,
        maximumAge: Duration,
        pullFrequency: Duration,
    ) : SelfRefreshing<List<T>>(maximumAge, pullFrequency), ModelCacheLimitReadable<T> {
        override val log: Log? = this@ModelCache.log?.tag("${query.condition} ${query.orderBy}")

        /** [query] with the current [limit] applied. */
        private var currentQuery: Query<T> = query

        override val socketCondition: Condition<T> get() = query.condition
        override fun cached(): WithTimestamp<List<T>>? =
            store.known(currentQuery)?.let { WithTimestamp(it.items, it.at) }

        override fun isLive(cached: WithTimestamp<List<T>>): Boolean =
            socketCoveredSince(cached.at) { it == query.condition }

        /**
         * True when knowledge runs out before the limit does - because the limit was raised, or
         * because a row inside the covered range was deleted or moved out of it and the list is now
         * a row short of what was asked for.
         */
        override fun needsMore(): Boolean = store.known(currentQuery)?.partial == true

        override suspend fun retrieve() { queryInternal(currentQuery) }

        override var limit: Int
            get() = currentQuery.limit
            @Deprecated("Use limit(n) instead, which reports when the new items have arrived.")
            set(value) {
                require(value > 0) { "A limit of $value asks the server for nothing." }
                currentQuery = currentQuery.copy(limit = value)
                // Nobody is awaiting the extra items, so let the polling loop pick them up.
                interruptPolling()
            }

        /**
         * Grows or shrinks the list, retrieving only what is actually missing.
         *
         * When knowledge already reaches the new limit - because the list was shortened, or because
         * an earlier answer proved there is nothing more to find - this returns without a request.
         * Otherwise it pages forward from the edge of what is known, with
         * `condition AND orderBy.after(boundary)`, and appends.  Scrolling therefore costs one page
         * per page rather than growing with the length of the list.
         *
         * The limit moves immediately, so readers keep seeing the rows already retrieved while the
         * next page loads rather than blanking.
         */
        override suspend fun limit(count: Int) {
            require(count > 0) { "A limit of $count asks the server for nothing." }
            if (count == currentQuery.limit) return
            currentQuery = currentQuery.copy(limit = count)

            // Nothing is known yet, so there is no edge to page from - read the list instead.
            val known = store.known(currentQuery) ?: return fetch()
            if (!known.partial) return
            // A partial answer always has a boundary; that is what makes it partial.
            val boundary = store.boundary(currentQuery)!!

            recordingOutcome {
                val at = scope.now()
                val pageLimit = count - known.items.size
                val page = skipCache.query(
                    Query(
                        condition = Condition.And(listOf(currentQuery.condition, currentQuery.orderBy.after(boundary))),
                        orderBy = currentQuery.orderBy,
                        limit = pageLimit,
                    )
                )
                store.paged(currentQuery, boundary, page, pageLimit, at)
            }
        }

        override fun equals(other: Any?): Boolean = other is ModelCache<T, ID>.ModelCacheLimitReadableImpl
                && query == other.query
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = query.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }


    // =============================================================================
    // Mutation operations - all recorded in the store as changes we watched happen
    // =============================================================================

    /**
     * Records a deletion and wakes the polling loops.
     *
     * A limited list that loses a row from inside its covered range is now a row short of what its
     * reader asked for, and waking the loops lets it fetch the row that moved up to fill the gap.
     * Loops with nothing to do re-check and go straight back to sleep.
     */
    private fun deletionRecorded(ids: Set<ID>) {
        store.deleted(ids, scope.now())
        interrupt.interrupt()
    }

    override suspend fun add(item: T): T =
        skipCache.insert(item).also { store.mutated(listOf(it), scope.now()) }

    override suspend fun addAll(items: List<T>): List<T> =
        skipCache.insertBulk(items).also { store.mutated(it, scope.now()) }

    override suspend fun upsert(item: T): ModelCacheItemReadable<T> {
        skipCache.upsert(item._id, item).let { store.mutated(listOf(it), scope.now()) }
        return this[item._id]
    }

    /**
     * Performs a bulk modification (e.g., update all items matching a condition).
     *
     * Clears the whole cache, because the server does not tell us which items the modification
     * actually touched.  Intentionally aggressive - serving stale data is worse than refetching.
     * Polling loops are interrupted too, or observers would sit in a loading state until their next
     * poll.
     *
     * @return Number of items modified
     */
    override suspend fun bulkModify(bulkUpdate: MassModification<T>): Int =
        skipCache.bulkModify(bulkUpdate).also {
            store.invalidate()
            interrupt.interrupt()
        }

    // =============================================================================
    // Local/testing utilities - manipulate cache without API calls
    // =============================================================================

    /**
     * Completely invalidates the cache and interrupts all polling loops.
     * Useful for forcing a full refresh or handling auth changes.
     */
    public suspend fun totallyInvalidate() {
        store.invalidate()
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
        store.cachedItems().filter(matching).forEach {
            modify(it)?.let { updated -> updates.add(updated) } ?: removals.add(it._id)
        }
        if (updates.isNotEmpty()) store.mutated(updates, scope.now())
        if (removals.isNotEmpty()) deletionRecorded(removals)
    }

    /**
     * Inserts into the cache without an API call; the item exists only locally until the server
     * confirms it.  For optimistic updates and testing.
     */
    public fun localInsert(item: T): T {
        store.mutated(listOf(item), scope.now())
        return item
    }
}
