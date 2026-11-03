package io.github.eslamasabry.oc.engine.project.gitops

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ubuntu's files on the app's side, in memory, with permission bits; records every write. */
private class MemoryDropFiles(
    private val root: String? = "/rootfs",
    private val chmodAllowed: (String, String) -> Boolean = { _, _ -> true },
) : SecretDropFiles {
    val files = LinkedHashMap<String, ByteArray>()
    val folders = HashSet<String>()
    val modes = HashMap<String, Int>()
    val writes = ArrayList<Pair<String, ByteArray>>()

    override suspend fun rootfs(): String? = root

    override suspend fun createFolders(path: String): Boolean {
        folders += path
        modes.getOrPut(path) { 0x1ed }
        return true
    }

    override suspend fun chmod(path: String, mode: String): Boolean {
        if (!chmodAllowed(path, mode)) return false
        modes[path] = mode.toInt(8)
        return true
    }

    override suspend fun mode(path: String): Int? = modes[path]

    override suspend fun write(path: String, bytes: ByteArray): Boolean {
        if (path !in files) modes[path] = 0x1a4 // 0644, like a fresh file under a default umask
        files[path] = bytes
        writes += path to bytes
        return true
    }

    override suspend fun size(path: String): Long? = files[path]?.size?.toLong()

    override suspend fun delete(path: String): Boolean {
        modes.remove(path)
        return files.remove(path) != null
    }
}

class GitSecretDropTest {
    @Test
    fun `B-SEC-081 the token file is 0600 in a 0700 folder and removed after`() = runTest {
        val disk = MemoryDropFiles()
        val drop = PrivateSecretDrop(disk, Random(1))
        val file = drop.place(FAKE_GITHUB_TOKEN).getOrNull()!!
        assertTrue(file.shellPath.startsWith("/root/.oc-git/c-"))
        assertEquals("/rootfs${file.shellPath}", file.hostPath)
        assertEquals(0x180, disk.modes[file.hostPath])
        assertEquals(0x1c0, disk.modes["/rootfs/root/.oc-git"])
        assertEquals(FAKE_GITHUB_TOKEN, disk.files[file.hostPath]!!.decodeToString())
        // The token was written only after the file was private: the first write is empty.
        assertEquals(0, disk.writes.first().second.size)

        drop.remove(file)
        assertNull(disk.files[file.hostPath])
        // The token was overwritten with zeros before the delete.
        assertTrue(disk.writes.last().second.all { it == 0.toByte() })
        // Twice, and after the script's own trap removed it: no error.
        drop.remove(file)
    }

    @Test
    fun `B-SEC-081 each command gets a new random name`() = runTest {
        val drop = PrivateSecretDrop(MemoryDropFiles(), Random(7))
        val a = drop.place(FAKE_GITHUB_TOKEN).getOrNull()!!
        val b = drop.place(FAKE_GITHUB_TOKEN).getOrNull()!!
        assertNotEquals(a.shellPath, b.shellPath)
        assertEquals(32, a.shellPath.removePrefix("/root/.oc-git/c-").length)
    }

    @Test
    fun `B-SEC-081 when the file cannot be made private nothing is left behind`() = runTest {
        val disk = MemoryDropFiles(chmodAllowed = { _, mode -> mode == "700" })
        assertIs<Outcome.Failed>(PrivateSecretDrop(disk, Random(1)).place(FAKE_GITHUB_TOKEN))
        assertTrue(disk.files.isEmpty())
        assertFalse(disk.writes.any { it.second.decodeToString() == FAKE_GITHUB_TOKEN })
    }

    @Test
    fun `B-SEC-081 no Ubuntu installed refuses`() = runTest {
        assertIs<Outcome.Failed>(PrivateSecretDrop(MemoryDropFiles(root = null), Random(1)).place(FAKE_GITHUB_TOKEN))
    }

    private suspend fun pushWith(push: StepAnswer, drop: FakeCredentialDrop = FakeCredentialDrop()): Triple<GitOpsResult, FakeOpsShell, FakeCredentialDrop> {
        val shell = FakeOpsShell.of(pushPlan(), envelope(mapOf("push" to push)))
        val result = GitRemoteOps(shell, "/root/projects/app", drop).push(gitHubToken = FAKE_GITHUB_TOKEN)
        return Triple(result, shell, drop)
    }

