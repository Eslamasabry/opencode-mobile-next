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
    val sent = mutableListOf<Map<String, Any?>>()
    var answer: Outcome<TeamCommandResult> = Outcome.Ok(TeamCommandResult(accepted = true))
    var duringCommand: suspend () -> Unit = {}

    override suspend fun config(profileId: String): TeamConfig? = config

    var health: TeamHealth = READY
    var drafts: String? = null

    override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> {
        readCount++
        return when (val read = reads.removeFirstOrNull() ?: Outcome.Failed(Failure.Unexpected())) {
            is Outcome.Ok -> Outcome.Ok(TeamRead(health, read.value))
            is Outcome.Failed -> read
        }
    }

    override suspend fun readDrafts(profileId: String): String? = drafts

    override suspend fun writeDrafts(profileId: String, json: String?): Outcome<Unit> {
        drafts = json
        return Outcome.Ok(Unit)
    }

    override suspend fun command(
        profileId: String,
        config: TeamConfig,
        command: TeamCommand,
        body: Map<String, Any?>,
    ): Outcome<TeamCommandResult> {
        sent += body
        duringCommand()
        return answer
    }

    companion object {
        /** An engine that lists every command and may run work. */
        val READY =
            TeamHealth(
                engineVersion = "1.0.0",
                execution = true,
                boundary = true,
                oc2 = true,
                oc2Verified = true,
                commandActions = TeamCommand.EXECUTION_ACTIONS + TeamAction.all(),
                boundaryTier = "landlock",
            )
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

    @Test
    fun `B-TEAM-008 a command names its project and revision, then the team is read again`() = runTest {
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(4, "a")), Outcome.Ok(workspace(5, "a")))))
        val engine = TeamEngine(host) { "req-1" }
        engine.refresh("p")
        val project = engine.state("p").value.workspace!!.project("a")!!.copy(revision = 9)
        val result = engine.execute("p", TeamCommand.answer(project, "r1", "Postgres"))
        assertTrue((result as Outcome.Ok).value.accepted)
        val body = host.sent.single()
        assertEquals("answerRequest", body["action"])
        assertEquals("a", body["projectId"])
        assertEquals(9L, body["expectedRevision"])
        assertEquals("r1", body["targetId"])
        assertEquals("Postgres", body["text"])
        assertEquals("req-1", body["requestId"])
        assertEquals(5, engine.state("p").value.workspace?.revision)
        assertFalse(engine.state("p").value.busy)
    }

    @Test
    fun `B-TEAM-009 one command at a time, and a refusal's code is kept for the page`() = runTest {
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(1, "a")))))
        val engine = TeamEngine(host)
        engine.refresh("p")
        val project = engine.state("p").value.workspace!!.project("a")!!
        var second: Outcome<TeamCommandResult>? = null
        host.answer = Outcome.Ok(TeamCommandResult(accepted = false, code = "staleRevision"))
        host.duringCommand = { second = engine.execute("p", TeamCommand.project(project, "pauseProject")) }
        engine.execute("p", TeamCommand.project(project, "stopProject"))
        assertEquals("busy", (second as Outcome.Ok).value.code)
        assertEquals(1, host.sent.size)
        assertEquals("staleRevision", engine.state("p").value.refusal)
        engine.clearRefusal("p")
        assertNull(engine.state("p").value.refusal)
    }

    @Test
    fun `B-TEAM-015 commands that run work are the ones Dart gates on a verified engine`() {
        assertTrue(TeamCommand.project(TeamProject(), "resumeProject").runsWork)
        assertFalse(TeamCommand.project(TeamProject(), "pauseProject").runsWork)
        assertFalse(TeamCommand.answer(TeamProject(), "r", "x").runsWork)
    }
}
