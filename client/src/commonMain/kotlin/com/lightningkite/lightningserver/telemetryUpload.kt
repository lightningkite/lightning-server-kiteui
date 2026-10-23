package com.lightningkite.lightningserver

import com.lightningkite.kiteui.LogRoot
import com.lightningkite.services.telemetry.TelemetryBatch

/**
 * The `upload` for KiteUI's `Telemetry`: `upload = telemetryUpload { api.clientTelemetry.ingest(it) }`.
 *
 * A batch the server refused for good (a 4xx other than 401, 408 or 429) is dropped. Anything else is
 * rethrown, so `Telemetry` keeps the batch and retries it later.
 */
public fun telemetryUpload(send: suspend (TelemetryBatch) -> Unit): suspend (TelemetryBatch) -> Unit = { batch ->
    try {
        send(batch)
    } catch (e: LsErrorException) {
        // 401 can be a stale token (sessions reject it even on endpoints that allow anonymous callers);
        // 408 and 429 pass with time.
        if (e.status !in 400..499 || e.status in setOf(401, 408, 429)) throw e
        // LogRoot, not Log: Telemetry records Log calls, which would turn this into more telemetry.
        LogRoot.tag("Telemetry").warn("Dropping a telemetry batch the server refused: ${e.message}")
    }
}
