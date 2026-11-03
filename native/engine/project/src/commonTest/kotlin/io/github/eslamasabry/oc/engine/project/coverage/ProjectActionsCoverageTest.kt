package io.github.eslamasabry.oc.engine.project.coverage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.engine.project.gitops.FAKE_GITHUB_TOKEN
import io.github.eslamasabry.oc.engine.project.gitops.FakeCredentialDrop
import io.github.eslamasabry.oc.engine.project.gitops.FakeOpsShell
import io.github.eslamasabry.oc.engine.project.gitops.GitRemoteOps
import io.github.eslamasabry.oc.engine.project.gitops.PrivateSecretDrop
import io.github.eslamasabry.oc.engine.project.gitops.SecretDropFiles
import io.github.eslamasabry.oc.engine.project.gitops.envelope
import io.github.eslamasabry.oc.engine.project.gitops.gitHubCredentialOptions
import io.github.eslamasabry.oc.engine.project.gitops.gitShCommand
import io.github.eslamasabry.oc.engine.project.gitops.ok as stepOk
import io.github.eslamasabry.oc.engine.project.gitops.pushPlan
import io.github.eslamasabry.oc.engine.project.repair.FakeZlib
import io.github.eslamasabry.oc.engine.project.repair.GitCheck
import io.github.eslamasabry.oc.engine.project.repair.GitLinkRepair
import io.github.eslamasabry.oc.engine.project.repair.GitRepairTermux
import io.github.eslamasabry.oc.engine.project.repair.MemoryFs
import io.github.eslamasabry.oc.engine.project.repair.RepairFileSystem
import io.github.eslamasabry.oc.engine.project.repair.Sha1
import io.github.eslamasabry.oc.engine.project.repair.TermuxGitFiles
import io.github.eslamasabry.oc.engine.project.repair.fakeDeflate
import io.github.eslamasabry.oc.engine.project.tasks.IsolatedTaskLaunch
import io.github.eslamasabry.oc.engine.project.tasks.IsolatedTaskPhase
import io.github.eslamasabry.oc.engine.project.tasks.WorktreeEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The step lines (`ocg_s <name> <argv>`) a script runs, and the folder it runs in. */
private fun steps(script: String): List<String> = script.lines().filter { it.startsWith("ocg_s ") && !it.startsWith("ocg_s()") }.map { it.substringBefore(" || {") }

private fun folder(script: String): String = script.lines().first { it.startsWith("cd -- ") }.removePrefix("cd -- ").substringBefore(" 2>/dev/null")

class ProjectActionsCoverageTest {
    @Test
    fun `B-BUILD-004 every mutating project action has a recorded decision`() {
        val ledger = ProjectActionLedger.actions
        assertEquals(ledger.size, ledger.map { it.id }.toSet().size, "ids are unique")
        val packages = setOf("scan", "repair", "gitops", "tasks", "worktree", "devserver", "reports", "coverage")
        for (action in ledger) {
            assertTrue(action.id.substringBefore('.') in packages, action.id)
            assertTrue(action.note.length > 30, "${action.id}: say the control and the call, or where/why")
            when (action.decision) {
                ActionDecision.Reachable -> assertTrue(action.proof?.startsWith("B-") == true, "${action.id}: a reachable action names its proof")
                ActionDecision.CoveredElsewhere, ActionDecision.NotOffered -> assertNull(action.proof, action.id)
            }
        }
        // The engine's mutating commands, each in the ledger.
        val commands =
            setOf(
                "gitops.addRemote", "gitops.clone", "gitops.push", "gitops.secretDrop", "gitops.signOutRejectedToken",
                "repair.restoreLinks", "repair.fetchFromTermux", "tasks.createWorktree", "tasks.openSession",
            )
        assertTrue(ledger.filter { it.decision == ActionDecision.Reachable }.map { it.id }.toSet().containsAll(commands))
    }

