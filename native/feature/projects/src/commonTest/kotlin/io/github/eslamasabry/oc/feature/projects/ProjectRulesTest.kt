package io.github.eslamasabry.oc.feature.projects

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.WorkspaceProject
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitle
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitles
import io.github.eslamasabry.oc.feature.projects.presentation.PathProblem
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

internal fun session(id: String, dir: String?, at: Long, parent: String? = null, title: String? = "Chat $id") =
    Session(
        SessionId(id),
        title,
        parentId = parent?.let(::SessionId),
        directory = dir,
        time = SessionTime(createdMs = at, updatedMs = at),
    )

class ProjectRulesTest {
    private fun project(id: String, dir: String, updated: Long) =
        WorkspaceProject(id, "", dir, persistentListOf(), updated, isGit = true)

    @Test
    fun `B-NAV-011 projects are ordered by update time with stable ties and unknown dates last`() {
        val ordered =
            ProjectRules.order(
                listOf(
                    project("unknown", "/w/u", 0),
                    project("tieA", "/w/a", 50),
                    project("new", "/w/n", 90),
                    project("tieB", "/w/b", 50),
                )
            )
        assertEquals(listOf("new", "tieA", "tieB", "unknown"), ordered.map { it.id })
    }

    @Test
    fun `B-NAV-011 a project with no name is named by its folder, and home or temp folders are never offered`() {
        val summaries =
            ProjectRules.fromServer(
                listOf(project("p", "/work/api/", 10), project("home", "/root", 20), project("tmp", "/tmp/x", 30)),
                listOf(session("s1", "/work/api", 5), session("s2", "/work/api", 6, parent = "s1")),
                setOf(SessionId("s1")),
            )
        assertEquals(listOf("api"), summaries.map { it.name })
        assertEquals(1, summaries.single().conversations) // the subagent stays with its parent
        assertEquals(1, summaries.single().running)
        assertEquals("/work/api", summaries.single().directory)
    }

    @Test
    fun `B-NAV-011 servers without a project list show the folders their conversations work in, newest first`() {
        val summaries =
            ProjectRules.fromSessions(
                listOf(
                    session("a", "/work/web", 10),
                    session("b", "/work/api", 30),
                    session("c", "/work/web", 20),
                    session("d", "/tmp", 99),
                    session("e", null, 98),
                ),
                emptySet(),
            )
        assertEquals(listOf("/work/api", "/work/web"), summaries.map { it.directory })
        assertEquals(listOf(1, 2), summaries.map { it.conversations })
    }

    @Test
    fun `B-NAV-010 conversations elsewhere lists other projects only, running first, then by recency`() {
        val sessions =
            listOf(
                session("here-busy", "/work/app", 99),
                session("idle-new", "/work/site", 50),
                session("busy-old", "/work/FinanceHub3", 1),
                session("idle-old", "/work/site", 10),
                session("child", "/work/site", 80, parent = "idle-new"),
            )
        val found = ProjectRules.elsewhere(sessions, setOf(SessionId("busy-old"), SessionId("here-busy")), "/work/app")
        assertEquals(listOf("busy-old", "idle-new", "idle-old"), found.map { it.id.raw })
    }

    @Test
    fun `the project page lists only this folder's conversations, newest first, trailing slash or not`() {
        val sessions = listOf(session("a", "/work/app/", 1), session("b", "/work/app", 5), session("c", "/work/x", 9))
        assertEquals(listOf("b", "a"), ProjectRules.inProject(sessions, "/work/app").map { it.id.raw })
    }

    @Test
    fun `B-ANDR-078 opened folders keep the newest first, each once, and Opened before shows the first six`() {
        var list = emptyList<String>()
        listOf("/w/a", "/w/b", "/w/a/", "/w/c").forEach { list = ProjectRules.remembered(list, it) }
        assertEquals(listOf("/w/c", "/w/a", "/w/b"), list)
        val many = (1..30).fold(emptyList<String>()) { acc, i -> ProjectRules.remembered(acc, "/w/p$i") }
        assertEquals(ProjectRules.REMEMBERED, many.size)
        assertEquals(6, ProjectRules.openedBefore(many + "/root").size)
        assertEquals(listOf("/w/ok"), ProjectRules.openedBefore(listOf("/w/ok", "/root", "relative")))
    }

