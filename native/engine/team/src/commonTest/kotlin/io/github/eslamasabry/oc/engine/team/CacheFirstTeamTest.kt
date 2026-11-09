package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class CacheFirstTeamTest {
    private class Shelf(var value: KeptTeam? = null) : TeamLastKnown {
        override fun peek(profileId: String) = value
        override suspend fun read(profileId: String) = value
        override suspend fun keep(profileId: String, kept: KeptTeam) {
            value = Json.decodeFromString(KeptTeam.serializer(), Json.encodeToString(KeptTeam.serializer(), kept))
        }
    }

    private val task = TeamTask(id = "t1", title = "Login", phaseId = "ph", status = "running", diff = "SECRET PATCH")
    private fun full() =
        TeamWorkspace(
            revision = 3,
            simulated = false,
            projects =
                listOf(
                    TeamProject(
                        id = "a", name = "App", status = "building", simulated = false,
                        phases = listOf(TeamProjectPhase(id = "ph", title = "One")),
                        tasks = listOf(task),
                    )
                ),
        )

    @Test
    fun `CF-46 the first frame with a kept workspace has the live frame's projects, lanes and no patch`() = runTest {
        val shelf = Shelf()
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(full()))))
        val live = TeamEngine(host, kept = shelf)
        live.refresh("p")
        val liveState = live.state("p").value

        val reopened = TeamEngine(FakeTeamHost(), kept = shelf).state("p").value // before any read
        assertEquals(TeamPhase.Ready, reopened.phase)
        assertFalse(reopened.stale)
        val a = reopened.workspace!!.projects
        assertEquals(liveState.workspace!!.projects.map { it.id to it.name }, a.map { it.id to it.name })
        assertEquals(listOf("t1" to "Login"), a.single().tasks.map { it.id to it.title })
        assertEquals("", a.single().tasks.single().diff)
        assertEquals(listOf("ph"), a.single().phases.map { it.id })
    }

    @Test
    fun `CF-47 a failed read keeps the kept projects, marked stale, and a good read replaces them in place`() = runTest {
        val shelf = Shelf(KeptTeam.of(full()))
        assertNotNull(shelf.value)
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Failed(Failure.Unexpected()), Outcome.Ok(workspace(1, "a", "b")))))
        val engine = TeamEngine(host, kept = shelf)
        engine.refresh("p")
        val failed = engine.state("p").value
        assertEquals(TeamPhase.Ready, failed.phase)
        assertTrue(failed.stale)
        assertEquals(listOf("a"), failed.workspace!!.projects.map { it.id })
        // Live data with an older revision than the kept one still replaces it: the kept page is only a first frame.
        engine.refresh("p")
        val live = engine.state("p").value
        assertFalse(live.stale)
        assertEquals(listOf("a", "b"), live.workspace!!.projects.map { it.id })
    }

    @Test
    fun `CF-48 with nothing kept the first-ever load is Loading`() = runTest {
        assertEquals(TeamPhase.Loading, TeamEngine(FakeTeamHost(), kept = Shelf()).state("p").value.phase)
    }
}
