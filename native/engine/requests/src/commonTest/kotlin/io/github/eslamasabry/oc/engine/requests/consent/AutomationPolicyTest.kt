package io.github.eslamasabry.oc.engine.requests.consent

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter `automation_policy_test.dart` (B-SEC-017, B-SEC-018) on the native owner. */
class AutomationPolicyTest {
    private class Store : AutomationStore {
        val values = LinkedHashMap<String, String>()
        var refuse = false
        var throwWrite = false
        var gate: CompletableDeferred<Unit>? = null
        var started: CompletableDeferred<Unit>? = null

        override fun read(key: String): String? = values[key]

        override suspend fun write(key: String, value: String): Boolean {
            started?.complete(Unit)
            started = null
            gate?.await()
            if (throwWrite) error("fake storage failure")
            if (refuse) return false
            values[key] = value
            return true
        }
    }

    private val store = Store()
    private val secrets = mutableSetOf<String>()
    private val protectedText: (String) -> Boolean = { text -> secrets.any { it in text } || "token=" in text }

    private fun owner(profile: String = "server-a") = AutomationPolicyOwner(profile, store, protectedText)

    private val consentOnly =
        setOf(
            AutomationBehavior.RestartDevServices, AutomationBehavior.StopIdleHelpers, AutomationBehavior.CleanCaches,
            AutomationBehavior.UpdateWhenIdle, AutomationBehavior.MonitorOtherServers, AutomationBehavior.MonitorQuota,
        )

    @Test
    fun `B-SEC-017 fresh profile defaults follow consent boundaries without writing`() {
        val policy = owner().value.value
        assertEquals(AutomationSupervision.High, policy.supervision)
        assertFalse(policy.allowsAutoApproval)
        assertFalse(policy.allowsAutoMerge)
        for (behavior in AutomationBehavior.entries) assertEquals(behavior !in consentOnly, policy.allows(behavior), behavior.name)
        assertTrue(store.values.isEmpty())
        assertFalse(policy.behaviors is MutableMap<*, *>, "the behaviours cannot be changed in place")
    }

    @Test
    fun `B-SEC-017 every choice can be disabled and survives restart per server`() = runTest {
        val owner = owner()
        owner.setSupervision(AutomationSupervision.Balanced)
        assertTrue(owner.value.value.allowsAutoApproval)
        assertTrue(owner.value.value.allowsAutoMerge)
        owner.setAutoApprove(false)
        assertFalse(owner.value.value.allowsAutoApproval)
        assertTrue(owner.value.value.allowsAutoMerge)
        owner.setAutoMergeOnGreen(false)
        for (behavior in AutomationBehavior.entries) {
            owner.setBehavior(behavior, true)
            assertTrue(owner.value.value.allows(behavior))
            owner.setBehavior(behavior, false)
        }
        val restored = owner().value.value
        val other = owner("server-b").value.value
        assertEquals(AutomationSupervision.Balanced, restored.supervision)
        assertFalse(restored.allowsAutoApproval)
        assertFalse(restored.allowsAutoMerge)
        assertTrue(restored.behaviors.values.none { it })
        assertTrue(other.allows(AutomationBehavior.Reconnect))
        assertFalse(other.allowsAutoApproval)
        assertEquals(setOf("oc.automation.server-a"), store.values.keys)

        owner.setSupervision(AutomationSupervision.Autonomous)
        assertTrue(owner.value.value.allowsAutoApproval)
        owner.setSupervision(AutomationSupervision.High)
        assertFalse(owner.value.value.allowsAutoApproval)
        assertFalse(owner.value.value.allowsAutoMerge)
        owner.disableAll()
        assertTrue(owner.value.value.behaviors.values.none { it })
    }

    @Test
    fun `B-SEC-017 concurrent edits compose and publish only after a durable save`() = runTest {
        val owner = owner()
        val observed = mutableListOf<AutomationPolicy>()
        val watch = launch { owner.value.drop(1).toList(observed) }
        runCurrent()
        store.gate = CompletableDeferred()
        val started = CompletableDeferred<Unit>().also { store.started = it }
        val first = async { owner.setBehavior(AutomationBehavior.Reconnect, false) }
        val second = async { owner.setBehavior(AutomationBehavior.MonitorQuota, true) }
        started.await()
        runCurrent()
        assertTrue(observed.isEmpty())
        assertTrue(owner.value.value.allows(AutomationBehavior.Reconnect))
        store.gate!!.complete(Unit)
        first.await()
        second.await()
        runCurrent()
        assertEquals(2, observed.size)
        assertFalse(owner.value.value.allows(AutomationBehavior.Reconnect))
        assertTrue(owner.value.value.allows(AutomationBehavior.MonitorQuota))
        assertTrue("\"monitorQuota\":true" in store.values.getValue("oc.automation.server-a"))
        watch.cancel()
    }

