package com.lightningkite.lskiteuistarter

import com.lightningkite.services.data.GenerateDataClassPaths
import com.lightningkite.services.data.Index
import com.lightningkite.services.data.References
import com.lightningkite.services.database.HasId
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A live race leaderboard, built as a pressure test for [com.lightningkite.lightningserver.db.ModelCache].
 *
 * Every field here is chosen to stress one property of the cache rather than to model a race well.
 * See each field for which one.
 */
@Serializable
@GenerateDataClassPaths
data class Club(
    override val _id: Uuid = Uuid.random(),
    val name: String,
    val region: String,
) : HasId<Uuid>

@Serializable
enum class Division { MOpen, M40, M50, WOpen, W40, W50 }

@Serializable
enum class RaceStatus { Registered, Racing, Finished, DNF, DQ }

@Serializable
@GenerateDataClassPaths
data class Racer(
    override val _id: Uuid = Uuid.random(),
    @Index val bib: Int,
    val name: String,
    /**
     * Fans out one leaderboard read into many item lookups, which the cache is supposed to answer
     * from what the list already covered rather than with a request each.
     */
    @References(Club::class) val club: Uuid? = null,
    @Index val division: Division = Division.MOpen,
    /**
     * Moves rows in and out of a filtered list's condition, which is what forces the cache to drop
     * claims that relied on them.
     */
    @Index val status: RaceStatus = RaceStatus.Registered,
    /**
     * With [elapsedSeconds], the leaderboard's sort key: furthest checkpoint first, then fastest.
     * Both halves churn as the race runs, so rows cross pagination boundaries in both directions -
     * the case a cursor-based claim is most likely to lose a row on.
     */
    @Index val checkpoint: Int = 0,
    @Index val elapsedSeconds: Int = 0,
    /** Officials only.  Masked by [com.lightningkite.lskiteuistarter.data.RaceEndpoints] below Admin. */
    val medicalNotes: String = "",
    /**
     * Incremented on every write, so two accounts of the same row are settled by which is genuinely
     * later.  Wired to `ModelCache(versionOf = ...)`; without it the cache falls back to timestamps,
     * which cannot separate two changes inside one clock tick.
     */
    val version: Long = 0,
    val updatedAt: Instant = Clock.System.now(),
) : HasId<Uuid> {
    companion object {
        /** Checkpoints on the course.  Reaching the last one finishes a racer. */
        const val CHECKPOINTS: Int = 10
    }
}

/** Wipes the race and lays out a fresh one.  [seed] makes a run reproducible. */
@Serializable
data class SeedRaceRequest(val racers: Int = 400, val clubs: Int = 12, val seed: Int = 1)

/**
 * Advances [racers] randomly chosen runners by one checkpoint, each by a different amount, so the
 * leaderboard reorders.  This is the out-of-band change the client never initiated.
 */
@Serializable
data class AdvanceRaceRequest(val racers: Int = 25, val seed: Int? = null)

@Serializable
data class RaceState(val racers: Int, val racing: Int, val finished: Int, val clubs: Int)

/**
 * Sustained out-of-band change, which is where a cache is actually dangerous.
 *
 * A single edit is easy to get right; what breaks caches is a stream of changes arriving while reads
 * are in flight, so that every answer is slightly out of date by the time it lands.  The weights pick
 * what the storm does, relative to each other.
 */
@Serializable
data class StormRequest(
    val opsPerSecond: Int = 20,
    val seconds: Int = 60,
    /** Moves a racer up a checkpoint, which reorders the leaderboard. */
    val advanceWeight: Int = 8,
    /** A late entry appears, possibly inside the window a reader has already claimed to know. */
    val insertWeight: Int = 1,
    /** A scratch.  Removals are the change a cache most easily misses. */
    val deleteWeight: Int = 1,
    /** Retires a whole division at once, moving many rows out of a filter in one write. */
    val massModifyWeight: Int = 0,
)

@Serializable
data class StormState(
    val active: Boolean,
    val secondsRemaining: Int,
    val opsApplied: Long,
    val race: RaceState,
)
