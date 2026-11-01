package io.github.eslamasabry.oc.android.camera.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanWindowTest {
    @Test
    fun `the crop is a centred square of the frame's short side times the fraction`() {
        assertEquals(CropRect(424, 144, 856, 576), ScanWindow.cropFor(1280, 720, 0.6f))
        assertEquals(CropRect(144, 424, 576, 856), ScanWindow.cropFor(720, 1280, 0.6f))
    }

    @Test
    fun `the fraction is clamped so the crop never leaves the frame or collapses`() {
        assertEquals(CropRect(280, 0, 1000, 720), ScanWindow.cropFor(1280, 720, 4f))
        assertEquals(ScanWindow.cropFor(1280, 720, ScanWindow.MIN_FRACTION), ScanWindow.cropFor(1280, 720, 0f))
        assertEquals(ScanWindow.cropFor(1280, 720, ScanWindow.MIN_FRACTION), ScanWindow.cropFor(1280, 720, Float.NaN))
    }

    @Test
    fun `the on-screen window maps to a fraction of the preview's short side`() {
        assertEquals(0.5f, ScanWindow.fractionFor(windowPx = 540, previewShortSidePx = 1080))
        assertEquals(1f, ScanWindow.fractionFor(windowPx = 2000, previewShortSidePx = 1080))
        assertEquals(1f, ScanWindow.fractionFor(windowPx = 540, previewShortSidePx = 0))
    }

    @Test
    fun `B-PROTO-069 only the code the person aims at is decoded`() {
        val scene = QrFixtures.frame("scene-two-codes-1280x720.png")
        val window = scene.withCrop(ScanWindow.cropFor(scene.width, scene.height, 0.6f))
        assertEquals(QrFixtures.PAIRING, ZxingCoreDecoder().decode(window))
    }

    @Test
    fun `a code outside the window is not read`() {
        val scene = QrFixtures.frame("scene-code-outside-window-1280x720.png")
        assertEquals(QrFixtures.URL, ZxingCoreDecoder().decode(scene)) // the full frame does see it
        assertNull(ZxingCoreDecoder().decode(scene.withCrop(ScanWindow.cropFor(scene.width, scene.height, 0.6f))))
    }
}
