package io.github.eslamasabry.oc.engine.analytics.health

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The setup funnel driven by progress snapshots: the event sequence is the contract. */
class SetupJourneyTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    private class Memory(var saved: SetupJourneySaved? = null) : SetupJourneyMemory {
        override fun load() = saved

        override fun save(saved: SetupJourneySaved?) {
            this.saved = saved
        }
    }

    private class Rig(scope: TestScope, val memory: Memory = Memory()) {
        val vendor = RecordingVendor()
        var now = 1_000_000L

        init {
            val service = TelemetryService(vendor, available = true, scope = scope)
            service.start(TelemetryConsent(usage = true), NoStore, MemoryTelemetryReportStore())
            AppTelemetry.install(service)
        }

        val journey = SetupJourney({ now }, memory, HealthFlow()) { 12.0 }

        fun events() = vendor.events.filter { it.first != "feature_used" }
    }

    private fun running(vararg ids: String) = ids.map { SetupStepView(it, SetupStepState.Running) }

    @Test
    fun `B-JOURNEY-020 a clean first setup walks the steps and ends with setup_done`() = runTest {
        val r = Rig(this)
        val j = r.journey
        j.observe("job1", SetupJobState.Running, running("linux") + SetupStepView("node", SetupStepState.Pending), true)
        r.now += 30_000
        j.observe(
            "job1", SetupJobState.Running,
            listOf(SetupStepView("linux", SetupStepState.Done), SetupStepView("node", SetupStepState.Running)), true,
        )
        r.now += 90_000
        j.observe(
            "job1", SetupJobState.Done,
            listOf(SetupStepView("linux", SetupStepState.Done), SetupStepView("node", SetupStepState.Done)), true,
        )
        assertEquals(
            listOf(
                "setup_step_reached" to mapOf("step" to "linux", "attempt" to "1"),
                "linux_setup_step" to
                    mapOf(
                        "step" to "unpack", "result" to "ok", "attempt" to "1", "ms_range" to "10000-60000",
                        "exit_kind" to "0", "signal" to "none", "compat_mode" to "none", "free_space_range" to "8-32",
                    ),
                "setup_step_reached" to mapOf("step" to "node", "attempt" to "1"),
                "linux_setup_step" to
                    mapOf(
                        "step" to "node", "result" to "ok", "attempt" to "1", "ms_range" to "60000-300000",
                        "exit_kind" to "0", "signal" to "none", "compat_mode" to "none", "free_space_range" to "8-32",
                    ),
                "linux_setup_step" to
                    mapOf(
                        "step" to "done", "result" to "ok", "ms_range" to "60000-300000", "exit_kind" to "0",
                        "signal" to "none", "compat_mode" to "none", "free_space_range" to "8-32",
                    ),
                "setup_done" to mapOf("minutes_range" to "2_to_5", "server_kind" to "phone"),
            ),
            r.events(),
        )
    }

    @Test
    fun `B-JOURNEY-021 a failed step carries its fail kind and the retry is attempt 2`() = runTest {
        val r = Rig(this)
        r.journey.observe("job1", SetupJobState.Running, running("node"), true)
        r.now += 5_000
        r.journey.observe("job1", SetupJobState.Failed, listOf(SetupStepView("node", SetupStepState.Failed, "network")), true)
        val failed = r.events().last { it.first == "linux_setup_step" }.second
        assertEquals("fail", failed["result"])
        assertEquals("network", failed["fail_kind"])
        assertNull(failed["exit_kind"], "native steps have no exit code: nothing is sent in its place")
        r.journey.observe("job2", SetupJobState.Running, running("node"), true)
        assertEquals(mapOf("step" to "node", "attempt" to "2"), r.events().last().second)
    }

    @Test
    fun `B-JOURNEY-022 a step that was already installed is skipped, a job cancelled mid-step is cancelled`() = runTest {
        val r = Rig(this)
        r.journey.observe("job1", SetupJobState.Running, listOf(SetupStepView("essentials", SetupStepState.Done)), true)
        assertEquals("skipped", r.events().last().second["result"])
        r.journey.observe("job1", SetupJobState.Running, running("opencode"), true)
        r.journey.observe("job1", SetupJobState.Cancelled, running("opencode"), true)
        assertEquals(mapOf("opencode" to "cancelled"), mapOf(r.events().last().second["step"] to r.events().last().second["result"]))
        r.journey.observe("job2", SetupJobState.Running, running("node"), true)
        r.journey.observe("job2", SetupJobState.Interrupted, running("node"), true)
        assertEquals("killed_by_system", r.events().last().second["fail_kind"])
    }

    @Test
    fun `B-JOURNEY-023 leaving a stopped setup for 5 minutes is abandoned, coming back is resumed`() = runTest {
        val r = Rig(this)
        val j = r.journey
        j.observe("job1", SetupJobState.Running, running("node"), true)
        j.observe("job1", SetupJobState.Failed, listOf(SetupStepView("node", SetupStepState.Failed, "storage")), true)
        j.onBackground()
        r.now += 4 * 60_000
        j.onForeground() // under 5 minutes: not abandoned
        j.onBackground()
        r.now += 20 * 60_000
        j.onForeground()
        assertEquals(
            mapOf("step" to "node", "reason" to "background", "minutes_range" to "15-60"),
            r.events().last().second,
        )
        j.observe("job2", SetupJobState.Running, running("node"), true)
        assertEquals(
            listOf("setup_abandoned", "setup_step_reached", "setup_resumed"),
            r.events().map { it.first }.takeLast(3),
        )
        assertEquals(mapOf("step" to "node", "minutes_range" to "15-60"), r.events().last().second)
    }

    @Test
    fun `B-JOURNEY-024 a running setup that goes on in the background is not abandoned`() = runTest {
        val r = Rig(this)
        r.journey.observe("job1", SetupJobState.Running, running("node"), true)
        r.journey.onBackground()
        r.now += 30 * 60_000
        r.journey.onForeground()
        assertEquals(listOf("setup_step_reached"), r.events().map { it.first })
    }

    @Test
    fun `B-JOURNEY-025 closing the app on an unfinished setup is abandoned on the next start, and survives the restart`() = runTest {
        val memory = Memory()
        val first = Rig(this, memory)
        first.journey.observe("job1", SetupJobState.Running, running("python"), true)
        assertEquals("python", memory.saved?.lastStep)
        val next = Rig(this, memory)
        next.now = first.now + 3 * 3_600_000L
        val j = next.journey
        j.coldStart()
        assertEquals(
            mapOf("step" to "python", "reason" to "closed", "minutes_range" to "60-1440"),
            next.events().last().second,
        )
        j.observe("job2", SetupJobState.Running, running("python"), true)
        assertEquals("setup_resumed", next.events().last().first)
        assertEquals(mapOf("step" to "python", "attempt" to "2"), next.events().first { it.first == "setup_step_reached" }.second)
    }

    @Test
    fun `B-JOURNEY-026 a finished setup is never abandoned, and the saved state round-trips as words and numbers`() = runTest {
        val memory = Memory()
        val r = Rig(this, memory)
        r.journey.observe("job1", SetupJobState.Running, running("linux"), true)
        r.journey.observe("job1", SetupJobState.Done, listOf(SetupStepView("linux", SetupStepState.Done)), true)
        val text = SetupJourneyCodec.encode(memory.saved!!)
        assertEquals(memory.saved, SetupJourneyCodec.decode(text))
        assertNull(SetupJourneyCodec.decode("garbage"))
        val later = Rig(this, memory)
        later.now += 24 * 3_600_000L
        later.journey.coldStart()
        assertEquals(emptyList(), later.events())
    }
}
