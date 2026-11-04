package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
// Flutter test/termux_migration_archive_test.dart, case for case, over real files in a temp directory.
class TermuxMigrationArchiveTest {
    private lateinit var support: Path
    private lateinit var store: TermuxMigrationArchiveStore

    @Before
    fun setUp() {
        support = tempDir("migration-archive-")
        store = TermuxMigrationArchiveStore(support, SameDispatchers(UnconfinedTestDispatcher()))
        Files.createDirectories(support.resolve("linux/ubuntu/root"))
        Files.write(support.resolve("linux/ubuntu.ready"), "ready".toByteArray())
    }

    @After
    fun tearDown() {
        support.deleteRecursivelyQuietly()
    }

    private class Entry(val path: String, val content: String = "", val type: Int = 48, val mode: Int = 420)

    private fun archive(entries: List<Entry>): Pair<Path, TermuxMigrationArchive> {
        val bytes = tar(entries)
        val file = support.resolve("transfer.tar")
        Files.write(file, bytes)
        return file to TermuxMigrationArchive(bytes.size.toLong(), sha(bytes))
    }

    private fun read(path: Path) = String(Files.readAllBytes(path))

    private val never = { false }

    @Test
    fun `B-LINUX-233 only a verified private config yields offline provider labels`() = runTest {
        val (file, expected) = archive(
            listOf(
                Entry(".config/opencode/opencode.json", """{"provider":{"anthropic":{"options":{"apiKey":"synthetic-private-key"}}}}"""),
                Entry(".oc-opencode2/config/opencode/opencode.jsonc", """{/* isolated */ "provider":{"openai":{}}}"""),
            ),
        )
        assertEquals(emptyList(), store.providerNames("names", expected))
        store.importItem("names", TermuxMigrationItem.config, file, expected, never)
        store.cleanupPartial("names")
        assertEquals(listOf("Anthropic", "OpenAI"), store.providerNames("names", expected))
        val exported = support.resolve("linux/ubuntu/root/.oc-migration-exports/names/config/.config/opencode/opencode.json")
        assertEquals(384, modeOf(exported))
        Files.write(exported, """{"provider":{"google":{}}}""".toByteArray())
        expectFailure(TermuxMigrationFailure.destinationConflict) { store.providerNames("names", expected) }
    }

    @Test
    fun `B-LINUX-233 projects import privately, keep executability and resume committing`() = runTest {
        val (file, expected) = archive(
            listOf(
                Entry("./", type = 53),
                Entry("./repo/", type = 53),
                Entry("./repo/readme", content = "content"),
                Entry("./repo/run", content = "#!/bin/sh\n", mode = 493),
            ),
        )
        store.importItem("job1", TermuxMigrationItem.projects, file, expected, never)
        val project = support.resolve("projects/termux-job1")
        assertEquals("content", read(project.resolve("repo/readme")))
        assertEquals(384, modeOf(project.resolve("repo/readme")))
        assertEquals(448, modeOf(project.resolve("repo/run")))
        assertTrue(store.imported("job1", TermuxMigrationItem.projects, expected))
        // No external completion marker is needed after an atomic rename.
        Files.delete(file)
        store.importItem("job1", TermuxMigrationItem.projects, file, expected, never)
        assertEquals("content", read(project.resolve("repo/readme")))
        val incoming = Files.createDirectories(store.jobDirectory("job1").resolve("incoming"))
        Files.write(incoming.resolve("projects.tar"), byteArrayOf(1, 2, 3))
        Files.write(incoming.resolve("projects.offset"), "3".toByteArray())
        store.cleanupPartial("job1")
        assertFalse(Files.exists(store.jobDirectory("job1")))
        assertEquals("content", read(project.resolve("repo/readme")))
        assertTrue(store.imported("job1", TermuxMigrationItem.projects, expected))
        // Completed cleanup is itself safe to repeat.
        store.cleanupPartial("job1")
    }

    @Test
    fun `B-LINUX-233 a changed imported file is preserved and fails closed on rerun`() = runTest {
        val (file, expected) = archive(listOf(Entry("repo/file", content = "old")))
        store.importItem("job2", TermuxMigrationItem.projects, file, expected, never)
        val imported = support.resolve("projects/termux-job2/repo/file")
        Files.write(imported, "owner changed it".toByteArray())
        expectFailure(TermuxMigrationFailure.destinationConflict) {
            store.importItem("job2", TermuxMigrationItem.projects, file, expected, never)
        }
        assertEquals("owner changed it", read(imported))
    }

    @Test
    fun `B-LINUX-233 a checksum mismatch never commits`() = runTest {
        val (file, expected) = archive(listOf(Entry("file", content = "private")))
        expectFailure(TermuxMigrationFailure.checksumMismatch) {
            store.importItem("bad", TermuxMigrationItem.config, file, TermuxMigrationArchive(expected.bytes, "0".repeat(64)), never)
        }
        assertFalse(Files.exists(support.resolve("linux/ubuntu/root/.oc-migration-exports")))
    }

