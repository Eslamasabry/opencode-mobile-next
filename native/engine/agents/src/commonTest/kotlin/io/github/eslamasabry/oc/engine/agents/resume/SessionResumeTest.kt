package io.github.eslamasabry.oc.engine.agents.resume

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionResumeTest {
    private fun build(id: String, dir: String?, workspace: String? = null) =
        SessionResumeCommand.build(SessionResumeCli.OpenCode2, id, dir, workspace)

    @Test
    fun `B-AGENT-227 OpenCode 2 resumes with cd and --session`() {
        val command = build("ses_0123abc", "/srv/project")
        assertTrue(command.available)
        assertNull(command.unavailable)
        assertEquals("cd '/srv/project' && opencode2 --session 'ses_0123abc'", command.command)
        assertEquals("opencode2", command.binary)
    }

    @Test
    fun `B-SEC-159 directories with spaces and shell metacharacters are quoted`() {
        val command = build("ses_1", "/home/dev/My Projects/acme \$HOME `x`")
        assertEquals("cd '/home/dev/My Projects/acme \$HOME `x`' && opencode2 --session 'ses_1'", command.command)
    }

    @Test
    fun `B-SEC-159 a single quote inside the directory becomes quote-backslash-quote-quote`() {
        val command = build("ses_1", "/home/dev/it's here")
        assertEquals("cd '/home/dev/it'\\''s here' && opencode2 --session 'ses_1'", command.command)
    }

    @Test
    fun `B-AGENT-228 a worktree session resumes inside the worktree directory`() {
        val command = build("ses_wt", "/srv/project/.worktrees/feature-x", null)
        assertEquals("cd '/srv/project/.worktrees/feature-x' && opencode2 --session 'ses_wt'", command.command)
    }

    @Test
    fun `B-AGENT-228 a managed workspace session is unavailable, not guessed`() {
        val command = build("ses_ws", "/workspace/project", "wrk_abc")
        assertFalse(command.available)
        assertNull(command.command)
        assertEquals(SessionResumeUnavailable.ManagedWorkspace, command.unavailable)
    }

    @Test
    fun `B-AGENT-228 a missing or blank directory is unavailable`() {
        for (directory in listOf(null, "", "   ")) {
            val command = build("ses_1", directory)
            assertFalse(command.available, "directory=$directory")
            assertEquals(SessionResumeUnavailable.MissingDirectory, command.unavailable, "directory=$directory")
        }
    }

    @Test
    fun `B-SEC-159 control characters in the directory are refused`() {
        assertEquals(SessionResumeUnavailable.UnsafeDirectory, build("ses_1", "/srv/proj\necho pwned").unavailable)
    }

    @Test
    fun `B-SEC-160 session ids that could read as flags or shell text are refused`() {
        for (id in listOf("-s", "--auto", "ses 1", "ses'1", "", "ses\$(x)")) {
            assertEquals(SessionResumeUnavailable.InvalidSessionId, build(id, "/srv/project").unavailable, "id=$id")
        }
    }
}
