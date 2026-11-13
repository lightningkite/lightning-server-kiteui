package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.lightningserver.networking.toTypedWebSocket
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lightningserver.typed.ClientModelRestUpdatesWebSocket
import com.lightningkite.services.database.*
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

public class ModelCache<T : HasId<ID>, ID : Comparable<ID>>(
    public val uncached: ClientModelRestEndpoints<T, ID>,
    override val serializer: KSerializer<T>,
    websocket: ClientModelRestUpdatesWebSocket<T, ID>? = @Suppress("UNCHECKED_CAST") (uncached as? ClientModelRestUpdatesWebSocket<T, ID>),
    override val defaultMaxAge: Duration = Duration.INFINITE,
    override val defaultPullFrequency: Duration = 5.minutes,
    private val scope: CoroutineScope = AppScope,
    private val log: Log? = null,
    versionOf: ((T) -> Long?)? = null,
) : ModelStore<T, ID> {
    internal val coverage: CoverageIndex<T, ID> = CoverageIndex(serializer, versionOf)
    private val interrupt: InterruptibleDelay = InterruptibleDelay()

    private val sockets: SharedCollectionUpdatesSocket<T, ID>? = websocket?.let {
        SharedCollectionUpdatesSocket(
            scope = scope,
            socket = it.updates().toTypedWebSocket(),
            log = log?.tag("Sockets"),
            onChange = { update ->
                if (update.overload) {
                    invalidateAll()
                } else {
                    coverage.socketDelta(
                        changed = update.updates,
                        removed = update.remove,
                        at = scope.now(),
                        live = { condition, since -> queryIsLive(condition, since) },
                    )
                    // Claude: A list that lost a row is now short of the limit its reader asked for.
                    if (update.remove.isNotEmpty()) interrupt.interrupt()
                }
            }
        )
    }

    private val freshnessChanges: Listenable = sockets?.let { coverage.updates + it.listeningStatus } ?: coverage.updates

    init {
        scope.launch {
            try {
                coverage.useMasking(uncached.permissions().readMask)
                // Claude: A non-empty mask can empty out what readers hold mid-poll, so wake them.
                interrupt.interrupt()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.log("Could not read permissions, so masked sorts stay conservative: $e")
            }
        }
    }

    @Deprecated("Renamed to uncached", ReplaceWith("uncached"))
    public val skipCache: ClientModelRestEndpoints<T, ID> get() = uncached

    // =============================================================================
    // Reads
    // =============================================================================

    override fun detail(id: ID, maximumAge: Duration, pullFrequency: Duration): Reactive<T?> =
        ItemReader(id, maximumAge, pullFrequency)

    override fun query(query: Query<T>, maximumAge: Duration, pullFrequency: Duration): LimitReactiveList<T> {
        // A skipped query says nothing about the rows before it, so "everything from the start of
        // the order through here" - the only claim this cache knows how to make - is unavailable.
        require(query.skip == 0) { "ModelCache cannot cache a query with skip; page with limit() instead." }
        require(query.limit > 0) { "ModelCache needs a positive limit; a limit of ${query.limit} asks the server for nothing." }
        return ListReader(totallyOrdered(query), maximumAge, pullFrequency)
    }

    override fun lastUpdate(id: ID): Reactive<LastUpdate?> {
        val query = coverage.idQuery(id)
        return freshnessChanges.lensListenable {
            coverage.known(query)?.let { LastUpdate(it.at, live = itemIsLive(it.items.firstOrNull(), it.at)) }
        }
    }

    override fun lastUpdate(query: Query<T>): Reactive<LastUpdate?> {
        val query = totallyOrdered(query)
        return freshnessChanges.lensListenable {
            coverage.known(query)?.let { LastUpdate(it.at, live = queryIsLive(query.condition, it.at)) }
        }
    }

    // =============================================================================
    // Writes - all recorded in the coverage index as changes we watched happen
    // =============================================================================

    override suspend fun insert(item: T): T =
        uncached.insert(item).also { coverage.mutated(listOf(it), scope.now()) }

    override suspend fun insertBulk(items: List<T>): List<T> =
        uncached.insertBulk(items).also { coverage.mutated(it, scope.now()) }

    override suspend fun upsert(id: ID, item: T): T =
        uncached.upsert(id, item).also { coverage.mutated(listOf(it), scope.now()) }

    override suspend fun replace(id: ID, item: T): T =
        uncached.replace(id, item).also { coverage.mutated(listOf(it), scope.now()) }

    override suspend fun modify(id: ID, modification: Modification<T>): T =
        uncached.modify(id, modification).also { coverage.mutated(listOf(it), scope.now()) }

    override suspend fun delete(id: ID) {
        uncached.delete(id)
        deletionRecorded(setOf(id))
    }

    override suspend fun bulkModify(bulkUpdate: MassModification<T>): Int {
        // Claude: Read before the change; afterwards the modified rows may no longer match.
        val held = coverage.cachedItems().filter { bulkUpdate.condition(it) }.mapTo(HashSet()) { it._id }
        val count = uncached.bulkModify(bulkUpdate)
        coverage.bulkModified(bulkUpdate.modification)
        reread(held)
        return count
    }

    override suspend fun bulkModify(ids: Set<ID>, modification: Modification<T>): Int {
        val count = uncached.bulkModify(MassModification(Condition.OnField(idProp, Condition.Inside(ids)), modification))
        reread(ids)
        return count
    }

    override suspend fun bulkDelete(condition: Condition<T>): Int {
        val count = uncached.bulkDelete(condition)
        localBulkDelete(condition)
        return count
    }

    // Claude: Fetches rather than only forgetting, because a list that covers this ID would otherwise keep answering for it.
    override suspend fun invalidate(id: ID) {
        coverage.invalidate(id)
        multiget(id)
        interrupt.interrupt()
    }

    override fun invalidateAll() {
        coverage.invalidate()
        interrupt.interrupt()
    }

    // Claude: Waking the readers lets a list that lost a row fetch the one that moved up to fill the gap.
    private suspend fun reread(ids: Set<ID>) {
        if (ids.isNotEmpty()) fetchByIds(ids)
        interrupt.interrupt()
    }

    private fun deletionRecorded(ids: Set<ID>) {
        coverage.deleted(ids, scope.now())
        interrupt.interrupt()
    }

    // =============================================================================
    // Local updates - change the cache without calling the server
    // =============================================================================

    override fun localUpsert(item: T) {
        coverage.mutated(listOf(item), scope.now())
    }

    override fun localDelete(id: ID) {
        deletionRecorded(setOf(id))
    }

    override fun localBulkModify(bulkUpdate: MassModification<T>) {
        val updated = coverage.cachedItems().filter { bulkUpdate.condition(it) }.map { bulkUpdate.modification(it) }
        coverage.bulkModified(bulkUpdate.modification)
        coverage.mutated(updated, scope.now())
        interrupt.interrupt()
    }

    override fun localBulkDelete(condition: Condition<T>) {
        deletionRecorded(coverage.cachedItems().filter { condition(it) }.mapTo(HashSet()) { it._id })
    }

    @Deprecated("Use localBulkModify or localBulkDelete instead")
    public fun localSignalUpdate(matching: (T) -> Boolean, modify: (T) -> T?) {
        val updates = HashSet<T>()
        val removals = HashSet<ID>()
        coverage.cachedItems().filter(matching).forEach {
            modify(it)?.let { updated -> updates.add(updated) } ?: removals.add(it._id)
        }
        if (updates.isNotEmpty()) coverage.mutated(updates, scope.now())
        if (removals.isNotEmpty()) deletionRecorded(removals)
    }

    // =============================================================================
    // Fetching
    // =============================================================================

    private val idProp = serializer._id()

    private val multiget: BatchAndQueue<ID, T?> = BatchAndQueue<ID, T?>(scope, log = log?.tag("multiget")) { ids ->
        val map = fetchByIds(ids.toSet()).associateBy { it._id }
        ids.map { map[it] }
    }

    private suspend fun fetchByIds(ids: Set<ID>): List<T> {
        val at = scope.now()
        val found = uncached.query(
            Query(
                condition = Condition.OnField(idProp, Condition.Inside(ids)),
                // Must cover the whole batch.  Anything the query doesn't return is treated as
                // confirmed-missing below, so a limit shorter than the batch would report existing
                // items as deleted.  Query's default limit is far smaller than a full batch.
                limit = ids.size,
            )
        )
        coverage.identified(found, ids - found.mapTo(HashSet()) { it._id }, at)
        return found
    }

    private val queryInternal: BatchAndQueue<Query<T>, List<T>> =
        BatchAndQueue<Query<T>, List<T>>(scope, log = log?.tag("queryInternal")) { queries ->
            coroutineScope {
                queries.map { query ->
                    async {
                        // Stamped before the request goes out, not after it returns, so a fetch that
                        // raced a change is never authoritative for longer than it was in flight.
                        val at = scope.now()
                        uncached.query(query).also { coverage.queried(query, it, at) }
                    }
                }.awaitAll()
            }
        }

    // Make the sort total before it ever reaches the server.  The cache already materializes
    // lists with `ensureTotal` applied, so without this the server and the client can disagree
    // about which tied rows fall inside the limit; it is also what makes a pagination cursor
    // (`orderBy.after`) well defined.  Note this changes the ORDER BY sent to the server.
    private fun totallyOrdered(query: Query<T>): Query<T> = query.copy(orderBy = query.orderBy.ensureTotal(serializer))

    // =============================================================================
    // Liveness
    // =============================================================================

    private fun socketCoveredSince(timestamp: Instant, matches: (Condition<T>) -> Boolean): Boolean =
        sockets?.listeningStatus?.value?.requirements
            ?.asSequence()
            ?.filter { matches(it.condition) }
            ?.minOfOrNull { it.coveredSince ?: Instant.DISTANT_FUTURE }
            ?.let { timestamp > it } == true

    private fun itemIsLive(item: T?, at: Instant): Boolean =
        item != null && socketCoveredSince(at) { it(item) }

    private fun queryIsLive(condition: Condition<T>, at: Instant): Boolean =
        socketCoveredSince(at) { it == condition }

    // =============================================================================
    // Readers
    // =============================================================================

    private abstract inner class SelfRefreshing<V>(
        protected val maximumAge: Duration,
        protected val pullFrequency: Duration,
    ) : Reactive<V> {
        protected abstract val socketCondition: Condition<T>

        protected abstract fun cached(): WithTimestamp<V>?

        protected abstract fun isLive(cached: WithTimestamp<V>): Boolean

        protected open fun needsMore(): Boolean = false

        protected abstract suspend fun retrieve()

        protected abstract val log: Log?

        protected val changes: Listenable get() = coverage.updates

        private val interrupt: InterruptibleDelay = this@ModelCache.interrupt.child()

        private val fetchError: Signal<Exception?> = Signal(null)

        private var enteredAt: Instant = Instant.DISTANT_PAST

        private fun WithTimestamp<V>.acceptableOnArrival(): Boolean =
            isLive(this) || enteredAt - at < maximumAge

        private fun WithTimestamp<V>.freshWithin(window: Duration): Boolean =
            isLive(this) || scope.now() - at < window

        protected suspend fun fetch(): Unit = recordingOutcome { retrieve() }

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

        private suspend fun fetchInBackground() {
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.log("Fetch failed: $e")
            }
        }

        private val refreshing: ResourceUse = ResourceUse(scope) {
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

            // Never go back to the server more often than this, no matter what was asked for.
            val floor = 5.seconds

            // How often this screen refreshes itself, or null when the caller asked for no polling.
            //
            // [maximumAge] deliberately plays no part here.  It is an entry condition, answered once
            // by [acceptable] above: arriving somewhere new is when it matters whether what we
            // inherited is fresh enough to show, because a screen that opens on stale data betrays
            // what people expect a page transition to do.  Staying on that screen is a different
            // question, and letting a tolerance for slightly stale data double as a polling rate is
            // how "I'll accept data ten seconds old" turns into hitting the server every ten seconds.
            // Zero asks for live data, which without a covering socket means polling at the floor.
            val cadence: Duration? = pullFrequency.takeIf { it.isFinite() }?.coerceAtLeast(floor)

            while (true) {
                val held = cached()
                val wait = when {
                    // Nothing held, or knowledge that runs out before the limit does.
                    held == null || needsMore() -> Duration.ZERO
                    // A socket is promising to tell us about changes, so there is nothing to poll for.
                    isLive(held) -> null
                    // Measured from when the row was last known rather than from when this reader
                    // arrived, so opening a screen beside data fetched a moment ago waits out the
                    // remainder of the interval instead of starting a fresh one.
                    else -> cadence?.minus(scope.now() - held.at)
                }
                when {
                    wait == null -> {
                        // Nothing to refresh for.  Wake anyway on the same cadence: a socket going
                        // down does not interrupt us, and noticing it late is how a reader ends up
                        // trusting a promise nobody is keeping any more.  Waking is not fetching.
                        log?.log("Nothing to refresh for")
                        interrupt.delay(floor)
                    }

                    wait > Duration.ZERO -> {
                        log?.log("No need to pull for $wait")
                        interrupt.delay(wait)
                    }

                    else -> {
                        log?.log("Needs pull, starting")
                        fetchInBackground()
                        // Wait a full interval before retrying, so failures don't spin.
                        interrupt.delay(cadence ?: floor)
                    }
                }
            }
        }

        private val processWhileRunning: ResourceUse = object : ResourceUse {
            override fun beginUse(): () -> Unit {
                enteredAt = scope.now()
                return refreshing.beginUse()
            }
        }

        override val state: ReactiveState<V>
            get() {
                val held = cached()
                if (held != null && held.acceptableOnArrival()) {
                    // A refresh that is failing only becomes the answer once what we hold has aged
                    // past what the caller was willing to open on.  Before that the data is the
                    // better answer; after it, saying nothing about a server we cannot reach would
                    // leave someone reading minutes-old rows with no sign anything is wrong.
                    fetchError.value
                        ?.takeIf { !held.freshWithin(maximumAge) }
                        ?.let { return ReactiveState.exception(it) }
                    return ReactiveState(held.item)
                }
                return fetchError.value?.let { ReactiveState.exception(it) } ?: ReactiveState.notReady
            }

        private val observed: Reactive<V> by lazy {
            object : Reactive<V> {
                override val state: ReactiveState<V> get() = this@SelfRefreshing.state
                override fun addListener(listener: () -> Unit): Release = changes.addListener(listener)
            }.lens { it }.uses(processWhileRunning)
        }

        // [state] also changes when a retrieve starts failing or recovers, which [observed] can't
        // see, so both have to be listened to.
        override fun addListener(listener: () -> Unit): Release = (observed + fetchError).addListener(listener)

        protected fun interruptPolling(): Unit = interrupt.interrupt()
    }

    private inner class ItemReader(
        val id: ID,
        maximumAge: Duration,
        pullFrequency: Duration,
    ) : SelfRefreshing<T?>(maximumAge, pullFrequency) {
        override val log: Log? = this@ModelCache.log?.tag("$id")

        private val query: Query<T> = coverage.idQuery(id)

        override val socketCondition: Condition<T> get() = query.condition
        override fun cached(): WithTimestamp<T?>? =
            coverage.known(query)?.let { WithTimestamp(it.items.firstOrNull(), it.at) }

        override suspend fun retrieve() {
            multiget(id)
        }

        override fun isLive(cached: WithTimestamp<T?>): Boolean = itemIsLive(cached.item, cached.at)

        override fun equals(other: Any?): Boolean = other is ModelCache<*, *>.ItemReader
                && id == other.id
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = id.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }

    private inner class ListReader(
        val query: Query<T>,
        maximumAge: Duration,
        pullFrequency: Duration,
    ) : SelfRefreshing<List<T>>(maximumAge, pullFrequency), LimitReactiveList<T> {
        override val log: Log? = this@ModelCache.log?.tag("${query.condition} ${query.orderBy}")

        private var currentQuery: Query<T> = query

        override val socketCondition: Condition<T> get() = query.condition
        override fun cached(): WithTimestamp<List<T>>? =
            coverage.known(currentQuery)?.let { WithTimestamp(it.items, it.at) }

        override fun isLive(cached: WithTimestamp<List<T>>): Boolean = queryIsLive(query.condition, cached.at)

        override fun needsMore(): Boolean = coverage.known(currentQuery)?.partial == true

        override suspend fun retrieve() {
            queryInternal(currentQuery)
        }

        override var limit: Int
            get() = currentQuery.limit
            @Deprecated("Use limit(n) instead, which reports when the new items have arrived.")
            set(value) {
                require(value > 0) { "A limit of $value asks the server for nothing." }
                currentQuery = currentQuery.copy(limit = value)
                // Nobody is awaiting the extra items, so let the polling loop pick them up.
                interruptPolling()
            }

        override suspend fun limit(count: Int) {
            require(count > 0) { "A limit of $count asks the server for nothing." }
            if (count == currentQuery.limit) return
            currentQuery = currentQuery.copy(limit = count)

            // Nothing is known yet, so there is no edge to page from - read the list instead.
            val known = coverage.known(currentQuery) ?: return fetch()
            if (!known.partial) return
            // A partial answer always has a boundary; that is what makes it partial.
            val boundary = coverage.boundary(currentQuery)!!

            recordingOutcome {
                val at = scope.now()
                val pageLimit = count - known.items.size
                val page = uncached.query(
                    Query(
                        condition = Condition.And(listOf(currentQuery.condition, currentQuery.orderBy.after(boundary))),
                        orderBy = currentQuery.orderBy,
                        limit = pageLimit,
                    )
                )
                coverage.paged(currentQuery, boundary, page, pageLimit, at)
            }
        }

        override fun equals(other: Any?): Boolean = other is ModelCache<*, *>.ListReader
                && query == other.query
                && maximumAge == other.maximumAge
                && pullFrequency == other.pullFrequency

        override fun hashCode(): Int = query.hashCode() + maximumAge.hashCode() + pullFrequency.hashCode()
    }
}
