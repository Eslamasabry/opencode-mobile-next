package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of test/setup_audit_store_test.dart. */
class SetupAuditStoreTest {
    private val prefs = FakePrefs(
        mapOf("oc.profiles" to """[{"id":"audit","name":"Audit","flavor":"v2","baseUrl":"http://localhost:4096"}]"""),
    )
    private val registry = SetupAuditRegistry(prefs, Redactor(), FixedClock())

    private fun owner(id: String = "audit"): SetupAuditStore = (registry.forProfile(id) as SetupOutcome.Ok).value

    private fun SetupOutcome<Unit>.code(): SetupFailureCode? = failureOrNull()?.code

    @Test
    fun `B-STATE-130 two controllers share one audit writer without losing records`() = runTest {
        val first = owner()
        val second = owner()
        assertSame(first, second)
        listOf(
            async { first.append("proposal-1", "proposed", operation = "review") },
            async { second.append("proposal-2", "pending", operation = "apply") },
        ).awaitAll()
        assertEquals(listOf("proposal-1", "proposal-2"), first.records.map { it.id })
        // Records are read-only values: even a cast-and-clear of one read never reaches the store.
        (first.records as? MutableList<SetupAuditRecord>)?.let { copy -> runCatchingUnsupported { copy.clear() } }
        assertEquals(listOf("proposal-1", "proposal-2"), first.records.map { it.id })
    }

    private fun runCatchingUnsupported(block: () -> Unit): Boolean = try {
        block()
        false
    } catch (_: UnsupportedOperationException) {
        true
    }

    @Test
    fun `B-STATE-130 audit history is bounded and legacy metadata remains readable`() = runTest {
        prefs.values["oc.setupAudit.audit"] = """[{"id":"proposal-0","action":"proposed","at":"2026-09-27T10:00:00Z"}]"""
        val owner = owner()
        assertTrue(owner.isAvailable)
        assertEquals(SetupAuditRecord("proposal-0", "proposed", "2026-09-27T10:00:00Z", null), owner.records.single())
        for (i in 1..102) owner.append("proposal-$i", "verified", operation = "apply")
        assertEquals(100, owner.records.size)
        assertEquals("proposal-3", owner.records.first().id)
        assertEquals(100, json(prefs.getString("oc.setupAudit.audit")!!).jsonArray.size)
    }

    @Test
    fun `B-STATE-130 receipt acceptance and historical Undo ids remain distinct metadata`() = runTest {
        val owner = owner()
        owner.append("undo-1", "verified", operation = "undo")
        owner.append("a".repeat(48), "accepted", operation = "apply")
        assertEquals("accepted", owner.records.last().action)
        assertEquals("undo-1", owner.records.first().id)
        assertEquals("undo", owner.records.first().operation)
    }

    @Test
    fun `B-STATE-131 absent profiles and invalid metadata cannot create audit keys`() = runTest {
        val absent = owner("missing")
        assertFalse(absent.isAvailable)
        assertEquals(SetupFailureCode.Offline, absent.append("proposal-1", "proposed").code())
        val owner = owner()
        assertEquals(SetupFailureCode.Storage, owner.append("https://example.invalid/private", "proposed").code())
        assertEquals(SetupFailureCode.Storage, owner.append("proposal-1", "untrusted prose").code())
        assertEquals(SetupFailureCode.Storage, owner.append("proposal-1", "proposed", operation = "raw input").code())
        assertFalse(prefs.containsKey("oc.setupAudit.audit"))
        assertFalse(prefs.containsKey("oc.setupAudit.missing"))
        assertEquals(SetupFailureCode.Storage, registry.forProfile("").failureOrNull()?.code)
    }

    @Test
    fun `B-STATE-131 corrupt or untrusted stored metadata refuses overwrite`() = runTest {
        val owner = owner()
        for (raw in listOf(
            "{broken",
            "{}",
            """[{"id":"proposal-1","action":"proposed","at":"2026-09-27T10:00:00Z","config":{}}]""",
            """[{"id":"proposal-1","action":"untrusted prose","at":"2026-09-27T10:00:00Z"}]""",
            """[{"id":"https://example.invalid/private","action":"proposed","at":"2026-09-27T10:00:00Z"}]""",
            """[{"id":"proposal-1","action":"proposed","at":"2026-09-27T10:00:00"}]""",
        )) {
            prefs.values["oc.setupAudit.audit"] = raw
            assertFalse(owner.isAvailable, raw)
            assertTrue(owner.records.isEmpty())
            assertEquals(SetupFailureCode.Storage, owner.append("proposal-2", "pending").code())
            assertEquals(raw, prefs.getString("oc.setupAudit.audit"))
        }
    }

    @Test
    fun `B-STATE-131 refused storage does not fabricate a durable audit record`() = runTest {
        val owner = owner()
        owner.append("proposal-1", "proposed")
        prefs.refuse = { it == "oc.setupAudit.audit" }
        assertEquals(SetupFailureCode.Storage, owner.append("proposal-2", "pending").code())
        assertEquals(listOf("proposal-1"), owner.records.map { it.id })
        prefs.refuse = { false }
        owner.append("proposal-2", "pending")
        assertEquals(2, owner.records.size)
    }

    @Test
    fun `B-STATE-131 profile deletion drains audit then rejects old and new handles`() = runTest {
        prefs.gateKey = "oc.setupAudit.audit"
        prefs.gate = CompletableDeferred()
        val owner = owner()
        val writing = async { owner.append("proposal-1", "pending", operation = "apply") }
        prefs.entered.await()
        var removed = false
        // The profile deletion: drain the audit owner, sweep the profile's keys, then drop the profile row.
        val removal = launch {
            registry.closeProfile("audit")
            ProfileKeyRule.scopedKeys("audit", prefs.keys(), setOf("audit")).forEach { prefs.remove(it) }
            prefs.setString("oc.profiles", "[]")
            removed = true
        }
        runCurrent()
        val removedBeforeDrain = removed
        assertEquals(SetupFailureCode.Offline, owner.append("proposal-2", "pending").code())
        prefs.gate!!.complete(Unit)
        assertTrue(writing.await() is SetupOutcome.Ok)
        removal.join()
        assertFalse(removedBeforeDrain)
        assertFalse(prefs.containsKey("oc.setupAudit.audit"))
        assertTrue(owner.records.isEmpty())
        val reopened = owner()
        assertSame(owner, reopened)
        assertEquals(SetupFailureCode.Offline, reopened.append("proposal-3", "pending").code())
    }

    @Test
    fun `B-STATE-131 close stops admission at once even while writes are draining`() = runTest {
        val owner = owner()
        val drain = launch(UnconfinedTestDispatcher(testScheduler)) { registry.closeProfile("audit") }
        assertFalse(owner.isAvailable)
        assertEquals(SetupFailureCode.Offline, owner.append("proposal-1", "pending").code())
        drain.join()
        assertFalse(prefs.containsKey("oc.setupAudit.audit"))
    }
}
