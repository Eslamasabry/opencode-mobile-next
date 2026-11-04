package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Port of test/builtin_work_leases_test.dart. */
class BuiltinWorkLeasesTest {
    private val channel = FakeBuiltinLinuxChannel().apply { answerValue(mapOf("held" to true, "capped" to false)) }
    private val linux = BuiltinLinuxBridge(channel, supported = true)

    private fun Outcome<WorkLeaseStatus>.status(): WorkLeaseStatus = getOrNull() ?: error("not ok: $this")

    private val invalidLease = Failure.Runtime(RuntimeProblem.WorkLeaseInvalid)

    @Test
    fun `B-LINUX-049 an agent chat lease carries the separate profile and opaque name`() = runTest {
        val state = linux.setPhoneAgentChatWorkLease(profileId = "phone_owner", leaseId = "agent.opaque", on = true)
        assertEquals("setPhoneAgentChatWorkLease", channel.calls.single().method)
        assertEquals(
            mapOf("profileId" to "phone_owner", "leaseId" to "agent.opaque", "on" to true, "forMs" to 900_000L),
            channel.calls.single().arguments,
        )
        assertTrue(state.status().held)
    }

    @Test
    fun `B-LINUX-049 agent off keeps its profile even after the helper is lost`() = runTest {
        channel.answerValue(mapOf("held" to false, "capped" to false))
        linux.setPhoneAgentChatWorkLease(profileId = "old_owner", leaseId = "agent.old", on = false)
        assertEquals(
            mapOf("profileId" to "old_owner", "leaseId" to "agent.old", "on" to false, "forMs" to 900_000L),
            channel.calls.single().arguments,
        )
    }

    @Test
    fun `B-LINUX-050 agent profile and opaque name are validated before native handoff`() = runTest {
        for (id in listOf("", "../owner", "owner\n", "a".repeat(81))) {
            assertEquals(
                invalidLease,
                (linux.setPhoneAgentChatWorkLease(profileId = id, leaseId = "agent.valid", on = true) as Outcome.Failed).failure,
            )
            assertEquals(
                invalidLease,
                (linux.setPhoneAgentChatWorkLease(profileId = "valid_owner", leaseId = id, on = true) as Outcome.Failed).failure,
            )
        }
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-050 invalid opaque IDs are refused with work_lease_invalid before native handoff`() = runTest {
        for (id in listOf("", "chat space", "../chat", "chat/other", "chat\n", "a".repeat(81))) {
            assertEquals(invalidLease, (linux.setChatWorkLease(leaseId = id, on = true) as Outcome.Failed).failure, id)
        }
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-050 a maximum length opaque ID reaches native unchanged`() = runTest {
        val id = "a".repeat(80)
        linux.setChatWorkLease(leaseId = id, on = true)
        assertEquals(id, channel.calls.single().arguments!!["leaseId"])
    }

    @Test
    fun `B-LINUX-051 an agent malformed receipt and an unsupported platform deny admission`() = runTest {
        channel.answerValue(mapOf("held" to true))
        assertFalse(linux.setPhoneAgentChatWorkLease(profileId = "owner", leaseId = "agent.valid", on = true).status().held)
        channel.calls.clear()
        val desktop = BuiltinLinuxBridge(channel, supported = false)
        assertFalse(desktop.setPhoneAgentChatWorkLease(profileId = "owner", leaseId = "agent.valid", on = true).status().held)
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-051 a native exhausted lease reports capped without reporting held`() = runTest {
        channel.answerValue(mapOf("held" to false, "capped" to true))
        val status = linux.setChatWorkLease(leaseId = "chat.expired", on = true).status()
        assertFalse(status.held)
        assertTrue(status.capped)
    }

    @Test
    fun `B-LINUX-051 other work holding the lock does not claim this chat lease`() = runTest {
        channel.answerValue(mapOf("held" to false, "capped" to false, "workHeld" to true))
        assertEquals(WorkLeaseStatus(held = false, capped = false), linux.setChatWorkLease(leaseId = "chat.refused", on = true).status())
    }

    @Test
    fun `B-LINUX-051 absent and malformed replies never fabricate admission`() = runTest {
        val malformed =
            listOf<Any?>(
                null, true, "held", 7, emptyList<Any?>(), emptyMap<String, Any?>(),
                mapOf("held" to true),
                mapOf("capped" to false),
                mapOf("held" to "true", "capped" to false),
                mapOf("held" to true, "capped" to 0),
                mapOf("held" to true, "capped" to true),
            )
        for (reply in malformed) {
            channel.answerValue(reply)
            assertEquals(WorkLeaseStatus.NotAdmitted, linux.setChatWorkLease(leaseId = "chat.safe", on = true).status(), "$reply")
        }
    }

    @Test
    fun `B-LINUX-051 unsupported platforms return no admission without native handoff`() = runTest {
        val status = BuiltinLinuxBridge(channel, supported = false).setChatWorkLease(leaseId = "chat.unsupported", on = true).status()
        assertFalse(status.held)
        assertFalse(status.capped)
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-052 named chat acquisition uses the frozen channel contract`() = runTest {
        val status = linux.setChatWorkLease(leaseId = "chat.opaque-1", on = true).status()
        assertEquals("setChatWorkLease", channel.calls.single().method)
        assertEquals(mapOf("leaseId" to "chat.opaque-1", "on" to true, "forMs" to 600_000L), channel.calls.single().arguments)
        assertTrue(status.held)
        assertFalse(status.capped)
    }

    @Test
    fun `B-LINUX-052 named chat release retains its ID and requested duration`() = runTest {
        channel.answerValue(mapOf("held" to false, "capped" to false))
        val status = linux.setChatWorkLease(leaseId = "chat.opaque-2", on = false, hold = 2.minutes).status()
        assertEquals("setChatWorkLease", channel.calls.single().method)
        assertEquals(mapOf("leaseId" to "chat.opaque-2", "on" to false, "forMs" to 120_000L), channel.calls.single().arguments)
        assertFalse(status.held)
        assertFalse(status.capped)
    }

    @Test
    fun `B-LINUX-052 the legacy timed hold keeps its bool return and original arguments`() = runTest {
        channel.answerValue(true)
        val held = linux.holdAwakeForWork(false, hold = 3.seconds)
        assertEquals(true, held.getOrNull())
        assertEquals("holdAwakeForWork", channel.calls.single().method)
        assertEquals(mapOf("on" to false, "forMs" to 3_000L), channel.calls.single().arguments)
    }
}
