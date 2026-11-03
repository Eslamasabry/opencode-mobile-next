package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.engine.settings.scope.ChatAgentChoices
import io.github.eslamasabry.oc.feature.settings.presentation.ChatAgentSelection
import io.github.eslamasabry.oc.feature.settings.presentation.StoredProfileAgentDefault
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Flutter `test/settings_session_agent_test.dart` (B-AGENT-229, amended to OpenCode 2 chats). */
class ChatAgentSelectionTest {
    private val laptop = ProfileId("laptop")
    private val phone = ProfileId("phone")

    private fun TestScope.store(disk: InMemoryPrefsBackend): KeyValueStore =
        PrefsKeyValueStore(disk, FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler))

    private fun selection(store: KeyValueStore): ChatAgentSelection {
        val lanes = DefaultWriterLanes()
        return ChatAgentSelection(ChatAgentChoices(store, lanes), StoredProfileAgentDefault(store, lanes))
    }

    @Test
    fun `B-AGENT-229 choosing a chat agent leaves other chats and the server default alone`() = runTest {
        val disk = InMemoryPrefsBackend()
        val store = store(disk)
        val agents = selection(store)
        assertIs<Outcome.Ok<*>>(agents.chooseDefault(laptop, "build"))
        assertIs<Outcome.Ok<*>>(agents.chooseDefault(phone, "build"))

        assertIs<Outcome.Ok<*>>(agents.chooseForChat(laptop, "a", "plan"))
        assertEquals("plan", agents.agentFor(laptop, "a"))
        assertEquals("build", agents.agentFor(laptop, "b"))
        assertEquals("build", store.string("oc.agent.laptop"))
        assertNotNull(store.string("oc.sessionAgents.laptop"))
        assertEquals("build", agents.agentFor(phone, "a"), "another server's chat with the same id keeps the default")
    }

    @Test
    fun `B-AGENT-229 a chat agent restores after a restart and stays with its server`() = runTest {
        val disk = InMemoryPrefsBackend()
        val first = selection(store(disk))
        first.chooseDefault(laptop, "build")
        first.chooseDefault(phone, "build")
        first.chooseForChat(laptop, "a", "plan")

        val restarted = selection(store(InMemoryPrefsBackend(disk.persisted())))
        assertEquals("plan", restarted.agentFor(laptop, "a"))
        assertEquals("build", restarted.agentFor(laptop, "b"))
        assertEquals("build", restarted.agentFor(phone, "a"))
        // A new default moves the chats that follow it, never the one with its own choice.
        assertIs<Outcome.Ok<*>>(restarted.chooseDefault(laptop, "review"))
        assertEquals("plan", restarted.agentFor(laptop, "a"))
        assertEquals("review", restarted.agentFor(laptop, "b"))
        assertTrue(disk.persisted().keys.none { it.endsWith("sessionAgents.phone") }, "nothing stored for the other server")
    }
}
