package io.github.eslamasabry.oc.engine.project.repair

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An in-memory filesystem with real link semantics: links are kept as links and resolved hop by hop by the code. */
internal class MemoryFs : RepairFileSystem {
    sealed interface Node {
        data object Dir : Node

        class File(val bytes: ByteArray) : Node

        data class Link(val target: String) : Node
    }

    val nodes = LinkedHashMap<String, Node>()

    fun dir(path: String) {
        var p = ""
        for (part in path.split('/').drop(1)) {
            p += "/$part"
            if (nodes[p] == null) nodes[p] = Node.Dir
        }
    }

    fun file(path: String, bytes: ByteArray) {
        dir(path.substringBeforeLast('/'))
        nodes[path] = Node.File(bytes)
    }

    fun link(path: String, target: String) {
        dir(path.substringBeforeLast('/'))
        nodes[path] = Node.Link(target)
    }

    fun bytes(path: String): ByteArray? = (nodes[path] as? Node.File)?.bytes

    override fun kind(path: String): RepairEntryKind? =
        when (nodes[path]) {
            Node.Dir -> RepairEntryKind.Folder
            is Node.File -> RepairEntryKind.File
            is Node.Link -> RepairEntryKind.Link
            null -> null
        }

    override fun list(path: String): List<String>? =
        if (nodes[path] != Node.Dir) null else nodes.keys.filter { it.substringBeforeLast('/') == path }.map { it.substringAfterLast('/') }

    override fun readLink(path: String): String? = (nodes[path] as? Node.Link)?.target

    override fun readText(path: String): String? = bytes(path)?.decodeToString()

    override fun size(path: String): Long? = bytes(path)?.size?.toLong()

    override fun readBytes(path: String): ByteArray? = bytes(path)

    override fun copy(from: String, to: String): Boolean {
        val b = bytes(from) ?: return false
        file(to, b.copyOf())
        return true
    }

    override fun rename(from: String, to: String): Boolean {
        val node = nodes.remove(from) ?: return false
        nodes[to] = node
        return true
    }

    override fun delete(path: String): Boolean = nodes.remove(path) != null

    override fun deleteTree(path: String): Boolean {
        nodes.keys.filter { it == path || it.startsWith("$path/") }.forEach { nodes.remove(it) }
        return true
    }

    override fun createFolders(path: String): Boolean {
        dir(path)
        return true
    }
}

/** A stand-in zlib: "compressed" bytes are 0x78 followed by the raw bytes. */
internal val FakeZlib = ZlibInflater { b -> if (b.isNotEmpty() && b[0] == 0x78.toByte()) b.copyOfRange(1, b.size) else null }

internal fun fakeDeflate(raw: ByteArray): ByteArray = byteArrayOf(0x78) + raw

private const val STORE = "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/opencode-ubuntu/rootfs/.l2s"
private const val PROJECTS = "/app/projects"
private const val STAGING = "/app/git-repair"
private const val TERMUX_SIDE = "/termux-side"

private class FakeTermux(
    private val fs: MemoryFs,
    /** Termux path -> the bytes it holds. */
    val bytes: Map<String, ByteArray>,
    private val problem: GitRepairTermux? = null,
    private val fail: Boolean = false,
) : TermuxGitFiles {
    val asked = ArrayList<String>()

    override suspend fun available(): GitRepairTermux? = problem

    override suspend fun fetch(paths: List<String>, into: String, progress: (Int) -> Unit): Outcome<Map<Int, String>> {
        fs.createFolders(into)
        asked += paths
        if (fail) return Failure.Termux().failed()
        val out = HashMap<Int, String>()
        paths.forEachIndexed { i, p -> bytes[p]?.let { fs.file("$into/$i", it); out[i] = "$into/$i" } }
        progress(paths.size)
        return out.ok()
    }
}

class GitLinkRepairTest {
    private val fs = MemoryFs()

