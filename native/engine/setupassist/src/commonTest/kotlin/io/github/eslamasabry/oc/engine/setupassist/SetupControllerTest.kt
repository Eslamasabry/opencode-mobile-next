package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/setup_controller_test.dart. */
class SetupControllerTest {
    private class Gateway(private val writable: Boolean = true) : SetupTransactionalConfigGateway {
        var config: JsonObject = obj("""{"model":"example/old","default_agent":"build"}""")
        var writes = 0
        var revision = 0
        val originals = mutableMapOf<String, SetupConfigRevision>()
        var throwOnPatch = false
        var readGate: CompletableDeferred<Unit>? = null

        override val support: SetupSupport
            get() = SetupSupport(readConfig = true, writeConfig = writable, mcpInventory = true, reason = "Reversible writes are unavailable.")

        override suspend fun readConfig(): SetupOutcome<JsonObject> {
            readGate?.await()
            return SetupOutcome.Ok(config)
        }

        override suspend fun patchConfig(patch: JsonObject): SetupOutcome<Unit> {
            writes++
            if (throwOnPatch) return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Transport, "untrusted server body"))
            config = JsonObject(config + patch)
            return SetupOutcome.Ok(Unit)
        }

        override suspend fun readSnapshot(): SetupOutcome<SetupConfigRevision> =
            readConfig().let { SetupOutcome.Ok(SetupConfigRevision("fake-source", "$revision", (it as SetupOutcome.Ok).value)) }

        private suspend fun snapshot() = (readSnapshot() as SetupOutcome.Ok).value

        override suspend fun commit(expected: SetupConfigRevision, edits: List<SetupEdit>, operationId: String): SetupOutcome<SetupConfigCommit> {
            if (expected.revision != "$revision") return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Conflict, "Configuration changed."))
            writes++
            if (throwOnPatch) return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Transport, "untrusted server body"))
            val before = snapshot()
            for (edit in edits) config = if (edit.remove) without(config, edit.path) else with(config, edit.path, edit.value!!)
            revision++
            originals[operationId] = before
            return SetupOutcome.Ok(SetupConfigCommit(before, snapshot(), operationId))
        }

        override suspend fun restore(commit: SetupConfigCommit, operationId: String): SetupOutcome<SetupConfigCommit> {
            if (commit.after.revision != "$revision") return SetupOutcome.Failed(SetupFailure(SetupFailureCode.Conflict, "Configuration changed."))
            val before = snapshot()
            config = originals.getValue(commit.undoHandle).config
            revision++
            writes++
            return SetupOutcome.Ok(SetupConfigCommit(before, snapshot(), operationId))
        }

        override suspend fun listMcpServers(): SetupOutcome<List<SetupMcpStatus>> =
            SetupOutcome.Ok(listOf(SetupMcpStatus("example", "needs_auth"), SetupMcpStatus("other", "arbitrary response body")))

        private fun with(map: JsonObject, path: List<String>, value: JsonElement): JsonObject =
            if (path.size == 1) {
                JsonObject(map + (path[0] to value))
            } else {
                JsonObject(map + (path[0] to with(map[path[0]] as? JsonObject ?: JsonObject(emptyMap()), path.drop(1), value)))
            }

        private fun without(map: JsonObject, path: List<String>): JsonObject =
            if (path.size == 1) JsonObject(map - path[0]) else JsonObject(map + (path[0] to without(map.getValue(path[0]).jsonObject, path.drop(1))))
    }

    private val redactor = Redactor()
    private val prefs = FakePrefs(mapOf("oc.profiles" to """[{"id":"one"},{"id":"two"}]"""))
    private val audits = SetupAuditRegistry(prefs, redactor, FixedClock())
    private val gateway = Gateway()
    private val ids = SetupIds.from(Random(7))
    private val controller = SetupController(gateway, audits, "one", "/repo", redactor, ids)
    private val modelEdit = SetupEdit(listOf("model"), JsonPrimitive("example/new"))

    @Test
    fun `B-SEC-165 a redacted read strips arbitrary environment credentials and server errors`() = runTest {
        gateway.config = JsonObject(
            gateway.config + ("mcp" to json("""{"example":{"environment":{"VALUE":"fake-private-value"},"headers":{"Custom":"fake-other-value"}}}""")),
        )
        assertTrue(controller.refresh() is SetupOutcome.Ok)
        val output = controller.snapshot.config.toString()
        assertFalse(output.contains("fake-private-value"))
        assertFalse(output.contains("fake-other-value"))
        assertEquals("unknown", controller.snapshot.servers.last().status)
        assertEquals("needs_auth", controller.snapshot.servers.first().status)
        assertEquals(setOf("oc.profiles"), prefs.keys())
    }

    @Test
    fun `B-SEC-165 secret inputs and overlapping patches fail validation`() = runTest {
        controller.refresh()
        assertTrue(controller.validate(listOf(SetupEdit(listOf("provider", "demo", "options", "apiKey"), JsonPrimitive("fake")))).isNotEmpty())
        assertTrue(controller.validate(listOf(SetupEdit(listOf("mcp", "demo"), json("""{"headers":{"Custom":"fake"}}""")))).isNotEmpty())
        assertEquals(listOf("Review overlapping fields as separate proposals."), controller.validate(listOf(modelEdit, modelEdit)))
        assertTrue(controller.validate(listOf(SetupEdit(listOf("unsupported"), JsonPrimitive(true)))).isNotEmpty())
        assertTrue(controller.validate(listOf(SetupEdit(listOf("model"), null))).isNotEmpty())
    }

    @Test
    fun `B-SEC-165 basic shape validation rejects malformed scalar and MCP inputs`() {
        assertTrue(controller.validate(listOf(SetupEdit(listOf("model"), JsonPrimitive(42)))).isNotEmpty())
        assertTrue(
            controller.validate(listOf(SetupEdit(listOf("mcp", "x"), json("""{"type":"remote","url":"https://example.com","enabled":true}""")))).isNotEmpty(),
        )
        assertTrue(controller.validate(listOf(SetupEdit(listOf("permission", "*"), JsonPrimitive("ask")))).isEmpty())
    }

    @Test
    fun `B-STATE-132 the controller serializes concurrent actions and exposes a loading stream`() = runTest {
        val phases = mutableListOf<SetupPhase>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { controller.changes.collect { phases += it.phase } }
        gateway.readGate = CompletableDeferred()
        val refreshing = async { controller.refresh() }
        runCurrent()
        assertEquals(SetupFailureCode.Busy, controller.refresh().failureOrNull()?.code)
        gateway.readGate!!.complete(Unit)
        assertTrue(refreshing.await() is SetupOutcome.Ok)
        watching.cancel()
        assertEquals(listOf(SetupPhase.Loading, SetupPhase.Ready), phases)
    }

    @Test
    fun `B-STATE-132 going offline during a refresh cannot publish a ready state`() = runTest {
        gateway.readGate = CompletableDeferred()
        val refreshing = async { controller.refresh() }
        runCurrent()
        controller.setOnline(false)
        gateway.readGate!!.complete(Unit)
        assertTrue(refreshing.await() is SetupOutcome.Failed)
        assertEquals(SetupPhase.Offline, controller.snapshot.phase)
    }

    @Test
    fun `B-STATE-132 dispose drains an in-flight refresh and clears the profile snapshot`() = runTest {
        controller.refresh()
        gateway.readGate = CompletableDeferred()
        val refreshing = async { controller.refresh() }
        runCurrent()
        val closing = launch { controller.dispose() }
        runCurrent()
        assertFalse(closing.isCompleted, "dispose waits for the in-flight refresh")
        gateway.readGate!!.complete(Unit)
        assertTrue(refreshing.await() is SetupOutcome.Failed)
        closing.join()
        assertTrue(controller.snapshot.config.isEmpty())
        assertNull(controller.snapshot.proposal)
        assertFalse(prefs.containsKey(controller.auditKey))
        assertEquals(SetupFailureCode.Busy, controller.refresh().failureOrNull()?.code)
    }

    @Test
    fun `B-STATE-132 the profile deletion sweep includes audit and keeps another profile`() = runTest {
        controller.refresh()
        controller.propose(listOf(modelEdit))
        prefs.setString("oc.setupAudit.two", "[]")
        val swept = ProfileKeyRule.scopedKeys("one", prefs.keys(), setOf("one", "two"))
        assertTrue(controller.auditKey in swept)
        audits.closeProfile("one")
        swept.forEach { prefs.remove(it) }
        assertFalse(prefs.containsKey(controller.auditKey))
        assertTrue(prefs.containsKey("oc.setupAudit.two"))
    }

    @Test
    fun `B-STATE-130 propose needs review, apply verifies the outcome, undo restores, and audit keeps only metadata`() = runTest {
        controller.refresh()
        val proposal = controller.propose(listOf(modelEdit)).valueOrNull()!!
        assertEquals(JsonPrimitive("example/old"), proposal.changes.single().before)
        assertEquals(JsonPrimitive("example/new"), proposal.changes.single().after)
        assertEquals(0, gateway.writes)
        assertTrue(controller.apply(proposal.id, confirmed = false) is SetupOutcome.Failed)
        val fresh = controller.propose(listOf(modelEdit)).valueOrNull()!!
        assertTrue(controller.apply(fresh.id, confirmed = true) is SetupOutcome.Ok)
        assertEquals(JsonPrimitive("example/new"), gateway.config["model"])
        assertTrue(controller.snapshot.canUndo)
        assertTrue(controller.undo(confirmed = true) is SetupOutcome.Ok)
        assertEquals(JsonPrimitive("example/old"), gateway.config["model"])
        assertFalse(controller.snapshot.canUndo)
        assertEquals(2, controller.audit.count { it.action == "verified" })
        assertEquals(listOf("apply", "undo"), controller.audit.filter { it.action == "accepted" }.map { it.operation })
        assertFalse(prefs.getString(controller.auditKey)!!.contains("example/"))
    }

    @Test
    fun `B-STATE-130 an unknown write outcome records uncertainty and prevents a blind retry`() = runTest {
        controller.refresh()
        val proposal = controller.propose(listOf(modelEdit)).valueOrNull()!!
        gateway.throwOnPatch = true
        assertEquals(SetupFailureCode.Uncertain, controller.apply(proposal.id, confirmed = true).failureOrNull()?.code)
        assertEquals("uncertain", controller.audit.last().action)
        assertFalse(controller.snapshot.canUndo)
        assertTrue(controller.propose(listOf(modelEdit)) is SetupOutcome.Failed)
        assertEquals(1, gateway.writes)
    }

    @Test
    fun `B-STATE-130 a changed server configuration rejects apply and undo, recorded as rejected`() = runTest {
        controller.refresh()
        var proposal = controller.propose(listOf(modelEdit)).valueOrNull()!!
        gateway.config = JsonObject(gateway.config + ("model" to JsonPrimitive("external/change")))
        assertEquals(SetupFailureCode.Conflict, controller.apply(proposal.id, confirmed = true).failureOrNull()?.code)
        assertEquals(0, gateway.writes)
        controller.refresh()
        proposal = controller.propose(listOf(modelEdit)).valueOrNull()!!
        controller.apply(proposal.id, confirmed = true)
        gateway.config = JsonObject(gateway.config + ("default_agent" to JsonPrimitive("external")))
        assertEquals(SetupFailureCode.Conflict, controller.undo(confirmed = true).failureOrNull()?.code)
        assertEquals(1, gateway.writes)
    }

    @Test
    fun `B-SEC-165 unsupported production writes and offline never call the transport`() = runTest {
        val readOnly = Gateway(writable = false)
        val other = SetupController(readOnly, audits, "two", "/repo", redactor, ids)
        other.refresh()
        val proposal = other.propose(listOf(modelEdit)).valueOrNull()!!
        assertFalse(proposal.canApply)
        assertEquals("Reversible writes are unavailable.", proposal.reason)
        assertTrue(other.apply(proposal.id, confirmed = true) is SetupOutcome.Failed)
        assertTrue(other.undo(confirmed = true) is SetupOutcome.Failed)
        assertEquals(0, readOnly.writes)
        controller.refresh()
        controller.setOnline(false)
        val offline = controller.propose(listOf(modelEdit)).valueOrNull()!!
        assertFalse(offline.canApply)
        assertTrue(controller.apply(offline.id, confirmed = true) is SetupOutcome.Failed)
        assertEquals(0, gateway.writes)
    }

    @Test
    fun `B-SEC-165 layered source config cannot masquerade as an effective diff`() = runTest {
        gateway.config = obj("""{"sources":[]}""")
        controller.refresh()
        assertEquals(SetupFailureCode.Unsupported, controller.propose(listOf(modelEdit)).failureOrNull()?.code)
    }
}
