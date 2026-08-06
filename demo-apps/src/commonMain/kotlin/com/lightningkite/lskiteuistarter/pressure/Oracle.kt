package com.lightningkite.lskiteuistarter.pressure

import com.lightningkite.lightningserver.db.ensureTotal
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.lskiteuistarter.Racer
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Judges the cache against the server, so "looks right" stops being the standard.
 *
 * For every list the screen is showing, this re-runs the same query straight against the server and
 * compares.  Divergence on its own is not a bug - during a race the two reads are simply taken at
 * different moments - so a finding only counts once it has *survived* [tolerance].  A row that is
 * missing for one round and back the next is churn; a row that stays missing is the failure this
 * whole exercise exists to catch.
 *
 * The truth channel deliberately bypasses [Instruments], so grading never inflates the request
 * counts a scenario is asserting on.
 */
class Oracle(private val truth: ClientModelRestEndpoints<Racer, Uuid>) {

    enum class Verdict {
        Ok,
        Settling,
        Failed,

        /**
         * Both sides were empty, so nothing was actually compared.
         *
         * Kept distinct from [Ok] deliberately: a run where the filter matched no rows agrees with
         * the server perfectly and proves nothing, and that must never be reported as a pass.
         */
        Vacuous,
    }

    /**
     * How long an observation may go without being re-pushed before it is treated as belonging to a
     * screen that has gone away.  Comfortably longer than [tick]'s interval, so it only ever fires
     * for a view that really has stopped reporting.
     */
    private val staleObservation = 30.seconds

    data class Finding(val key: String, val describe: String, val since: Instant)

    data class Report(
        val probe: String,
        val checkedAt: Instant,
        val cachedCount: Int,
        val truthCount: Int,
        val findings: List<Finding>,
        val verdict: Verdict,
        /** How long the longest-standing current finding has been standing. */
        val oldestFinding: Duration,
    )

    /**
     * How long a divergence may persist before it is called a failure.  Comfortably above the
     * cache's own five-second floor on polling, so an ordinary refresh cycle never trips it.
     */
    val tolerance: Signal<Duration> = Signal(6.seconds)

    val reports: Signal<Map<String, Report>> = Signal(emptyMap())

    /** Every sustained divergence seen this session, kept even after it resolves. */
    val failures: Signal<List<String>> = Signal(emptyList())

    /** Whatever went wrong reaching the server, so a dead truth channel doesn't read as a pass. */
    val lastError: Signal<String?> = Signal(null)

    val running: Signal<Boolean> = Signal(false)

    /** How many screens are currently handing the oracle something to grade. */
    val probes: Signal<Int> = Signal(0)

    /**
     * Bumped once per grading round for observers to depend on.
     *
     * A view pushes its snapshot from a reactive block, which only re-runs when something it reads
     * changes - so a board that is not moving would stop reporting and look like a screen that had
     * been closed.  Reading this makes every observer re-push on the oracle's own schedule as well.
     */
    val tick: Signal<Int> = Signal(0)

    private class Observation(
        val query: Query<Racer>,
        val items: List<Racer>,
        val limit: Int,
        val at: Instant,
    )

    private val observed = mutableMapOf<String, Observation>()
    private val firstSeen = mutableMapOf<String, MutableMap<String, Instant>>()
    private val alreadyReported = mutableSetOf<String>()

    /**
     * Tells the oracle what a screen is currently showing.
     *
     * Taking the list from the view rather than reading the cache directly is deliberate: it grades
     * what the user is actually looking at, and it cannot provoke a fetch that would change the very
     * request counts being measured.
     */
    fun observe(probe: String, query: Query<Racer>, items: List<Racer>, limit: Int) {
        observed[probe] = Observation(query, items, limit, Clock.System.now())
        probes.value = observed.size
    }

    fun forget(probe: String) {
        observed.remove(probe)
        probes.value = observed.size
        firstSeen.remove(probe)
        reports.value = reports.value - probe
    }

