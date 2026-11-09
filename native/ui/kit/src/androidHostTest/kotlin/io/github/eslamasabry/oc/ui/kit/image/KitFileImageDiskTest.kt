package io.github.eslamasabry.oc.ui.kit.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KitFileImageDiskTest {
    private fun tmp(): File = File.createTempFile("imgs", "").apply { delete(); mkdirs() }

    @Test
    fun `thumbnails live in a folder named for the profile and clear with it`() = runBlocking {
        val cache = tmp()
        val disk = KitFileImageDisk(KitFileImageDisk.folder(cache, "p1"))
        val other = KitFileImageDisk(KitFileImageDisk.folder(cache, "p2"))
        disk.put("k", byteArrayOf(1, 2, 3))
        other.put("k", byteArrayOf(9))
        assertContentEquals(byteArrayOf(1, 2, 3), disk.get("k"))
        assertEquals("oc.imageCache.p1", KitFileImageDisk.folder(cache, "p1").name)
        assertTrue(KitFileImageDisk.clear(cache, "p1"))
        assertFalse(KitFileImageDisk.folder(cache, "p1").exists())
        assertNull(disk.get("k"))
        assertContentEquals(byteArrayOf(9), other.get("k"))
    }

    @Test
    fun `the oldest thumbnails go when the folder passes its bound`() = runBlocking {
        val dir = tmp()
        val disk = KitFileImageDisk(dir, maxBytes = 25)
        repeat(4) { i -> disk.put("k$i", ByteArray(10)) }
        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 25)
    }
}
