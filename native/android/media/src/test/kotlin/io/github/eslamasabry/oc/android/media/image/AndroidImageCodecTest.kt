package io.github.eslamasabry.oc.android.media.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.robolectric.RuntimeEnvironment
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.media.attach.MagicBytes
import io.github.eslamasabry.oc.engine.media.attach.PhotoRules
import io.github.eslamasabry.oc.engine.media.attach.PreparedImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Real decode/encode through Robolectric's native graphics (plan 10 §10.3 step 2). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AndroidImageCodecTest {
    private object Direct : AppDispatchers {
        private val direct = UnconfinedTestDispatcher()
        override val main: CoroutineDispatcher = direct
        override val default: CoroutineDispatcher = direct
        override val io: CoroutineDispatcher = direct

        override fun serial(name: String): CoroutineDispatcher = direct
    }

    private val codec = AndroidImageCodec(RuntimeEnvironment.getApplication().contentResolver, Direct)

    private fun picture(width: Int, height: Int, alpha: Boolean, format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (alpha) Color.argb(128, 10, 20, 30) else Color.rgb(10, 20, 30))
        bitmap.setHasAlpha(alpha)
        return ByteArrayOutputStream().use {
            bitmap.compress(format, 90, it)
            it.toByteArray()
        }
    }

    private fun ok(o: Outcome<PreparedImage>): PreparedImage = (o as Outcome.Ok).value

    @Test
    fun `B-MEDIA-007 a large opaque photo is scaled to 2048 and sent as JPEG`() {
        val image = ok(codec.prepare(picture(4000, 1000, alpha = false, Bitmap.CompressFormat.JPEG), "image/jpeg", PhotoRules.Prompt))
        assertEquals("image/jpeg", image.mime)
        assertEquals(2048, image.width)
        assertEquals(512, image.height)
        assertEquals("image/jpeg", MagicBytes.imageMime(image.bytes))
    }

    @Test
    fun `B-MEDIA-007 a picture with alpha stays PNG and small ones are not upscaled`() {
        val image = ok(codec.prepare(picture(300, 200, alpha = true, Bitmap.CompressFormat.PNG), "image/png", PhotoRules.Prompt))
        assertEquals("image/png", image.mime)
        assertEquals(300, image.width)
        assertEquals("image/png", MagicBytes.imageMime(image.bytes))
    }

    @Test
    fun `B-MEDIA-008 undecodable bytes are unsupported and a small WebP is kept byte for byte`() {
        assertEquals(
            Outcome.Failed(Failure.Media(MediaProblem.PhotoUnsupported)),
            codec.prepare("not a picture".encodeToByteArray(), "image/png", PhotoRules.Prompt),
        )
        @Suppress("DEPRECATION")
        val webp = picture(64, 64, alpha = false, Bitmap.CompressFormat.WEBP)
        assertSame(webp, ok(codec.prepare(webp, "image/webp", PhotoRules.Prompt)).bytes)
    }

    @Test
    fun `B-MEDIA-008 a re-encode over the byte limit is refused`() {
        val tiny = PhotoRules(maxEdgePx = 2048, jpegQuality = 85, maxBytes = 10)
        assertEquals(
            Outcome.Failed(Failure.Media(MediaProblem.PhotoTooLarge)),
            codec.prepare(picture(400, 400, alpha = false, Bitmap.CompressFormat.JPEG), "image/jpeg", tiny),
        )
    }
}
