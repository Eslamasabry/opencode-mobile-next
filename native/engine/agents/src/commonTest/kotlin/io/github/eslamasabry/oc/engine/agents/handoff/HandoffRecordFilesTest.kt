package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports the record-file half of test/paseo_agent_handoff_test.dart (B-SEC-111). */
class HandoffRecordFilesTest {
    private val directory = "/work/app"
    private val record = "handoff_0123456789abcdef"

    /** An in-memory daemon workspace, like the Flutter FakeDaemon handlers. */
    private inner class Daemon : HandoffWorkspace, HandoffHostLease {
        val files = mutableMapOf<String, String>()
        val folders = mutableSetOf(".")
        val sent = mutableListOf<String>()
        val writes = mutableListOf<String>()
        var writeConflict = false
        var readbackMismatch = false
        var parentIsSymlink = false
        var hostAllowed = true
        var acquisitions = 0
        var releases = 0
        var connected = true
        var featureNegotiated = true

        override val scope: String get() = directory
        override val handoffFiles: Boolean get() = HandoffRecordFiles.available(connected, featureNegotiated)
        override val epoch: Long = 1

        override suspend fun acquire(): Outcome<Unit> {
            acquisitions++
            return if (hostAllowed) Unit.ok() else Failure.Busy().failed()
        }

        override fun release() {
            releases++
        }

        override suspend fun create(parent: String, name: String, kind: HandoffEntryKind): Outcome<HandoffEntryCreated> {
            sent += "create:$parent/$name"
            val path = if (parent == ".") name else "$parent/$name"
            val success = path !in files && path !in folders
            if (success) if (kind == HandoffEntryKind.Directory) folders += path else files[path] = ""
            return HandoffEntryCreated(directory, parent, if (success) path else null, success).ok()
        }

        override suspend fun list(path: String): Outcome<HandoffListing> {
            sent += "list:$path"
            return HandoffListing(directory, path, false, listOf(HandoffListEntry(".handoff", if (parentIsSymlink) "file" else "directory"))).ok()
        }

        override suspend fun read(path: String, maxBytes: Int): Outcome<HandoffFileRead> {
            sent += "read:$path"
            val text = files[path]
            val content = if (readbackMismatch && text != "") "different" else text
            return HandoffFileRead(
                directory, path, text == null,
                HandoffFileData(path, "text", "utf-8", content, "2026-10-14T00:00:00.000Z", "revision-1"),
            ).ok()
        }

        override suspend fun write(path: String, content: String, expectedModifiedAt: String, expectedRevision: String?): Outcome<String?> {
            sent += "write:$path"
            writes += path
            assertEquals("revision-1", expectedRevision)
            assertEquals("2026-10-14T00:00:00.000Z", expectedModifiedAt)
            if (!writeConflict) files[path] = content
            return (if (writeConflict) "conflict" else "written").ok()
        }
    }

    private val daemon = Daemon()
    private val writer = HandoffRecordFiles(daemon, daemon)

    private suspend fun write(dir: String = directory, id: String = record, markdown: String = "User: Continue the work.\n") =
        writer.write(dir, id, markdown)

    private fun refused(outcome: Outcome<HandoffFileWrite>): HandoffFileRefusal =
        assertIs<HandoffFileWrite.Refused>(assertIs<Outcome.Ok<HandoffFileWrite>>(outcome).value).reason

    @Test
    fun `B-SEC-111 writes and verifies ignore before record in exclusively created folder`() = runTest {
        val path = assertIs<HandoffFileWrite.Written>((write() as Outcome.Ok).value).relativePath
        assertEquals(".handoff/$record/record.md", path)
        assertEquals("User: Continue the work.\n", daemon.files[path])
        assertContains(daemon.files[".handoff/$record/.gitignore"]!!, "\n*\n")
        assertEquals(listOf(".handoff/$record/.gitignore", ".handoff/$record/record.md"), daemon.writes)
        assertTrue(HandoffRecordFiles.available(connected = true, workspaceFileEditing = true))
        assertFalse(HandoffCapabilities().agentHandoffFiles)
    }

    @Test
    fun `B-SEC-111 file mutation holds the host lease and denied host cannot write`() = runTest {
        write()
        assertEquals(1, daemon.acquisitions)
        assertEquals(1, daemon.releases)
        val sent = daemon.sent.size
        daemon.hostAllowed = false
        assertEquals(HandoffFileRefusal.HostUnavailable, refused(write(id = "denied")))
        assertEquals(sent, daemon.sent.size)
        assertEquals(2, daemon.acquisitions)
        assertEquals(1, daemon.releases)
    }

    @Test
    fun `B-SEC-111 existing transfer folder is never overwritten or reused`() = runTest {
        write()
        val before = daemon.files.toMap()
        assertEquals(HandoffFileRefusal.FolderExists, refused(write(markdown = "different")))
        assertEquals(before, daemon.files)
    }

    @Test
    fun `B-SEC-111 existing shared ignore file remains untouched`() = runTest {
        daemon.folders += ".handoff"
        daemon.files[".handoff/.gitignore"] = "owner rules"
        assertIs<HandoffFileWrite.Written>((write() as Outcome.Ok).value)
        assertEquals("owner rules", daemon.files[".handoff/.gitignore"])
    }

    @Test
    fun `B-SEC-111 parent symlink is refused before record or ignore creation`() = runTest {
        daemon.folders += ".handoff"
        daemon.parentIsSymlink = true
        assertEquals(HandoffFileRefusal.NoHandoffFolder, refused(write()))
        assertTrue(daemon.files.isEmpty())
        assertFalse(".handoff/$record" in daemon.folders)
    }

    @Test
    fun `B-SEC-111 missing negotiated feature and disconnect disable writes`() = runTest {
        daemon.featureNegotiated = false
        assertFalse(daemon.handoffFiles)
        assertEquals(HandoffFileRefusal.RecordRefused, refused(write()))
        assertTrue(daemon.sent.isEmpty())
        daemon.featureNegotiated = true
        daemon.connected = false
        assertEquals(HandoffFileRefusal.RecordRefused, refused(write()))
        assertTrue(daemon.sent.isEmpty())
    }

    @Test
    fun `B-SEC-111 invalid scope, path identifiers and byte limits do not dispatch`() = runTest {
        for (id in listOf("../escape", "id/name", "", "x".repeat(129))) {
            assertEquals(HandoffFileRefusal.RecordRefused, refused(write(id = id)))
        }
        assertEquals(HandoffFileRefusal.RecordRefused, refused(write(dir = "/different")))
        assertEquals(HandoffFileRefusal.RecordRefused, refused(write(markdown = "é".repeat(50_000))))
        assertEquals(
            HandoffFileRefusal.NotFullyMasked,
            refused(write(markdown = "Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456")),
        )
        assertTrue(daemon.sent.isEmpty())
    }

    @Test
    fun `B-SEC-111 ignore conflict or readback mismatch never writes conversation content`() = runTest {
        daemon.writeConflict = true
        assertEquals(HandoffFileRefusal.WriteNotConfirmed, refused(write()))
        assertFalse(".handoff/$record/record.md" in daemon.files)
        daemon.writeConflict = false
        daemon.readbackMismatch = true
        assertEquals(HandoffFileRefusal.ReadBackDiffers, refused(write(id = "another_record")))
        assertFalse(".handoff/another_record/record.md" in daemon.files)
    }
}
