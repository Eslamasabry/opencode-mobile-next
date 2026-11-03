package io.github.eslamasabry.oc.engine.aisetup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupTransactionTest {
    private val host = AtomicHost()
    private val audit = FakeAuditLog()
    private val redaction = FakeRedaction()
    private var current = true
    private val controller = SetupController(host, audit, redaction, "profile", "/repo", isCurrent = { current })
    private val model = SetupEdit(listOf("model"), str("example/new"))

    private suspend fun prepare(edits: List<SetupEdit> = listOf(model)): SetupProposal {
        controller.refresh()
        return controller.propose(edits).value()
    }

    private fun TestScope.recordPhases(): MutableList<SetupPhase> {
        val phases = mutableListOf<SetupPhase>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.changes.collect { phases += it.phase }
        }
        return phases
    }

    private fun mcp(): JsonObject = host.config["mcp"] as JsonObject

    // ---- B-AGENT-233

    @Test
    fun `B-AGENT-233 exact raw snapshots detach and are read-only`() {
        val list = mutableListOf<JsonElement>(str("original"))
        val nested = linkedMapOf<String, JsonElement>("nullable" to JsonNull, "list" to JsonArray(list))
        val source = linkedMapOf<String, JsonElement>("nested" to JsonObject(nested))
        val revision = SetupConfigRevision("opaque", "one", JsonObject(source))
        nested["nullable"] = str("changed")
        list.add(str("later"))
        source["new"] = str("true")
        val retained = revision.config["nested"] as JsonObject
        assertEquals(JsonNull, retained["nullable"])
        assertEquals(JsonArray(listOf(str("original"))), retained["list"])
        assertFalse(revision.config.containsKey("new"))
        assertFalse((revision.config as Any) is MutableMap<*, *>)
        assertFalse((retained as Any) is MutableMap<*, *>)
        assertFalse((retained["list"] as Any) is MutableList<*>)
    }

    @Test
    fun `B-AGENT-233 a scalar or null ancestor cannot be silently replaced by nested edits`() = runTest {
        for (root in listOf("permission", "mcp")) {
            val ancestor = if (root == "permission") str("deny") else JsonNull
            host.config = JsonObject(host.config + (root to ancestor))
            val edit = if (root == "permission") {
                SetupEdit(listOf("permission", "read"), str("ask"))
            } else {
                SetupEdit(listOf("mcp", "demo"), remote("https://example.com"))
            }
            val proposal = prepare(listOf(edit))
            assertFalse(proposal.canApply, root)
            assertEquals(SetupReason.ReplacesContainer, proposal.reason)
            assertEquals(SetupFailureCode.Invalid, controller.apply(proposal.id, confirmed = true).code())
            assertEquals(0, host.writes)
            assertEquals(ancestor, host.config[root])
        }
    }

    @Test
    fun `B-AGENT-233 an absent permission ancestor can be created and exactly removed by Undo`() = runTest {
        assertFalse(host.config.containsKey("permission"))
        val proposal = prepare(listOf(SetupEdit(listOf("permission", "read"), str("ask"))))
        assertTrue(proposal.canApply)
        controller.apply(proposal.id, confirmed = true).value()
        assertEquals(json("""{"read":"ask"}"""), host.config["permission"])
        controller.undo(confirmed = true).value()
        assertFalse(host.config.containsKey("permission"))
    }

    @Test
    fun `B-AGENT-233 disabled MCP add Undo restores the absent key and exact unrelated nulls`() = runTest {
        val before = host.config
        val proposal = prepare(
            listOf(SetupEdit(listOf("mcp", "demo"), remote("https://example.com/mcp"))),
        )
        assertTrue(proposal.canApply)
        assertFalse(proposal.changes.single().beforePresent)
        assertTrue(proposal.changes.single().afterPresent)
        controller.apply(proposal.id, confirmed = true).value()
        assertTrue(mcp().containsKey("demo"))
        controller.undo(confirmed = true).value()
        assertEquals(before, host.config)
        assertFalse(mcp().containsKey("demo"))
        assertEquals(1, host.restores)
        assertEquals(
            listOf(SetupAuditOperation.Apply, SetupAuditOperation.Undo),
            controller.audit.filter { it.action == SetupAuditAction.Verified }.map { it.operation },
        )
    }

    @Test
    fun `B-AGENT-233 Undo preserves a present null instead of deleting its source key`() = runTest {
        host.config = JsonObject(host.config + ("default_agent" to JsonNull))
        val proposal = prepare(listOf(SetupEdit(listOf("default_agent"), str("build"))))
        assertTrue(proposal.changes.single().beforePresent)
        assertEquals(JsonNull, proposal.changes.single().before)
        controller.apply(proposal.id, confirmed = true).value()
        controller.undo(confirmed = true).value()
        assertTrue(host.config.containsKey("default_agent"))
        assertEquals(JsonNull, host.config["default_agent"])
    }

    // ---- B-AGENT-234

    @Test
    fun `B-AGENT-234 legacy PATCH capability cannot enable Apply without an atomic source transaction`() = runTest {
        val other = SetupController(LegacyHost(), audit, redaction, "profile", "/repo")
        other.refresh().value()
        val proposal = other.propose(listOf(model)).value()
        assertFalse(proposal.canApply)
        assertFalse(other.support.writeConfig)
        assertEquals(SetupReason.NoAtomicRestore, proposal.reason)
        assertEquals(SetupFailureCode.Unsupported, other.apply(proposal.id, confirmed = true).code())
    }

    @Test
    fun `B-AGENT-234 unreviewable executable and provider edits stay unavailable even on atomic hosts`() = runTest {
        controller.refresh().value()
        for (edit in listOf(
            SetupEdit(listOf("provider", "example"), json("""{"name":"Example"}""")),
            SetupEdit(listOf("agent", "build"), json("""{"description":"example"}""")),
            SetupEdit(listOf("command", "run"), json("""{"description":"example"}""")),
            SetupEdit(listOf("mcp", "local"), json("""{"type":"local","command":["example"],"enabled":false}""")),
        )) {
            val proposal = controller.propose(listOf(edit)).value()
            assertFalse(proposal.canApply, "${edit.path}")
            assertNotNull(proposal.reason)
            assertEquals(SetupFailureCode.Invalid, controller.apply(proposal.id, confirmed = true).code())
        }
        assertEquals(0, host.writes)
    }

    @Test
    fun `B-AGENT-234 enabled MCP removal stays unavailable without runtime restoration`() = runTest {
        host.config = JsonObject(
            host.config + ("mcp" to json("""{"live":{"type":"remote","enabled":true,"url":"https://example.com"}}""")),
        )
        val proposal = prepare(listOf(SetupEdit(listOf("mcp", "live"), remove = true)))
        assertFalse(proposal.canApply)
        assertEquals(SetupReason.OnlyDisabledRemoteMcp, proposal.reason)
        assertEquals(0, host.writes)
    }

    @Test
    fun `B-AGENT-234 atomic CAS rejects a concurrent change after controller preflight`() = runTest {
        val proposal = prepare()
        host.beforeCommit = { host.version++ }
        assertEquals(SetupFailureCode.Conflict, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(0, host.writes)
        assertEquals(SetupAuditAction.Rejected, controller.audit.last().action)
    }

    @Test
    fun `B-AGENT-234 a revision-only change with equal config blocks Apply before dispatch`() = runTest {
        val proposal = prepare()
        host.version++
        assertEquals(SetupFailureCode.Conflict, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(0, host.writes)
    }

    // ---- B-AGENT-235

    @Test
    fun `B-AGENT-235 an audit refusal after dispatch produces uncertainty without Undo or replay`() = runTest {
        for (refused in listOf(SetupAuditAction.Accepted, SetupAuditAction.Verified)) {
            val host = AtomicHost()
            val audit = FakeAuditLog()
            val controller = SetupController(host, audit, redaction, "profile", "/repo")
            controller.refresh()
            val proposal = controller.propose(listOf(model)).value()
            audit.refuse = { it == refused }
            assertEquals(SetupFailureCode.Uncertain, controller.apply(proposal.id, confirmed = true).code())
            assertEquals(1, host.writes)
            assertEquals(str("example/new"), host.config["model"])
            assertEquals(SetupPhase.Uncertain, controller.snapshot.phase)
            assertFalse(controller.snapshot.canUndo)
            assertEquals(SetupAuditAction.Uncertain, controller.audit.last().action)
            assertTrue(controller.audit.none { it.action == SetupAuditAction.Verified })
            assertEquals(SetupFailureCode.Invalid, controller.apply(proposal.id, confirmed = true).code())
            assertEquals(SetupFailureCode.Invalid, controller.undo(confirmed = true).code())
            assertEquals(1, host.writes)
            assertEquals(0, host.restores)
        }
    }

    @Test
    fun `B-AGENT-235 MCP removal Undo restores the exact object via the host handle without resending credentials`() =
        runTest {
            host.config = JsonObject(
                host.config + (
                    "mcp" to json(
                        """{"demo":{"type":"remote","enabled":false,"url":"https://example.com/mcp",
                        "headers":{"Authorization":"FAKE_PRIVATE_HEADER_VALUE"},"nullable":null}}""",
                    )
                ),
            )
            val before = host.config
            val proposal = prepare(listOf(SetupEdit(listOf("mcp", "demo"), remove = true)))
            controller.apply(proposal.id, confirmed = true).value()
            assertFalse(mcp().containsKey("demo"))
            assertEquals(null, host.sent.single().value)
            controller.undo(confirmed = true).value()
            assertEquals(before.toString(), host.config.toString())
            assertFalse(controller.audit.toString().contains("FAKE_PRIVATE_HEADER_VALUE"))
            assertFalse(controller.snapshot.config.toString().contains("FAKE_PRIVATE_HEADER_VALUE"))
        }

    @Test
    fun `B-AGENT-235 an Undo revision conflict prevents restore even with unchanged values`() = runTest {
        val proposal = prepare()
        controller.apply(proposal.id, confirmed = true).value()
        host.version++
        assertEquals(SetupFailureCode.Conflict, controller.undo(confirmed = true).code())
        assertEquals(0, host.restores)
    }

    @Test
    fun `B-AGENT-235 receipt, readback and lost-response uncertainty disable retry and Undo`() = runTest {
        for (mode in listOf("receipt", "readback", "lost-response")) {
            val host = AtomicHost()
            val audit = FakeAuditLog()
            val controller = SetupController(host, audit, redaction, "profile", "/repo")
            controller.refresh()
            val proposal = controller.propose(listOf(model)).value()
            host.badReceipt = mode == "receipt"
            host.badReadback = mode == "readback"
            host.loseResponse = mode == "lost-response"
            assertEquals(SetupFailureCode.Uncertain, controller.apply(proposal.id, confirmed = true).code(), mode)
            assertEquals(SetupPhase.Uncertain, controller.snapshot.phase)
            assertFalse(controller.snapshot.canUndo)
            assertEquals(SetupFailureCode.Invalid, controller.apply(proposal.id, confirmed = true).code())
            assertEquals(SetupFailureCode.Invalid, controller.undo(confirmed = true).code())
            assertEquals(1, host.writes)
            assertEquals(0, host.restores)
            assertEquals(SetupAuditAction.Uncertain, controller.audit.last().action)
        }
    }

    // ---- B-AGENT-236

    @Test
    fun `B-AGENT-236 a scope change while reading preflight sends no mutation`() = runTest {
        val proposal = prepare()
        host.duringRead = { current = false }
        assertEquals(SetupFailureCode.Offline, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(0, host.writes)
    }

    @Test
    fun `B-AGENT-236 offline after dispatch reconciles the original scope without ready publication`() = runTest {
        val proposal = prepare()
        val phases = recordPhases()
        host.afterCommit = { controller.setOnline(false) }
        assertEquals(SetupFailureCode.Offline, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(3, host.reads)
        assertEquals(1, host.writes)
        assertEquals(SetupAuditAction.Verified, controller.audit.last().action)
        assertEquals(SetupPhase.Offline, controller.snapshot.phase)
        assertFalse(SetupPhase.Ready in phases)
    }

    @Test
    fun `B-AGENT-236 a scope change after dispatch reconciles but never publishes ready to another scope`() = runTest {
        val proposal = prepare()
        host.afterCommit = { current = false }
        assertEquals(SetupFailureCode.Offline, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(3, host.reads)
        assertEquals(SetupAuditAction.Verified, controller.audit.last().action)
        assertEquals(SetupPhase.Applying, controller.snapshot.phase)
    }

    @Test
    fun `B-AGENT-236 operation phases expose applying, verifying, ready and undoing`() = runTest {
        val proposal = prepare()
        val phases = recordPhases()
        controller.apply(proposal.id, confirmed = true).value()
        controller.undo(confirmed = true).value()
        assertEquals(
            listOf(
                SetupPhase.Applying, SetupPhase.Verifying, SetupPhase.Ready,
                SetupPhase.Undoing, SetupPhase.Verifying, SetupPhase.Ready,
            ),
            phases,
        )
    }
}
