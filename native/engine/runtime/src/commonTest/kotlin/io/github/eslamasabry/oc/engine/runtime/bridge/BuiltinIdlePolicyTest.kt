package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of test/builtin_idle_policy_test.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuiltinIdlePolicyTest {
    private fun receipt(): Map<String, Any?> =
        mapOf(
            "installed" to true,
            "phase" to "ready",
            "serverRunning" to false,
            "serverRestartWanted" to true,
            "serverIdlePolicySupported" to true,
            "serverIdleEnabled" to true,
            "serverIdleMinutes" to 5,
            "serverIdleStopped" to true,
            "serverIdleHelperStopped" to false,
            "serverIdleGeneration" to 9,
        )

    private val channel = FakeBuiltinLinuxChannel().apply { answerValue(receipt()) }
    private val linux = BuiltinLinuxBridge(channel, supported = true)
    private val unsafeOwners = listOf("", "../owner", "owner\n", "a".repeat(81))

    private fun Outcome<*>.runtimeProblem(): RuntimeProblem? = ((this as? Outcome.Failed)?.failure as? Failure.Runtime)?.problem

    @Test
    fun `B-LINUX-018 idle policy carries authored booleans and minute bounds`() = runTest {
        val state = linux.setPhoneServerIdlePolicy(enabled = false, idleMinutes = 60)
        assertEquals("setPhoneServerIdlePolicy", channel.calls.single().method)
        assertEquals(mapOf("enabled" to false, "idleMinutes" to 60), channel.calls.single().arguments)
        assertTrue(state.getOrNull()!!.serverIdleReceiptValid)
        for (minutes in listOf(-1, 0, 61)) {
            assertIs<Outcome.Failed>(linux.setPhoneServerIdlePolicy(enabled = true, idleMinutes = minutes))
        }
        assertEquals(1, channel.calls.size)
    }

    @Test
    fun `B-LINUX-018 resume and completion retain the exact owner generation and have no start fallback`() = runTest {
        assertIs<Outcome.Ok<*>>(linux.resumeIdleStoppedPhoneServer("phone_owner", expectedIdleGeneration = 9))
        assertIs<Outcome.Ok<*>>(linux.completePhoneServerIdleResume("phone_owner", expectedIdleGeneration = 9))
        assertEquals(listOf("resumeIdleStoppedPhoneServer", "completePhoneServerIdleResume"), channel.methods)
        assertTrue(
            channel.calls.all {
                it.arguments == mapOf("profileId" to "phone_owner", "expectedIdleGeneration" to 9L)
            },
        )
    }

    @Test
    fun `B-LINUX-018 unsafe owners and nonpositive generations never reach the native channel`() = runTest {
        for (id in unsafeOwners) {
            assertEquals(RuntimeProblem.IdleResumeStale, linux.resumeIdleStoppedPhoneServer(id, 9).runtimeProblem())
            assertEquals(RuntimeProblem.IdleResumeStale, linux.completePhoneServerIdleResume(id, 9).runtimeProblem())
        }
        for (generation in listOf(0L, -1L)) {
            assertEquals(RuntimeProblem.IdleResumeStale, linux.resumeIdleStoppedPhoneServer("owner", generation).runtimeProblem())
            assertEquals(RuntimeProblem.IdleResumeStale, linux.completePhoneServerIdleResume("owner", generation).runtimeProblem())
        }
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-018 the tri-state work heartbeat carries no work text and validates the readable alias`() = runTest {
        channel.answerValue(null)
        val states = listOf(true, false, null)
        for (busy in states) linux.observePhoneAgentWork(profileId = "readable_alias", busy = busy)
        assertEquals(states.map { mapOf("profileId" to "readable_alias", "busy" to it) }, channel.calls.map { it.arguments })
        assertTrue(channel.calls.all { it.method == "observePhoneAgentWork" })
        linux.observePhoneAgentWork(profileId = "../owner", busy = false)
        assertEquals(3, channel.calls.size)
    }

    @Test
    fun `B-LINUX-018 unsupported platforms never dispatch idle start or heartbeat methods`() = runTest {
        val desktop = BuiltinLinuxBridge(channel, supported = false)
        assertFalse(desktop.setPhoneServerIdlePolicy(enabled = true, idleMinutes = 1).getOrNull()!!.serverIdlePolicySupported)
        assertFalse(desktop.resumeIdleStoppedPhoneServer("owner", 9).getOrNull()!!.serverIdlePolicySupported)
        desktop.observePhoneAgentWork(profileId = "owner", busy = false)
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-018 a stalled native completion is bounded at 8 seconds and has no manual fallback`() = runTest {
        channel.stalls = true
        val result = linux.completePhoneServerIdleResume("owner", 9)
        assertEquals(8_000L, testScheduler.currentTime)
        assertEquals(Failure.Runtime(RuntimeProblem.IdleResumeUnavailable), (result as Outcome.Failed).failure)
        assertEquals("completePhoneServerIdleResume", channel.calls.single().method)
    }

    @Test
    fun `B-LINUX-019 supporting status requires every correctly typed admission field`() {
        val good = receipt()
        assertTrue(RuntimeStatusMap.parse(good).serverIdleReceiptValid)
        for (field in listOf("serverIdleEnabled", "serverIdleStopped", "serverIdleHelperStopped", "serverRunning", "serverRestartWanted")) {
            for (value in listOf(null, 0, "true")) {
                assertFalse(RuntimeStatusMap.parse(good + (field to value)).serverIdleReceiptValid, "$field=$value")
            }
        }
        for (value in listOf(null, -1, 9.0, "9", Double.NaN)) {
            assertFalse(RuntimeStatusMap.parse(good + ("serverIdleGeneration" to value)).serverIdleReceiptValid, "generation=$value")
        }
        for (value in listOf(null, 0, 61, 5.0, "5")) {
            assertFalse(RuntimeStatusMap.parse(good + ("serverIdleMinutes" to value)).serverIdleReceiptValid, "minutes=$value")
        }
        assertFalse(RuntimeStatusMap.parse(good + ("serverIdleGeneration" to 0)).serverIdleReceiptValid)
        assertTrue(
            RuntimeStatusMap.parse(good + ("serverIdleStopped" to false) + ("serverIdleGeneration" to 0)).serverIdleReceiptValid,
        )
        assertFalse(RuntimeStatusMap.Absent.serverIdleReceiptValid)
        assertFalse(RuntimeStatusMap.parse(emptyMap<String, Any?>()).serverIdlePolicySupported)
    }

    @Test
    fun `B-LINUX-019 unknown or malformed native replies expose only the fixed recovery error`() = runTest {
        val replies =
            listOf(
                ChannelReply.Value(null),
                ChannelReply.Value(emptyMap<String, Any?>()),
                ChannelReply.Value(receipt() + ("serverIdleGeneration" to 9.0)),
                ChannelReply.Error("private-code", redacted("private-detail")),
                ChannelReply.Missing,
            )
        for (reply in replies) {
            channel.answer = { reply }
            val result = linux.resumeIdleStoppedPhoneServer("owner", 9)
            // Only the fixed problem: no native code, message or details reach the person.
            assertEquals(Failure.Runtime(RuntimeProblem.IdleResumeUnavailable, details = null), (result as Outcome.Failed).failure)
        }
        assertTrue(channel.calls.all { it.method == "resumeIdleStoppedPhoneServer" })
    }

    @Test
    fun `B-LINUX-019 an old channel can refuse a heartbeat without authorizing or exposing output`() = runTest {
        channel.answerError("not_available", details = "private-detail")
        linux.observePhoneAgentWork(profileId = "owner", busy = false)
        assertEquals(1, channel.calls.size)
    }
}
