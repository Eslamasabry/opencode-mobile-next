package io.github.eslamasabry.oc.android.media.image

import io.github.eslamasabry.oc.android.media.image.ImageScale.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure sizing and memory rules (plan 10 §10.3 step 2; B-MEDIA-007 "photos are scaled down before reading"). */
class ImageScaleTest {
    @Test
    fun `B-MEDIA-007 the long edge goes to 2048 with the aspect kept and never upscales`() {
        assertEquals(Size(2048, 1536), ImageScale.target(4000, 3000, 2048))
        assertEquals(Size(1152, 2048), ImageScale.target(1800, 3200, 2048))
        assertEquals(Size(800, 600), ImageScale.target(800, 600, 2048))
        assertEquals(Size(2048, 1), ImageScale.target(100_000, 10, 2048))
    }

    @Test
    fun `B-MEDIA-007 decode sampling stays above the target and inside the memory cap`() {
        val target = ImageScale.target(6000, 4500, 2048)
        assertEquals(2, ImageScale.sampleSize(6000, 4500, target)) // 3000×2250 still covers 2048×1536; 1500×1125 would not
        // 8000×6000 at sample 2 would be a 48 MB bitmap: the memory cap wins over the last bit of sharpness.
        val sample = ImageScale.sampleSize(8000, 6000, ImageScale.target(8000, 6000, 2048))
        assertEquals(4, sample)
        assertTrue(ImageScale.decodeBytes(8000 / sample, 6000 / sample) <= ImageScale.MAX_DECODE_BYTES)
        // A huge panorama: the sample grows until the decode fits the cap.
        val wide = ImageScale.target(40_000, 4_000, 2048)
        val wideSample = ImageScale.sampleSize(40_000, 4_000, wide)
        assertTrue(ImageScale.decodeBytes(40_000 / wideSample, 4_000 / wideSample) <= ImageScale.MAX_DECODE_BYTES)
        assertEquals(1, ImageScale.sampleSize(800, 600, ImageScale.target(800, 600, 2048)))
    }

    @Test
    fun `B-MEDIA-008 a decompression bomb header is refused before decoding`() {
        assertFalse(ImageScale.acceptable(100_000, 100_000))
        assertFalse(ImageScale.acceptable(0, 10))
        assertTrue(ImageScale.acceptable(8000, 6000))
    }

    @Test
    fun `B-MEDIA-007 alpha encodes as PNG, otherwise JPEG, and small GIF or WebP stay as they are`() {
        assertEquals(ImageScale.Encoding.Png, ImageScale.encodingFor(hasAlpha = true))
        assertEquals(ImageScale.Encoding.Jpeg, ImageScale.encodingFor(hasAlpha = false))
        val max = 10 * 1024 * 1024
        assertTrue(ImageScale.keepOriginal("image/gif", 1000, 500, 500, max, 2048))
        assertTrue(ImageScale.keepOriginal("image/webp", 1000, 2048, 10, max, 2048))
        assertFalse(ImageScale.keepOriginal("image/gif", 1000, 4000, 500, max, 2048))
        assertFalse(ImageScale.keepOriginal("image/gif", max + 1, 500, 500, max, 2048))
        assertFalse(ImageScale.keepOriginal("image/png", 1000, 500, 500, max, 2048))
    }
}
