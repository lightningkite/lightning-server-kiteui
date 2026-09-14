package com.lightningkite.lskiteuistarter.sdk

import com.lightningkite.kiteui.Log
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lskiteuistarter.Club
import com.lightningkite.lskiteuistarter.Racer
import com.lightningkite.lskiteuistarter.pressure.InstrumentedRest
import com.lightningkite.lskiteuistarter.pressure.InstrumentedRestWithUpdates
import com.lightningkite.lskiteuistarter.pressure.Instruments
import com.lightningkite.lskiteuistarter.pressure.Oracle
import com.lightningkite.lskiteuistarter.pressure.SoakRun
import kotlin.uuid.Uuid


class UserSession(
    val api: Api,
    val userId: Uuid,
) : CachedApi(api) {

    /** Shared by both caches under test, so the request counts cover the whole screen. */
    val instruments: Instruments = Instruments()

    /**
     * The leaderboard's cache.
     *
     * Replaces the generated one so the app cannot accidentally read through an uninstrumented
     * cache, and so this collection gets the two things the generator cannot supply: somewhere to
     * send its log, and a way to read a row's version.  Without [ModelCache.versionOf] the cache
     * settles conflicting accounts of a row by timestamp, which cannot separate two changes inside
     * one clock tick - so this is also the only place that path gets exercised at all.
     */
    override val racers: ModelCache<Racer, Uuid> = ModelCache(
        InstrumentedRestWithUpdates(api.race.racer, instruments, "racer"),
        Racer.serializer(),
        log = Log.tag("racers"),
        versionOf = { it.version },
    )

    /** No update socket behind this one; see the server's ClubEndpoints. */
    override val clubs: ModelCache<Club, Uuid> = ModelCache(
        InstrumentedRest(api.race.club, instruments, "club"),
        Club.serializer(),
        log = Log.tag("clubs"),
    )

    /** Reads the server directly, deliberately outside [instruments]. */
    val oracle: Oracle = Oracle(api.race.racer)

    val soak: SoakRun = SoakRun(this)
}