    /** A repository with 6 loose objects (2 commits, 2 trees, 2 blobs stand-ins); returns their relative paths. */
    private fun repository(name: String): List<String> {
        val repo = "$PROJECTS/$name"
        fs.file("$repo/.git/HEAD", "ref: refs/heads/main\n".encodeToByteArray())
        return (0 until 6).map { i ->
            val raw = "blob ${i + 4}\u0000obj$i".encodeToByteArray()
            val id = Sha1.hex(raw)
            val relative = "objects/${id.take(2)}/${id.drop(2)}"
            fs.file("$repo/.git/$relative", fakeDeflate(raw))
            relative
        }.sorted()
    }

    /** What the app's own proot leaves after the folder moved: each object a link to `.l2s.` names under the old path. */
    private fun linkLikeAMovedFolder(repo: String, oldRepo: String, objects: List<String>) {
        objects.forEachIndexed { i, rel ->
            val obj = "$repo/.git/$rel"
            val folder = obj.substringBeforeLast('/')
            val oldFolder = folder.replaceFirst(repo, oldRepo)
            val stored = "$folder/.l2s.tmp_obj_$i.0001.0001"
            fs.rename(obj, stored)
            fs.link("$folder/.l2s.tmp_obj_$i.0001", "$oldFolder/.l2s.tmp_obj_$i.0001.0001")
            fs.link(obj, "$oldFolder/.l2s.tmp_obj_$i.0001")
        }
    }