    @Test
    fun `B-BUILD-005 add remote sends exactly git remote add in the project folder`() = runTest {
        val shell = FakeOpsShell.of(envelope(mapOf("run" to stepOk())))
        GitRemoteOps(shell, "/root/projects/app").addRemote("backup", "https://example.invalid/o/r.git")
        assertEquals("'/root/projects/app'", folder(shell.scripts.single()))
        assertEquals(
            listOf("ocg_s run " + gitShCommand(listOf("git", "--no-pager", "remote", "add", "backup", "https://example.invalid/o/r.git"))),
            steps(shell.scripts.single()),
        )
    }

    @Test
    fun `B-BUILD-005 clone sends exactly git clone in the parent folder`() = runTest {
        val shell = FakeOpsShell.of(envelope(mapOf("clone" to stepOk())))
        GitRemoteOps(shell, "/root/projects/app").clone("https://example.invalid/o/r.git", "r")
        assertEquals("'/root/projects'", folder(shell.scripts.single()))
        assertEquals(
            listOf("ocg_s clone " + gitShCommand(listOf("git", "--no-pager", "clone", "--", "https://example.invalid/o/r.git", "r"))),
            steps(shell.scripts.single()),
        )
    }

    @Test
    fun `B-BUILD-005 push with a token sends exactly the credential options and the push`() = runTest {
        val shell = FakeOpsShell.of(pushPlan(), envelope(mapOf("push" to stepOk())))
        val drop = FakeCredentialDrop()
        GitRemoteOps(shell, "/root/projects/app", drop).push(FAKE_GITHUB_TOKEN)
        assertEquals(
            listOf(
                "ocg_s head 'git' '--no-pager' 'symbolic-ref' '--short' '-q' 'HEAD'",
                "ocg_s upstream 'git' '--no-pager' 'rev-parse' '--abbrev-ref' '--symbolic-full-name' '@{u}'",
                "ocg_s remotes 'git' '--no-pager' 'remote'",
            ),
            steps(shell.scripts[0]),
        )
        assertEquals(
            listOf("ocg_s push " + gitShCommand(listOf("git", "--no-pager") + gitHubCredentialOptions() + "push")),
            steps(shell.scripts[1]),
        )
        assertTrue("OC_TOKEN_FILE='${drop.placed.single()}'; export OC_TOKEN_FILE" in shell.scripts[1])
    }

    @Test
    fun `B-BUILD-005 the secret drop makes the folder and file private before the token is written`() = runTest {
        val calls = ArrayList<String>()
        val modes = HashMap<String, Int>()
        val sizes = HashMap<String, Long>()
        val files =
            object : SecretDropFiles {
                override suspend fun rootfs(): String = "/rootfs"

                override suspend fun createFolders(path: String): Boolean = true.also { calls += "mkdir $path" }

                override suspend fun chmod(path: String, mode: String): Boolean = true.also { calls += "chmod $mode ${path.substringAfterLast('/').take(2)}"; modes[path] = mode.toInt(8) }

                override suspend fun mode(path: String): Int? = modes[path]

                override suspend fun write(path: String, bytes: ByteArray): Boolean = true.also {
                    calls += "write ${if (bytes.isEmpty()) "empty" else if (bytes.all { it == 0.toByte() }) "zeros" else "secret"}"
                    sizes[path] = bytes.size.toLong()
                }

                override suspend fun size(path: String): Long? = sizes[path]

                override suspend fun delete(path: String): Boolean = true.also { calls += "delete"; sizes.remove(path) }
            }
        val drop = PrivateSecretDrop(files, Random(3))
        val file = (drop.place(FAKE_GITHUB_TOKEN) as Outcome.Ok).value
        drop.remove(file)
        assertEquals(
            listOf("mkdir /rootfs/root/.oc-git", "chmod 700 .o", "write empty", "chmod 600 c-", "write secret", "write zeros", "delete"),
            calls,
        )
    }

