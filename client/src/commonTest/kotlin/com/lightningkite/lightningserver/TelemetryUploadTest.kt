package com.lightningkite.lightningserver

import com.lightningkite.services.telemetry.TelemetryAttributes
import com.lightningkite.services.telemetry.TelemetryBatch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Clock

class TelemetryUploadTest {
    private val batch = TelemetryBatch(TelemetryAttributes.empty, Clock.System.now())
    private fun refusingWith(status: Int) = telemetryUpload { throw LsErrorException(LSError(http = status)) }

    @Test
    fun permanentRefusalsAreDropped() = runTest {
        refusingWith(400)(batch)
        refusingWith(403)(batch)
        refusingWith(413)(batch)
    }

    @Test
    fun everythingElseIsRetried() = runTest {
        for (status in listOf(401, 408, 429, 500, 503)) {
            assertFailsWith<LsErrorException> { refusingWith(status)(batch) }
        }
        assertFailsWith<IllegalStateException> { telemetryUpload { error("offline") }(batch) }
    }
}
