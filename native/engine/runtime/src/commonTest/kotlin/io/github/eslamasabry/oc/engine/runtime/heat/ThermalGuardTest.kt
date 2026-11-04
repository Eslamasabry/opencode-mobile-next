package io.github.eslamasabry.oc.engine.runtime.heat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.runtime.recovery.AutomationGate
import io.github.eslamasabry.oc.engine.runtime.recovery.RecoveryGate
import io.github.eslamasabry.oc.engine.runtime.recovery.RuntimeAutomation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val none = ThermalReading(ThermalStatus.None)
private val moderate = ThermalReading(ThermalStatus.Moderate)
private val severe = ThermalReading(ThermalStatus.Severe)
private val critical = ThermalReading(ThermalStatus.Critical)
private val t0 = Instant.parse("2026-09-26T14:00:00Z")
private val phoneId = ProfileId("phone")
private val phoneTeam = HeatPausable(phoneId, url = "http://127.0.0.1:8472", city = "phone", builtin = true)

/** Flutter `_FakePort`. */
private class FakePausePort : HeatPausePort {
    var running = listOf(phoneTeam)
    val calls = mutableListOf<String>()
    var reachable = true

    override suspend fun runningHere(): List<HeatPausable> = running

    override suspend fun pause(target: HeatPausable, now: Instant): HeatHold {
        calls += "pause ${target.id.raw}"
        return HeatHold(target, since = now, sessions = listOf("gc-7"))
    }

    override suspend fun stop(hold: HeatHold): HeatHold {
        calls += "stop ${hold.target.id.raw}"
        return hold.copy(serviceStopped = true)
    }

    override suspend fun resume(hold: HeatHold, stillAllowed: () -> Boolean): Boolean {
        calls += "resume ${hold.target.id.raw} ${hold.sessions.joinToString(",")}" + if (hold.serviceStopped) " +service" else ""
        return reachable
    }
}

/** The preferences store in memory, keyed exactly like Flutter's. */
private class MemoryGuardStore : ThermalGuardStore {
    val values = linkedMapOf<String, Any>()

    override suspend fun readEnabled(): Boolean? = values[ThermalGuardStore.ENABLED_KEY] as? Boolean

    override suspend fun writeEnabled(value: Boolean): Outcome<Unit> {
        values[ThermalGuardStore.ENABLED_KEY] = value
        return Unit.ok()
    }

    override suspend fun readHolds(): Map<String, String> =
        values.filterKeys { it.startsWith(ThermalGuardStore.HOLD_KEY_PREFIX) }
            .map { (k, v) -> k.removePrefix(ThermalGuardStore.HOLD_KEY_PREFIX) to v as String }.toMap()

    override suspend fun writeHold(id: ProfileId, raw: String): Outcome<Unit> {
        values[ThermalGuardStore.holdKey(id)] = raw
        return Unit.ok()
    }

    override suspend fun removeHold(id: String): Outcome<Unit> {
        values.remove(ThermalGuardStore.HOLD_KEY_PREFIX + id)
        return Unit.ok()
    }

    fun hold(id: String): String? = values[ThermalGuardStore.HOLD_KEY_PREFIX + id] as? String
}

/** `oc.automation.<id>`'s thermalRecovery choice per profile; on by default. */
private class Policy : AutomationGate {
    val off = mutableSetOf<Pair<ProfileId, RuntimeAutomation>>()

    fun set(profile: ProfileId, behavior: RuntimeAutomation, on: Boolean) {
        if (on) off -= profile to behavior else off += profile to behavior
    }

    override fun allows(profile: ProfileId, behavior: RuntimeAutomation): Boolean = (profile to behavior) !in off
}

/** Port of test/thermal_guard_test.dart groups ThermalGuard, the supervisor policy checks, and the shell line and setting. */
class ThermalGuardTest {
    private val clock = TestClock(t0)
    private val port = FakePausePort()
    private val store = MemoryGuardStore()
    private val policy = Policy()
    private val acts = mutableListOf<HeatAct>()

    private fun guard(port: HeatPausePort = this.port) =
        ThermalGuard(port, store, policy, clock, onAct = { acts += it })

    private fun at(offset: kotlin.time.Duration) {
        clock.now = t0 + offset
    }

