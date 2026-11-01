package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

class NativeComponentUpdateRecoveryTest {
    private val files = File("/app/files")
    private val home = File(files, "linux")
    private val root = File(home, "ubuntu")
    private val code = "/opt/opencode"
    private val lock = "/home/oc/.local/share/oc-agents/.lock-claude"
    private val directory get() = NativeComponentUpdateRecovery.Kind.DIRECTORY
    private val regular get() = NativeComponentUpdateRecovery.Kind.REGULAR
    private val symlink get() = NativeComponentUpdateRecovery.Kind.SYMLINK
    private fun guest(path: String) = File(root, path.removePrefix("/"))
    private fun helper(fs: MemoryFs) = NativeComponentUpdateRecovery(files, home, root, fs)
    private fun fixture() = MemoryFs().apply { mkdir(files); mkdir(home); mkdir(root) }
    private fun fail(action: () -> Unit): IllegalStateException {
        val error = assertThrows(IllegalStateException::class.java, action)
        assertEquals(NativeComponentUpdateRecovery.FAILURE, error.message)
        assertNull(error.cause)
        return error
    }

    @Test fun noPendingLeavesInstalledCodeAndRetainedOneGoodAlone() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code/current"), regular, "active")
            mkdir(guest("$code.oc-good")); put(guest("$code.oc-good/previous"), regular, "good")
        }
        assertFalse(helper(fs).hasPending())
        helper(fs).recoverAfterQuiescence { error("must not need admission") }
        assertTrue(fs.exists(guest("$code/current")))
        assertTrue(fs.exists(guest("$code.oc-good/previous")))
        assertTrue(fs.writes.isEmpty())
    }

    @Test fun pendingRecoveryCannotWriteWithoutPositiveQuiescenceProof() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "new\n")
        }
        assertTrue(helper(fs).hasPending())
        fail { helper(fs).recoverAfterQuiescence { false } }
        assertTrue(fs.exists(guest(code))); assertTrue(fs.exists(guest("$code.oc-pending")))
        assertTrue(fs.writes.isEmpty())
    }

    @Test fun quiescenceIsRecheckedImmediatelyBeforeFirstMutation() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "new")
        }
        var checks = 0
        fail { helper(fs).recoverAfterQuiescence { ++checks == 1 } }
        assertEquals(2, checks); assertTrue(fs.writes.isEmpty())
    }

    @Test fun revocationAfterDeletePreservesGoodAndPendingForNextRecovery() {
        val fs = fixture().apply {
            mkdir(guest(code)); mkdir(guest("$code.oc-good"))
            put(guest("$code.oc-pending"), regular, "existing")
        }
        fail { helper(fs).recoverAfterQuiescence { fs.writes.isEmpty() } }
        assertFalse(fs.exists(guest(code))); assertTrue(fs.exists(guest("$code.oc-good")))
        assertTrue(fs.exists(guest("$code.oc-pending")))
        helper(fs).recoverAfterQuiescence { true }
        assertTrue(fs.exists(guest(code))); assertFalse(fs.exists(guest("$code.oc-pending")))
    }

    @Test fun existingRestoresPriorCodeAndNeverAdoptsOrDeletesLegacyOldPidSibling() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code/bad"), regular, "bad")
            mkdir(guest("$code.oc-good")); put(guest("$code.oc-good/good"), regular, "good")
            mkdir(guest("$code.old.123")); put(guest("$code.old.123/unknown"), regular, "legacy")
            put(guest("$code.oc-pending"), regular, "existing\n")
        }
        helper(fs).recoverAfterQuiescence { true }
        assertFalse(fs.exists(guest("$code/bad"))); assertTrue(fs.exists(guest("$code/good")))
        assertFalse(fs.exists(guest("$code.oc-good"))); assertFalse(fs.exists(guest("$code.oc-pending")))
        assertEquals("legacy", fs.text(guest("$code.old.123/unknown")))
    }

    @Test fun existingWithoutGoodAcceptsOnlyAnAlreadyPresentValidActiveGeneration() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "existing")
        }
        helper(fs).recoverAfterQuiescence { true }
        val writes = fs.writes.toList()
        helper(fs).recoverAfterQuiescence { true }
        assertEquals(writes, fs.writes); assertTrue(fs.exists(guest(code)))
        val absent = fixture().apply { put(guest("$code.oc-pending"), regular, "existing") }
        fail { helper(absent).recoverAfterQuiescence { true } }
        assertTrue(absent.writes.isEmpty()); assertTrue(absent.exists(guest("$code.oc-pending")))
    }

    @Test fun newRemovesIncompleteFirstInstallAndIsIdempotentWhenActiveIsAlreadyAbsent() {
        for (present in listOf(true, false)) {
            val fs = fixture().apply {
                if (present) { mkdir(guest(code)); put(guest("$code/bad"), regular, "bad") }
                put(guest("$code.oc-pending"), regular, "new\n")
            }
            helper(fs).recoverAfterQuiescence { true }
            assertFalse(fs.exists(guest(code))); assertFalse(fs.exists(guest("$code.oc-pending")))
        }
    }

    @Test fun everyCodeGenerationIsRecoveredBeforeAnyCommandLink() {
        val fs = fixture().apply {
            for (path in listOf("/opt/opencode", "/opt/opencode2", "/opt/oc-claude/claude",
                "/home/oc/.local/share/oc-paseo/0.9.2-82d16f9c432d",
                "/home/oc/.local/share/oc-agents/claude/2.1.283")) {
                if (path == "/opt/oc-claude/claude") put(guest(path), regular, "new") else mkdir(guest(path))
                put(guest("$path.oc-pending"), regular, "new")
            }
            for (path in listOf("/usr/local/bin/opencode", "/usr/local/bin/opencode2", "/usr/local/bin/claude",
                "/home/oc/.local/bin/paseo", "/home/oc/.local/bin/claude")) {
                put(guest(path), symlink, "/not-followed")
                put(guest("$path.oc-pending"), regular, "new")
            }
        }
        helper(fs).recoverAfterQuiescence { true }
        val lastCode = fs.writes.indexOf("unlink:${guest("/home/oc/.local/share/oc-agents/claude/2.1.283.oc-pending")}")
        val firstLink = fs.writes.indexOf("unlink:${guest("/usr/local/bin/opencode")}")
        assertTrue(lastCode >= 0 && firstLink > lastCode)
    }

    @Test fun codeRootsAndGoodMustHaveTheirExpectedTypes() {
        for (bad in listOf(symlink, regular, NativeComponentUpdateRecovery.Kind.OTHER)) {
            val fs = fixture().apply { put(guest(code), bad, "unsafe"); put(guest("$code.oc-pending"), regular, "new") }
            fail { helper(fs).recoverAfterQuiescence { true } }; assertTrue(fs.writes.isEmpty())
        }
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code.oc-good"), symlink, "/outside")
            put(guest("$code.oc-pending"), regular, "existing")
        }
        fail { helper(fs).recoverAfterQuiescence { true } }; assertTrue(fs.writes.isEmpty())
    }

    @Test fun symlinkAncestorsCannotHideOrRedirectPendingRecovery() {
        for (ancestor in listOf(files, home, root, guest("/opt"))) {
            val fs = fixture().apply {
                put(guest("$code.oc-pending"), regular, "new")
                put(ancestor, symlink, "/outside")
            }
            fail { helper(fs).hasPending() }; assertTrue(fs.writes.isEmpty())
        }
    }

    @Test fun receiptMustBeSmallRegularFileAndExactKnownIntent() {
        for (value in listOf("", "existing trailing", "new\nprivate", "x".repeat(17))) {
            val fs = fixture().apply { mkdir(guest(code)); put(guest("$code.oc-pending"), regular, value) }
            fail { helper(fs).recoverAfterQuiescence { true } }; assertTrue(fs.writes.isEmpty())
        }
        val linked = fixture().apply { mkdir(guest(code)); put(guest("$code.oc-pending"), symlink, "new") }
        fail { helper(linked).recoverAfterQuiescence { true } }
        assertEquals(0, linked.opens); assertTrue(linked.writes.isEmpty())
    }

    @Test fun openedReceiptMustMatchLstatDeviceInodeAndType() {
        for (alter in listOf<(NativeComponentUpdateRecovery.Stat) -> NativeComponentUpdateRecovery.Stat>(
            { it.copy(inode = it.inode + 1) }, { it.copy(device = it.device + 1) }, { it.copy(kind = symlink) })) {
            val fs = fixture().apply {
                mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "new")
                openedStat = alter
            }
            fail { helper(fs).recoverAfterQuiescence { true } }
            assertEquals(1, fs.closes); assertTrue(fs.writes.isEmpty())
        }
    }

    @Test fun growingReceiptReadIsBoundedAndRefusesSeventeenthByte() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "new")
            readBytes = "new".padEnd(17, ' ').toByteArray()
        }
        fail { helper(fs).recoverAfterQuiescence { true } }
        assertEquals(17, fs.maximumRead); assertTrue(fs.writes.isEmpty())
    }

    @Test fun directoryChangedToSymlinkDuringEnumerationIsRefusedBeforeUnlink() {
        val fs = fixture().apply {
            mkdir(guest(code)); mkdir(guest("$code.oc-good")); put(guest("$code.oc-pending"), regular, "existing")
            onList = { file -> if (file == guest(code)) put(file, symlink, "/outside") }
        }
        fail { helper(fs).recoverAfterQuiescence { true } }; assertTrue(fs.writes.isEmpty())
    }

    @Test fun recursiveDeleteUnlinksSymlinkObjectWithoutFollowingItsGuestTarget() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code/link"), symlink, "/outside/keep")
            put(File("/outside/keep"), regular, "keep")
            put(guest("$code.oc-pending"), regular, "new")
        }
        helper(fs).recoverAfterQuiescence { true }
        assertFalse(fs.exists(guest("$code/link"))); assertEquals("keep", fs.text(File("/outside/keep")))
        assertFalse(fs.listed.contains(guest("$code/link")))
    }

    @Test fun recursiveDeletionHasFiniteDepthAndNeverTraversesUnexpectedObjects() {
        val fs = fixture().apply {
            var path = guest(code)
            mkdir(path)
            repeat(65) { path = File(path, "child"); mkdir(path) }
            put(guest("$code.oc-pending"), regular, "new")
        }
        fail { helper(fs).recoverAfterQuiescence { true } }; assertTrue(fs.writes.isEmpty())
        val special = fixture().apply {
            mkdir(guest(code)); put(guest("$code/socket"), NativeComponentUpdateRecovery.Kind.OTHER, "")
            put(guest("$code.oc-pending"), regular, "new")
        }
        fail { helper(special).recoverAfterQuiescence { true } }; assertTrue(special.writes.isEmpty())
    }

    @Test fun directorySyncMustSucceedBeforePendingReceiptIsCleared() {
        val fs = fixture().apply {
            mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "existing")
            failSync = true
        }
        fail { helper(fs).recoverAfterQuiescence { true } }
        assertTrue(fs.exists(guest("$code.oc-pending")))
        fs.failSync = false
        helper(fs).recoverAfterQuiescence { true }
        val sync = fs.writes.indexOf("sync:${guest("/opt")}")
        val clear = fs.writes.indexOf("unlink:${guest("$code.oc-pending")}")
        assertTrue(sync >= 0 && clear > sync)
    }

    @Test fun excessiveEntriesStopDeletionAndRetainTheRecoveryJournal() {
        val fs = fixture().apply {
            mkdir(guest(code))
            repeat(100_001) { put(guest("$code/item$it"), regular, "") }
            put(guest("$code.oc-pending"), regular, "new")
        }
        fail { helper(fs).recoverAfterQuiescence { true } }
        assertTrue(fs.exists(guest(code)))
        assertTrue(fs.exists(guest("$code.oc-pending")))
        assertTrue(fs.writes.size < 100_001)
    }

    @Test fun emptyLegacyLockIsRemovedOnlyAfterProofAndNoOldPidDirectoryIsAdopted() {
        val fs = fixture().apply { mkdir(guest(lock)); mkdir(guest("/opt/opencode.old.42")) }
        assertTrue(helper(fs).hasPending())
        fail { helper(fs).recoverAfterQuiescence { false } }; assertTrue(fs.writes.isEmpty())
        helper(fs).recoverAfterQuiescence { true }
        assertFalse(fs.exists(guest(lock))); assertTrue(fs.exists(guest("/opt/opencode.old.42")))
    }

    @Test fun nonemptyLegacyLockRefusesBeforeAnyJournalMutation() {
        val fs = fixture().apply {
            mkdir(guest(lock)); put(guest("$lock/unknown"), regular, "unknown")
            mkdir(guest(code)); put(guest("$code.oc-pending"), regular, "new")
        }
        fail { helper(fs).recoverAfterQuiescence { true } }
        assertTrue(fs.writes.isEmpty()); assertTrue(fs.exists(guest("$code.oc-pending")))
    }

    @Test fun symlinkOrRegularLegacyLockRefusesEvenWithoutAJournal() {
        for (type in listOf(symlink, regular)) {
            val fs = fixture().apply { put(guest(lock), type, "/outside") }
            fail { helper(fs).hasPending() }; assertTrue(fs.writes.isEmpty())
        }
    }

    private class MemoryFs : NativeComponentUpdateRecovery.Fs {
        private data class Node(val stat: NativeComponentUpdateRecovery.Stat, val bytes: ByteArray)
        private val nodes = linkedMapOf<File, Node>()
        private var inode = 1L
        val writes = mutableListOf<String>()
        val listed = mutableListOf<File>()
        var opens = 0
        var closes = 0
        var maximumRead = 0
        var openedStat: ((NativeComponentUpdateRecovery.Stat) -> NativeComponentUpdateRecovery.Stat)? = null
        var readBytes: ByteArray? = null
        var onList: ((File) -> Unit)? = null
        var failSync = false
        fun exists(file: File) = file in nodes
        fun text(file: File) = nodes.getValue(file).bytes.toString(Charsets.UTF_8)
        fun mkdir(file: File) {
            if (file in nodes) return
            file.parentFile?.let { mkdir(it) }
            put(file, NativeComponentUpdateRecovery.Kind.DIRECTORY, "", parents = false)
        }
        fun put(file: File, type: NativeComponentUpdateRecovery.Kind, text: String, parents: Boolean = true) {
            if (parents) file.parentFile?.let { mkdir(it) }
            val bytes = text.toByteArray()
            nodes[file] = Node(NativeComponentUpdateRecovery.Stat(type, 1L, inode++, bytes.size.toLong()), bytes)
        }
        override fun lstat(file: File) = nodes[file]?.stat
        override fun openReceiptNoFollow(file: File): NativeComponentUpdateRecovery.ReceiptHandle {
            opens++
            val captured = nodes.getValue(file)
            return object : NativeComponentUpdateRecovery.ReceiptHandle {
                override fun stat() = openedStat?.invoke(captured.stat) ?: captured.stat
                override fun read(maximum: Int): ByteArray {
                    maximumRead = maximum
                    return (readBytes ?: captured.bytes).take(maximum).toByteArray()
                }
                override fun close() { closes++ }
            }
        }
        override fun children(directory: File): List<File> {
            listed.add(directory)
            check(nodes.getValue(directory).stat.kind == NativeComponentUpdateRecovery.Kind.DIRECTORY)
            onList?.invoke(directory)
            return nodes.keys.filter { it != directory && it.parentFile == directory }
        }
        override fun rename(source: File, destination: File) {
            writes.add("rename:$source:$destination")
            val moving = nodes.filterKeys { it == source || it.path.startsWith(source.path + "/") }
            moving.keys.forEach(nodes::remove)
            for ((file, node) in moving) nodes[File(destination.path + file.path.removePrefix(source.path))] = node
        }
        override fun unlink(file: File) {
            check(nodes.getValue(file).stat.kind != NativeComponentUpdateRecovery.Kind.DIRECTORY)
            writes.add("unlink:$file"); nodes.remove(file)
        }
        override fun rmdir(directory: File) {
            check(nodes.keys.none { it != directory && it.parentFile == directory })
            writes.add("rmdir:$directory"); nodes.remove(directory)
        }
        override fun syncDirectory(directory: File) {
            if (failSync) throw IOException("private filesystem path must never be forwarded")
            writes.add("sync:$directory")
        }
    }
}