    fun reset() {
        firstSeen.clear()
        alreadyReported.clear()
        failures.value = emptyList()
        reports.value = emptyMap()
    }

    /**
     * Starts grading on a timer.  Idempotent, so returning to the console does not stack up loops.
     */
    fun start(scope: CoroutineScope, interval: Duration = 2.seconds) {
        if (running.value) return
        running.value = true
        scope.launch {
            while (running.value) {
                tick.value++
                try {
                    checkAll()
                    lastError.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError.value = e.message ?: e.toString()
                }
                delay(interval)
            }
        }
    }

    fun stop() {
        running.value = false
    }

    suspend fun checkAll() {
        val now = Clock.System.now()
        for ((name, observation) in observed.toList()) {
            // A screen that has gone away stops refreshing its observation.  Grading a snapshot that
            // nothing is showing any more would report failures against a view that no longer exists.
            if (now - observation.at > staleObservation) {
                forget(name)
                continue
            }
            check(name, observation)
        }
    }

    private suspend fun check(probe: String, observation: Observation) {
        // Match the cache's own reading of the query: it makes the sort total before sending it, and
        // it serves however many rows the view has paged to rather than the query's original limit.
        val asked = observation.query.copy(
            orderBy = observation.query.orderBy.ensureTotal(Racer.serializer()),
            limit = observation.limit,
        )
        val truthItems = truth.query(asked)
        val now = Clock.System.now()

        val cachedById = observation.items.associateBy { it._id }
        val truthById = truthItems.associateBy { it._id }

        val current = mutableListOf<Pair<String, String>>()
        for ((id, row) in truthById) {
            if (id !in cachedById) current += "missing:$id" to "bib ${row.bib} (${row.name}) is on the server but not in the list"
        }
        for ((id, row) in cachedById) {
            if (id !in truthById) current += "extra:$id" to "bib ${row.bib} (${row.name}) is in the list but not on the server"
        }
        for ((id, cached) in cachedById) {
            val actual = truthById[id] ?: continue
            if (cached.version != actual.version) {
                current += "stale:$id" to
                        "bib ${cached.bib} is at version ${cached.version}, server says ${actual.version}"
            }
        }
        // Order is only meaningful over the rows both sides agree exist; a missing row is already
        // reported as missing and would otherwise be counted twice.
        val shared = truthItems.map { it._id }.filter { it in cachedById }
        val cachedShared = observation.items.map { it._id }.filter { it in truthById }
        if (shared != cachedShared) {
            current += "order" to "the same racers are listed in a different order than the server gives them"
        }

        val seen = firstSeen.getOrPut(probe) { mutableMapOf() }
        val keys = current.map { it.first }.toSet()
        seen.keys.retainAll(keys)
        for (key in keys) seen.getOrPut(key) { now }

        val tolerated = tolerance.value
        val findings = current.map { (key, describe) -> Finding(key, describe, seen[key] ?: now) }
        val sustained = findings.filter { now - it.since > tolerated }

        for (finding in sustained) {
            val id = "$probe/${finding.key}"
            if (alreadyReported.add(id)) {
                failures.value = failures.value +
                        "[$probe] ${finding.describe} - unresolved after ${(now - finding.since).inWholeSeconds}s"
            }
        }

        reports.value = reports.value + (probe to Report(
            probe = probe,
            checkedAt = now,
            cachedCount = observation.items.size,
            truthCount = truthItems.size,
            findings = findings.sortedBy { it.since },
            verdict = when {
                sustained.isNotEmpty() -> Verdict.Failed
                findings.isNotEmpty() -> Verdict.Settling
                observation.items.isEmpty() && truthItems.isEmpty() -> Verdict.Vacuous
                else -> Verdict.Ok
            },
            oldestFinding = findings.maxOfOrNull { now - it.since } ?: Duration.ZERO,
        ))
    }
}
