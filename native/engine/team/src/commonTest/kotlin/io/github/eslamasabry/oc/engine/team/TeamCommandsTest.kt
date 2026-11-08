package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TeamCommandsTest {
    private fun health(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "profileId" to "p",
            "engineVersion" to "1.2.0",
            "capabilities" to mapOf("execution" to true, "boundary" to true, "oc2" to true, "oc2Verified" to true),
            "commandActions" to listOf("createProject", "createQuickTask", "deleteProject", "approvePlan", "pauseTask", "stopTask"),
        ).apply { putAll(pairs) }

    @Test
    fun `B-TEAM-020 health reads as Dart's, refusing another profile or a bad tier`() {
        val ok = TeamHealth.parse(health("boundaryTier" to "proot"), "p")!!
        assertTrue(ok.canExecute)
        assertEquals("proot", ok.boundaryTier)
        assertTrue(ok.capabilities.lifecycle)
        assertTrue(ok.capabilities.lanes)
        assertFalse(ok.capabilities.promotion)
        assertNull(TeamHealth.parse(health(), "other"))
        assertNull(TeamHealth.parse(health("boundaryTier" to "chroot"), "p"))
        assertNull(TeamHealth.parse(health("schemaVersion" to 2), "p"))
        val unverified = TeamHealth.parse(health("capabilities" to mapOf("execution" to true, "boundary" to true, "oc2" to true)), "p")!!
        assertFalse(unverified.canExecute)
        assertFalse(unverified.capabilities.lanes)
    }

    @Test
    fun `B-TEAM-021 a command the engine cannot run is refused with its code and sends nothing`() = runTest {
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(workspace(3, "a")))))
        host.health = FakeTeamHost.READY.copy(boundary = false)
        val engine = TeamEngine(host) { "r" }
        engine.refresh("p")
        val project = engine.state("p").value.workspace!!.project("a")!!
        val result = engine.execute("p", TeamCommand.project(project, TeamAction.RESUME_PROJECT))
        assertEquals("boundaryUnverified", (result as Outcome.Ok).value.code)
        assertEquals("boundaryUnverified", engine.state("p").value.refusal)
        assertEquals(TeamAction.RESUME_PROJECT, engine.state("p").value.refusedAction)
        assertTrue(host.sent.isEmpty())
        assertFalse(engine.state("p").value.capabilities.resume)
    }

    @Test
    fun `B-TEAM-022 the demo is never gated`() = runTest {
        val demo = TeamWorkspace(revision = 1, simulated = true, projects = listOf(TeamProject(id = "a")))
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(demo))))
        host.health = FakeTeamHost.READY.copy(commandActions = emptySet(), execution = false)
        val engine = TeamEngine(host) { "r" }
        engine.refresh("p")
        assertTrue(engine.state("p").value.capabilities.lanes)
        engine.execute("p", TeamCommand.project(demo.projects[0], TeamAction.PAUSE_PROJECT))
        assertEquals(1, host.sent.size)
    }

    @Test
    fun `B-TEAM-023 a new project's body carries the typed spec, settings and repos as Dart writes them`() {
        val command =
            TeamCommand(
                action = TeamAction.CREATE_PROJECT,
                projectId = "",
                expectedRevision = null,
                name = "Sync",
                text = "Make sync work",
                settings = TeamProjectSettings(mode = "single", budget = TeamBudget(chosen = true, daily = 5.0, total = 20.0)),
                spec = TeamSpec(goal = "Make sync work", contextFiles = listOf("README.md")),
                repos = listOf(TeamRepo(id = "r1", name = "app", serverId = "phone", path = "/home/app")),
                serverId = "phone",
            )
        val body = command.body("req-1")
        // It goes out as JSON: every value encodes.
        assertTrue(DartJson.encode(body).startsWith("{\"findingIds\":[]"))
        assertEquals("createProject", body["action"])
        assertEquals("Sync", body["name"])
        assertEquals("phone", body["serverId"])
        val settings = body["settings"] as Map<*, *>
        assertEquals("single", settings["mode"])
        assertEquals(5.0, (settings["budget"] as Map<*, *>)["daily"])
        val spec = body["spec"] as Map<*, *>
        assertEquals(listOf("README.md"), spec["contextFiles"])
        assertEquals(1, spec["version"])
        val repo = (body["repos"] as List<*>).single() as Map<*, *>
        assertEquals("/home/app", repo["path"])
        assertEquals("fixture-base", repo["devCommit"])
        assertNull(body["tasks"])
        assertEquals(
            listOf(
                "findingIds", "requestId", "action", "projectId", "expectedRevision", "targetId", "text", "name", "serverId",
                "roleId", "expectedDevCommit", "expectedMainCommit", "confirmed", "settings", "spec", "repos", "tasks",
                "phases", "role", "server",
            ),
            body.keys.toList(),
        )
    }

    @Test
    fun `B-TEAM-024 promote pins both commits and merge names the repo`() {
        val repo = TeamRepo(id = "r", devCommit = "def5678", mainCommit = "abc1234")
        val p = TeamProject(id = "a", revision = 4, repos = listOf(repo))
        val promote = TeamCommand.promote(p, repo).body("x")
        assertEquals("def5678", promote["expectedDevCommit"])
        assertEquals("abc1234", promote["expectedMainCommit"])
        assertEquals(true, promote["confirmed"])
        val merge = TeamCommand.mergeToDev(p, repo).body("x")
        assertEquals("processMergeQueue", merge["action"])
        assertEquals("r", merge["targetId"])
        assertEquals(4L, merge["expectedRevision"])
    }

    @Test
    fun `B-TEAM-025 editor drafts are one map per profile, kept per target`() = runTest {
        val host = FakeTeamHost()
        val engine = TeamEngine(host)
        engine.saveDraft("p", "team-editor.create.", "{\"a\":1}")
        engine.saveDraft("p", "team-editor.quick.", "q")
        assertEquals("{\"a\":1}", engine.draft("p", "team-editor.create."))
        engine.saveDraft("p", "team-editor.create.", null)
        assertNull(engine.draft("p", "team-editor.create."))
        assertEquals("q", engine.draft("p", "team-editor.quick."))
        engine.saveDraft("p", "team-editor.quick.", null)
        assertNull(host.drafts)
    }

    @Test
    fun `B-TEAM-026 the workspace keeps what the editors and merge pages need`() {
        val text =
            """
            {"schemaVersion":1,"revision":2,"simulated":false,"servers":[{"id":"s","memoryMb":512}],
             "roles":[{"id":"worker","name":"Worker","instructions":"Write code"}],
             "defaultSettings":{"mode":"parallel","maxLanes":3},
             "projects":[{"id":"a","spendDay":"2026-11-08","timelineTruncated":true,
               "repos":[{"id":"r","devCommit":"d","mainCommit":"m","sharedRemote":true}],
               "specDraft":{"goal":"g","constraints":"c","decisions":"d","outOfScope":"o","contextFiles":["x"],"approvedBy":"person"},
               "tasks":[{"id":"t","diff":"diff --git a b","affected":true}],
               "requests":[{"id":"q","answer":"yes"}],
               "receipts":[{"id":"rc","kind":"merge","repoId":"r","before":"m","after":"d","at":"2026-11-08T00:00:00Z"}]}]}
            """.trimIndent()
        val w = TeamWorkspaceJson.decode(text)!!
        assertEquals(512.0, w.servers[0].memoryMb)
        assertEquals("Write code", w.roles[0].instructions)
        assertEquals(3, w.defaultSettings!!.maxLanes)
        val p = w.projects[0]
        assertEquals("d", p.repos[0].devCommit)
        assertTrue(p.repos[0].sharedRemote)
        assertEquals("c", p.specDraft.constraints)
        assertEquals(listOf("x"), p.specDraft.contextFiles)
        assertEquals("diff --git a b", p.tasks[0].diff)
        assertEquals("yes", p.requests[0].answer)
        assertEquals("merge", p.receipts[0].kind)
        assertTrue(p.timelineTruncated)
    }
}
