package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import io.github.eslamasabry.oc.ui.kit.scene.SceneArt
import io.github.eslamasabry.oc.ui.kit.scene.SceneFrame
import io.github.eslamasabry.oc.ui.kit.scene.ScenePalette
import io.github.eslamasabry.oc.ui.kit.scene.paintScene
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paints a scene into a bitmap with a palette of pure, distinct colours, so a test can ask "what colour is at this
 * scene point" and prove geometry, staging and colour roles without a golden.
 */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
internal object SceneProbe {
    init {
        // Paths and bitmaps need Compose's Skiko graphics; a composition registers it, a plain unit test must.
        androidx.compose.ui.platform.registerSkikoComposeImplementation()
    }

    /** Touch the probe so the graphics implementation is registered before a scene builds its paths. */
    fun ensure() {}

    val accent = Color(0xFF00FF00)
    val ink = Color(0xFF0000FF)
    val muted = Color(0xFFFF0000)
    val line = Color(0xFFFFFF00)
    val surface = Color(0xFF00FFFF)
    val none = Color.Transparent

    val palette =
        ScenePalette(
            accent = accent,
            accentSoft = accent.copy(alpha = .12f),
            ink = ink,
            muted = muted,
            line = line,
            surface = surface,
            success = Color(0xFF808080),
            warning = Color(0xFFFF8000),
            failure = ink,
            progress = accent,
        )

    fun frame(entrance: Float = 1f, loop: Float = 0f, looping: Boolean = false): SceneFrame =
        SceneFrame(entrance, loop, looping, palette)

    /** The scene painted at [scale] pixels per scene unit, box at the origin. */
    fun render(scene: SceneArt, frame: SceneFrame = frame(), scale: Float = 4f, mirror: Boolean = false): Pixels {
        val w = (scene.box.width * scale).roundToInt()
        val h = (scene.box.height * scale).roundToInt()
        val image = ImageBitmap(w, h)
        paintScene(Canvas(image), Size(w.toFloat(), h.toFloat()), scene, frame, mirror)
        return Pixels(image.toPixelMap(), scale)
    }

    class Pixels(val map: PixelMap, val scale: Float) {
        fun at(x: Float, y: Float): Color =
            map[(x * scale).toInt().coerceIn(0, map.width - 1), (y * scale).toInt().coerceIn(0, map.height - 1)]

        fun at(p: Offset): Color = at(p.x, p.y)

        /** Every pixel, for "this colour appears nowhere" checks. */
        fun all(): Sequence<Color> = sequence { for (y in 0 until map.height) for (x in 0 until map.width) yield(map[x, y]) }
    }

    fun close(actual: Color, expected: Color, tol: Float = .06f): Boolean =
        abs(actual.alpha - expected.alpha) <= tol &&
            (expected.alpha == 0f ||
                (abs(actual.red - expected.red) <= tol && abs(actual.green - expected.green) <= tol && abs(actual.blue - expected.blue) <= tol))

    fun assertColor(expected: Color, actual: Color, what: String, tol: Float = .06f) {
        assertTrue(close(actual, expected, tol), "$what: expected $expected, got $actual")
    }

    fun assertNear(expected: Float, actual: Float, what: String, tol: Float = .01f) {
        assertTrue(abs(expected - actual) <= tol, "$what: expected $expected, got $actual")
    }

    fun assertNear(expected: Offset, actual: Offset, what: String, tol: Float = .01f) {
        assertNear(expected.x, actual.x, "$what x", tol)
        assertNear(expected.y, actual.y, "$what y", tol)
    }

    /** The point at [fraction] of [path]'s length. */
    fun along(path: Path, fraction: Float): Offset {
        val m = PathMeasure().apply { setPath(path, false) }
        return m.getPosition(m.length * fraction)
    }

    fun start(path: Path): Offset = along(path, 0f)

    fun end(path: Path): Offset = along(path, 1f)

    fun <T> assertOrder(expected: List<String>, entries: List<T>) {
        assertEquals(expected, entries.map { it.toString() })
    }
}
