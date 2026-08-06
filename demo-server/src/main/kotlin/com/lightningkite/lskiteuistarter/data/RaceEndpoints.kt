package com.lightningkite.lskiteuistarter.data

import com.lightningkite.lightningserver.auth.require
import com.lightningkite.lightningserver.definition.Task
import com.lightningkite.lightningserver.definition.builder.ServerBuilder
import com.lightningkite.lightningserver.definition.launch
import com.lightningkite.lightningserver.runtime.ServerRuntime
import com.lightningkite.lightningserver.http.get
import com.lightningkite.lightningserver.http.post
import com.lightningkite.lightningserver.typed.ApiHttpHandler
import com.lightningkite.lightningserver.typed.ModelRestEndpoints
import com.lightningkite.lightningserver.typed.ModelRestEndpointsAndUpdatesWebsocket.Companion.plus
import com.lightningkite.lightningserver.typed.ModelRestUpdatesWebsocket
import com.lightningkite.lightningserver.typed.auth
import com.lightningkite.lightningserver.typed.modelInfo
import com.lightningkite.lightningserver.typed.sdk.module
import com.lightningkite.lskiteuistarter.*
import com.lightningkite.lskiteuistarter.UserAuth.RoleCache.userRole
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.Mask
import com.lightningkite.services.database.ModelPermissions
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.condition
import com.lightningkite.services.database.eq
import com.lightningkite.services.database.interceptChange
import com.lightningkite.services.database.mask
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database.modification
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A live race leaderboard, and the levers for putting a client's cache under pressure.
 *
 * The point of the pressure endpoints is that they change the database *out of band* - the client
 * under test never issued these writes, so the only ways it can learn about them are the update
 * socket and its own polling.  That is exactly the path where a cache serves a stale or, far worse,
 * a short list.
 */
object RaceEndpoints : ServerBuilder() {

    /**
     * Each model is mounted as its own child module rather than `include`d here.  `include` merges a
     * model's endpoints into the enclosing SDK interface, and an interface can only carry one model,
     * so including both would silently drop whichever lost - the generated client would simply have
     * no clubs in it.
     */
    val club = path.path("club") module ClubEndpoints
    val racer = path.path("racer") module RacerEndpoints

    /**
     * Deliberately has no update socket, unlike [RacerEndpoints].  Clubs are the referenced side of
     * the leaderboard's foreign key, so this is what an item lookup with no real-time channel behind
     * it costs - it has only coverage and polling to work with.
     */
    object ClubEndpoints : ServerBuilder() {
        val info = Server.database.modelInfo(
            auth = UserAuth.require(),
            tableName = "Club",
            permissions = { ModelPermissions.allowAll<Club>() },
        )
        val rest = path include ModelRestEndpoints(info)
    }

    object RacerEndpoints : ServerBuilder() {
        val info = Server.database.modelInfo(
            auth = UserAuth.require(),
            tableName = "Racer",
            // Every write bumps the version, so the client can settle conflicting accounts of a row by
            // which is genuinely later rather than by comparing clocks it doesn't share.
            signals = {
                it.interceptChange { incoming ->
                    Modification.Chain(
                        listOf(
                            incoming,
                            modification<Racer> {
                                it.version += 1L
                                it.updatedAt assign Clock.System.now()
                            },
                        )
                    )
                }
            },
            permissions = {
                val official = auth.userRole() >= UserRole.Admin
                ModelPermissions(
                    all = Condition.Always,
                    // Officials only.  Masked fields are also why sorting is not free: the server adds
                    // a sort's read mask to the effective condition, so ordering by a field you cannot
                    // see silently drops rows.
                    readMask = if (official) Mask() else mask<Racer> { it.medicalNotes.mask("") },
                )
            },
        )
        val rest = path include ModelRestEndpoints(info) + ModelRestUpdatesWebsocket(info)
    }

    val seed = path.path("seed").post bind ApiHttpHandler(
        summary = "Seed Race",
        description = "Wipes the race and lays out a fresh field of racers, all at the start line.",
        auth = UserAuth.require(),
        implementation = { input: SeedRaceRequest ->
            val random = Random(input.seed)
            val clubTable = ClubEndpoints.info.table()
            val racerTable = RacerEndpoints.info.table()
            racerTable.deleteMany(Condition.Always)
            clubTable.deleteMany(Condition.Always)

            val clubs = clubTable.insert(
                (1..input.clubs).map {
                    Club(name = "${clubNames[it % clubNames.size]} $it", region = regions[it % regions.size])
                }
            )
            racerTable.insert(
                (1..input.racers).map { bib ->
                    Racer(
                        bib = bib,
                        name = "${firstNames[random.nextInt(firstNames.size)]} ${lastNames[random.nextInt(lastNames.size)]}",
                        club = clubs[random.nextInt(clubs.size)]._id,
                        division = Division.entries[random.nextInt(Division.entries.size)],
                        status = RaceStatus.Racing,
                        medicalNotes = if (random.nextInt(20) == 0) "Asthma - inhaler in drop bag" else "",
                    )
                }
            )
            state()
        }
    )

