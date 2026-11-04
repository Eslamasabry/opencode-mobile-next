package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Outcome
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real file system, links never followed. */
internal object JavaStorageTree : StorageTree {
    override fun kind(path: String): StorageEntryKind {
        val p = Paths.get(path)
        return when {
            Files.isSymbolicLink(p) -> StorageEntryKind.Link
            !Files.exists(p, LinkOption.NOFOLLOW_LINKS) -> StorageEntryKind.Missing
            Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) -> StorageEntryKind.Directory
            Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) -> StorageEntryKind.File
            else -> StorageEntryKind.Other
        }
    }

    override fun list(path: String): List<String>? = File(path).list()?.toList()

    override fun size(path: String): Long? = File(path).takeIf { it.isFile }?.length()

    override fun readLink(path: String): String? =
        try { Files.readSymbolicLink(Paths.get(path)).toString() } catch (_: Exception) { null }

    override fun realPath(path: String): String? =
        try { Paths.get(path).toRealPath().toString() } catch (_: Exception) { null }
}

/**
 * Deletes [root] without ever following a link. Never use File.deleteRecursively here: Kotlin's file walk follows
 * symbolic links into directories, and this fixture links out of its folder.
 */
internal fun deleteWithoutFollowingLinks(root: Path) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
        Files.newDirectoryStream(root).use { children -> children.forEach(::deleteWithoutFollowingLinks) }
    }
    Files.delete(root)
}

/** Port of test/manage_space/project_export_test.dart (the measuring half). */
class AppStorageFactsTest {
    private val files: Path = Files.createTempDirectory("oc-export-")
    private val outsideDir: Path = Files.createTempDirectory("oc-export-outside-").also { Files.writeString(it.resolve("keep.txt"), "keep") }
    private val scanner = ProjectStorageScanner(JavaStorageTree)

    private fun write(relative: String, text: String) {
        val file = files.resolve(relative).toFile()
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    init {
        write("projects/app/lib/main.dart", "void main() {}\n") // 15
        write("projects/app/.env", "TOKEN=secret\n") // 13
        write("projects/app/.env.example", "TOKEN=\n") // 7
        write("projects/app/.git/config", "[remote]\nurl = https://" + "me" + ":tok@x.dev/r\n") // built so the push scan sees no credential URL
        write("projects/app/deploy/server.pem", "key")
        write("projects/web/index.html", "<p>hi</p>") // 9
        write("projects/web/.git/config", "[remote]\nurl = https://x.dev/r\n")
        write("linux/ubuntu.ready", "")
        write("linux/ubuntu/usr/bin/tool", "0123456789") // 10
        write("linux/ubuntu/root/.local/share/opencode/auth.json", """{"k":"v"}""")
        write("linux/ubuntu/root/.local/share/opencode/storage/s1.json", "{}")
        write("linux/ubuntu/root/.local/share/opencode/log/today.log", "log")
        write("linux/ubuntu/root/.config/opencode/opencode.json", "{}")
        // A link out of the project (to a folder outside the app's files) must never be followed or listed.
        Files.createSymbolicLink(files.resolve("projects/web/outside"), outsideDir)
        Files.createSymbolicLink(files.resolve("projects/linked"), files)
    }

    @AfterTest
    fun cleanUp() {
        deleteWithoutFollowingLinks(files)
        assertTrue(Files.exists(outsideDir.resolve("keep.txt")), "cleanup never follows a link")
        deleteWithoutFollowingLinks(outsideDir)
    }

    private fun plan(includePrivate: Boolean = false) = (scanner.plan(files.toString(), includePrivate) as Outcome.Ok).value

    @Test
    fun `B-STATE-028 measures each project, the server and saved private data`() {
        val facts = scanner.scan(files.toString())
        assertEquals(listOf("app", "web"), facts.projects.map { it.name })
        val app = facts.projects.first()
        val gitConfig = files.resolve("projects/app/.git/config").toFile().length()
        assertEquals(15 + 13 + 7 + gitConfig + 3, app.bytes)
        assertEquals(5, app.files)
        assertEquals(3, app.privateFiles) // .env, .git/config with a token, .pem
        assertEquals(1, facts.projects.last().privateFiles)
        assertTrue(facts.serverInstalled)
        assertTrue(facts.serverBytes >= 10)
        assertEquals(9L + 2 + 2, facts.privateDataBytes)
        assertEquals(4, facts.privateFiles)
    }

    @Test
    fun `B-STATE-028 nothing installed measures as nothing, never an error`() {
        val empty = Files.createTempDirectory("oc-export-empty-")
        try {
            val facts = scanner.scan(empty.toString())
            assertTrue(facts.projects.isEmpty())
            assertFalse(facts.serverInstalled)
            assertEquals(0, facts.serverBytes)
            assertTrue((scanner.plan(empty.toString()) as Outcome.Ok).value.isEmpty())
        } finally {
            deleteWithoutFollowingLinks(empty)
        }
    }

    @Test
    fun `B-STATE-028 a plain export leaves every credential and link out, a private one adds OpenCode data but not logs`() {
        val names = plan().map { it.name }.toSet()
        assertTrue("projects/app/lib/main.dart" in names)
        assertTrue("projects/app/.env.example" in names)
        assertTrue("projects/web/index.html" in names)
        for (hidden in listOf("projects/web/.git/config", "projects/app/.env", "projects/app/.git/config", "projects/app/deploy/server.pem")) {
            assertFalse(hidden in names, hidden)
        }
        assertFalse(names.any { "auth.json" in it || it.startsWith("opencode/") || "outside" in it || "linked" in it })
        val private = plan(includePrivate = true)
        val privateNames = private.map { it.name }.toSet()
        assertTrue("projects/app/.env" in privateNames)
        assertTrue("opencode/.local/share/opencode/auth.json" in privateNames)
        assertTrue("opencode/.config/opencode/opencode.json" in privateNames)
        assertFalse(privateNames.any { "/log/" in it })
        assertTrue(private.filter { it.private }.map { it.name }.containsAll(listOf("projects/app/.env", "opencode/.local/share/opencode/auth.json")))
    }

    @Test
    fun `B-STATE-028 private root folders count as private and projects not yet moved out of Ubuntu are measured`() {
        write("projects/.ssh/config", "Host example")
        write("linux/ubuntu/root/projects/old/readme.md", "old")
        assertEquals(1, scanner.scan(files.toString()).projects.single { it.name == ".ssh" }.privateFiles)
        assertTrue("projects/old/readme.md" in plan().map { it.name })
        assertEquals(listOf(".ssh", "app", "old", "web"), scanner.scan(files.toString()).projects.map { it.name })
    }
}