    @Test
    fun `B-SEC-081 the token is only in the 0600 file, never in a script`() = runTest {
        val (result, shell, drop) = pushWith(ok())
        assertEquals(GitOpsNote.Pushed, result.note)
        assertEquals(listOf(FAKE_GITHUB_TOKEN), drop.secrets)
        for (script in shell.scripts) {
            assertFalse(FAKE_GITHUB_TOKEN in script)
            assertFalse("FAKEtoken" in script)
        }
        val push = shell.scripts[1]
        val file = drop.placed.single()
        assertTrue("OC_TOKEN_FILE='$file'; export OC_TOKEN_FILE" in push)
        assertTrue("rm -f -- '\\''$file'\\''" in push)
        assertTrue("'-c' 'credential.helper=' '-c' 'credential.helper=!f()" in push)
        assertTrue("'-c' 'url.https://github.com/.insteadOf=git@github.com:'" in push)
        assertTrue(push.indexOf("'credential.helper='") < push.indexOf("'credential.helper=!f()"))
        assertTrue("GIT_TERMINAL_PROMPT" in push)
        // The plan (no network) never names the file.
        assertFalse(file in shell.scripts[0])
        assertTrue(drop.allRemoved)
    }

    @Test
    fun `B-SEC-081 the file is removed when the push fails`() = runTest {
        val (result, _, drop) = pushWith(failed(HTTPS_AUTH_ERROR, 128))
        assertEquals(GitOpsProblem.GitHubTokenRejected, result.problem)
        assertFalse(FAKE_GITHUB_TOKEN in result.failure!!.details)
        assertTrue(drop.allRemoved)
    }

    @Test
    fun `B-SEC-081 a token echoed by git is masked in Details`() = runTest {
        val (result, _, _) = pushWith(failed("fatal: unable to access 'https://x:$FAKE_GITHUB_TOKEN@example.invalid/': 403\n", 128))
        assertFalse(FAKE_GITHUB_TOKEN in result.failure!!.details)
    }

    @Test
    fun `B-SEC-081 the file is removed when the bridge fails`() = runTest {
        val drop = FakeCredentialDrop()
        val shell = FakeOpsShell(listOf(GitOpsReply.Output(pushPlan()), GitOpsReply.Failed(GitOpsShellFailure.Unavailable)))
        val result = GitRemoteOps(shell, "/root/projects/app", drop).push(gitHubToken = FAKE_GITHUB_TOKEN)
        assertEquals(GitOpsProblem.EnvironmentUnavailable, result.problem)
        assertEquals(1, drop.placed.size)
        assertTrue(drop.allRemoved)
    }

    @Test
    fun `B-SEC-081 a file that cannot be made private means no push`() = runTest {
        val shell = FakeOpsShell(emptyList())
        val result = GitRemoteOps(shell, "/root/projects/app", FakeCredentialDrop(failPlace = true)).push(gitHubToken = FAKE_GITHUB_TOKEN)
        assertEquals(GitOpsProblem.EnvironmentUnavailable, result.problem)
        assertTrue(shell.scripts.isEmpty())
    }

    @Test
    fun `B-SEC-081 no drop (Termux, servers) means the token is never used`() = runTest {
        val shell = FakeOpsShell.of(pushPlan(), envelope(mapOf("push" to failed(HTTPS_AUTH_ERROR, 128))))
        val result = GitRemoteOps(shell, "/root/projects/app").push(gitHubToken = FAKE_GITHUB_TOKEN)
        assertEquals(GitOpsProblem.AuthRequired, result.problem)
        assertFalse("credential.helper" in shell.scripts.joinToString())
        assertFalse(FAKE_GITHUB_TOKEN in shell.scripts.joinToString())
    }

    @Test
    fun `B-SEC-081 the helper names only the variable`() {
        assertTrue("\$OC_TOKEN_FILE" in GIT_HUB_CREDENTIAL_HELPER)
        assertTrue("github.com" in GIT_HUB_CREDENTIAL_HELPER)
    }
}
