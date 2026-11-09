package io.github.eslamasabry.oc.ui.kit.image

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint

class KitImagesTest {
    @AfterTest fun reset() {
        KitImages.clearMemory()
        KitImages.diskFor = null
    }

    private fun picture(w: Int, h: Int): ByteArray {
        val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(w, h)) }
        Canvas(bitmap).drawPaint(Paint().apply { color = 0xFF3366CC.toInt() })
        return Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @Test
    fun `a 4000x3000 picture decodes to a 400 px tile, far below full size`() {
        val full = 4000L * 3000 * 4
        val decoded = assertNotNull(KitImages.decode(picture(4000, 3000), 400))
        assertEquals(400, maxOf(decoded.width, decoded.height))
        val bytes = decoded.width.toLong() * decoded.height * 4
        assertTrue(bytes * 50 < full, "tile decode held $bytes bytes against $full at full size")
    }

    @Test
    fun `a small picture is not enlarged and keeps its size`() {
        val decoded = assertNotNull(KitImages.decode(picture(120, 80), 400))
        assertEquals(120 to 80, decoded.width to decoded.height)
    }

    @Test
    fun `the second request is a cache hit and does not read again`() = runBlocking {
        var reads = 0
        val bytes = picture(1200, 900)
        val first = KitImages.load("pic:a", 300) { reads++; bytes }
        assertNotNull(first)
        assertEquals(first, KitImages.peek("pic:a", 300))
        val second = KitImages.load("pic:a", 300) { reads++; bytes }
        assertEquals(1, reads)
        assertTrue(first === second, "the very same bitmap comes back, so a lazy item does not blink")
    }

    @Test
    fun `trim frees the cache`() = runBlocking {
        KitImages.load("pic:b", 300) { picture(1200, 900) }
        assertTrue(KitImages.memoryBytes > 0)
        KitImages.trim(0f)
        assertEquals(0L, KitImages.memoryBytes)
        assertNull(KitImages.peek("pic:b", 300))
    }

    @Test
    fun `a partial trim keeps only the fraction of the budget`() = runBlocking {
        repeat(6) { KitImages.load("pic:$it", 1500) { picture(2000, 1500) } }
        KitImages.trim(0.25f)
        assertTrue(KitImages.memoryBytes <= KitImages.memoryBudgetBytes / 4)
    }

    @Test
    fun `memory never passes its budget`() = runBlocking {
        repeat(30) { KitImages.load("big:$it", 2000) { picture(2000, 1500) } }
        assertTrue(KitImages.memoryBytes <= KitImages.memoryBudgetBytes)
    }

    @Test
    fun `refused bytes give null and are not cached`() = runBlocking {
        assertNull(KitImages.load("bad", 300) { byteArrayOf(1, 2, 3) })
        assertNull(KitImages.peek("bad", 300))
    }

    @Test
    fun `a scoped load writes a thumbnail to disk and a later open skips the read`() = runBlocking {
        val store = HashMap<String, ByteArray>()
        val scopes = ArrayList<String>()
        KitImages.diskFor = { scope ->
            scopes += scope
            object : KitImageDisk {
                override suspend fun get(key: String) = store[key]
                override suspend fun put(key: String, bytes: ByteArray) { store[key] = bytes }
            }
        }
        KitImages.load("pic:d", 300, scope = "p1") { picture(1200, 900) }
        assertEquals(1, store.size)
        KitImages.clearMemory()
        var reads = 0
        val again = KitImages.load("pic:d", 300, scope = "p1") { reads++; null }
        assertNotNull(again)
        assertEquals(0, reads)
        assertEquals(listOf("p1", "p1"), scopes)
    }
}
