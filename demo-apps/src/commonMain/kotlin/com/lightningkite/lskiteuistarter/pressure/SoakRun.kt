package com.lightningkite.lskiteuistarter.pressure

import com.lightningkite.lskiteuistarter.StormRequest
import com.lightningkite.lskiteuistarter.sdk.UserSession
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Holds the cache under sustained change for a while and writes down what happened.
 *
 * A scenario proves a specific property once.  This asks a different question: over minutes of
 * continuous churn, does anything drift?  Bugs that need a particular interleaving to appear only
 * show up when something keeps trying interleavings, and the answer is only worth anything if it
 * comes with the evidence - so this ends in a report a person can attach to a bug.
 */
class SoakRun(private val session: UserSession) {

    data class Sample(
        val at: Instant,
        val requests: Int,
        val verdict: Oracle.Verdict?,
        val cachedCount: Int,
        val truthCount: Int,
        val oldestFinding: Duration,
        /** True when a row the server has was absent from the list - the failure that matters most. */
        val missingRows: Int,
    )

    val running: Signal<Boolean> = Signal(false)
    val samples: Signal<List<Sample>> = Signal(emptyList())
    val startedAt: Signal<Instant?> = Signal(null)
    val lastReport: Signal<String?> = Signal(null)

    /**
     * Runs a storm for [seconds] while sampling what the oracle sees, then stops the storm and
     * returns the report.
     */
    suspend fun run(seconds: Int, opsPerSecond: Int): String {
        if (running.value) return "already running"
        running.value = true
        val started = Clock.System.now()
        startedAt.value = started
        samples.value = emptyList()
        session.oracle.reset()
        session.instruments.reset()

        try {
            session.api.race.startStorm(StormRequest(opsPerSecond = opsPerSecond, seconds = seconds))
            val endsAt = started + seconds.seconds
            while (Clock.System.now() < endsAt) {
                delay(2.seconds)
                val report = session.oracle.reports.value["leaderboard"]
                samples.value = samples.value + Sample(
                    at = Clock.System.now(),
                    requests = session.instruments.total(),
                    verdict = report?.verdict,
                    cachedCount = report?.cachedCount ?: 0,
                    truthCount = report?.truthCount ?: 0,
                    oldestFinding = report?.oldestFinding ?: Duration.ZERO,
                    missingRows = report?.findings?.count { it.key.startsWith("missing:") } ?: 0,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            try {
                session.api.race.stopStorm()
            } catch (_: Exception) {
                // Nothing useful to do about it here; the storm expires on its own anyway.
            }
            running.value = false
        }

        return report(seconds, opsPerSecond).also { lastReport.value = it }
    }

    fun report(seconds: Int, opsPerSecond: Int): String {
        val taken = samples.value
        val minutes = seconds / 60.0
        val counters = session.instruments.counters.value
        val worstDivergence = taken.maxOfOrNull { it.oldestFinding } ?: Duration.ZERO
        val shortSamples = taken.count { it.missingRows > 0 }
        val worstShortfall = taken.maxOfOrNull { it.missingRows } ?: 0
        val verdicts = taken.groupingBy { it.verdict?.name ?: "none" }.eachCount()
        val vacuous = taken.count { it.verdict == Oracle.Verdict.Vacuous }

        return buildString {
            appendLine("# ModelCache soak report")
            appendLine()
            appendLine("- started: ${startedAt.value}")
            appendLine("- duration: ${seconds}s at $opsPerSecond storm ops/second")
            appendLine("- samples: ${taken.size}")
            appendLine()
            if (vacuous > 0) {
                appendLine("> **$vacuous of ${taken.size} samples compared nothing** - the filter matched no rows on")
                appendLine("> either side.  Those samples prove nothing; seed the race before reading this run.")
                appendLine()
            }
            appendLine("## Verdicts")
            for ((verdict, count) in verdicts.entries.sortedBy { it.key }) {
                appendLine("- $verdict: $count")
            }
            appendLine()
            appendLine("## Divergence")
            appendLine("Under a storm the two reads are taken at different moments, so brief divergence")
            appendLine("is expected and is not a fault.  What matters is whether any of it *persisted*.")
            appendLine()
            appendLine("- samples where the list was short a row the server had: $shortSamples of ${taken.size}")
            appendLine("- most rows missing in any one sample: $worstShortfall")
            appendLine(
                "- longest a divergence survived: ${worstDivergence.inWholeMilliseconds}ms " +
                        "(zero means none outlived the check that first saw it)"
            )
            appendLine("- tolerance before that counts as a failure: ${session.oracle.tolerance.value}")
            appendLine("- **sustained failures: ${session.oracle.failures.value.size}**")
            for (failure in session.oracle.failures.value) appendLine("  - $failure")
            appendLine()
            appendLine("## Requests the cache made")
            appendLine("- total: ${counters.values.sum()}")
            if (minutes > 0) appendLine("- per minute: ${(counters.values.sum() / minutes).toInt()}")
            for ((op, count) in counters.entries.sortedBy { it.key }) appendLine("- $op: $count")
            appendLine()
            appendLine("## Samples")
            appendLine("| at | requests | verdict | cached | server | oldest finding | missing |")
            appendLine("|---|---|---|---|---|---|---|")
            for (s in taken) {
                appendLine(
                    "| ${s.at} | ${s.requests} | ${s.verdict ?: "-"} | ${s.cachedCount} | " +
                            "${s.truthCount} | ${s.oldestFinding.inWholeMilliseconds}ms | ${s.missingRows} |"
                )
            }
        }
    }
}
