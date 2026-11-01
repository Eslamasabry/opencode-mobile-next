package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDeletionTest {
    private val doomed = id("doomed")
    private val keeper = id("keeper")

    private fun <T> Outcome<T>.ok(): T = assertIs<Outcome.Ok<T>>(this).value

    /** Flutter `test/profile_deletion_test.dart` `_seed()`: everything for `doomed`, the same shapes for `keeper`. */
    private suspend fun Harness.seedFixture() {
        seed(
            "oc.profiles" to json(
                listOf(
                    mapOf("id" to "doomed", "name" to "Old workstation", "baseUrl" to "https://old.example:4096", "username" to "dev", "flavor" to "v2", "serverVersion" to "0.14.2"),
                    mapOf("id" to "keeper", "name" to "Laptop", "baseUrl" to "https://keep.example:4096", "username" to ""),
                ),
            ),
            "oc.activeProfile" to "doomed",
            "oc.widgetFollowup.doomed" to "{}", "oc.widgetFollowup.keeper" to "{}",
            "oc.model.doomed" to "anthropic|claude", "oc.modelExplicit.doomed" to true, "oc.agent.doomed" to "build",
            "oc.variant.doomed" to "high", "oc.sessionDisplayModels.doomed.agents" to "{}",
            "oc.location.doomed" to json(mapOf("directory" to "/home/dev/code", "workspace" to "main")),
            "oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Fdev%2Fcode%0Amain" to true,
            "oc.model.keeper" to "openai|gpt", "oc.agent.keeper" to "plan",
            "oc.location.keeper" to json(mapOf("directory" to "/srv", "workspace" to null)),
            "oc.offlineQueue" to json(
                listOf(
                    mapOf("id" to "q1", "profileID" to "doomed", "sessionID" to "ses_a", "text" to "secret prompt", "attachments" to listOf(mapOf("url" to "data:image/png;base64,AAAA")), "createdAt" to 1),
                    mapOf("id" to "q2", "profileID" to "doomed", "sessionID" to "ses_b", "text" to "another", "createdAt" to 2),
                    mapOf("id" to "q3", "profileID" to "keeper", "sessionID" to "ses_c", "text" to "keep me", "createdAt" to 3),
                ),
            ),
            "oc.sessionDrafts" to json(
                listOf(
                    mapOf("sessionID" to "ses_a", "profileID" to "doomed", "text" to "half-written prompt", "updatedAt" to 1),
                    mapOf("sessionID" to "ses_x", "text" to "orphan draft", "updatedAt" to 2),
                    mapOf("sessionID" to "ses_c", "profileID" to "keeper", "text" to "keep this draft", "updatedAt" to 3),
                ),
            ),
            "oc.widgetSessions" to json(mapOf("profileID" to "doomed", "sessions" to listOf(mapOf("id" to "ses_a", "title" to "Refactor billing")))),
            "oc.pinnedShortcuts" to json(mapOf("profileID" to "doomed", "sessions" to listOf(mapOf("title" to "Refactor billing")))),
            "oc.attentionTile" to json(mapOf("profileID" to "doomed", "count" to 2)),
            "oc.appearance" to "dark", "oc.transcript.reasoningExpanded" to true,
        )
        secrets.values["pw.doomed"] = "doomed-password"
        secrets.values["pw.keeper"] = "keeper-password"
        check(store.load() is Outcome.Ok)
    }

    private fun TestScope.harness() = Harness(StandardTestDispatcher(testScheduler))

    @Test
    fun `B-STATE-063 a clean run reports a complete deletion and leaves nothing of the server, the other whole`() = runTest {
        val h = harness()
        h.seedFixture()

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok()

        assertTrue(result.complete)
        assertTrue(result.failures.isEmpty())
        assertEquals(2, result.removedByOwner[":engine:delivery"])
        assertEquals(2, result.removedByOwner[":engine:composer"]) // owned + unattributable
        assertEquals(listOf("keeper"), h.store.profiles.value.map { it.id.raw })
        assertNull(h.store.activeId.value)
        assertNull(h.string("oc.activeProfile"))
        assertFalse("doomed" in assertNotNull(h.string("oc.profiles")))
        assertFalse("0.14.2" in assertNotNull(h.string("oc.profiles")))
        assertTrue("pw.doomed" in h.secrets.deleted)
        assertEquals("keeper-password", h.secrets.values["pw.keeper"])

        val disk = h.persistedText()
        for (gone in listOf("secret prompt", "data:image/png", "half-written prompt", "orphan draft", "Refactor billing", "oc.model.doomed", "oc.agent.doomed", "oc.location.doomed", "oc.sessionDisplayModels.doomed.agents", "oc.providerRuntimeRefresh.v1.doomed")) {
            assertFalse(gone in disk, gone)
        }
        for (kept in listOf("keep me", "keep this draft", "oc.model.keeper", "oc.location.keeper", "oc.widgetFollowup.keeper", "oc.appearance", "oc.transcript.reasoningExpanded")) {
            assertTrue(kept in disk, kept)
        }
        // Nothing deleted comes back after a restart.
        val restarted = Harness(StandardTestDispatcher(testScheduler), h.secrets, h.backend)
        assertEquals(listOf("keeper"), restarted.store.load().ok().map { it.id.raw })
        assertTrue(restarted.scope.scopedKeys(doomed).isEmpty())
    }

    @Test
    fun `B-STATE-063 deleting the other profile leaves the first one whole`() = runTest {
        val h = harness()
        h.seedFixture()

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(keeper).ok()

        assertTrue(result.complete)
        assertEquals(1, result.removedByOwner[":engine:delivery"])
        assertEquals(2, result.removedByOwner[":engine:composer"]) // keeper's own draft plus the unattributable one
        assertNull(result.removedByOwner[":android:widgets"]) // the snapshot is doomed's
        assertEquals("doomed", h.string("oc.activeProfile"))
        assertEquals("build", h.string("oc.agent.doomed"))
        assertTrue("secret prompt" in assertNotNull(h.string("oc.offlineQueue")))
        assertTrue("Refactor billing" in assertNotNull(h.string("oc.pinnedShortcuts")))
    }

    @Test
    fun `B-STATE-063 a refused queue write keeps the server, its queue, its row and its secret`() = runTest {
        val h = harness()
        h.seedFixture()
        h.backend.refuse += "oc.offlineQueue"

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok()

        assertFalse(result.removedProfile)
        assertEquals(listOf(":engine:delivery"), result.failedOwners)
        assertEquals(listOf("doomed", "keeper"), h.store.profiles.value.map { it.id.raw })
        assertTrue("doomed" in assertNotNull(h.string("oc.profiles")))
        assertEquals("doomed", h.string("oc.activeProfile"))
        assertTrue(h.secrets.deleted.isEmpty(), "the Keystore entry must stay")
        assertTrue("secret prompt" in assertNotNull(h.string("oc.offlineQueue")))
        // Everything is reopened: the profile's lane admits writes again.
        h.kv.commit(h.lanes.forProfile(doomed)) { putString("oc.model.doomed", "again") }.ok()
    }

    @Test
    fun `B-STATE-063 a refused draft write keeps the server and the done queue step is reported as done`() = runTest {
        val h = harness()
        h.seedFixture()
        h.backend.refuse += "oc.sessionDrafts"

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok()

        assertFalse(result.removedProfile)
        assertEquals(listOf(":engine:composer"), result.failedOwners)
        assertEquals(2, result.removedByOwner[":engine:delivery"])
        assertTrue("half-written" in assertNotNull(h.string("oc.sessionDrafts")))
        assertTrue(h.secrets.deleted.isEmpty())
    }

    @Test
    fun `B-STATE-063 widget snapshot, launcher shortcut and tile refusals each keep the server`() = runTest {
        val h = harness()
        h.seedFixture()
        h.backend.refuse += setOf("oc.pinnedShortcuts", "oc.attentionTile")

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok()

        assertFalse(result.removedProfile)
        assertEquals(listOf(":android:background", ":android:background"), result.failedOwners)
        assertNotNull(h.string("oc.pinnedShortcuts"))
        assertNotNull(h.string("oc.attentionTile"))
        assertNull(h.string("oc.widgetSessions"))
        assertEquals(2, h.store.profiles.value.size)
    }

    @Test
    fun `B-STATE-063 every refusal is reported, not just the first`() = runTest {
        val h = harness()
        h.seedFixture()
        h.backend.refuse += setOf("oc.offlineQueue", "oc.sessionDrafts", "oc.location.doomed")

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok()

        assertEquals(listOf(":engine:delivery", ":engine:composer", ProfileDeletion.SWEEP_OWNER), result.failedOwners)
        assertFalse(result.removedProfile)
        // The sweep is one commit: a refused key keeps every scoped key (no half-swept server).
        assertEquals("build", h.string("oc.agent.doomed"))
        assertTrue(result.failures.all { it.failure is Failure.StorageRefused })
    }

    @Test
    fun `B-STATE-063 an unreadable queue is never treated as empty`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed("oc.offlineQueue" to "{not json")

        val result = h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok()

        assertFalse(result.removedProfile)
        assertEquals(Failure.StorageRefused(), result.failures.single().failure)
    }

    @Test
    fun `B-STATE-061 deletion tombstones the shared phone owner, sweeps its recovery budget and clears an ownerless widget snapshot`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed(
            "oc.builtinServerOwner" to "doomed",
            "oc.builtinRecovery.doomed" to """{"version":1,"attempts":3}""",
            "oc.widgetSessions" to json(mapOf("connected" to true, "sessions" to listOf(mapOf("id" to "ses_a", "title" to "Legacy")))),
        )

        assertTrue(h.deletion(cleaners = h.embeddedCleaners()).delete(doomed).ok().complete)

        assertEquals("", h.string("oc.builtinServerOwner"))
        assertNull(h.string("oc.builtinRecovery.doomed"))
        assertNull(h.string("oc.widgetSessions"))
        // Idempotent: nothing left to clear for that id.
        val again = EmbeddedRecordCleaner(h.kv, h.lanes.app, FlutterEraEdits.WidgetSnapshot, 500).clear(doomed, DeletionOptions())
        assertEquals(0, again.ok())
    }

    @Test
    fun `B-STATE-013 deletion stops new writes and waits for a pending write before sweeping`() =
        drainsPendingWriteThenSweeps("oc.defaultNotices.audit")

    @Test
    fun `B-AGENT-301 team project delete drains in-flight writes without resurrection`() =
        drainsPendingWriteThenSweeps("oc.orchestration.audit.project")

    @Test
    fun `B-SEC-176 a queued team editor save cannot recreate data once deletion starts`() =
        drainsPendingWriteThenSweeps("oc.teamEditorDrafts.audit")

    /**
     * Flutter `default_notice_deletion_test.dart`: a write admitted on the profile's lane before the delete (held at
     * [gate], like the delayed disk there) lands first; the delete waits for it, then sweeps it.
     */
    private fun drainsPendingWriteThenSweeps(key: String) = runTest {
        val h = harness()
        val audit = id("audit")
        h.store.upsert(StoredProfile(audit, "Audit", "http://localhost:4096")).ok()
        val gate = CompletableDeferred<Unit>()

        val writing = async {
            h.lanes.forProfile(audit).write {
                gate.await()
                h.kv.commit(WriterLane("disk")) { putString(key, "{}") }
            }
        }
        runCurrent()
        val removal = async { h.deletion().delete(audit) }
        runCurrent()
        assertFalse(removal.isCompleted, "deletion outran the pending write")
        // A write asked for after the delete started is refused, and so is a save of the profile.
        assertEquals(
            Failure.Refused(RefusalReason.ProfileClosing),
            (h.kv.commit(h.lanes.forProfile(audit)) { putString(key, "late") } as Outcome.Failed).failure,
        )
        assertEquals(Failure.Refused(RefusalReason.ProfileClosing), (h.store.upsert(StoredProfile(audit, "A", "http://localhost:1")) as Outcome.Failed).failure)

        gate.complete(Unit)
        writing.await().ok()
        assertTrue(removal.await().ok().complete)
        assertNull(h.kv.get(key))
        assertTrue(h.scope.scopedKeys(audit).isEmpty())
        // The lane stays closed after the deletion: nothing late can re-create the key.
        assertIs<Outcome.Failed>(h.kv.commit(h.lanes.forProfile(audit)) { putString(key, "late") })
    }

    @Test
    fun `B-AGENT-302 profile deletion closes the team simulator before sweeping its records`() = runTest {
        val h = harness()
        h.seedFixture()
        val events = mutableListOf<String>()
        val simulator = object : ProfileCloser {
            override val owner = "deferred:team simulator"
            override fun stopAdmission(profile: ProfileId) { events += "stop" }
            override suspend fun drain(profile: ProfileId): Outcome<Unit> {
                // A late simulator tick lands while it closes, through a lane that is not the profile's.
                h.kv.commit(h.lanes.app) { putString("oc.orchestration.doomed.snapshot", """{"runs":[]}""") }
                events += "drained"
                return Unit.ok()
            }
            override fun reopen(profile: ProfileId) { events += "reopen" }
        }
        val beforeSweep = recorder("before", DeletionStep.STASH, events)
        val afterSweep = recorder("after", DeletionStep.RETAINED_COPIES, events)

        assertTrue(h.deletion(setOf(simulator), setOf(beforeSweep, afterSweep)).delete(doomed).ok().complete)

        assertEquals(listOf("stop", "drained", "before", "after"), events)
        assertNull(h.kv.get("oc.orchestration.doomed.snapshot"))
    }

    @Test
    fun `B-AGENT-193 deletion fences the phone agent backend first and sweeps the built-in profile's agent keys`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed("oc.model.doomed.agents" to "x", "oc.sessionModels.doomed.agents" to "{}", "oc.model.keeper.agents" to "y")
        val events = mutableListOf<String>()
        val agents = object : ProfileCloser {
            override val owner = ":engine:agents"
            override fun stopAdmission(profile: ProfileId) { events += "fence" }
            override suspend fun drain(profile: ProfileId): Outcome<Unit> = Unit.ok().also { events += "closed" }
            override fun reopen(profile: ProfileId) {}
        }
        val firstCleaner = recorder("cleaner", DeletionStep.QUEUE, events)

        assertTrue(h.deletion(setOf(agents), setOf(firstCleaner)).delete(doomed).ok().complete)

        assertEquals(listOf("fence", "closed", "cleaner"), events)
        assertNull(h.kv.get("oc.model.doomed.agents"))
        assertNull(h.kv.get("oc.sessionModels.doomed.agents"))
        assertEquals("y", h.string("oc.model.keeper.agents"))
        // The backend's own lane is closed too: no late token or attach write for "<id>.agents".
        assertIs<Outcome.Failed>(h.kv.commit(h.lanes.forProfile(id("doomed.agents"))) { putString("oc.model.doomed.agents", "late") })
    }

    @Test
    fun `B-STATE-058 a failed phone engine deletion keeps the server and its tombstone, and a retry finishes`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed("oc.teamEngineDeleted.doomed" to true, "oc.automation.doomed" to "{}")
        h.secrets.values["oc.teamEngineAuth.doomed"] = "engine-bearer-token"
        var engineAnswers = false
        val phoneEngine = object : ProfileResourceCleaner {
            override val owner = ":engine:runtime phone engine"
            override val order = DeletionStep.DEVICE_SURFACES
            override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
                if (engineAnswers) 1.ok() else Failure.Device().failed()
        }

        val blocked = h.deletion(cleaners = setOf(phoneEngine)).delete(doomed).ok()
        assertFalse(blocked.removedProfile)
        assertEquals(listOf(":engine:runtime phone engine"), blocked.failedOwners)
        assertEquals(true, h.kv.get("oc.teamEngineDeleted.doomed")?.let { (it as? PrefValue.Bool)?.value })
        assertNotNull(h.string("oc.automation.doomed")) // restore authority kept through the failure
        assertEquals("engine-bearer-token", h.secrets.values["oc.teamEngineAuth.doomed"])

        engineAnswers = true
        val done = h.deletion(cleaners = setOf(phoneEngine)).delete(doomed).ok()
        assertTrue(done.complete)
        assertNull(h.kv.get("oc.teamEngineDeleted.doomed"))
        assertNull(h.kv.get("oc.automation.doomed"))
        assertFalse("oc.teamEngineAuth.doomed" in h.secrets.values)
    }

    @Test
    fun `B-STATE-020 deletion removes the memory pairing, token, owner key and outbox of that server only`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed("oc.factsbookNode.doomed" to "link-a", "oc.factsbookNode.keeper" to "link-b")
        for (p in listOf("oc.factsbook.", "oc.factsbookOwner.", "oc.factsbookOutbox.")) {
            h.secrets.values[p + "doomed"] = "fb-secret-a"
            h.secrets.values[p + "keeper"] = "fb-secret-b"
        }
        h.secrets.values["oc.factsbook-node.passphrase"] = "vault-passphrase"

        assertTrue(h.deletion().delete(doomed).ok().complete)

        assertNull(h.kv.get("oc.factsbookNode.doomed"))
        assertEquals("link-b", h.string("oc.factsbookNode.keeper"))
        for (p in listOf("oc.factsbook.", "oc.factsbookOwner.", "oc.factsbookOutbox.")) {
            assertFalse(p + "doomed" in h.secrets.values, p)
            assertEquals("fb-secret-b", h.secrets.values[p + "keeper"], p)
        }
    }

    @Test
    fun `B-SEC-075 the memory vault passphrase stays in secure storage and is never taken by a profile deletion`() = runTest {
        val h = harness()
        h.seedFixture()
        h.secrets.values["oc.factsbook-node.passphrase"] = "vault-passphrase"
        h.seed("oc.factsbook-node.stopped" to true)

        assertTrue(h.deletion().delete(doomed).ok().complete)

        // App-wide memory data: the deferred feature removes it, never a server deletion; it is not on disk in prefs.
        assertEquals("vault-passphrase", h.secrets.values["oc.factsbook-node.passphrase"])
        assertNotNull(h.kv.get("oc.factsbook-node.stopped"))
        assertFalse("vault-passphrase" in h.persistedText())
    }

    @Test
    fun `B-STATE-145 the last-known team record is saved under the profile scope and swept on deletion`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed("oc.orchestration.doomed.lastKnown" to """{"runs":[{"id":"r1"}]}""", "oc.orchestration.keeper.lastKnown" to "{}")
        h.secrets.values["oc.orchestration.doomed.grant"] = "team-grant-value"

        assertTrue("oc.orchestration.doomed.lastKnown" in h.scope.scopedKeys(doomed))
        assertTrue(h.deletion().delete(doomed).ok().complete)

        assertNull(h.kv.get("oc.orchestration.doomed.lastKnown"))
        assertEquals("{}", h.string("oc.orchestration.keeper.lastKnown"))
        assertFalse("oc.orchestration.doomed.grant" in h.secrets.values)
    }

    @Test
    fun `B-AGENT-311 the remembered work graph view is swept with the profile`() = runTest {
        val h = harness()
        h.seedFixture()
        h.seed("oc.orchestration.doomed.workView" to """{"zoom":1.5}""", "oc.orchestration.keeper.workView" to "{}")

        assertTrue(h.deletion().delete(doomed).ok().complete)

        assertNull(h.kv.get("oc.orchestration.doomed.workView"))
        assertNotNull(h.kv.get("oc.orchestration.keeper.workView"))
    }

    @Test
    fun `B-STATE-063 a second delete of the same server while one runs gets the same result`() = runTest {
        val h = harness()
        h.seedFixture()
        val deletion = h.deletion(cleaners = h.embeddedCleaners())

        val first = async { deletion.delete(doomed) }
        val second = async { deletion.delete(doomed) }

        assertEquals(first.await(), second.await())
        assertTrue(first.await().ok().complete)
        assertEquals(1, h.secrets.deleted.count { it == "pw.doomed" })
    }

    @Test
    fun `B-STATE-063 a cleaner that throws is reported as its owner's failure and the server is kept`() = runTest {
        val h = harness()
        h.seedFixture()
        val broken = object : ProfileResourceCleaner {
            override val owner = "test:broken"
            override val order = DeletionStep.DEVICE_SURFACES
            override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
                throw IllegalStateException("secret-text-must-not-leak")
        }

        val result = h.deletion(cleaners = h.embeddedCleaners() + broken).delete(doomed).ok()

        assertFalse(result.removedProfile)
        assertEquals(listOf("test:broken"), result.failedOwners)
        assertIs<Failure.Unexpected>(result.failures.single().failure)
        assertFalse("secret-text-must-not-leak" in result.toString())
        assertEquals(2, h.store.profiles.value.size)
    }

    private fun recorder(name: String, order: Int, events: MutableList<String>) = object : ProfileResourceCleaner {
        override val owner = "test:$name"
        override val order = order
        override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> = 0.ok().also { events += name }
    }
}