    @Test
    fun `B-LINUX-233 traversal, links, duplicate names and a bad header checksum are rejected`() = runTest {
        for (entries in listOf(
            listOf(Entry("../escape", content = "x")),
            listOf(Entry("/absolute", content = "x")),
            listOf(Entry("link", type = 50)),
            listOf(Entry("hardlink", type = 49)),
            listOf(Entry("same"), Entry("same")),
            listOf(Entry("parent"), Entry("parent/child")),
        )) {
            val (file, expected) = archive(entries)
            expectAnyFailure { store.importItem("malicious", TermuxMigrationItem.projects, file, expected, never) }
        }
        assertFalse(Files.exists(support.resolve("escape")))
        assertFalse(Files.exists(support.resolve("absolute")))
        val bytes = tar(listOf(Entry("file")))
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val file = support.resolve("transfer.tar")
        Files.write(file, bytes)
        expectFailure(TermuxMigrationFailure.invalidArchive) {
            store.importItem("malicious", TermuxMigrationItem.projects, file, TermuxMigrationArchive(bytes.size.toLong(), sha(bytes)), never)
        }
        assertFalse(Files.exists(support.resolve("projects/termux-malicious")))
    }

    @Test
    fun `B-LINUX-233 a linked destination parent never reads or changes the link target`() = runTest {
        val outside = Files.createDirectory(support.resolve("outside"))
        Files.write(outside.resolve("sentinel"), "untouched".toByteArray())
        Files.createSymbolicLink(support.resolve("projects"), outside)
        val (file, expected) = archive(listOf(Entry("file", content = "x")))
        expectFailure(TermuxMigrationFailure.destinationConflict) {
            store.importItem("linked", TermuxMigrationItem.projects, file, expected, never)
        }
        assertEquals("untouched", read(outside.resolve("sentinel")))
        assertFalse(Files.exists(outside.resolve("termux-linked")))
    }

    @Test
    fun `B-LINUX-233 stale staging is replaced and cancellation cleans only partial files`() = runTest {
        val stage = Files.createDirectories(store.jobDirectory("resume").resolve("staging/projects"))
        Files.write(stage.resolve("old-partial"), "partial".toByteArray())
        val (file, expected) = archive(listOf(Entry("large", content = "x".repeat(150_000))))
        var checks = 0
        expectFailure(TermuxMigrationFailure.cancelled) {
            store.importItem("resume", TermuxMigrationItem.projects, file, expected) { ++checks > 4 }
        }
        assertFalse(Files.exists(stage))
        store.importItem("resume", TermuxMigrationItem.projects, file, expected, never)
        assertTrue(store.imported("resume", TermuxMigrationItem.projects, expected))
        assertFalse(Files.exists(support.resolve("projects/termux-resume/old-partial")))
    }

    @Test
    fun `B-LINUX-233 a credentialed config stays a private export with no payload in its manifest or errors`() = runTest {
        val secret = "fake-test-credential-never-display"
        val (file, expected) = archive(listOf(Entry("opencode.json", content = secret, mode = 511)))
        store.importItem("config", TermuxMigrationItem.config, file, expected, never)
        val export = support.resolve("linux/ubuntu/root/.oc-migration-exports/config/config")
        assertEquals(384, modeOf(export.resolve("opencode.json")))
        assertEquals(448, modeOf(export))
        assertFalse(read(export.resolve(".oc-migration-manifest.json")).contains(secret))
        assertFalse(Files.exists(support.resolve("linux/ubuntu/root/.config/opencode/opencode.json")))
        assertEquals("TermuxMigrationException(storage)", TermuxMigrationException(TermuxMigrationFailure.storage).toString())
    }

    @Test
    fun `B-LINUX-233 a config item without the in-app Linux is refused before anything is committed`() = runTest {
        Files.delete(support.resolve("linux/ubuntu.ready"))
        assertFalse(store.builtinInstalled())
        val (file, expected) = archive(listOf(Entry("opencode.json", content = "{}")))
        expectFailure(TermuxMigrationFailure.unavailable) { store.importItem("nolinux", TermuxMigrationItem.config, file, expected, never) }
        assertFalse(Files.exists(support.resolve("linux/ubuntu/root/.oc-migration-exports/nolinux")))
        assertFalse(Files.exists(store.jobDirectory("nolinux").resolve("staging/config")))
    }

    private fun sha(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun tar(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream()
        for (entry in entries) {
            val header = ByteArray(512)
            fun text(offset: Int, value: String) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                encoded.copyInto(header, offset)
            }
            val payload = entry.content.toByteArray(Charsets.UTF_8)
            text(0, entry.path)
            text(100, entry.mode.toString(8).padStart(7, '0') + "\u0000")
            text(108, "0000000\u0000")
            text(116, "0000000\u0000")
            text(124, payload.size.toString(8).padStart(11, '0') + "\u0000")
            text(136, "00000000000\u0000")
            header.fill(32, 148, 156)
            header[156] = entry.type.toByte()
            text(257, "ustar\u0000")
            text(263, "00")
            val checksum = header.sumOf { it.toInt() and 0xff }
            text(148, checksum.toString(8).padStart(6, '0') + "\u0000 ")
            out.write(header)
            out.write(payload)
            out.write(ByteArray((512 - payload.size % 512) % 512))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }
}
