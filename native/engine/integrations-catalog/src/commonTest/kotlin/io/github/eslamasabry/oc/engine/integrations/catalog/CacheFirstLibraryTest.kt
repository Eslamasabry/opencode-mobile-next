package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.ReferenceInfo
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/** Cache-first (owner rule, 2026-11-09), the Tools library: kept lists on the first frame, replaced in place. */
class CacheFirstLibraryTest {
    private val profile = ProfileId("p")
    private val command = CommandInfo("review", "Review the diff", null, false)

    private class Shelf(var value: KeptLibrary? = null) : LibraryLastKnown {
        override fun peek(profile: ProfileId, place: String) = value

        override suspend fun keep(profile: ProfileId, place: String, kept: KeptLibrary) {
            value = Json.decodeFromString(KeptLibrary.serializer(), Json.encodeToString(KeptLibrary.serializer(), kept))
        }
    }

    private fun source(commands: () -> Outcome<ImmutableList<CommandInfo>>) =
        object : LibrarySource {
            override val profile = this@CacheFirstLibraryTest.profile
            override val origin = "https://srv"
            override val location = ServerLocation.Default
            // Commands only: the library keeps a read when every section it asked for answered, and the fake
            // operations gateway answers no catalog, skills, references, providers or MCP lists.
            override val capabilities = ServerCapabilities(slashCommands = true, serverCatalog = false)
            override val server: ServerGateway = FakeServerGateway()
            override val operations: ServerOperationsGateway =
                object : FakeOperationsGateway() {
                    override suspend fun commands() = commands()
                }

            override fun current() = true
        }

    private val noRecovery =
        object : LibraryRecoveryStore {
            override suspend fun read(): Outcome<String?> = Outcome.Ok(null)

            override suspend fun write(json: String): Outcome<Unit> = Outcome.Ok(Unit)
        }

    private fun TestScope.session(src: LibrarySource, shelf: LibraryLastKnown?) =
        LibrarySession({ src }, noRecovery, backgroundScope, Clock.System, shelf)

    @Test
    fun `CF-93 the first frame of the library with kept lists has the live commands, and still counts as loading`() = runTest {
        val shelf = Shelf()
        val live = session(source { Outcome.Ok(persistentListOf(command)) }, shelf)
        live.refresh()
        advanceUntilIdle()
        // The keep runs in the session's background scope, which advanceUntilIdle leaves alone; runCurrent runs it.
        runCurrent()
        val reopened = session(source { Outcome.Ok(persistentListOf(command)) }, shelf).state.value
        assertEquals(live.state.value.commands, reopened.commands)
        assertTrue(reopened.loading)
        assertTrue(reopened.fromKept)
        assertFalse(reopened.coldLoading)
    }

    @Test
    fun `CF-94 a failed read keeps the kept commands`() = runTest {
        val shelf = Shelf(KeptLibrary(commands = listOf(KeptLibrary.KeptCommand("review", "Review the diff", null, false))))
        val s = session(source { Outcome.Failed(Failure.Network(NetworkWhy.Reset)) }, shelf)
        s.refresh()
        assertEquals(listOf("review"), s.state.value.commands.map { it.name })
        assertTrue("commands" in s.state.value.failedSections)
    }

    @Test
    fun `CF-95 with nothing kept the first-ever load is a cold skeleton load`() = runTest {
        val first = session(source { Outcome.Ok(persistentListOf()) }, Shelf()).state.value
        assertTrue(first.coldLoading)
    }

    @Test
    fun `CF-96 the kept library holds names and labels, never connection secrets or an oversized skill`() {
        val inventory =
            LibraryInventory(
                skills = persistentListOf(SkillInfo("s", "big", null, "/x", "x".repeat(KeptLibrary.MAX_SKILL_BYTES + 1), false)),
                references = persistentListOf(ReferenceInfo("doc", "/doc", null)),
                servers = persistentListOf(McpServerInfo("git", "connected", "e".repeat(1000))),
            )
        val kept = KeptLibrary.of(inventory)
        assertTrue(kept.skills.isEmpty())
        // B-292: a server's MCP error prose can echo a secret and no screen shows it, so it is never kept.
        assertNull(kept.servers.single().error)
        val text = Json.encodeToString(KeptLibrary.serializer(), kept)
        assertFalse(Regex("(?i)\"(token|password|secret|apikey)\"").containsMatchIn(text))
    }
}
