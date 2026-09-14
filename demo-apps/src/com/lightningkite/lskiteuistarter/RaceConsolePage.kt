package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.toBlob
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.lightningserver.db.ModelCacheLimitReadable
import com.lightningkite.lightningserver.db.flatten
import com.lightningkite.lskiteuistarter.pressure.Oracle
import com.lightningkite.lskiteuistarter.sdk.UserSession
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database.andNotNull
import com.lightningkite.services.database.condition
import com.lightningkite.services.database.eq
import com.lightningkite.services.database.modification
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val PAGE_SIZE = 25

/**
 * The leaderboard's order: furthest along the course first, then fastest to get there.
 *
 * A two-part sort over two fields that both change as the race runs, which is the hard case - the
 * cache has to keep a pagination boundary meaningful while rows move across it in both directions.
 */
private val leaderboardOrder = listOf(
    SortPart(Racer.path.checkpoint, ascending = false),
    SortPart(Racer.path.elapsedSeconds, ascending = true),
)

private data class ScenarioResult(val name: String, val passed: Boolean, val detail: String)

/**
 * A live race leaderboard with the machinery for judging whether its cache is telling the truth.
 *
 * The board on the left is an ordinary screen - the kind any app would ship.  The rail on the right
 * is what makes it a test: what the cache actually asked the server for, how its answers compare to
 * the server's, and a set of one-click scenarios that assert specific properties rather than leaving
 * you to squint at the list.
 */
@Routable("/race")
class RaceConsolePage : Page {
    override val title: Reactive<String> get() = Constant("Race Console")

    private val divisionFilter = Signal<Division?>(null)
    private val statusFilter = Signal<RaceStatus?>(RaceStatus.Racing)
    private val selectedRacer = Signal<Uuid?>(null)
    private val results = Signal<List<ScenarioResult>>(emptyList())
    private val busy = Signal<String?>(null)
    private val stormOpsPerSecond = Signal(20)
    private val stormSeconds = Signal(60)
    private val socketCut = Signal(false)
    private val httpOffline = Signal(false)

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull

        val query = remember {
            Query(
                condition = Condition.andNotNull(
                    divisionFilter()?.let { d -> condition<Racer> { it.division eq d } },
                    statusFilter()?.let { s -> condition<Racer> { it.status eq s } },
                ),
                orderBy = leaderboardOrder,
                limit = PAGE_SIZE,
            )
        }

        // One list object for the life of the filter, because paging is done by growing this list's
        // window - rebuilding it per page would throw away the boundary the cache paged from.
        val board = remember { session().racers.list(query(), pullFrequency = 15.seconds) }.flatten()

        // Hand the oracle exactly what the screen is showing, on every change and on its tick, so a
        // board that happens to be still is not mistaken for a screen that has gone away.
        reactive {
            val oracle = session().oracle
            oracle.tick()
            oracle.observe("leaderboard", query(), board(), board.limit)
        }
        launch { session.await().oracle.start(AppScope) }

        // The switches live on the page; the instruments they drive live on the session.
        reactive {
            val instruments = session().instruments
            instruments.cutSocket(socketCut())
            instruments.offline.value = httpOffline()
        }

        // Rides the oracle's tick instead of running a timer of its own, so it stops when the screen
        // does rather than polling on forever in the background.
        val stormState = rememberSuspending {
            session().oracle.tick()
            session().api.race.stormState()
        }

