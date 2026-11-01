package io.github.eslamasabry.oc.android.media.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Rules: Flutter `ClipboardImagePolicyTest` (copied). Bridge: Robolectric's ClipboardManager and ContentResolver. */
@RunWith(RobolectricTestRunner::class)
class ClipboardImagesTest {
    private object Direct : AppDispatchers {
        private val direct = UnconfinedTestDispatcher()
        override val main: CoroutineDispatcher = direct
        override val default: CoroutineDispatcher = direct
        override val io: CoroutineDispatcher = direct

        override fun serial(name: String): CoroutineDispatcher = direct
    }

    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val images = AndroidClipboardImages(context, Direct)
    private val uri = Uri.parse("content://test.provider/clip/1")

    private fun clip(vararg mimes: String) = ClipData(ClipDescription("picture", mimes), ClipData.Item(uri))

    @Test
    fun `B-MEDIA-007 image types are accepted and others refused`() {
        assertTrue(ClipboardImagePolicy.isImageMime("image/png"))
        assertTrue(ClipboardImagePolicy.isImageMime("IMAGE/JPEG; charset=x"))
        assertFalse(ClipboardImagePolicy.isImageMime("text/plain"))
        assertFalse(ClipboardImagePolicy.isImageMime("image/"))
        assertFalse(ClipboardImagePolicy.isImageMime(null))
        assertEquals("image/webp", ClipboardImagePolicy.firstImageMime(listOf("text/uri-list", null, "image/webp", "image/png")))
        assertNull(ClipboardImagePolicy.firstImageMime(listOf("text/plain", null)))
    }

    @Test
    fun `B-MEDIA-007 the clipboard read stops one byte past the 10 MB cap`() {
        val data = ByteArray(100) { it.toByte() }
        assertArrayEquals(data, ClipboardImagePolicy.readLimited(data.inputStream(), maxBytes = 100))
        assertThrows(ClipboardImagePolicy.TooLargeException::class.java) {
            ClipboardImagePolicy.readLimited(ByteArray(101).inputStream(), maxBytes = 100)
        }
        assertEquals(10 * 1024 * 1024, ClipboardImagePolicy.MAX_BYTES)
    }

    @Test
    fun `B-MEDIA-007 hasImage reads the description only and read returns the picture`() =
        runTest {
            assertFalse(images.hasImage())
            manager.setPrimaryClip(ClipData.newPlainText("t", "hello"))
            assertFalse(images.hasImage())
            assertEquals(Outcome.Ok(null), images.read(1000))

            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            shadowOf(context.contentResolver).registerInputStream(uri, png.inputStream())
            manager.setPrimaryClip(clip("image/png"))
            assertTrue(images.hasImage())
            val read = (images.read(1000) as Outcome.Ok).value!!
            assertEquals("image/png", read.mime)
            assertArrayEquals(png, read.bytes)
        }

    @Test
    fun `B-MEDIA-007 an oversize clipboard picture is refused, an unreadable one is no picture`() =
        runTest {
            shadowOf(context.contentResolver).registerInputStream(uri, ByteArray(11).inputStream())
            manager.setPrimaryClip(clip("image/jpeg"))
            assertEquals(Outcome.Failed(Failure.Media(MediaProblem.ClipboardImageTooLarge)), images.read(10))

            shadowOf(context.contentResolver).registerInputStream(uri, ByteArray(0).inputStream())
            assertEquals(Outcome.Ok(null), images.read(10))
        }
}
