package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

internal class FakeTeamHost(
    var config: TeamConfig? = PHONE,
    val reads: ArrayDeque<Outcome<TeamWorkspace>> = ArrayDeque(),
) : TeamHost {
    var readCount = 0

    override suspend fun config(profileId: String): TeamConfig? = config

    override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamWorkspace> {
        readCount++
        return reads.removeFirstOrNull() ?: Outcome.Failed(Failure.Unexpected())
    }

    companion object {
        val PHONE = TeamConfig(TeamProvider.PhoneEngine, "http://127.0.0.1:4098", hostMode = TeamHostMode.Phone)
    }
}

internal fun workspace(revision: Long, vararg names: String) =
    TeamWorkspace(revision = revision, simulated = false, projects = names.map { TeamProject(id = it, name = it, simulated = false) })

class TeamEngineTest {
    @Test
    fun `B-TEAM-001 a profile without a team config shows the team off and reads nothing`() = runTest {
        val host = FakeTeamHost(config = null)
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertEquals(TeamPhase.Off, engine.state("p").value.phase)
        assertEquals(0, host.readCount)
    }

    @Test
    fun `B-TEAM-002 a computer team is named, not read as a phone engine`() = runTest {
        val host = FakeTeamHost(config = TeamConfig(TeamProvider.GasCity, "https://city.example:8372"))
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertEquals(TeamPhase.Computer, engine.state("p").value.phase)
        assertEquals(0, host.readCount)
    }

    @Test
    fun `B-TEAM-003 the phone team's workspace becomes the projects`() = runTest {
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(4, "a", "b")))))
        val engine = TeamEngine(host)
        engine.refresh("p")
        val state = engine.state("p").value
        assertEquals(TeamPhase.Ready, state.phase)
        assertEquals(listOf("a", "b"), state.workspace?.projects?.map { it.id })
        assertFalse(state.stale)
    }

    @Test
    fun `B-TEAM-004 an engine that is not answering with nothing read is the phone-team-off page`() = runTest {
        val engine = TeamEngine(FakeTeamHost())
        assertTrue(engine.refresh("p") is Outcome.Failed)
        val state = engine.state("p").value
        assertEquals(TeamPhase.PhoneOff, state.phase)
        assertNull(state.workspace)
    }

    @Test
    fun `B-TEAM-005 a failed read keeps the last projects and marks them last known`() = runTest {
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(4, "a")))))
        val engine = TeamEngine(host)
        engine.refresh("p")
        engine.refresh("p")
        val state = engine.state("p").value
        assertEquals(TeamPhase.Ready, state.phase)
        assertTrue(state.stale)
        assertEquals(listOf("a"), state.workspace?.projects?.map { it.id })
        host.reads.add(Outcome.Ok(workspace(5, "a")))
        engine.refresh("p")
        assertFalse(engine.state("p").value.stale)
    }

    @Test
    fun `B-TEAM-006 an older revision never replaces a newer one`() = runTest {
        val host =
            FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(7, "new")), Outcome.Ok(workspace(6, "old")))))
        val engine = TeamEngine(host)
        engine.refresh("p")
        engine.refresh("p")
        assertEquals(listOf("new"), engine.state("p").value.workspace?.projects?.map { it.id })
    }

    @Test
    fun `B-TEAM-007 profiles keep their own team`() = runTest {
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(1, "a")))))
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertEquals(TeamPhase.Loading, engine.state("q").value.phase)
    }
}