        col {
            raceControls(session)
            expanding.rowCollapsingToColumn(80.rem) {
                weight(2f).col { leaderboard(session, board) }
                weight(1f).scrolling.col {
                    oraclePanel(session)
                    stormPanel(session, stormState)
                    scenarioPanel(session, board, query)
                    soakPanel(session)
                    instrumentsPanel(session)
                }
            }
        }
    }

    /**
     * The storm, and the two ways of breaking the connection.
     *
     * Cutting the socket and going offline are separate switches on purpose: losing real-time
     * updates while HTTP still works is a different failure from losing the server altogether, and
     * a cache can pass one and fail the other.
     */
    private fun ElementWriter.CanAddTheme.stormPanel(
        session: Reactive<UserSession>,
        stormState: Reactive<StormState>,
    ) {
        card.col {
            h3("Pressure")
            text {
                ::content {
                    val storm = stormState()
                    if (storm.active) "storm: running, ${storm.secondsRemaining}s left, ${storm.opsApplied} ops applied"
                    else "storm: idle (${storm.race.racing} racing, ${storm.race.finished} finished)"
                }
            }
            row {
                centered.subtext("Ops/second")
                select { bind(stormOpsPerSecond, Constant(listOf(5, 20, 50, 100))) { it.toString() } }
                centered.subtext("for")
                select { bind(stormSeconds, Constant(listOf(30, 60, 120, 300))) { "${it}s" } }
            }
            row {
                important.button {
                    text("Start storm")
                    onClick {
                        session().api.race.startStorm(
                            StormRequest(opsPerSecond = stormOpsPerSecond(), seconds = stormSeconds())
                        )
                    }
                }
                button {
                    text("Stop storm")
                    onClick { session().api.race.stopStorm() }
                }
            }
            row {
                centered.subtext("Cut update socket")
                centered.switch { checked bind socketCut }
            }
            row {
                centered.subtext("HTTP offline")
                centered.switch { checked bind httpOffline }
            }
        }
    }

    private fun ElementWriter.CanAddTheme.soakPanel(session: Reactive<UserSession>) {
        card.col {
            h3("Soak")
            subtext("Runs the storm with the oracle grading throughout, then writes a report you can attach to a bug.")
            text {
                ::content {
                    val soak = session().soak
                    if (soak.running()) "running, ${soak.samples().size} samples"
                    else soak.lastReport()?.let { "finished - report ready (${it.length} chars)" } ?: "idle"
                }
            }
            row {
                important.button {
                    text { ::content { "Soak ${stormSeconds()}s" } }
                    onClick {
                        session().soak.run(seconds = stormSeconds(), opsPerSecond = stormOpsPerSecond())
                    }
                }
                button {
                    text("Download report")
                    onClick {
                        val report = session().soak.lastReport()
                            ?: throw PlainTextException("Run a soak first.", "No report yet")
                        ExternalServices.download("modelcache-soak.md", report.toBlob("text/markdown"))
                    }
                }
            }
        }
    }

    private fun ElementWriter.CanAddTheme.raceControls(session: Reactive<UserSession>) {
        card.row {
            centered.h2("Race Console")
            expanding.space()
            button {
                text("Seed 400")
                onClick { session().api.race.seedRace(SeedRaceRequest(racers = 400, clubs = 12)) }
            }
            button {
                text("Advance 50")
                onClick { session().api.race.advanceRace(AdvanceRaceRequest(racers = 50)) }
            }
            important.button {
                text("Scratch one")
                onClick { session().api.race.scratchRacer(null) }
            }
        }
    }

    private fun ElementWriter.CanAddWeight.leaderboard(
        session: Reactive<UserSession>,
        board: ModelCacheLimitReadable<Racer>,
    ) {
        // Adds straight into the caller's weighted column.  An intermediate plain column would be
        // content-height rather than a flex container, and the expanding recycler inside it would
        // collapse to nothing.
        card.row {
            centered.subtext("Division")
            select {
                bind(divisionFilter, Constant(listOf(null) + Division.entries)) { it?.name ?: "All" }
            }
            centered.subtext("Status")
            select {
                bind(statusFilter, Constant(listOf(null) + RaceStatus.entries)) { it?.name ?: "All" }
            }
            expanding.space()
            centered.subtext { ::content { "${board().size} shown, window ${board.limit}" } }
            button {
                text("Load $PAGE_SIZE more")
                onClick { board.limit(board.limit + PAGE_SIZE) }
            }
        }
        expanding.recyclerView {
            // Deliberately not the auto-paginating helper: growing the window on scroll would
            // fire requests in the middle of a measurement and make the counts unreadable.
            children(board, id = { it._id }) { racer ->
                card.row {
                    sizeConstraints(width = 3.5.rem).text { ::content { "#${racer().bib}" } }
                    expanding.col {
                        text { ::content { racer().name } }
                        subtext {
                            ::content {
                                val clubId = racer().club
                                if (clubId == null) "unattached"
                                else session().clubs.item(clubId)()?.name ?: "loading club..."
                            }
                        }
                    }
                    centered.col {
                        text { ::content { "CP ${racer().checkpoint}" } }
                        subtext { ::content { elapsed(racer().elapsedSeconds) } }
                    }
                    centered.col {
                        subtext { ::content { racer().status.name } }
                        subtext { ::content { "v${racer().version}" } }
                    }
                    button {
                        text("Open")
                        onClick { selectedRacer.value = racer()._id }
                    }
                }
            }
        }
        card.col {
            subtext("Detail (a separate read by id, answered from the list's coverage if the cache is right)")
            text {
                ::content {
                    val id = selectedRacer()
                    val racer = id?.let { session().racers.item(it)() }
                    if (racer == null) (if (id == null) "nothing selected" else "not found")
                    else "#${racer.bib} ${racer.name} - CP ${racer.checkpoint}, ${elapsed(racer.elapsedSeconds)}, v${racer.version}"
                }
            }
        }
    }

    private fun ElementWriter.CanAddTheme.oraclePanel(session: Reactive<UserSession>) {
        card.col {
            h3("Oracle")
            subtext("Re-runs the same query straight against the server and compares. Divergence only counts once it outlives the tolerance.")
            // A paused oracle must never be mistaken for a passing one.
            subtext {
                ::content {
                    val o = session().oracle
                    "${if (o.running()) "running" else "PAUSED"}, ${o.probes()} probe(s) observed"
                }
            }
            text {
                ::content {
                    val report = session().oracle.reports()["leaderboard"]
                    if (report == null) "no reading yet"
                    else "${report.verdict}: cache ${report.cachedCount} rows, server ${report.truthCount} rows" +
                            if (report.findings.isEmpty()) "" else ", oldest divergence ${report.oldestFinding.inWholeSeconds}s"
                }
            }
            text {
                ::content { session().oracle.lastError()?.let { "truth channel error: $it" } ?: "" }
            }
            colOf(remember { session().oracle.reports()["leaderboard"]?.findings ?: emptyList() }, id = { it.key }) { finding ->
                subtext { ::content { "- ${finding().describe}" } }
            }
            h3("Sustained failures this session")
            text { ::content { if (session().oracle.failures().isEmpty()) "none" else "" } }
            colOf(remember { session().oracle.failures() }, id = { it }) { failure ->
                danger.text { ::content { failure() } }
            }
        }
    }

    private fun ElementWriter.CanAddTheme.instrumentsPanel(session: Reactive<UserSession>) {
        card.col {
            h3("Requests the cache made")
            row {
                button {
                    text("Reset counts")
                    onClick { session().instruments.reset() }
                }
                centered.subtext { ::content { "${session().instruments.counters().values.sum()} total" } }
            }
            colOf(
                remember { session().instruments.counters().entries.map { it.key to it.value }.sortedBy { it.first } },
                id = { it.first },
            ) { entry ->
                subtext { ::content { "${entry().first}: ${entry().second}" } }
            }
            h3("Last calls")
            colOf(
                remember { session().instruments.calls().takeLast(12).reversed() },
                id = { "${it.at}/${it.op}/${it.detail}" },
            ) { call ->
                subtext { ::content { "${call().op} ${call().detail}" } }
            }
        }
    }

    private fun ElementWriter.CanAddTheme.scenarioPanel(
        session: Reactive<UserSession>,
        board: ModelCacheLimitReadable<Racer>,
        query: Reactive<Query<Racer>>,
    ) {
        card.col {
            h3("Scenarios")
            subtext { ::content { busy()?.let { "running: $it" } ?: "idle" } }

            scenarioButton("1. Item read is free once a list covers it") {
                val items = board.awaitOnce()
                if (items.isEmpty()) return@scenarioButton false to "board is empty - seed the race first"
                val target = items.last()
                session.awaitOnce().instruments.reset()
                val fetched = session.awaitOnce().racers.item(target._id).awaitOnce()
                delay(400)
                val queries = session.awaitOnce().instruments.count("racer.query")
                (queries == 0) to
                        "read bib ${target.bib} by id; racer.query calls: $queries (expected 0), resolved: ${fetched != null}"
            }

            scenarioButton("2. Referenced rows batch into one request") {
                val s = session.awaitOnce()
                val clubIds = board.awaitOnce().mapNotNull { it.club }.distinct()
                if (clubIds.isEmpty()) return@scenarioButton false to "no clubs referenced - seed the race first"
                s.clubs.store.invalidate()
                s.instruments.reset()
                // Concurrently, because coalescing is exactly what is being measured; asking one at a
                // time would serialise them and each would legitimately be its own request.
                val names = coroutineScope {
                    clubIds.map { id -> async { s.clubs.item(id).awaitOnce()?.name } }.awaitAll()
                }
                delay(400)
                val queries = s.instruments.count("club.query")
                (queries <= 2) to
                        "${clubIds.size} distinct clubs resolved (${names.count { it != null }} found) in $queries club.query calls (expected 1-2, not ${clubIds.size})"
            }

            scenarioButton("3. Paging costs one request and loses nothing") {
                val s = session.awaitOnce()
                val before = board.awaitOnce()
                val windowBefore = board.limit
                s.instruments.reset()
                board.limit(windowBefore + PAGE_SIZE)
                val after = board.awaitOnce()
                val queries = s.instruments.count("racer.query")
                val duplicates = after.groupBy { it._id }.filterValues { it.size > 1 }.keys
                val lost = before.map { it._id }.toSet() - after.map { it._id }.toSet()
                val grew = after.size > before.size
                (queries == 1 && duplicates.isEmpty() && lost.isEmpty() && grew) to
                        "window $windowBefore -> ${board.limit}; rows ${before.size} -> ${after.size}; " +
                        "racer.query calls: $queries (expected 1); duplicates: ${duplicates.size}; dropped: ${lost.size}"
            }

            scenarioButton("4. An out-of-band reorder converges") {
                val s = session.awaitOnce()
                val before = board.awaitOnce().map { it._id }
                val startedAt = Clock.System.now()
                s.api.race.advanceRace(AdvanceRaceRequest(racers = 50))
                val settled = awaitOracle(s, startedAt, Oracle.Verdict.Ok)
                val after = board.awaitOnce().map { it._id }
                val reordered = after != before
                (settled != null) to
                        if (settled == null) "still diverging after 25s - see the oracle panel"
                        else "converged in ${settled.inWholeMilliseconds}ms; order changed: $reordered"
            }

            scenarioButton("5. A deleted row leaves, and the list refills") {
                val s = session.awaitOnce()
                val before = board.awaitOnce()
                if (before.size < 2) return@scenarioButton false to "not enough rows - seed the race first"
                val victim = before[before.size / 2]
                val startedAt = Clock.System.now()
                s.api.race.scratchRacer(victim._id)
                var noticedAfterMs: Long = -1
                val deadline = startedAt + 25.seconds
                while (Clock.System.now() < deadline) {
                    if (board.awaitOnce().none { it._id == victim._id }) {
                        noticedAfterMs = (Clock.System.now() - startedAt).inWholeMilliseconds
                        break
                    }
                    delay(250)
                }
                val after = board.awaitOnce()
                val gone = after.none { it._id == victim._id }
                // The window still asks for the same number of rows, so losing one and not replacing
                // it is the cache serving a short list - the failure this whole exercise is about.
                val refilled = after.size == before.size
                (gone && refilled) to
                        "scratched bib ${victim.bib}; left the board after ${if (noticedAfterMs < 0) "never (25s)" else "${noticedAfterMs}ms"}; " +
                        "rows ${before.size} -> ${after.size} (window ${board.limit}, expected refill)"
            }

            scenarioButton("6. A referenced row stays free once fetched") {
                val s = session.awaitOnce()
                val clubIds = board.awaitOnce().mapNotNull { it.club }.distinct()
                if (clubIds.isEmpty()) return@scenarioButton false to "no clubs referenced - seed the race first"
                // Warm them without invalidating first, then ask again.  The second read is the
                // measurement: coverage the cache already has should cost nothing to reuse.
                coroutineScope { clubIds.map { id -> async { s.clubs.item(id).awaitOnce() } }.awaitAll() }
                delay(500)
                s.instruments.reset()
                coroutineScope { clubIds.map { id -> async { s.clubs.item(id).awaitOnce() } }.awaitAll() }
                delay(500)
                val queries = s.instruments.count("club.query")
                (queries == 0) to
                        "re-read ${clubIds.size} already-fetched clubs; club.query calls: $queries (expected 0)"
            }

            scenarioButton("7. A cut socket is noticed, and reconnecting catches up") {
                val s = session.awaitOnce()
                s.api.race.startStorm(StormRequest(opsPerSecond = 20, seconds = 45))
                delay(2000)
                // Cut the wire and let the storm change things the client cannot be told about.
                socketCut.value = true
                s.instruments.reset()
                // Sample across the whole cut, not just at the end: a single reading taken at the
                // wrong moment can miss every divergence and make a broken cut look like a pass.
                var worstWhileCut = Duration.ZERO
                var divergingSamples = 0
                repeat(30) {
                    delay(500)
                    val report = s.oracle.reports.value["leaderboard"]
                    if (report != null && report.findings.isNotEmpty()) {
                        divergingSamples++
                        if (report.oldestFinding > worstWhileCut) worstWhileCut = report.oldestFinding
                    }
                }
                val duringCut = s.instruments.total()
                socketCut.value = false
                val restoredAt = Clock.System.now()
                val settled = awaitOracle(s, restoredAt, Oracle.Verdict.Ok)
                s.api.race.stopStorm()
                // Under a storm a cache with no real-time channel has to fall behind at some point.
                // If it never does, the wire was not actually cut and this proves nothing.
                val cutWasReal = divergingSamples > 0
                (settled != null && cutWasReal) to
                        "15s with the socket down under storm: $duringCut requests, $divergingSamples/30 " +
                        "samples diverging, worst ${worstWhileCut.inWholeMilliseconds}ms; after reconnect " +
                        (settled?.let { "converged in ${it.inWholeMilliseconds}ms" } ?: "never converged in 25s") +
                        (if (cutWasReal) "" else " - INCONCLUSIVE: never diverged, so the socket probably stayed up")
            }

            scenarioButton("8. A bulk change is reflected in the visible list") {
                val s = session.awaitOnce()
                val before = board.awaitOnce()
                val division = before.firstOrNull()?.division
                    ?: return@scenarioButton false to "board is empty - seed the race first"
                val doomed = before.filter { it.division == division }.map { it._id }.toSet()
                // The documented gotcha: a bulk modify does not fetch rows the cache never had, so
                // this asks only about the rows on screen - which it must get right.
                val changed = s.racers.bulkModify(
                    MassModification(
                        condition<Racer> { it.division eq division },
                        modification<Racer> { it.status assign RaceStatus.Finished },
                    )
                )
                val settled = awaitOracle(s, Clock.System.now(), Oracle.Verdict.Ok)
                val after = board.awaitOnce()
                val stillThere = after.count { it._id in doomed }
                (stillThere == 0 && settled != null) to
                        "retired $changed racers in $division; ${doomed.size} were visible, $stillThere still shown; " +
                        (settled?.let { "list settled in ${it.inWholeMilliseconds}ms" } ?: "list never settled in 25s")
            }

            scenarioButton("9. A write that cannot reach the server fails loudly") {
                val s = session.awaitOnce()
                val target = board.awaitOnce().firstOrNull()
                    ?: return@scenarioButton false to "board is empty - seed the race first"
                val before = s.racers.item(target._id).awaitOnce()
                httpOffline.value = true
                val thrown = try {
                    s.racers.item(target._id).modify(modification<Racer> { it.name assign "SHOULD NOT STICK" })
                    null
                } catch (e: Exception) {
                    e
                } finally {
                    httpOffline.value = false
                }
                delay(500)
                val after = s.racers.item(target._id).awaitOnce()
                val unchanged = after?.name == before?.name
                // A failed write must leave nothing behind.  A cache that optimistically kept the new
                // value would show a change the server never accepted.
                (thrown != null && unchanged) to
                        "modify while offline threw ${thrown?.let { it::class.simpleName } ?: "nothing"}; " +
                        "cached name ${if (unchanged) "unchanged" else "left as '${after?.name}'"}"
            }

            scenarioButton("10. An idle list with no socket costs nothing") {
                val s = session.awaitOnce()
                // Clubs have no update socket, so this is the pure polling path.  Nothing is changing
                // and nothing is being asked for, so a cache honouring maximumAge=INFINITE and
                // pullFrequency=0 should send no requests at all.
                s.instruments.reset()
                delay(13000)
                val queries = s.instruments.count("club.query")
                (queries == 0) to
                        "13s idle with a still board: $queries club.query calls (expected 0). " +
                        "Non-zero means the 5s polling floor is being applied as a minimum rate rather " +
                        "than a cap - see ModelCache.processWhileRunning."
            }

            h3("Results")
            colOf(results, id = { it.name + it.detail }) { result ->
                col {
                    text { ::content { "${if (result().passed) "PASS" else "FAIL"} - ${result().name}" } }
                    subtext { ::content { result().detail } }
                }
            }
        }
    }

    private fun ElementWriter.CanAddTheme.scenarioButton(
        name: String,
        block: suspend () -> Pair<Boolean, String>,
    ) {
        button {
            text(name)
            onClick {
                busy.value = name
                val result = try {
                    val (passed, detail) = block()
                    ScenarioResult(name, passed, detail)
                } catch (e: Exception) {
                    ScenarioResult(name, false, "threw ${e::class.simpleName}: ${e.message}")
                } finally {
                    busy.value = null
                }
                results.value = results.value + result
            }
        }
    }

    /** Waits for the oracle to reach [want] on a reading taken after [after].  Null if it never does. */
    private suspend fun awaitOracle(
        session: UserSession,
        after: kotlin.time.Instant,
        want: Oracle.Verdict,
    ): kotlin.time.Duration? {
        val deadline = after + 25.seconds
        while (Clock.System.now() < deadline) {
            val report = session.oracle.reports.value["leaderboard"]
            if (report != null && report.checkedAt > after && report.verdict == want) {
                return report.checkedAt - after
            }
            delay(250)
        }
        return null
    }
}

private fun elapsed(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
