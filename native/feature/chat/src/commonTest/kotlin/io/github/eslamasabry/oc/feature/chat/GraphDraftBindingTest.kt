package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorld
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GraphDraftBindingTest {
    private val prefs = MemoryComposerPrefs()
    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String): Boolean = profileId in setOf("p1", "p2")

            override fun count(): Int = 2
        }

    private fun TestScope.fixture(): Pair<GraphChatWorlds, ConversationDrafts> {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val drafts = Drafts(prefs, AttachmentVault(MemoryPayloadStore()), profiles, TestClock())
        val owner = ConversationDrafts(drafts, app, dispatchers)
        val registry =
            ConnectionRegistry(
                emptySet(),
                { _, _, _ -> error("no server is opened by a draft test") },
                app,
                dispatchers,
            )
        return GraphChatWorlds(registry, setOf(owner)) to owner
    }

    private fun ref(profile: String = "p1") = ConversationRef(ProfileId(profile), SessionId("same"))

    @Test
    fun `B-STATE-100 world drafts autosave and restart restore exact text before editor admission`() =
        runTest {
            val (worlds, _) = fixture()
            val world = worlds.open(ref())
            assertFalse(world.draft.value.ready)
            world.draftChanged("")
            runCurrent()
            world.draftChanged("  exact text\n ")
            advanceTimeBy(600)
            runCurrent()
            assertEquals("  exact text\n ", world.draft.value.saved)
            worlds.release(world)
            val restarted = fixture().first.open(ref())
            restarted.draftChanged("")
            runCurrent()
            assertTrue(restarted.draft.value.ready)
            assertEquals("  exact text\n ", restarted.draft.value.text)
        }

    @Test
    fun `B-STATE-101 refused world save keeps text and exposes failure rather than acknowledging it`() =
        runTest {
            val world = fixture().first.open(ref())
            runCurrent()
            world.draftChanged("keep on failure")
            prefs.refuse = true
            assertIs<Outcome.Failed>(world.saveDraft())
            runCurrent()
            assertEquals("keep on failure", world.draft.value.text)
            assertNotNull(world.draft.value.failure)
            assertNotEquals(world.draft.value.text, world.draft.value.saved)
        }

    @Test
    fun `B-STATE-102 identical text after later edits advances the world revision and targets remain isolated`() =
        runTest {
            val (worlds, _) = fixture()
            val first = worlds.open(ref())
            val second = worlds.open(ref("p2"))
            runCurrent()
            first.draftChanged("original")
            val revision = first.draft.value.editRevision
            first.draftChanged("changed")
            first.draftChanged("original")
            assertTrue(first.draft.value.editRevision > revision)
            assertEquals("", second.draft.value.text)
            assertFalse(first.draft.value.toString().contains("original"))
        }

    @Test
    fun `B-STATE-102 profile closure fences a displayed world before late editor writes`() =
        runTest {
            val (worlds, owner) = fixture()
            val world = worlds.open(ref())
            runCurrent()
            world.draftChanged("must disappear on profile removal")
            assertIs<Outcome.Ok<Unit>>(world.saveDraft())
            owner.closeProfile(ProfileId("p1"))
            world.draftChanged("late resurrection")
            runCurrent()
            assertFalse(world.draft.value.ready)
            assertNotNull(world.draft.value.failure)
            val restored = fixture().first.open(ref())
            runCurrent()
            assertEquals("", restored.draft.value.text)
        }

    @Test
    fun `B-STATE-100 a retained world rebinds when its closed empty draft handle has retired`() =
        runTest {
            val owner = fixture().second
            val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
            val registry =
                ConnectionRegistry(
                    emptySet(),
                    { _, _, _ -> error("draft-only test") },
                    AppCoroutineScope(backgroundScope.coroutineContext),
                    dispatchers,
                )
            // A pending accepted bubble can keep the world alive after its empty draft session
            // retires.
            val world = GraphChatWorld(ref(), registry, owner)
            world.attach()
            runCurrent()
            world.detach()
            runCurrent()
            world.attach()
            runCurrent()
            world.draftChanged("new words after reopening")
            assertIs<Outcome.Ok<Unit>>(world.saveDraft())
            runCurrent()
            assertEquals("new words after reopening", world.draft.value.text)
            assertEquals(world.draft.value.text, world.draft.value.saved)
        }
}