    @Test
    fun `B-SEC-017 refused and throwing writes preserve truth and permit retry`() = runTest {
        val owner = owner()
        owner.setBehavior(AutomationBehavior.Reconnect, false)
        val before = owner.value.value
        for (throws in listOf(false, true)) {
            store.refuse = !throws
            store.throwWrite = throws
            assertIs<Outcome.Failed>(owner.setSupervision(AutomationSupervision.Autonomous))
            assertFalse(owner.value.value.allowsAutoApproval)
            val reopened = owner().value.value
            assertFalse(reopened.allowsAutoApproval)
            assertFalse(reopened.allows(AutomationBehavior.Reconnect))
        }
        assertEquals(before, owner.value.value, "nothing was published")
        store.refuse = false
        store.throwWrite = false
        assertIs<Outcome.Ok<Unit>>(owner.setSupervision(AutomationSupervision.Balanced))
        assertEquals(AutomationSupervision.Balanced, owner.value.value.supervision)
    }

    @Test
    fun `B-SEC-017 malformed, future and partial storage never grant consent`() {
        for (raw in listOf(
            "{",
            "[]",
            """{"version":2,"supervision":"autonomous","autoApprove":true}""",
            """{"version":1,"supervision":"unknown","behaviors":{}}""",
            """{"version":1,"supervision":"balanced","autoApprove":"true","behaviors":{"monitorQuota":"true"}}""",
        )) {
            store.values["oc.automation.server-a"] = raw
            val restored = owner().value.value
            assertFalse(restored.allowsAutoApproval, raw)
            assertFalse(restored.allowsAutoMerge, raw)
            assertTrue(restored.behaviors.values.none { it }, raw)
        }
    }

    @Test
    fun `B-SEC-018 a reversible deletion pause drains admitted edits and resumes the same owner`() = runTest {
        val shared = owner("shared-a")
        store.gate = CompletableDeferred()
        val started = CompletableDeferred<Unit>().also { store.started = it }
        val first = async { shared.setBehavior(AutomationBehavior.Reconnect, false) }
        started.await()
        val second = async { shared.setBehavior(AutomationBehavior.MonitorQuota, true) }
        runCurrent()
        val paused = async { shared.pauseForDeletion() }
        runCurrent()
        assertIs<Outcome.Failed>(shared.disableAll())
        store.gate!!.complete(Unit)
        first.await()
        second.await()
        paused.await()
        assertFalse(shared.value.value.allows(AutomationBehavior.Reconnect))
        assertTrue(shared.value.value.allows(AutomationBehavior.MonitorQuota))

        shared.cancelDeletion()
        assertIs<Outcome.Ok<Unit>>(shared.setBehavior(AutomationBehavior.Reconnect, true))
        assertTrue(shared.value.value.allows(AutomationBehavior.Reconnect))
        assertTrue(shared.value.value.allows(AutomationBehavior.MonitorQuota))
        assertTrue("\"monitorQuota\":true" in store.values.getValue("oc.automation.shared-a"))
        shared.prepareForDeletion()
        shared.cancelDeletion()
        assertIs<Outcome.Failed>(shared.disableAll(), "a closed owner stays closed")
    }

    @Test
    fun `B-SEC-018 quiescing before the profile sweep prevents late resurrection`() = runTest {
        val other = owner("server-b")
        other.setSupervision(AutomationSupervision.Balanced)
        val keep = store.values["oc.automation.server-b"]
        val owner = owner()
        store.gate = CompletableDeferred()
        val started = CompletableDeferred<Unit>().also { store.started = it }
        val write = async { owner.setSupervision(AutomationSupervision.Autonomous) }
        started.await()
        val stopped = async { owner.prepareForDeletion() }
        runCurrent()
        assertIs<Outcome.Failed>(owner.disableAll())
        store.gate!!.complete(Unit)
        write.await()
        stopped.await()
        // The sweep runs only after the owner drained: nothing is written after it.
        store.values.remove("oc.automation.server-a")
        assertIs<Outcome.Failed>(owner.setBehavior(AutomationBehavior.Reconnect, false))
        assertNull(store.values["oc.automation.server-a"])
        assertEquals(keep, store.values["oc.automation.server-b"])
    }

    @Test
    fun `B-SEC-018 the redaction boundary rejects protected payloads and secret ids`() = runTest {
        secrets += "autonomous"
        val owner = owner()
        assertIs<Outcome.Failed>(owner.setSupervision(AutomationSupervision.Autonomous))
        assertTrue(store.values.isEmpty())
        assertFalse(owner.value.value.allowsAutoApproval)
        assertFailsWith<IllegalArgumentException> { owner("") }
        assertFailsWith<IllegalArgumentException> { owner("token=fake-only-credential") }
    }

    @Test
    fun `B-SEC-018 deleting a server closes its shared policy, the sweep removes the record and a later lookup starts clean`() = runTest {
        store.values["oc.automation.profile-2"] = AutomationPolicy().withSupervision(AutomationSupervision.Balanced).toJson().toString()
        val policies = AutomationPolicies(store, protectedText)
        val policy = policies.forProfile("profile-2")
        kotlin.test.assertSame(policy, policies.forProfile("profile-2"))
        assertEquals(AutomationSupervision.Balanced, policy.value.value.supervision)
        policies.closeProfile("profile-2")
        store.values.remove("oc.automation.profile-2") // the profile sweep
        assertIs<Outcome.Failed>(policy.setSupervision(AutomationSupervision.Autonomous))
        assertNull(store.values["oc.automation.profile-2"])
        val fresh = policies.forProfile("profile-2")
        assertFalse(fresh === policy)
        assertEquals(AutomationSupervision.High, fresh.value.value.supervision)
    }
}
