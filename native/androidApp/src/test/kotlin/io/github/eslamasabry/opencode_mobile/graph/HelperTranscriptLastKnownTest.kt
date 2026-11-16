package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.cache.FileLastKnown
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.profiles.DeferredFeatureData
import io.github.eslamasabry.oc.engine.profiles.PhoneServerProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileDeletion
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.transcript.KeptTranscript
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.detachedTranscript
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path

class HelperTranscriptLastKnownTest {
    private val rootDir = File(System.getProperty("java.io.tmpdir"), "helper-lk-test-${Random.nextLong().toULong()}")
    private val phoneId = ProfileId("phone")
    private val helperId = ProfileId("phone-agents")
    private val remoteId = ProfileId("laptop")

    @AfterTest
    fun cleanUp() {
        rootDir.deleteRecursively()
    }

    private class MemorySecrets : SecretStore {
        val map = LinkedHashMap<String, String>()

        override suspend fun read(name: String): Outcome<String?> = map[name].ok()

        override suspend fun write(name: String, value: String): Outcome<Unit> {
            map[name] = value
            return Unit.ok()
        }

        override suspend fun delete(name: String): Outcome<Unit> {
            map.remove(name)
            return Unit.ok()
        }

        override suspend fun names(prefix: String): Outcome<Set<String>> =
            map.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }.ok()
    }

    private inner class Fixture(scope: TestScope, val countByProfile: MutableMap<ProfileId, Int> = HashMap()) {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val clock = TestClock()
        val backend = InMemoryPrefsBackend()
        val prefs =
            PrefsKeyValueStore(backend, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), dispatcher)
        val secrets = MemorySecrets()
        val lanes = DefaultWriterLanes()
        val redactor = Redactor()
        val profileScope =
            DefaultProfileScope(prefs, secrets, lanes.app).also {
                ProfileKeys.register(it)
                DeferredFeatureData.register(it)
            }
        val profiles =
            ProfileStore(
                prefs,
                secrets,
                lanes,
                redactor,
                phoneServer = { PhoneServerProfile.managedOnPhone(it, 4098) },
            )
        var lastKnown = FileLastKnown(Path(rootDir.absolutePath), dispatcher, clock)

        fun transcriptStore(): TranscriptLastKnownStore =
            TranscriptLastKnownStore(lastKnown, profiles, prefs) { loaded ->
                countByProfile[loaded.id] ?: 0
            }

        suspend fun seedProfiles() {
            prefs.warm()
            profiles.load()
            profiles.upsert(
                StoredProfile(phoneId, "This phone", "http://127.0.0.1:4098", backend = BackendKind.OpenCode2),
                SecretEdit.Set("pw-phone"),
            )
            profiles.upsert(
                StoredProfile(
                    helperId,
                    "This phone",
                    "ws://127.0.0.1:4099",
                    backend = BackendKind.Paseo,
                    codexDirectory = "/root/projects",
                ),
                SecretEdit.Set("pw-helper"),
            )
            profiles.upsert(
                StoredProfile(remoteId, "Laptop", "https://laptop.example", backend = BackendKind.OpenCode2),
                SecretEdit.Set("pw-remote"),
            )
            prefs.commit(lanes.app) {
                putString("oc.builtinServerOwner", phoneId.raw)
                putString("oc.phoneAgentOwner.${helperId.raw}", phoneId.raw)
            }
        }

        fun restartStore(): TranscriptLastKnownStore {
            lastKnown = FileLastKnown(Path(rootDir.absolutePath), dispatcher, clock)
            return transcriptStore()
        }
    }

    private fun sampleMessages(sessionId: String, label: String): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("u-$sessionId", sessionId, Role.User, providerId = "claude-code", time = MsgTime(10, 11)),
                listOf(Part("pu-$sessionId", "u-$sessionId", sessionId, PartType.Text, "text", text = "Prompt $label"))
                    .toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo("a-$sessionId", sessionId, Role.Assistant, providerId = "claude-code", time = MsgTime(12, 13), finish = "stop"),
                listOf(Part("pa-$sessionId", "a-$sessionId", sessionId, PartType.Text, "text", text = "Reply $label"))
                    .toImmutableList(),
            ),
        )

    @Test
    fun `HTLK-001 90 phone-agent conversations each opened once all open with their kept turns without eviction`() =
        runTest {
            val f = Fixture(this, mutableMapOf(helperId to 90))
            f.seedProfiles()
            val store = f.transcriptStore()

            for (i in 1..90) {
                val sid = "ag-$i"
                store.keep(ConversationRef(helperId, SessionId(sid)), "Conv $i", sampleMessages(sid, "#$i"))
            }

            // Cold restart (new FileLastKnown reading disk): every one of the 90 must still be there.
            val fresh = f.restartStore()
            for (i in 1..90) {
                val sid = "ag-$i"
                val kept = fresh.read(ConversationRef(helperId, SessionId(sid)))
                assertNotNull(kept, "conversation $sid should not be evicted below the sized cap")
                assertEquals("Conv $i", kept.title)
                assertEquals("Reply #$i", kept.history.last().parts.single().text)
            }
        }

    @Test
    fun `HTLK-002 a helper with thousands of conversations enforces the hard cap so disk is bounded`() = runTest {
        val f = Fixture(this, mutableMapOf(helperId to 2_500))
        f.seedProfiles()
        val store = f.transcriptStore()
        val total = AppChatLastKnown.MAX_HELPER_ENTRIES + 20

        for (i in 1..total) {
            val sid = "ag-$i"
            store.keep(ConversationRef(helperId, SessionId(sid)), "Conv $i", sampleMessages(sid, "#$i"))
        }

        val fresh = f.restartStore()
        var present = 0
        for (i in 1..total) {
            if (fresh.read(ConversationRef(helperId, SessionId("ag-$i"))) != null) present++
        }
        assertEquals(AppChatLastKnown.MAX_HELPER_ENTRIES, present, "hard cap limits kept entries on disk")
        assertNull(fresh.read(ConversationRef(helperId, SessionId("ag-1"))), "oldest beyond cap is evicted")
        assertNotNull(fresh.read(ConversationRef(helperId, SessionId("ag-$total"))), "newest is kept")
    }

    @Test
    fun `HTLK-003 warming newest Home rows fills memory so opening them is a memory peek`() = runTest {
        val f = Fixture(this, mutableMapOf(helperId to 20))
        f.seedProfiles()
        val store = f.transcriptStore()
        for (i in 1..15) {
            val sid = "ag-$i"
            store.keep(ConversationRef(helperId, SessionId(sid)), "Conv $i", sampleMessages(sid, "#$i"))
        }

        // Cold restart: nothing in memory yet.
        val fresh = f.restartStore()
        val newest = ConversationRef(helperId, SessionId("ag-15"))
        val oldest = ConversationRef(helperId, SessionId("ag-1"))
        assertNull(fresh.peek(newest), "memory is empty before warming")

        val items =
            (15 downTo 1).map { i ->
                ChatFeedItem(
                    sourceId = helperId.raw,
                    profileId = helperId,
                    sessionId = SessionId("ag-$i"),
                    title = "Conv $i",
                    directory = "/root/projects",
                    projectName = "projects",
                    status = ChatStatus.Idle,
                    lastActivityMs = i.toLong() * 1_000L,
                )
            }
        val top =
            HomeTranscriptWarmer.topHomeRefs(
                ChatFeedSnapshot(items.toImmutableList()),
                f.profiles.profiles.value,
                isShown = { true },
                readInventory = { emptyList<Session>() },
            )
        assertEquals(HomeTranscriptWarmer.WARM_TOP_HOME_ROWS, top.size)

        val port =
            object : ChatLastKnown {
                override fun peek(ref: ConversationRef): KeptTranscript? = fresh.peek(ref)

                override suspend fun read(ref: ConversationRef): KeptTranscript? = fresh.read(ref)

                override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) =
                    fresh.keep(ref, title, messages)

                override fun detached(ref: ConversationRef): TranscriptHandle =
                    detachedTranscript(ref.sessionId.raw, f.dispatchers, f.clock, backgroundScope)
            }

        HomeTranscriptWarmer.warmTopHomeRefs(top, port, HashSet(), Mutex()) { true }

        // Top rows are now a synchronous memory peek; rows outside the top window were not read yet.
        assertNotNull(fresh.peek(newest), "top Home row is warm in memory")
        assertNotNull(fresh.peek(ConversationRef(helperId, SessionId("ag-6"))), "10th Home row is warm in memory")
        assertNull(fresh.peek(oldest), "older row beyond top window stays unread until opened")
    }

    @Test
    fun `HTLK-004 profile deletion and removing the phone agents sweep the helper profile kept pages`() = runTest {
        val f = Fixture(this, mutableMapOf(helperId to 10))
        f.seedProfiles()
        val store = f.transcriptStore()

        val phoneRef = ConversationRef(phoneId, SessionId("p-1"))
        val helperRef = ConversationRef(helperId, SessionId("ag-1"))
        val remoteRef = ConversationRef(remoteId, SessionId("r-1"))
        store.keep(phoneRef, "Phone", sampleMessages("p-1", "phone"))
        store.keep(helperRef, "Agent", sampleMessages("ag-1", "agent"))
        store.keep(remoteRef, "Remote", sampleMessages("r-1", "remote"))

        // 1. Profile deletion of "This phone" sweeps both the phone profile and its helper profile.
        val cleaner =
            object : ProfileResourceCleaner {
                override val owner = ":core:data (last-known server data)"
                override val order = 0

                override suspend fun clear(
                    profileId: ProfileId,
                    options: io.github.eslamasabry.oc.engine.profiles.DeletionOptions,
                ): Outcome<Int> = clearProfileAndHelpers(f.lastKnown, f.profiles, f.prefs, profileId)
            }
        val deleted = ProfileDeletion(f.profiles, f.profileScope, f.lanes, cleaners = setOf(cleaner)).delete(phoneId)
        assertIs<Outcome.Ok<*>>(deleted)
        assertNull(store.peek(phoneRef))
        assertNull(store.peek(helperRef))
        val afterDelete = f.restartStore()
        assertNull(afterDelete.read(phoneRef), "phone kept pages deleted")
        assertNull(afterDelete.read(helperRef), "helper kept pages deleted with phone profile")
        assertNotNull(afterDelete.read(remoteRef), "unrelated remote server keeps its pages")

        // 2. Removing the phone agents sweeps the helper's shelf while keeping other servers intact.
        f.seedProfiles()
        val store2 = f.transcriptStore()
        store2.keep(helperRef, "Agent", sampleMessages("ag-1", "agent2"))
        store2.keep(remoteRef, "Remote", sampleMessages("r-1", "remote"))
        assertIs<Outcome.Ok<Int>>(sweepPhoneAgentLastKnown(f.lastKnown, f.profiles, f.prefs, owner = phoneId))
        assertNull(store2.peek(helperRef))
        val afterAgentSweep = f.restartStore()
        assertNull(afterAgentSweep.read(helperRef), "helper kept pages swept when agents removed")
        assertNotNull(afterAgentSweep.read(remoteRef), "remote kept pages remain untouched")
    }
}
