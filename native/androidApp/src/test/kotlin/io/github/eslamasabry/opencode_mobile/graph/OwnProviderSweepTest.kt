package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.scope.OwnedData
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentStore
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderStore
import io.github.eslamasabry.oc.engine.agents.store.AgentSecrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** Issue 147: deleting the connection that owns the phone's agents removes its own-provider key and settings. */
class OwnProviderSweepTest {
    private class Secrets : SecretStore, AgentSecrets {
        val values = linkedMapOf<String, String>()

        override suspend fun read(name: String): Outcome<String?> = values[name].ok()

        override suspend fun write(name: String, value: String): Outcome<Unit> = Unit.ok().also { values[name] = value }

        override suspend fun delete(name: String): Outcome<Unit> = Unit.ok().also { values.remove(name) }

        override suspend fun names(prefix: String): Outcome<Set<String>> =
            values.keys.filter { it.startsWith(prefix) }.toSet().ok()
    }

    @Test
    fun `B-AGENT-OWN-020 the profile deletion finds and removes the own-provider key and its endpoint, and nothing else`() =
        runTest {
            val kv =
                PrefsKeyValueStore(
                    InMemoryPrefsBackend(),
                    FlutterPrefsCodec(JavaListDecoder.Unsupported),
                    StateMigrationGate.alwaysOpen(),
                    StandardTestDispatcher(testScheduler),
                )
            val lanes = DefaultWriterLanes()
            val secrets = Secrets()
            val scope = DefaultProfileScope(kv, secrets, lanes.app)
            OwnProviderSweep.register(scope)
            val gone = ProfileId("1735000000000001")
            val kept = ProfileId("1735000000000002")
            for (owner in listOf(gone, kept)) {
                val store = OwnProviderStore(KvAgentPrefs(kv, lanes.forProfile(owner)), secrets) {}
                assertTrue(store.save(owner, AgentId.CLAUDE, "https://llm.example.test", "fake-key-${owner.raw}", null) is Outcome.Ok)
            }

            val owned = assertIs<Outcome.Ok<OwnedData>>(scope.ownedData(gone))
            assertEquals(setOf("oc.agentProviderKeys.${gone.raw}"), owned.value.secretNames)
            assertTrue("oc.agentOwnProviders.${gone.raw}" in owned.value.prefKeys)

            assertTrue(scope.sweep(gone) is Outcome.Ok)
            assertNull(kv.get("oc.agentOwnProviders.${gone.raw}"))
            // The deletion removes the secure-store names ownedData listed (ProfileDeletion); the other owner keeps its own.
            owned.value.secretNames.forEach { secrets.delete(it) }
            assertEquals(setOf("oc.agentProviderKeys.${kept.raw}"), secrets.values.keys)
            assertTrue(kv.get("oc.agentOwnProviders.${kept.raw}") != null)
        }

    @Test
    fun `B-AGENT-OWN-021 the profile deletion finds and removes the own agents and their values`() = runTest {
        val kv =
            PrefsKeyValueStore(
                InMemoryPrefsBackend(),
                FlutterPrefsCodec(JavaListDecoder.Unsupported),
                StateMigrationGate.alwaysOpen(),
                StandardTestDispatcher(testScheduler),
            )
        val lanes = DefaultWriterLanes()
        val secrets = Secrets()
        val scope = DefaultProfileScope(kv, secrets, lanes.app)
        OwnAgentSweep.register(scope)
        val gone = ProfileId("1735000000000001")
        val store = OwnAgentStore(KvAgentPrefs(kv, lanes.forProfile(gone)), secrets) {}
        assertTrue(store.save(gone, null, "Mine", "mine acp", "", listOf("MY_KEY" to "fake-key-000000")) is Outcome.Ok)
        val owned = assertIs<Outcome.Ok<OwnedData>>(scope.ownedData(gone))
        assertEquals(setOf("oc.ownAgentEnv.${gone.raw}"), owned.value.secretNames)
        assertTrue("oc.ownAgents.${gone.raw}" in owned.value.prefKeys)
        assertTrue(scope.sweep(gone) is Outcome.Ok)
        assertNull(kv.get("oc.ownAgents.${gone.raw}"))
    }
}
