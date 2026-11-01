package io.github.eslamasabry.oc.ui.kit.content

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.content.image.KitZoomState
import io.github.eslamasabry.oc.ui.kit.content.image.ZoomMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plan 10 §9.3 zoom limits (Flutter `KitZoomMode.fit`: floor = fit, ceiling = 5 × fit). */
class ZoomMathTest {
    private val viewport = Size(400f, 800f)

    @Test
    fun `MEDIA-VOICE-42 fit is the floor and five times fit the ceiling`() {
        assertEquals(1f, ZoomMath.clampScale(0.2f))
        assertEquals(5f, ZoomMath.clampScale(9f))
        assertEquals(2f, ZoomMath.clampScale(2f))
    }

    @Test
    fun `MEDIA-VOICE-42 a zoomed picture never moves past its edges`() {
        assertEquals(Offset.Zero, ZoomMath.clampOffset(Offset(50f, 50f), viewport, 1f))
        assertEquals(Offset(200f, -400f), ZoomMath.clampOffset(Offset(900f, -900f), viewport, 2f))
    }

    @Test
    fun `MEDIA-VOICE-42 a pinch keeps the point under the fingers still`() {
        val focus = Offset(300f, 200f)
        val after = ZoomMath.offsetAfterZoom(Offset.Zero, focus, viewport, from = 1f, to = 2f)
        // The content point under the focus (relative to the centre) is (100, −200); at 2× it must still land there.
        val centre = Offset(200f, 400f)
        val point = Offset(100f, -200f)
        assertEquals(focus, centre + after + point * 2f)
    }

    @Test
    fun `MEDIA-VOICE-42 the buttons step, stop at the limits, and Fit returns to rest`() {
        val state = KitZoomState()
        assertTrue(state.isFitted)
        assertFalse(state.canZoomOut)
        repeat(10) { state.zoomIn() }
        assertEquals(5f, state.scale)
        assertFalse(state.canZoomIn)
        state.zoomOut()
        assertTrue(state.scale < 5f)
        state.fit()
        assertTrue(state.isFitted)
        assertEquals(Offset.Zero, state.offset)
    }
}