    @Test
    fun `B-STATE-157 a typed folder must be a full path, never the root, a home, dot-dot or a line break`() {
        assertNull(ProjectRules.pathProblem("/root/projects/my-app"))
        assertNull(ProjectRules.pathProblem("  /home/me/code/site/ "))
        assertNull(ProjectRules.pathProblem("C:/Users/me/code"))
        assertEquals(PathProblem.NotAbsolute, ProjectRules.pathProblem("my-app"))
        assertEquals(PathProblem.NotAbsolute, ProjectRules.pathProblem("   "))
        assertEquals(PathProblem.BadParts, ProjectRules.pathProblem("/work/../etc"))
        assertEquals(PathProblem.BadParts, ProjectRules.pathProblem("/work/a\nb"))
        assertEquals(PathProblem.HomeOrRoot, ProjectRules.pathProblem("/"))
        assertEquals(PathProblem.HomeOrRoot, ProjectRules.pathProblem("/root"))
        assertEquals(PathProblem.HomeOrRoot, ProjectRules.pathProblem("/home/me/"))
    }

    @Test
    fun `B-NAV-067 conversation titles lose leaked markup and dated placeholders read as new`() {
        assertEquals(ChatTitle.Text("Fix login"), ChatTitles.present("Fix login <tool_call>{\"x\":1}"))
        assertEquals(ChatTitle.New, ChatTitles.present("New session - 2026-09-02T14:47:06Z"))
        assertEquals(ChatTitle.Untitled, ChatTitles.present("  "))
    }

    @Test
    fun `B-NAV-011 projects with no date, zero or below, keep the server's order after the dated ones`() {
        val ordered =
            ProjectRules.order(
                listOf(project("negative", "/w/n", -1), project("zero", "/w/z", 0), project("dated", "/w/d", 5))
            )
        assertEquals(listOf("dated", "negative", "zero"), ordered.map { it.id })
    }

    @Test
    fun `B-NAV-067 half-cut markers and embedded stamps leave project conversation titles like every other list`() {
        assertEquals(ChatTitle.Text("prime the merge queue."), ChatTitles.present("prime the merge queue.<tool_ca…"))
        assertEquals(ChatTitle.Text("prime the merge queue."), ChatTitles.present("prime the merge queue. <fu..."))
        assertEquals(ChatTitle.Text("Fix login"), ChatTitles.present("Fix login 2026-09-28T10:00:00.000Z"))
        assertEquals(ChatTitle.Untitled, ChatTitles.present("<tool_call><fu…"))
        assertEquals(ChatTitle.Text("Use <f"), ChatTitles.present("Use <f"))
    }

    @Test
    fun `B-AGENT-264 the AI Team's folders are not projects, a person's project the team works on is`() {
        val summaries =
            ProjectRules.fromServer(
                listOf(
                    project("mine", "/root/projects/my-app", 10),
                    project("home", "/root/aiteam", 20),
                    project("gc", "/srv/city/.gc/worktrees/my-app/mayor", 30),
                    project("termux", "/data/data/com.termux/files/home/.oc/aiteam", 40),
                ),
                emptyList(),
                emptySet(),
            )
        assertEquals(listOf("/root/projects/my-app"), summaries.map { it.directory })
        val fromChats =
            ProjectRules.fromSessions(
                listOf(session("a", "/root/aiteam/city/rig", 9), session("b", "/root/projects/my-app", 1)),
                emptySet(),
            )
        assertEquals(listOf("/root/projects/my-app"), fromChats.map { it.directory })
        val elsewhere =
            ProjectRules.elsewhere(
                listOf(session("t", "/root/aiteam/city", 9), session("p", "/work/site", 1)),
                emptySet(),
                "/work/app",
            )
        assertEquals(listOf("p"), elsewhere.map { it.id.raw })
    }
}
