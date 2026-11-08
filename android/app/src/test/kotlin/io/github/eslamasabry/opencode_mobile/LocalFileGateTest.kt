package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Opening a file hands out only shared-storage files, never the app's private storage. */
class LocalFileGateTest {
    @Test fun onlyAFileInsideSharedStorageIsAllowed() {
        val base = Files.createTempDirectory("gate").toFile()
        try {
            val shared = File(base, "sdcard").apply { mkdirs() }
            val priv = File(base, "files/rootfs").apply { mkdirs() }
            val pw = File(priv, "password").apply { writeText("x") }
            val apk = File(shared, "Download/a.apk").apply { parentFile.mkdirs(); writeText("x") }
            val other = File(shared, "Android/data/other/f.txt").apply { parentFile.mkdirs(); writeText("x") }
            assertEquals(apk.canonicalFile, LocalFileGate.allowed(shared, apk.path))
            assertNull(LocalFileGate.allowed(shared, pw.path))
            assertNull(LocalFileGate.allowed(shared, "${shared.path}/../files/rootfs/password"))
            assertNull(LocalFileGate.allowed(shared, other.path))
            assertNull(LocalFileGate.allowed(shared, shared.path))
            assertNull(LocalFileGate.allowed(shared, File(shared, "Download").path))
            assertNull(LocalFileGate.allowed(shared, File(shared, "missing.txt").path))
            assertNull(LocalFileGate.allowed(shared, ""))
            assertNull(LocalFileGate.allowed(shared, null))
            val link = File(shared, "Download/link")
            try {
                Files.createSymbolicLink(link.toPath(), pw.toPath())
                assertNull(LocalFileGate.allowed(shared, link.path))
            } catch (_: UnsupportedOperationException) {
            }
        } finally {
            base.deleteRecursively()
        }
    }
}