    val advance = path.path("advance").post bind ApiHttpHandler(
        summary = "Advance Race",
        description = "Moves randomly chosen racers up one checkpoint, reordering the leaderboard.",
        auth = UserAuth.require(),
        implementation = { input: AdvanceRaceRequest ->
            val random = input.seed?.let { Random(it) } ?: Random.Default
            val racerTable = RacerEndpoints.info.table()
            val running = racerTable
                .find(condition { it.status eq RaceStatus.Racing })
                .toList()
                .shuffled(random)
                .take(input.racers)
            for (racer in running) {
                // A different leg time per racer per leg is what makes rows overtake each other,
                // rather than the whole field advancing in lockstep and preserving its order.
                val legSeconds = 200 + random.nextInt(400)
                val finishing = racer.checkpoint + 1 >= Racer.CHECKPOINTS
                racerTable.updateOneIgnoringResult(
                    condition { it._id eq racer._id },
                    modification {
                        it.checkpoint += 1
                        it.elapsedSeconds += legSeconds
                        if (finishing) it.status assign RaceStatus.Finished
                    },
                )
            }
            state()
        }
    )

    val scratch = path.path("scratch").post bind ApiHttpHandler(
        summary = "Scratch Racer",
        description = "Deletes one racer out of band.  Removals are the change a cache most easily misses.",
        auth = UserAuth.require(),
        implementation = { input: Uuid? ->
            val racerTable = RacerEndpoints.info.table()
            val victim = input
                ?: racerTable.find(Condition.Always, limit = 1).toList().firstOrNull()?._id
                ?: throw IllegalStateException("No racers to scratch")
            racerTable.deleteOneIgnoringOld(condition { it._id eq victim })
            state()
        }
    )

    val raceState = path.path("state").get bind ApiHttpHandler(
        summary = "Race State",
        auth = UserAuth.require(),
        implementation = { _: Unit -> state() }
    )

    /**
     * Runs the storm outside the request that asked for it.
     *
     * A scheduled task cannot do this - scheduling is driven by a once-per-minute engine tick, and a
     * storm needs to act many times a second.
     */
    val stormRunner = path.path("storm-runner") bind Task<StormRequest>(timeout = 11.minutes) { input ->
        stormLoop(input)
    }

    val startStorm = path.path("storm").post bind ApiHttpHandler(
        summary = "Start Storm",
        description = "Mutates the race continuously, so a reader is never looking at a still target.",
        auth = UserAuth.require(),
        implementation = { input: StormRequest ->
            require(input.opsPerSecond in 1..500) { "opsPerSecond must be between 1 and 500" }
            require(input.seconds in 1..600) { "seconds must be between 1 and 600" }
            val endsAt = Clock.System.now() + input.seconds.seconds
            Server.cache().set(
                STORM_ENDS_AT,
                endsAt.toString(),
                String.serializer(),
                input.seconds.seconds + 1.minutes,
            )
            Server.cache().set(STORM_OPS, 0L, Long.serializer(), input.seconds.seconds + 1.minutes)
            stormRunner.launch(input)
            stormState()
        }
    )

    val stopStorm = path.path("storm").path("stop").post bind ApiHttpHandler(
        summary = "Stop Storm",
        auth = UserAuth.require(),
        implementation = { _: Unit ->
            Server.cache().remove(STORM_ENDS_AT)
            stormState()
        }
    )

    val stormStatus = path.path("storm").path("state").get bind ApiHttpHandler(
        summary = "Storm State",
        auth = UserAuth.require(),
        implementation = { _: Unit -> stormState() }
    )
}

/** Set while a storm is running; clearing it is how [stopStorm] stops one early. */
private const val STORM_ENDS_AT = "race-storm-ends-at"
private const val STORM_OPS = "race-storm-ops"
private const val NEXT_BIB = "race-next-bib"

context(server: ServerRuntime)
private suspend fun stormEndsAt(): Instant? =
    Server.cache().get(STORM_ENDS_AT, String.serializer())?.let { Instant.parse(it) }

context(server: ServerRuntime)
private suspend fun stormState(): StormState {
    val endsAt = stormEndsAt()
    val remaining = endsAt?.minus(Clock.System.now())?.inWholeSeconds?.coerceAtLeast(0) ?: 0
    return StormState(
        active = remaining > 0,
        secondsRemaining = remaining.toInt(),
        opsApplied = Server.cache().get(STORM_OPS, Long.serializer()) ?: 0L,
        race = state(),
    )
}

/**
 * Applies [StormRequest.opsPerSecond] changes a second until the storm's time runs out.
 *
 * Targets are drawn from the head of the leaderboard rather than the whole field, because that is
 * the stretch a reader is actually watching - churn a thousand rows down the order costs the same to
 * generate and tests nothing.
 */