    /** A project brought from Termux's Ubuntu: each object a link into Termux's private `.l2s` store. */
    private fun linkIntoTermux(repo: String, objects: List<String>, only: Int? = null): MutableMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        objects.forEachIndexed { i, rel ->
            if (only != null && i >= only) return out
            val obj = "$repo/.git/$rel"
            val target = "$STORE/.l2s.tmp_obj_X$i.0001"
            out[target] = fs.bytes(obj)!!
            fs.delete(obj)
            fs.link(obj, target)
        }
        return out
    }

    /** Non-`.l2s.` links left under objects. */
    private fun links(repo: String): Int =
        fs.nodes.filter { (k, v) -> k.startsWith("$repo/.git/objects/") && v is MemoryFs.Node.Link && !k.substringAfterLast('/').startsWith(".l2s.") }.size

    /** Git's verdict stand-in: every object is a plain file whose inflated bytes hash to its name. */
    private fun readable(repo: String, objects: List<String>): Boolean =
        objects.all { rel -> GitObjectLinks(fs, FakeZlib).verify(rel, "$repo/.git/$rel") && fs.kind("$repo/.git/$rel") == RepairEntryKind.File }

    private val checked = ArrayList<String>()

    private fun repair(termux: TermuxGitFiles, objects: () -> List<String>) =
        GitLinkRepair(
            projects = PROJECTS,
            staging = STAGING,
            fs = fs,
            inflater = FakeZlib,
            termux = termux,
            check = { ubuntu ->
                checked += ubuntu
                val host = PROJECTS + ubuntu.removePrefix("/root/projects")
                GitCheck(ok = readable(host, objects()), output = "")
            },
            jobName = { "job-1" },
        )

    @Test
    fun `B-LINUX-072 a project whose folder moved is repaired from its own files`() = runTest {
        val objects = repository("app")
        linkLikeAMovedFolder("$PROJECTS/app", "$PROJECTS/app-before", objects)
        assertFalse(readable("$PROJECTS/app", objects))
        val stages = HashSet<GitRepairStage>()
        val outcome = repair(FakeTermux(fs, emptyMap())) { objects }.repair("/root/projects/app") { stages += it.stage }
        assertEquals(GitRepairResult.Repaired, outcome.result)
        assertEquals(6, outcome.restored)
        assertTrue(outcome.missing.isEmpty())
        assertTrue(readable("$PROJECTS/app", objects))
        assertEquals(0, links("$PROJECTS/app"))
        assertTrue(stages.containsAll(listOf(GitRepairStage.Looking, GitRepairStage.Checking)))
        assertEquals(listOf("/root/projects/app"), checked)
    }

    @Test
    fun `B-LINUX-072 a healthy project needs nothing`() = runTest {
        val objects = repository("app")
        assertEquals(GitRepairResult.Healthy, repair(FakeTermux(fs, emptyMap())) { objects }.repair("/root/projects/app").result)
    }

    @Test
    fun `B-LINUX-073 objects in Termux are fetched, checked by name and put back`() = runTest {
        val objects = repository("app")
        val bytes = linkIntoTermux("$PROJECTS/app", objects)
        // One "object" from Termux is not what its name says: refused.
        bytes[bytes.keys.first()] = fakeDeflate("blob 3\u0000bad".encodeToByteArray())
        val termux = FakeTermux(fs, bytes)
        val outcome = repair(termux) { objects }.repair("/root/projects/app/sub")
        assertEquals(6, termux.asked.size)
        assertTrue(termux.asked.all { GitObjectLinks.TERMUX_STORE.matches(it) })
        assertEquals(GitRepairResult.CannotRepair, outcome.result)
        assertEquals(5, outcome.restored)
        assertEquals(1, outcome.missing.size)
        assertTrue("Missing (1)" in outcome.details)
        // Staged files are cleaned up.
        assertTrue(fs.list(STAGING).orEmpty().isEmpty())
    }

    @Test
    fun `B-LINUX-073 every object from Termux puts the project right`() = runTest {
        val objects = repository("app")
        val outcome = repair(FakeTermux(fs, linkIntoTermux("$PROJECTS/app", objects))) { objects }.repair("/root/projects/app")
        assertEquals(GitRepairResult.Repaired, outcome.result)
        assertEquals(6, outcome.restored)
        assertTrue(readable("$PROJECTS/app", objects))
        assertEquals(0, links("$PROJECTS/app"))
    }

    @Test
    fun `B-LINUX-073 a Termux that does not answer leaves the links and says so`() = runTest {
        val objects = repository("app")
        val outcome = repair(FakeTermux(fs, linkIntoTermux("$PROJECTS/app", objects), fail = true)) { objects }.repair("/root/projects/app")
        assertEquals(GitRepairResult.Termux, outcome.result)
        assertEquals(GitRepairTermux.NoAnswer, outcome.termux)
        assertEquals(6, links("$PROJECTS/app"))
        assertTrue(fs.list(STAGING).orEmpty().isEmpty())
    }

    @Test
    fun `B-LINUX-074 Termux not allowed yet says so and changes nothing it needs`() = runTest {
        val objects = repository("app")
        linkIntoTermux("$PROJECTS/app", objects, only = 2)
        val outcome = repair(FakeTermux(fs, emptyMap(), GitRepairTermux.NotAllowed)) { objects }.repair("/root/projects/app")
        assertEquals(GitRepairResult.Termux, outcome.result)
        assertEquals(GitRepairTermux.NotAllowed, outcome.termux)
        assertEquals(2, links("$PROJECTS/app"))
    }

    @Test
    fun `B-LINUX-074 Termux gone the missing files are named plainly`() = runTest {
        val objects = repository("app")
        linkIntoTermux("$PROJECTS/app", objects)
        val outcome = repair(FakeTermux(fs, emptyMap(), GitRepairTermux.NotInstalled)) { objects }.repair("/root/projects/app")
        assertEquals(GitRepairResult.CannotRepair, outcome.result)
        assertEquals(6, outcome.missing.size)
        assertTrue("Termux is not installed" in outcome.details)
    }

    @Test
    fun `B-SEC-091 only the projects folder is reached`() = runTest {
        repository("app")
        val r = repair(FakeTermux(fs, emptyMap())) { emptyList() }
        for (path in listOf("/root", "/root/projects", "/etc/x", "/root/projects/../x")) {
            assertEquals(GitRepairResult.NotRepository, r.repair(path).result, path)
        }
        assertTrue(checked.isEmpty())
    }

    @Test
    fun `B-SEC-091 a Termux path outside a proot-distro store is never asked for`() {
        assertTrue(GitObjectLinks.TERMUX_STORE.matches("$STORE/.l2s.tmp_obj_a0001"))
        for (
            path in listOf(
                "/data/data/com.termux/files/home/.ssh/id_ed25519",
                "$STORE/../../../../../../home/.ssh/id_ed25519",
                "/data/data/com.termux/files/usr/var/lib/proot-distro/containers/../x/rootfs/.l2s/.l2s.a",
            )
        ) {
            assertFalse(GitObjectLinks.TERMUX_STORE.matches(path), path)
        }
        // A link into Termux outside a store is missing, never asked for.
        val objects = repository("app")
        fs.delete("$PROJECTS/app/.git/${objects[0]}")
        fs.link("$PROJECTS/app/.git/${objects[0]}", "/data/data/com.termux/files/home/.ssh/id_ed25519")
        val scan = GitObjectLinks(fs, FakeZlib).scan("$PROJECTS/app/.git")
        assertEquals(GitLinkPlace.Missing, scan.links.single().place)
    }

    @Test
    fun `B-SEC-091 the Termux script uploads nothing outside a store`() {
        val paths = listOf("/etc/passwd", "$STORE/.l2s.missing0001", "$STORE/../../x/.l2s/.l2s.a")
        val lines = TermuxGitFetchScript.lines(paths, 0)
        assertEquals(listOf("1|$STORE/.l2s.missing0001"), lines)
        val script = TermuxGitFetchScript.script(port = 1, token = "t", lines = listOf("0|/etc/passwd") + lines)
        assertTrue("/data/data/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*|" in script)
        assertTrue("*) continue ;;" in script)
        assertTrue("case \"\$path\" in *..*/*|*/..*) continue ;; esac" in script)
        assertTrue(script.trimEnd().endsWith("printf 'done\\n'"))
    }

    @Test
    fun `B-SEC-091 pack files are checked by their trailing checksum`() {
        val body = ByteArray(100) { it.toByte() }
        fs.file("/x/good.pack", body + Sha1.digest(body))
        fs.file("/x/bad.pack", body + ByteArray(20))
        fs.file("/x/short.pack", ByteArray(20))
        val name = "objects/pack/pack-0123456789012345678901234567890123456789.pack"
        val links = GitObjectLinks(fs, FakeZlib)
        assertTrue(links.verify(name, "/x/good.pack"))
        assertFalse(links.verify(name, "/x/bad.pack"))
        assertFalse(links.verify(name, "/x/short.pack"))
        assertFalse(links.verify("config", "/x/good.pack"))
        assertFalse(links.verify(name, "/x/none.pack"))
    }

    @Test
    fun `B-SEC-091 SHA-1 matches the standard vectors`() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Sha1.hex(ByteArray(0)))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1.hex("abc".encodeToByteArray()))
        assertEquals(
            "84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            Sha1.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        assertEquals("34aa973cd4c4daa4f61eeb2bdbad27316534016f", Sha1.hex(ByteArray(1_000_000) { 'a'.code.toByte() }))
        // Git's own name for the empty blob.
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", Sha1.hex("blob 0\u0000".encodeToByteArray()))
    }

    @Test
    fun `B-LINUX-072 a worktree's git file leads to the main repository's objects`() {
        fs.file("$PROJECTS/main/.git/HEAD", "x".encodeToByteArray())
        fs.file("$PROJECTS/main/.git/worktrees/wt/commondir", "../..\n".encodeToByteArray())
        fs.file("$PROJECTS/wt/.git", "gitdir: /root/projects/main/.git/worktrees/wt\n".encodeToByteArray())
        val links = GitObjectLinks(fs, FakeZlib)
        val host = { p: String -> if (p.startsWith("/root/projects/")) PROJECTS + p.removePrefix("/root/projects") else p }
        assertEquals("$PROJECTS/main/.git/worktrees/wt/../..", links.gitFolder("$PROJECTS/wt", host))
        assertNull(links.gitFolder("$PROJECTS/none", host))
    }
}
