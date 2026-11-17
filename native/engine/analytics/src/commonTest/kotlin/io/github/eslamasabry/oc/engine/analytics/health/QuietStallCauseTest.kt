package io.github.eslamasabry.oc.engine.analytics.health

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuietStallCauseTest {
    @AfterTest fun reset() = AppTelemetry.reset()
    private class Rig(scope: TestScope) {
        val vendor = RecordingVendor()
        var now = 1_000L
        init {
            val service = TelemetryService(vendor, true, scope = scope)
            service.start(TelemetryConsent(usage = true), object : TelemetryConsentStore {
                override suspend fun write(consent: TelemetryConsent) = true
            }, MemoryTelemetryReportStore())
            AppTelemetry.install(service)
        }
        val journey = ChatJourney({ now })
        init { journey.sent("s", false); journey.busy("s") }
        fun stall(): Map<String, String> {
            now += 60_001
            journey.checkStalls()
            return vendor.events.last { it.first == "turn_stall" }.second
        }
    }
    @Test fun `B-435 confirmed busy silence is thinking and unprobed silence stays stream silent`() = runTest {
        val unprobed = Rig(this)
        assertEquals("stream_silent", unprobed.stall()["cause"])
        val live = Rig(this)
        live.journey.runChecked("s", true)
        assertEquals("thinking_silent", live.stall()["cause"])
    }
    @Test fun `B-435 tool input keeps the tool wait and reasoning keeps its kind`() = runTest {
        val tool = Rig(this)
        tool.journey.part("s", "m", "tool")
        tool.journey.delta("s", "tool")
        val event = tool.stall()
        assertEquals("waiting_tool", event["stage"])
        assertEquals("tool_running", event["cause"])
        val reasoning = Rig(this)
        reasoning.journey.delta("s", "reasoning")
        reasoning.journey.runChecked("s", true)
        assertEquals("thinking_silent", reasoning.stall()["cause"])
    }
    @Test fun `B-435 retry resets silence and labels a later quiet retry`() = runTest {
        val r = Rig(this)
        r.now += 50_000
        r.journey.retrying("s")
        r.now += 10_001
        r.journey.checkStalls()
        assertTrue(r.vendor.events.none { it.first == "turn_stall" }, "retry is progress")
        assertEquals("retrying", r.stall()["cause"])
    }
    @Test fun `B-435 dropped failed and refetched quiet runs report dead stream while approval is unknown`() = runTest {
        val dropped = Rig(this)
        dropped.journey.stream(false, false)
        assertEquals("stream_dead_refetched", dropped.stall()["cause"])
        val failed = Rig(this)
        failed.journey.runChecked("s", null)
        assertEquals("stream_dead_refetched", failed.stall()["cause"])
        val recovered = Rig(this)
        recovered.now += 60_001
        recovered.journey.recovered("s")
        recovered.journey.ended("s")
        assertEquals("stream_dead_refetched", recovered.vendor.events.lastOrNull { it.first == "turn_stall" }?.second?.get("cause"))
        val approval = Rig(this)
        approval.journey.approvalOpen("s", 1)
        assertEquals("unknown", approval.stall()["cause"])
    }
}