    @Test
    fun `B-BUILD-005 repair replaces each link through a temporary file beside it`() = runTest {
        val recorded = ArrayList<String>()
        val fs = RecordingFs(MemoryFs(), recorded)
        val raw = "blob 1\u0000x".encodeToByteArray()
        val id = Sha1.hex(raw)
        val obj = "/p/app/.git/objects/${id.take(2)}/${id.drop(2)}"
        fs.inner.file("/p/app/.git/HEAD", "x".encodeToByteArray())
        fs.inner.file("/p/app/.git/objects/${id.take(2)}/.l2s.a0001", fakeDeflate(raw))
        fs.inner.link(obj, "/old/.l2s.a0001")
        val outcome = GitLinkRepair("/p", "/stage", fs, FakeZlib, NoTermux, { GitCheck(true) }, { "job" }).repair("/root/projects/app")
        assertEquals(1, outcome.restored)
        val tmp = "/p/app/.git/objects/${id.take(2)}/.oc-repair-${id.drop(2)}.tmp"
        assertEquals(listOf("copy /p/app/.git/objects/${id.take(2)}/.l2s.a0001 $tmp", "delete $obj", "rename $tmp $obj"), recorded)
    }

    @Test
    fun `B-BUILD-005 repair asks Termux only for store paths and removes its staging folder`() = runTest {
        val store = "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/u/rootfs/.l2s/.l2s.b0001"
        val recorded = ArrayList<String>()
        val fs = RecordingFs(MemoryFs(), recorded)
        val raw = "blob 1\u0000y".encodeToByteArray()
        val id = Sha1.hex(raw)
        fs.inner.file("/p/app/.git/HEAD", "x".encodeToByteArray())
        fs.inner.link("/p/app/.git/objects/${id.take(2)}/${id.drop(2)}", store)
        fs.inner.link("/p/app/.git/objects/${id.take(2)}/${"0".repeat(38)}", "/data/data/com.termux/files/home/.ssh/id")
        val asked = ArrayList<Pair<List<String>, String>>()
        val termux =
            object : TermuxGitFiles {
                override suspend fun available(): GitRepairTermux? = null

                override suspend fun fetch(paths: List<String>, into: String, progress: (Int) -> Unit): Outcome<Map<Int, String>> {
                    asked += paths to into
                    fs.inner.file("$into/0", fakeDeflate(raw))
                    return mapOf(0 to "$into/0").ok()
                }
            }
        GitLinkRepair("/p", "/stage", fs, FakeZlib, termux, { GitCheck(true) }, { "job-7" }).repair("/root/projects/app")
        assertEquals(listOf(listOf(store) to "/stage/job-7"), asked)
        assertTrue("deleteTree /stage/job-7" in recorded)
    }

    @Test
    fun `B-BUILD-005 an isolated task creates once and opens exactly the created directory`() = runTest {
        var creates = 0
        val opened = ArrayList<String>()
        val events = MutableSharedFlow<WorktreeEvent>(extraBufferCapacity = 4)
        val launch =
            IsolatedTaskLaunch(
                backgroundScope,
                "p1",
                events,
                create = { creates++; WorktreeInfo("w", "/data/worktree/p1/w", null, false).ok() },
                open = { dir -> opened += dir; Session(SessionId("s"), directory = dir).ok() },
            )
        launch.start()
        launch.start()
        runCurrent()
        events.emit(WorktreeEvent(ready = true, directory = "/data/worktree/p1/w/", project = "p1"))
        runCurrent()
        launch.open()
        assertEquals(1, creates)
        assertEquals(listOf("/data/worktree/p1/w"), opened)
        assertEquals(IsolatedTaskPhase.Opened, launch.state.value.phase)
        assertNotNull(launch.state.value.session)
    }
}

private object NoTermux : TermuxGitFiles {
    override suspend fun available(): GitRepairTermux? = null

    override suspend fun fetch(paths: List<String>, into: String, progress: (Int) -> Unit): Outcome<Map<Int, String>> = emptyMap<Int, String>().ok()
}

/** Records every change the repair makes; reads pass through. */
private class RecordingFs(val inner: MemoryFs, private val log: MutableList<String>) :
    RepairFileSystem by inner {
    override fun copy(from: String, to: String): Boolean = inner.copy(from, to).also { log += "copy $from $to" }

    override fun rename(from: String, to: String): Boolean = inner.rename(from, to).also { log += "rename $from $to" }

    override fun delete(path: String): Boolean = inner.delete(path).also { log += "delete $path" }

    override fun deleteTree(path: String): Boolean = inner.deleteTree(path).also { log += "deleteTree $path" }
}
