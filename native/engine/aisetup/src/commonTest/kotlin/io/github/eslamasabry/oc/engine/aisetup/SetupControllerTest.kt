package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.github.eslamasabry.oc.core.common.Outcome

class SetupControllerTest {
    private val gateway = AtomicHost(
        config = json("""{"model":"example/old","default_agent":"build"}"""),
        supportValue = SetupSupport(readConfig = true, writeConfig = true, mcpInventory = true),
    ).apply {
        servers = listOf(SetupMcpStatus("example", "needs_auth"), SetupMcpStatus("other", "arbitrary response body"))
    }
    private val audit = FakeAuditLog()
    private val redaction = FakeRedaction()
    private val controller = SetupController(gateway, audit, redaction, "one", "/repo")
    private val modelEdit = SetupEdit(listOf("model"), str("example/new"))

    // ---- B-AGENT-230

    @Test
    fun `B-AGENT-230 propose requires review, apply verifies the outcome, then undo restores`() = runTest {
        controller.refresh().value()
        val proposal = controller.propose(listOf(modelEdit)).value()
        assertEquals(str("example/old"), proposal.changes.single().before)
        assertEquals(str("example/new"), proposal.changes.single().after)
        assertEquals(0, gateway.writes)
        assertIs<Outcome.Failed>(controller.apply(proposal.id, confirmed = false))
        val fresh = controller.propose(listOf(modelEdit)).value()
        controller.apply(fresh.id, confirmed = true).value()
        assertEquals(str("example/new"), gateway.config["model"])
        assertTrue(controller.snapshot.canUndo)
        controller.undo(confirmed = true).value()
        assertEquals(str("example/old"), gateway.config["model"])
        assertFalse(controller.snapshot.canUndo)
        assertEquals(2, controller.audit.count { it.action == SetupAuditAction.Verified })
        assertFalse(controller.audit.toString().contains("example/"))
    }

    @Test
    fun `B-AGENT-230 changed server configuration prevents apply, and undo never overwrites`() = runTest {
        controller.refresh().value()
        var proposal = controller.propose(listOf(modelEdit)).value()
        gateway.config = JsonObject(gateway.config + ("model" to str("external/change")))
        assertEquals(SetupFailureCode.Conflict, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(0, gateway.writes)
        controller.refresh().value()
        proposal = controller.propose(listOf(modelEdit)).value()
        controller.apply(proposal.id, confirmed = true).value()
        gateway.config = JsonObject(gateway.config + ("default_agent" to str("external")))
        assertEquals(SetupFailureCode.Conflict, controller.undo(confirmed = true).code())
        assertEquals(1, gateway.writes)
        assertEquals(0, gateway.restores)
    }

    @Test
    fun `B-AGENT-230 the transaction facet makes disabled MCP additions reviewable and other removals not`() = runTest {
        controller.refresh().value()
        var proposal = controller.propose(
            listOf(SetupEdit(listOf("mcp", "new"), remote("https://example.com/mcp"))),
        ).value()
        assertTrue(proposal.canApply)
        proposal = controller.propose(listOf(SetupEdit(listOf("model"), remove = true))).value()
        assertFalse(proposal.canApply)
        assertEquals(SetupReason.NoReversibleAction, proposal.reason)
        assertEquals(0, gateway.writes)
    }

    @Test
    fun `B-AGENT-230 an unknown write outcome records uncertainty and prevents blind retry`() = runTest {
        controller.refresh().value()
        val proposal = controller.propose(listOf(modelEdit)).value()
        gateway.throwOnCommit = true
        assertEquals(SetupFailureCode.Uncertain, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(SetupAuditAction.Uncertain, controller.audit.last().action)
        assertFalse(controller.snapshot.canUndo)
        // The base was dropped: a new proposal needs a fresh read first.
        assertEquals(SetupFailureCode.Invalid, controller.propose(listOf(modelEdit)).code())
        assertEquals(SetupPhase.Error, controller.snapshot.phase)
        assertEquals(SetupReason.ReadFirst, controller.snapshot.reason)
        assertEquals(1, gateway.writes)
    }

    // ---- B-AGENT-231

    @Test
    fun `B-AGENT-231 unsupported production writes never call the transport`() = runTest {
        val readOnly = AtomicHost(
            supportValue = SetupSupport(readConfig = true, writeConfig = false, reason = SetupReason.NoSetupApis),
        )
        val other = SetupController(readOnly, FakeAuditLog(), redaction, "two", "/repo")
        other.refresh().value()
        val proposal = other.propose(listOf(modelEdit)).value()
        assertFalse(proposal.canApply)
        assertEquals(readOnly.support.reason, proposal.reason)
        assertEquals(SetupFailureCode.Unsupported, other.apply(proposal.id, confirmed = true).code())
        assertEquals(SetupFailureCode.Unsupported, other.undo(confirmed = true).code())
        assertEquals(0, readOnly.writes)
        assertEquals(0, readOnly.restores)
    }

    @Test
    fun `B-AGENT-231 offline cannot dispatch writes while proposals remain reviewable`() = runTest {
        controller.refresh().value()
        controller.setOnline(false)
        val proposal = controller.propose(listOf(modelEdit)).value()
        assertFalse(proposal.canApply)
        assertEquals(SetupReason.ReconnectBeforeApplying, proposal.reason)
        assertEquals(SetupPhase.Offline, controller.snapshot.phase)
        assertEquals(SetupFailureCode.Offline, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(0, gateway.writes)
    }

    @Test
    fun `B-AGENT-231 layered source config cannot masquerade as an effective diff`() = runTest {
        gateway.config = json("""{"sources":[]}""")
        controller.refresh().value()
        assertEquals(SetupFailureCode.Unsupported, controller.propose(listOf(modelEdit)).code())
        assertEquals(SetupReason.LayeredSources, controller.snapshot.reason)
    }

    @Test
    fun `B-AGENT-231 an audit storage refusal prevents mutation`() = runTest {
        controller.refresh().value()
        val proposal = controller.propose(listOf(modelEdit)).value()
        audit.refuse = { true }
        assertEquals(SetupFailureCode.Storage, controller.apply(proposal.id, confirmed = true).code())
        assertEquals(0, gateway.writes)
    }

    @Test
    fun `B-AGENT-231 setup mutation capabilities default off for unknown servers`() {
        val capabilities = ServerCapabilities()
        assertFalse(capabilities.setupConfigWrite)
        assertFalse(capabilities.setupAssistantSession)
        assertFalse(capabilities.setupConfigRead)
        val support = SetupSupport.from(capabilities)
        assertEquals(SetupSupport(), support)
        assertTrue(SetupSupport.from(capabilities.copy(setupConfigRead = true)).readConfig)
        assertNotNull(support.reason)
    }
}
