package io.github.eslamasabry.oc.engine.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitParsersTest {
    private val nul = "\u0000"
    private val us = "\u001f"

    @Test
    fun `B-BUILD-015 status parses unicode renames with spaces, edits and untracked files`() {
        val out =
            "# branch.oid 1234abcd${nul}# branch.head main${nul}# branch.upstream origin/main${nul}# branch.ab +2 -1${nul}" +
                "1 .M N... 100644 100644 100644 aaa bbb src/app.kt${nul}" +
                "2 R. N... 100644 100644 100644 aaa bbb R100 docs/مرحبا new.md${nul}docs/old name.md${nul}" +
                "? notes/to do.txt${nul}"
        val status = parseGitStatus(out)
        assertEquals("main", status.branch.head)
        assertEquals("origin/main", status.branch.upstream)
        assertEquals(2, status.branch.ahead)
        assertEquals(1, status.branch.behind)
        assertEquals(3, status.files.size)
        assertEquals(GitChangeKind.Modified, status.files[0].unstaged)
        assertNull(status.files[0].staged)
        val renamed = status.files[1]
        assertEquals("docs/مرحبا new.md", renamed.path)
        assertEquals("docs/old name.md", renamed.originalPath)
        assertEquals(GitChangeKind.Renamed, renamed.staged)
        assertTrue(status.files[2].isUntracked)
        assertEquals("notes/to do.txt", status.files[2].path)
    }

    @Test
    fun `B-BUILD-015 conflicts, detached HEAD, no upstream and unborn branch are typed states`() {
        val detached = parseGitStatus("# branch.oid abc${nul}# branch.head (detached)${nul}")
        assertTrue(detached.branch.detached)
        assertFalse(detached.branch.hasUpstream)
        val unborn = parseGitStatus("# branch.oid (initial)${nul}# branch.head main${nul}")
        assertTrue(unborn.branch.unborn)
        val codes =
            mapOf(
                "UU" to GitConflictKind.BothModified,
                "AA" to GitConflictKind.BothAdded,
                "DD" to GitConflictKind.BothDeleted,
                "AU" to GitConflictKind.AddedByUs,
                "UA" to GitConflictKind.AddedByThem,
                "DU" to GitConflictKind.DeletedByUs,
                "UD" to GitConflictKind.DeletedByThem,
            )
        for ((xy, kind) in codes) {
            val s = parseGitStatus("# branch.head main${nul}u $xy N... 100644 100644 100644 100644 a b c clash file.kt${nul}")
            assertEquals(kind, s.files.single().conflict, xy)
            assertEquals("clash file.kt", s.files.single().path)
            assertTrue(s.hasConflicts)
        }
    }

    @Test
    fun `B-BUILD-015 numstat joins status rows including binary files and renames`() {
        val status =
            parseGitStatus(
                "# branch.head main${nul}1 MM N... 100644 100644 100644 a b app.kt${nul}" +
                    "1 .M N... 100644 100644 100644 a b logo.png${nul}" +
                    "2 R. N... 100644 100644 100644 a b R90 new.kt${nul}old.kt${nul}",
            )
        val work = parseGitNumstat("3\t1\tapp.kt${nul}-\t-\tlogo.png${nul}")
        val index = parseGitNumstat("2\t0\tapp.kt${nul}4\t2\t${nul}old.kt${nul}new.kt${nul}")
        assertTrue(work[1].binary)
        assertEquals("new.kt", index[1].path)
        assertEquals("old.kt", index[1].originalPath)
        val joined = withGitNumstat(status, unstaged = work, staged = index)
        assertEquals(5, joined.files[0].totalAdded)
        assertEquals(1, joined.files[0].totalDeleted)
        assertTrue(joined.files[1].binary)
        assertEquals(4, joined.files[2].stagedAddedLines)
    }

    @Test
    fun `B-BUILD-016 log, branches, operation markers and remotes parse`() {
        val log =
            parseGitLog(
                "abc123${us}abc${us}Ada${us}ada\u0040example.invalid${us}1700000000${us}p1 p2${us}HEAD -> main, origin/main${us}Merge it$nul" +
                    "\ndef456${us}def${us}Bob${us}b\u0040example.invalid${us}1700000100${us}${us}${us}First$nul",
            )
        assertEquals(2, log.size)
        assertTrue(log[0].isMerge)
        assertEquals(listOf("HEAD -> main", "origin/main"), log[0].refs.toList())
        assertEquals("First", log[1].subject)
        assertEquals(1_700_000_100L, log[1].timeEpochSeconds)

        val branches =
            parseGitBranches(
                listOf(
                    "refs/heads/main${us}abc${us}origin/main${us}ahead 2, behind 1${us}*${us}1700000000${us}Tip",
                    "refs/heads/old${us}def${us}origin/old${us}gone${us} ${us}1700000000${us}Old",
                    "refs/remotes/origin/HEAD${us}abc${us}${us}${us} ${us}1700000000${us}Tip",
                    "refs/remotes/origin/main${us}abc${us}${us}${us} ${us}1700000000${us}Tip",
                    "refs/remotes/origin/feature/x${us}fff${us}${us}${us} ${us}1700000000${us}Feature",
                ).joinToString("\n"),
            )
        assertEquals("main", branches.current?.name)
        assertEquals(2, branches.current?.ahead)
        assertEquals(1, branches.current?.behind)
        assertTrue(branches.local[1].upstreamGone)
        assertEquals(listOf("origin/main", "origin/feature/x"), branches.remote.map { it.name })
        assertEquals(listOf("feature/x"), branches.remoteOnly.map { it.localName })

        assertEquals(GitOperation.Rebase, parseGitOperation("rebase-merge\n"))
        assertEquals(GitOperation.Merge, parseGitOperation("MERGE_HEAD\n"))
        assertEquals(GitOperation.CherryPick, parseGitOperation("CHERRY_PICK_HEAD\n"))
        assertEquals(GitOperation.None, parseGitOperation(""))

        val remotes =
            parseGitRemotes(
                "remote.origin.url https://user:tok3n-secret\u0040github.com/o/r.git\nremote.fork.url git\u0040github.com:me/r.git\n" +
                    "remote.lab.url https://gitlab.example/o/r.git\n",
            )
        assertEquals(GitRemoteKind.GitHubHttps, remotes[0].kind)
        assertEquals("https://github.com/o/r.git", remotes[0].address)
        assertEquals(GitRemoteKind.GitHubSsh, remotes[1].kind)
        assertEquals(GitRemoteKind.Other, remotes[2].kind)
    }

    @Test
    fun `B-BUILD-016 stderr is classified into typed states`() {
        assertEquals(GitProblem.CannotFastForward, classifyGitError("fatal: Not possible to fast-forward, aborting."))
        assertEquals(GitProblem.PushRejected, classifyGitError(" ! [rejected]        main -> main (fetch first)"))
        assertEquals(GitProblem.NoUpstream, classifyGitError("There is no tracking information for the current branch."))
        assertEquals(GitProblem.GitHubTokenRejected, classifyGitError("fatal: Authentication failed for 'https://github.com/o/r'", usedGitHubToken = true))
        assertEquals(GitProblem.AuthRequired, classifyGitError("fatal: could not read Username for 'https://github.com': terminal prompts disabled"))
        assertEquals(GitProblem.IdentityMissing, classifyGitError("*** Please tell me who you are."))
        assertEquals(GitProblem.NothingToCommit, classifyGitError("nothing to commit, working tree clean"))
        assertEquals(GitProblem.NotARepository, classifyGitError("fatal: not a git repository (or any of the parent directories): .git"))
        assertEquals(GitProblem.Network, classifyGitError("fatal: unable to access 'https://x/': Could not resolve host: x"))
        assertNull(classifyGitError("something else entirely"))
    }

    @Test
    fun `B-BUILD-016 scrubbing masks tokens and URL user info`() {
        val text = "fatal: https://x-access-token:ghp_abcdef123456\u0040github.com/o/r failed with ghp_abcdef123456"
        val scrubbed = scrubGitText(text, listOf("ghp_abcdef123456"))
        assertFalse("ghp_abcdef123456" in scrubbed)
        assertTrue("://•••@github.com" in scrubbed)
    }

    @Test
    fun `B-BUILD-021 branch names are validated before any shell runs`() {
        listOf("main", "feature/x", "fix-1.2", "user/مرحبا").forEach { assertTrue(isValidGitBranchName(it), it) }
        listOf("", "-x", "/x", "x/", "x.", "a..b", "a//b", "a@{b", "a b", "a~b", "a^b", "a:b", "a?b", "a*b", "a[b", ".x", "x.lock", "@")
            .forEach { assertFalse(isValidGitBranchName(it), it) }
    }
}
