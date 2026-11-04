package io.github.eslamasabry.oc.engine.linuxsetup

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of test/setup_finish_host_test.dart. */
class SetupFinishTest {
    private val url = "http://localhost:4097"

    private class Store(initial: List<FinishProfile> = emptyList(), override var activeId: String? = null) : FinishProfiles {
        val saved = initial.toMutableList()
        var writes = 0
        override val profiles: List<FinishProfile> get() = saved

        override suspend fun upsert(profile: FinishProfile): Boolean {
            writes++
            if (saved.none { it === profile }) saved.add(profile)
            return true
        }
    }

    private class Server(var running: Boolean = false, var problem: FinishStartProblem? = null) : FinishServer {
        val calls = mutableListOf<String>()
        var startedProfileId: String? = null

        override suspend fun status(): HostReply<SetupLinuxStatus> {
            calls.add("status")
            return HostReply.Ok(SetupLinuxStatus(installed = true, serverRunning = running))
        }

        override suspend fun stop() {
            calls.add("stop")
        }

        override suspend fun start(profile: FinishProfile, migrateLegacyHistory: Boolean): FinishStartProblem? {
            calls.add("start")
            startedProfileId = profile.id
            return problem
        }
    }

    private fun finisher(
        store: Store,
        server: Server,
        connected: Boolean = false,
        onConnect: (FinishProfile) -> Unit = {},
    ) = SetupFinisher(
        store = store, server = server,
        connect = {
            onConnect(it)
            null
        },
        isConnectedTo = { connected }, serverUrl = url, managesServerUrl = { it == url },
        newId = { "new-phone" }, newPassword = { "generated-password" },
    )

    @Test
    fun `B-LINUX-205 finish stops the running server only when OpenCode was replaced`() = runTest {
        for (changed in listOf(true, false)) {
            val server = Server(running = true, problem = FinishStartProblem.Other)
            finisher(Store(), server, connected = true).finish(SetupFinishRequest(openCodeChanged = changed))
            assertEquals(if (changed) listOf("stop", "start") else emptyList(), server.calls.filter { it != "status" })
        }
    }

    @Test
    fun `B-LINUX-205 an ownership failure keeps its stable recovery code through finish`() = runTest {
        var connected = false
        val failure =
            finisher(Store(), Server(problem = FinishStartProblem.ComponentRecovery), onConnect = { connected = true })
                .finish(SetupFinishRequest(openCodeChanged = true))
        assertEquals(SetupFinishFailure.RuntimeOwnershipUnknown, failure)
        assertFalse(connected)
    }

    @Test
    fun `B-LINUX-205 the built-in finisher refuses Termux before touching profiles or native`() = runTest {
        val store = Store()
        val server = Server()
        var connected = false
        val failure =
            finisher(store, server, onConnect = { connected = true })
                .finish(SetupFinishRequest(openCodeChanged = true, host = SetupHostKind.Termux))
        assertEquals(SetupFinishFailure.CannotStart, failure)
        assertTrue(server.calls.isEmpty())
        assertEquals(0, store.writes)
        assertTrue(store.profiles.isEmpty())
        assertFalse(connected)
    }

    @Test
    fun `B-LINUX-205 switching the app server to OpenCode 2 takes AI Team along`() = runTest {
        val older = FinishProfile(id = "phone", name = "This phone", baseUrl = url, password = "kept", orchestration = "builtin-team")
        val store = Store(listOf(older))
        finisher(store, Server(problem = FinishStartProblem.ComponentRecovery)).finish(SetupFinishRequest(openCodeChanged = true))
        val finisher = finisher(store, Server())
        assertSame(older, finisher.findProfile())
        assertEquals("builtin-team", older.orchestration)
        // An alias with the team hands it to a target without one, and keeps none itself.
        val target = FinishProfile(id = "target", name = "Phone", baseUrl = url, password = "p")
        val alias = FinishProfile(id = "alias", name = "Alias", baseUrl = url, password = "a", orchestration = "builtin-team", teamEngineAuth = "t")
        val aliasStore = Store(listOf(target, alias), activeId = "target")
        finisher(aliasStore, Server(problem = FinishStartProblem.Other)).finish(SetupFinishRequest(openCodeChanged = true))
        assertEquals("builtin-team", target.orchestration)
        assertEquals("t", target.teamEngineAuth)
        assertNull(alias.orchestration)
        assertEquals("", alias.teamEngineAuth)
    }

    @Test
    fun `B-LINUX-205 the legacy source survives an earlier OC2 alias, active or not`() = runTest {
        for (active in listOf(true, false)) {
            val alias = FinishProfile(id = "earlier-oc2", name = "Earlier OC2", baseUrl = url, password = "alias-secret")
            val original =
                FinishProfile(
                    id = "original-phone", name = "My phone", baseUrl = url, password = "original-secret",
                    requiresOpenCode2Migration = true,
                )
            val store = Store(listOf(alias, original), activeId = if (active) original.id else null)
            val server = Server()
            var connected: FinishProfile? = null
            val failure = finisher(store, server, onConnect = { connected = it }).finish(SetupFinishRequest(openCodeChanged = true))
            assertNull(failure)
            assertSame(original, connected)
            assertEquals(original.id, server.startedProfileId)
            assertFalse(original.requiresOpenCode2Migration)
            assertTrue(original.openCode2HistoryPending, "a healthy server does not prove the history import completed")
            assertEquals("original-secret", original.password)
            assertFalse(alias.openCode2HistoryPending)
            assertEquals("alias-secret", alias.password)
            assertEquals(listOf("earlier-oc2", "original-phone"), store.profiles.map { it.id })
        }
    }
}
