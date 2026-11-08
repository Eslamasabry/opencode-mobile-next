package io.github.eslamasabry.oc.android.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-184: only a regular file in shared storage goes to another app. */
class LocalFileGateTest {
    private val base = Files.createTempDirectory("gate").toFile()
    private val shared = File(base, "emulated/0").apply { mkdirs() }
    private val private = File(base, "data/files").apply { mkdirs() }

    @AfterTest fun clean() { base.deleteRecursively() }

    private fun file(dir: File, name: String) = File(dir, name).apply { parentFile.mkdirs(); writeText("x") }

    @Test fun `a file in a shared folder is allowed`() {
        val f = file(shared, "Download/a.apk")
        assertEquals(f.canonicalFile, LocalFileGate.allowed(shared, f.path))
    }

    @Test fun `dot dot cannot climb out of shared storage`() {
        val secret = file(private, "server-password")
        assertNull(LocalFileGate.allowed(shared, "${shared.path}/Download/../../../data/files/${secret.name}"))
    }

    @Test fun `a link out of shared storage is judged by where it lands`() {
        val secret = file(private, "server-password")
        val link = File(shared, "Download/looks-fine.txt").also { it.parentFile.mkdirs() }
        Files.createSymbolicLink(link.toPath(), secret.toPath())
        assertNull(LocalFileGate.allowed(shared, link.path))
    }

    @Test fun `another app's Android folder is refused`() {
        assertNull(LocalFileGate.allowed(shared, file(shared, "Android/data/other/x.txt").path))
    }

    @Test fun `the storage root, a folder, a missing file and a blank path are refused`() {
        assertNull(LocalFileGate.allowed(shared, shared.path))
        assertNull(LocalFileGate.allowed(shared, File(shared, "Download").apply { mkdirs() }.path))
        assertNull(LocalFileGate.allowed(shared, "${shared.path}/nope.txt"))
        assertNull(LocalFileGate.allowed(shared, ""))
        assertNull(LocalFileGate.allowed(shared, null))
    }

    @Test fun `a sibling folder that shares the root's name prefix is refused`() {
        val twin = File(base, "emulated/01").apply { mkdirs() }
        assertNull(LocalFileGate.allowed(shared, file(twin, "a.txt").path))
    }
}
