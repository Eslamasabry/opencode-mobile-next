package io.github.eslamasabry.oc.engine.project.gitops

import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitRemotesTest {
    @Test
    fun `B-BUILD-014 the remote list keeps each address without user info`() {
        val remotes =
            parseGitRemotes(
                "remote.origin.url https://example.invalid/o/r.git\n" +
                    "remote.fork.url git@example.invalid:me/r.git\n" +
                    "remote.tok.url https://user:ghp_SECRET@example.invalid/o/r.git\n",
            )
        assertEquals("https://example.invalid/o/r.git", remotes[0].address)
        assertEquals("git@example.invalid:me/r.git", remotes[1].address)
        assertEquals("https://example.invalid/o/r.git", remotes[2].address)
        assertFalse("SECRET" in remotes.joinToString { it.address.orEmpty() })
    }

    @Test
    fun `B-BUILD-014 remote names are checked before any shell runs`() {
        assertTrue(isValidGitRemoteName("origin"))
        assertTrue(isValidGitRemoteName("my-fork.2"))
        for (bad in listOf("", "-x", "a b", "a/b")) assertFalse(isValidGitRemoteName(bad), bad)
    }

    @Test
    fun `B-BUILD-014 addresses are checked before any shell runs`() {
        for (good in listOf("https://example.invalid/o/r.git", "http://example.invalid/r", "ssh://git@host.invalid/o/r.git", "git@example.invalid:o/r.git", "/srv/git/r.git", "file:///srv/git/r.git")) {
            assertNull(gitRemoteAddressProblem(good), good)
        }
        for (bad in listOf("", "-oProxyCommand=x", "ext::sh -c touch% /tmp/x", "ext::x", "https://example.invalid/o/r with space", "ftp://example.invalid/r")) {
            assertEquals(GitOpsProblem.InvalidRemoteUrl, gitRemoteAddressProblem(bad), bad)
        }
        assertEquals(GitOpsProblem.RemoteAddressHasPassword, gitRemoteAddressProblem("https://user:ghp_x" + "@example.invalid/o/r.git"))
    }

    @Test
    fun `B-BUILD-014 clone folder names come from the address`() {
        assertEquals("my-app", suggestGitCloneFolder("https://example.invalid/o/my-app.git"))
        assertEquals("x", suggestGitCloneFolder("git@example.invalid:o/x.git"))
        assertEquals("r", suggestGitCloneFolder("https://h/o/r/"))
        assertEquals("proj", suggestGitCloneFolder("/srv/git/proj.git"))
        for (bad in listOf("my app", "..", ".hidden", "a/b")) assertFalse(isValidGitFolderName(bad), bad)
        assertTrue(isValidGitFolderName("ok_1.2-x"))
    }

    @Test
    fun `B-BUILD-014 a bad address never reaches the shell`() = runTest {
        val shell = FakeOpsShell(emptyList())
        val repo = GitRemoteOps(shell, "/w/p")
        assertEquals(GitOpsProblem.InvalidRemoteUrl, repo.addRemote("origin", "ext::x").problem)
        assertEquals(GitOpsProblem.InvalidRemoteName, repo.addRemote("a b", "https://h/r").problem)
        assertEquals(GitOpsProblem.RemoteAddressHasPassword, repo.clone("https://u:p" + "@h/r", "r").problem)
        assertEquals(GitOpsProblem.InvalidFolderName, repo.clone("https://h/r", "..").problem)
        assertTrue(shell.scripts.isEmpty())
    }

    @Test
    fun `B-BUILD-014 an existing remote and an existing folder are classified`() {
        assertEquals(GitOpsProblem.RemoteExists, classifyGitOpsError("error: remote origin already exists."))
        assertEquals(
            GitOpsProblem.FolderExists,
            classifyGitOpsError("fatal: destination path 'r' already exists and is not an empty directory."),
        )
    }

    @Test
    fun `B-BUILD-014 adding a remote shows it with its address and refuses a duplicate`() = runTest {
        val shell =
            FakeOpsShell.of(
                envelope(mapOf("run" to ok())),
                envelope(mapOf("remotes" to ok("remote.origin.url /srv/up.git\nremote.backup.url /srv/up.git\n"))),
                envelope(mapOf("run" to failed("error: remote backup already exists.\n", exit = 3))),
            )
        val repo = GitRemoteOps(shell, "/w/proj")
        val added = repo.addRemote(" backup ", " /srv/up.git ")
        assertEquals(GitOpsNote.RemoteAdded, added.note)
        assertTrue("'git' '--no-pager' 'remote' 'add' 'backup' '/srv/up.git'" in shell.scripts[0])
        assertTrue("cd -- '/w/proj'" in shell.scripts[0])
        val remotes = repo.remotes().getOrNull()!!
        assertEquals(listOf("origin", "backup"), remotes.map { it.name })
        assertEquals("/srv/up.git", remotes.first { it.name == "backup" }.address)
        assertEquals(GitOpsProblem.RemoteExists, repo.addRemote("backup", "/srv/up.git").problem)
    }

    @Test
    fun `B-BUILD-014 clone lands beside the project and a taken folder is refused`() = runTest {
        val shell =
            FakeOpsShell.of(
                envelope(mapOf("clone" to ok())),
                envelope(mapOf("clone" to failed("fatal: destination path 'copy' already exists and is not an empty directory.\n", 128))),
                envelope(mapOf("clone" to failed("fatal: repository '/srv/nowhere.git' does not exist\n", 128))),
            )
        val repo = GitRemoteOps(shell, "/srv/work/proj")
        val done = repo.clone("/srv/up.git", "copy")
        assertEquals(GitOpsNote.Cloned, done.note)
        assertEquals("/srv/work/copy", done.clonedTo)
        assertTrue("cd -- '/srv/work'" in shell.scripts[0])
        assertTrue("'git' '--no-pager' 'clone' '--' '/srv/up.git' 'copy'" in shell.scripts[0])
        assertEquals(180_000L, shell.timeouts[0])
        assertEquals(GitOpsProblem.FolderExists, repo.clone("/srv/up.git", "copy").problem)
        val missing = repo.clone("/srv/nowhere.git", "x")
        assertFalse(missing.ok)
        assertNull(missing.clonedTo)
    }

    @Test
    fun `B-BUILD-014 a cut-off answer or a missing git is a failure, not a success`() = runTest {
        val repo = GitRemoteOps(FakeOpsShell.of("@@OCG begin\n@@OCG nogit\n", "garbage"), "/w/p")
        assertEquals(GitOpsProblem.GitMissing, repo.addRemote("o", "/srv/r.git").problem)
        assertEquals(GitOpsProblem.OutputTooLarge, repo.clone("/srv/r.git", "r").problem)
    }
}