context(server: ServerRuntime)
private suspend fun stormLoop(input: StormRequest) {
    val racers = RaceEndpoints.RacerEndpoints.info.table()
    val random = Random.Default
    val picker = listOf(
        StormOp.Advance to input.advanceWeight,
        StormOp.Insert to input.insertWeight,
        StormOp.Delete to input.deleteWeight,
        StormOp.MassModify to input.massModifyWeight,
    ).filter { it.second > 0 }
    if (picker.isEmpty()) return
    val totalWeight = picker.sumOf { it.second }

    while (true) {
        val endsAt = stormEndsAt() ?: return
        if (Clock.System.now() >= endsAt) return
        val tickStarted = Clock.System.now()

        var head = racers
            .find(condition { it.status eq RaceStatus.Racing }, orderBy = leaderboardOrder, limit = 200)
            .toList()

        if (head.isEmpty()) {
            // A ten-checkpoint course empties in about a minute under a storm, and a storm that runs
            // out of racers stops testing anything - worse, a filtered board then agrees with an
            // equally empty server and grades as correct.  So send the field back out for another lap.
            val restarted = racers.updateManyIgnoringResult(
                condition { it.status eq RaceStatus.Finished },
                modification {
                    it.status assign RaceStatus.Racing
                    it.checkpoint assign 0
                    it.elapsedSeconds assign 0
                },
            )
            if (restarted == 0) return
            head = racers
                .find(condition { it.status eq RaceStatus.Racing }, orderBy = leaderboardOrder, limit = 200)
                .toList()
            if (head.isEmpty()) return
        }

        var applied = 0L
        repeat(input.opsPerSecond) {
            val op = run {
                var roll = random.nextInt(totalWeight)
                picker.first { (_, weight) ->
                    roll -= weight
                    roll < 0
                }.first
            }
            val target = head[random.nextInt(head.size)]
            when (op) {
                StormOp.Advance -> {
                    val legSeconds = 200 + random.nextInt(400)
                    val finishing = target.checkpoint + 1 >= Racer.CHECKPOINTS
                    racers.updateOneIgnoringResult(
                        condition { it._id eq target._id },
                        modification {
                            it.checkpoint += 1
                            it.elapsedSeconds += legSeconds
                            if (finishing) it.status assign RaceStatus.Finished
                        },
                    )
                }

                StormOp.Insert -> {
                    val bib = Server.cache().add(NEXT_BIB, 1L).toInt() + 100_000
                    racers.insert(
                        listOf(
                            Racer(
                                bib = bib,
                                name = "Late Entry $bib",
                                club = target.club,
                                division = target.division,
                                status = RaceStatus.Racing,
                                // Slots in beside an existing racer rather than at the back, so late
                                // arrivals land inside a window a reader already believes it knows.
                                checkpoint = target.checkpoint,
                                elapsedSeconds = target.elapsedSeconds + random.nextInt(20) - 10,
                            )
                        )
                    )
                }

                StormOp.Delete -> racers.deleteOneIgnoringOld(condition { it._id eq target._id })

                StormOp.MassModify -> racers.updateManyIgnoringResult(
                    condition { it.division eq target.division },
                    modification { it.status assign RaceStatus.Finished },
                )
            }
            applied++
        }
        Server.cache().add(STORM_OPS, applied)

        val spent = Clock.System.now() - tickStarted
        if (spent < 1.seconds) delay(1.seconds - spent)
    }
}

private enum class StormOp { Advance, Insert, Delete, MassModify }

/** The order the leaderboard is read in; the storm aims at the rows a reader can see. */
private val leaderboardOrder = listOf(
    SortPart(Racer.path.checkpoint, ascending = false),
    SortPart(Racer.path.elapsedSeconds, ascending = true),
)

context(server: ServerRuntime)
private suspend fun state(): RaceState {
    val racers = RaceEndpoints.RacerEndpoints.info.table()
    return RaceState(
        racers = racers.count(Condition.Always),
        racing = racers.count(condition { it.status eq RaceStatus.Racing }),
        finished = racers.count(condition { it.status eq RaceStatus.Finished }),
        clubs = RaceEndpoints.ClubEndpoints.info.table().count(Condition.Always),
    )
}

private val clubNames = listOf(
    "Ridgeline Runners", "Harbor Track Club", "Summit Striders", "Cedar Valley AC",
    "Northgate Milers", "Lakeshore Pacers", "Foothill Flyers", "Old Mill Harriers",
)
private val regions = listOf("North", "South", "East", "West")
private val firstNames = listOf(
    "Ada", "Ben", "Cora", "Dev", "Elena", "Finn", "Greta", "Hugo",
    "Iris", "Jonas", "Kira", "Luca", "Mira", "Nils", "Opal", "Pilar",
)
private val lastNames = listOf(
    "Alvarez", "Brandt", "Chen", "Dahl", "Ericsson", "Fontaine", "Grimm", "Halloran",
    "Ibarra", "Jankowski", "Kaur", "Lindqvist", "Moreau", "Nakamura", "Okafor", "Petrov",
)