    @Test
    fun `B-ANDR-086 SEVERE pauses once, keeps it, tells once with the shell line and no notification`() = runTest {
        val guard = guard()
        guard.observe(severe)
        assertEquals(listOf("pause phone"), port.calls)
        assertEquals(ThermalHold.Paused, guard.hold)
        assertEquals(ThermalNoticeKind.Paused, guard.notice.value?.kind)
        assertTrue(guard.pauseActive.value, "the one status notification says paused")
        val saved = Json.parseToJsonElement(store.hold("phone")!!).jsonObject
        assertEquals(listOf("gc-7"), saved["sessions"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("severe", saved["status"]!!.jsonPrimitive.content)

        // Still hot, more readings: nothing again, no second line.
        guard.dismiss()
        guard.observe(severe)
        guard.observe(ThermalReading(ThermalStatus.Severe, headroom = 1.1))
        assertEquals(listOf("pause phone"), port.calls)
        assertNull(guard.notice.value)
        assertEquals(listOf(ThermalNoticeKind.Paused), acts.map { it.kind })
    }

    @Test
    fun `B-ANDR-086 CRITICAL stops it after the pause, and cool for two minutes gives back service and sessions`() = runTest {
        val guard = guard()
        guard.observe(severe)
        guard.observe(critical)
        assertEquals(listOf("pause phone", "stop phone"), port.calls)
        assertEquals(ThermalHold.Stopped, guard.hold)
        assertEquals(ThermalNoticeKind.Stopped, guard.notice.value?.kind)
        assertTrue(guard.pauseActive.value)

        at(1.minutes)
        guard.observe(moderate)
        assertEquals("stop phone", port.calls.last())
        at(3.minutes)
        guard.observe(moderate)
        assertEquals("resume phone gc-7 +service", port.calls.last())
        assertEquals(ThermalHold.None, guard.hold)
        assertEquals(ThermalNoticeKind.Resumed, guard.notice.value?.kind)
        assertFalse(guard.pauseActive.value)
        assertNull(store.hold("phone"))
    }

    @Test
    fun `B-ANDR-086 every confirmed pause, stop and resume is reported once per team`() = runTest {
        val guard = guard()
        guard.observe(severe)
        guard.observe(severe)
        guard.observe(critical)
        at(1.minutes)
        guard.observe(moderate)
        at(3.minutes)
        guard.observe(moderate)
        assertEquals(
            listOf(
                Triple(ThermalNoticeKind.Paused, phoneId, t0),
                Triple(ThermalNoticeKind.Stopped, phoneId, t0),
                Triple(ThermalNoticeKind.Resumed, phoneId, t0 + 3.minutes),
            ),
            acts.map { Triple(it.kind, it.target.id, it.at) },
        )
        assertTrue(acts.all { it.since == t0 }, "each act carries the episode's start")
    }

    @Test
    fun `B-ANDR-086 no notification while the app is on screen, only the shell line`() = runTest {
        // The guard has no notification path at all: on screen or off, the line and the one status say it.
        val guard = guard()
        guard.observe(severe)
        assertEquals(ThermalNoticeKind.Paused, guard.notice.value?.kind)
        assertEquals(1, acts.size)
    }

    @Test
    fun `B-ANDR-086 an unconfirmed resume is not reported and is tried again after another cool wait`() = runTest {
        val guard = guard()
        guard.observe(severe)
        port.reachable = false
        at(1.minutes)
        guard.observe(none)
        at(3.minutes)
        guard.observe(none)
        assertTrue(port.calls.last().startsWith("resume"))
        assertEquals(ThermalHold.Paused, guard.hold)
        assertEquals(listOf(ThermalNoticeKind.Paused), acts.map { it.kind })
        assertEquals(t0 + 5.minutes + 1.seconds, guard.nextCheckAt.value, "the wait starts again from the try")
        port.reachable = true
        at(5.minutes + 1.seconds)
        guard.observe(none)
        assertEquals(ThermalHold.None, guard.hold)
    }

    @Test
    fun `B-ANDR-088 recovery off still protects the phone but never resumes or reports a resume until enabled and confirmed`() =
        runTest {
            policy.set(phoneId, RuntimeAutomation.ThermalRecovery, false)
            val guard = guard()
            guard.observe(severe)
            guard.observe(critical)
            at(1.minutes)
            guard.observe(none)
            at(3.minutes)
            guard.observe(none)
            assertEquals(listOf("pause phone", "stop phone"), port.calls)
            assertEquals(listOf(ThermalNoticeKind.Paused, ThermalNoticeKind.Stopped), acts.map { it.kind })
            assertEquals(ThermalHold.Stopped, guard.hold)
            assertTrue(store.hold("phone") != null)
            assertNull(guard.nextCheckAt.value, "no cool check while recovery is off")

            policy.set(phoneId, RuntimeAutomation.ThermalRecovery, true)
            port.reachable = false
            at(5.minutes)
            guard.observe(none)
            assertEquals(listOf(ThermalNoticeKind.Paused, ThermalNoticeKind.Stopped), acts.map { it.kind })
            port.reachable = true
            at(7.minutes)
            guard.observe(none)
            assertEquals(ThermalNoticeKind.Resumed, acts.last().kind)
            assertEquals(ThermalHold.None, guard.hold)
            assertNull(store.hold("phone"))
        }

    /** A supervisor port that resumes in steps through [RecoveryGate.steps] (the D6 Gas City binding's shape). */
    private class SteppedPort(private val gate: RecoveryGate, private val duringRead: () -> Unit = {}) : HeatPausePort {
        val requests = mutableListOf<String>()

        override suspend fun runningHere(): List<HeatPausable> = listOf(phoneTeam)

        override suspend fun pause(target: HeatPausable, now: Instant): HeatHold = HeatHold(target, now)

        override suspend fun stop(hold: HeatHold): HeatHold = hold

        override suspend fun resume(hold: HeatHold, stillAllowed: () -> Boolean): Boolean =
            gate.steps(
                hold.target.id,
                listOf(
                    { requests += "GET"; duringRead(); true },
                    { requests += "PATCH"; true },
                ),
            )
    }

    @Test
    fun `B-ANDR-088 recovery policy prevents automatic supervisor work`() = runTest {
        policy.set(phoneId, RuntimeAutomation.ThermalRecovery, false)
        val supervisor = SteppedPort(RecoveryGate(policy, RuntimeAutomation.ThermalRecovery))
        assertFalse(supervisor.resume(HeatHold(phoneTeam, t0)) { true })
        assertEquals(emptyList(), supervisor.requests)

        // Through the guard: it never even reaches the port.
        val guard = guard(supervisor)
        guard.observe(severe)
        at(3.minutes)
        guard.observe(none)
        at(6.minutes)
        guard.observe(none)
        assertEquals(emptyList(), supervisor.requests)
        assertEquals(ThermalHold.Paused, guard.hold)
    }

    @Test
    fun `B-ANDR-088 disabling recovery while reading health prevents the resume`() = runTest {
        val gate = RecoveryGate(policy, RuntimeAutomation.ThermalRecovery)
        val supervisor = SteppedPort(gate) { policy.set(phoneId, RuntimeAutomation.ThermalRecovery, false) }
        assertFalse(supervisor.resume(HeatHold(phoneTeam, t0)) { gate.allows(phoneId) })
        assertEquals(listOf("GET"), supervisor.requests)
    }

    @Test
    fun `B-ANDR-090 a pause survives the app dying and the next process resumes it`() = runTest {
        val first = guard()
        first.observe(severe)
        first.close()
        assertFalse(first.pauseActive.value)

        val second = guard()
        second.start(moderate)
        assertEquals(ThermalHold.Paused, second.hold)
        assertTrue(second.pauseActive.value)
        assertEquals(listOf("pause phone"), port.calls)
        at(2.minutes)
        second.observe(moderate)
        assertEquals("resume phone gc-7", port.calls.last())
        assertEquals(ThermalHold.None, second.hold)
    }

    @Test
    fun `B-ANDR-090 an unconfirmed wake keeps the durable hold and a new process finishes it`() = runTest {
        // Flutter's GasCity variant (500, no answer, 404): the session list itself is D6; the guard half is this.
        val resumed = mutableListOf<List<String>>()
        var confirm = false
        val waking =
            object : HeatPausePort {
                override suspend fun runningHere() = listOf(phoneTeam)

                override suspend fun pause(target: HeatPausable, now: Instant) =
                    HeatHold(target, now, sessions = listOf("gc-1", "gc-2"))

                override suspend fun stop(hold: HeatHold) = hold

                override suspend fun resume(hold: HeatHold, stillAllowed: () -> Boolean): Boolean {
                    resumed += hold.sessions
                    return confirm
                }
            }
        val first = guard(waking)
        first.observe(severe)
        first.observe(moderate)
        at(2.minutes)
        first.observe(moderate)
        assertEquals(1, resumed.size)
        assertEquals(ThermalHold.Paused, first.hold)
        assertEquals(ThermalNoticeKind.Paused, first.notice.value?.kind)
        assertEquals(listOf(ThermalNoticeKind.Paused), acts.map { it.kind })
        val persisted = Json.parseToJsonElement(store.hold("phone")!!).jsonObject
        assertTrue(persisted["sessions"]!!.jsonArray.any { it.jsonPrimitive.content == "gc-2" })

        first.close()
        confirm = true
        val second = guard(waking)
        second.start(moderate)
        at(4.minutes)
        second.observe(moderate)
        assertEquals(listOf("gc-1", "gc-2"), resumed.last(), "only durable state is used")
        assertEquals(ThermalHold.None, second.hold)
        assertEquals(ThermalNoticeKind.Resumed, second.notice.value?.kind)
        assertNull(store.hold("phone"))
        assertEquals(listOf(ThermalNoticeKind.Paused, ThermalNoticeKind.Resumed), acts.map { it.kind })
    }

    @Test
    fun `B-ANDR-090 a Flutter-saved pause is read by the native process and a broken one is swept`() = runTest {
        store.values["oc.thermalPause.phone"] =
            """{"url":"http://127.0.0.1:8472","city":"phone","builtin":true,"since":"2026-09-26T14:00:00.000Z",""" +
            """"sessions":["gc-7","",3],"serviceStopped":true,"status":"critical"}"""
        store.values["oc.thermalPause.broken"] = """{"url":"","since":"x"}"""
        val guard = guard()
        guard.start(moderate)
        assertEquals(ThermalHold.Stopped, guard.hold, "a stopped service restores as a stop")
        val held = guard.holds.getValue(phoneId)
        assertEquals(listOf("gc-7"), held.sessions)
        assertEquals(ThermalStatus.Critical, held.status)
        assertEquals(t0, held.since)
        assertTrue(held.target.builtin)
        assertNull(store.hold("broken"))
        assertEquals(held, HeatHoldCodec.decode("phone", HeatHoldCodec.encode(held)), "round trip")
    }

    @Test
    fun `B-ANDR-110 the shell line shows once when paused, a dismissed line stays away, then the resume shows`() = runTest {
        val guard = guard()
        assertNull(guard.notice.value)
        guard.observe(severe)
        assertEquals(ThermalNoticeEnglish.PAUSED_NOTICE, guard.notice.value?.let { ThermalNoticeEnglish.notice(it.kind) })
        assertEquals("Your phone is hot — paused the AI Team to cool down. It resumes by itself.", ThermalNoticeEnglish.PAUSED_NOTICE)

        guard.dismiss()
        assertNull(guard.notice.value)
        guard.observe(severe)
        assertNull(guard.notice.value)

        at(1.minutes)
        guard.observe(none)
        at(3.minutes)
        guard.observe(none)
        assertEquals(ThermalNoticeKind.Resumed, guard.notice.value?.kind)
        assertEquals("Resumed the AI Team — your phone has cooled down.", ThermalNoticeEnglish.notice(ThermalNoticeKind.Resumed))
    }

    @Test
    fun `B-ANDR-110 the setting is on by default, turning it off is saved and stops any new action`() = runTest {
        val guard = guard()
        assertTrue(guard.enabled())
        assertEquals("Pause the AI Team when the phone is hot", ThermalNoticeEnglish.SETTING)
        assertEquals(
            "The team pauses with its work kept and resumes by itself once the phone cools.",
            ThermalNoticeEnglish.SETTING_DETAIL,
        )
        guard.setEnabled(false)
        assertFalse(guard.enabled())
        assertEquals(false, store.values["oc.thermalGuard"])
        guard.observe(critical)
        assertEquals(emptyList(), port.calls)
        assertNull(guard.notice.value)
    }

    @Test
    fun `B-ANDR-086 nothing works on this phone - nothing is done or reported`() = runTest {
        port.running = emptyList()
        val guard = guard()
        guard.observe(critical)
        assertEquals(emptyList(), port.calls)
        assertEquals(ThermalHold.None, guard.hold)
        assertNull(guard.notice.value)
        assertFalse(guard.pauseActive.value)
        assertEquals(emptyList(), acts)
    }
}
