package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.line
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.SceneDraw
import io.github.eslamasabry.oc.ui.kit.scene.ServersLaptop
import io.github.eslamasabry.oc.ui.kit.scene.ServersPhone
import io.github.eslamasabry.oc.ui.kit.scene.ServersPortal
import io.github.eslamasabry.oc.ui.kit.scene.paintSparkle
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/scenes/servers_cast.dart. */
class ServersCastParityTest {
    init {
        SceneProbe.ensure()
    }

    private val phone = ServersPhone(Rect(26f, 18f, 54f, 66f))
    private val laptop = ServersLaptop(Rect(112f, 16f, 168f, 56f))

    @Test
    fun `B-KIT-O29-070 the phone's corners, speaker and three screen lines scale with its body`() {
        assertNear(7f, phone.radius, "corner = width x .25")
        assertNear(Offset(40f - 28f * .14f, 18f + 48f * .1f), SceneProbe.start(phone.speaker), "speaker start")
        assertNear(Offset(40f + 28f * .14f, 18f + 48f * .1f), SceneProbe.end(phone.speaker), "speaker end")
        assertEquals(listOf(.34f to .5f, .48f to .34f, .62f to .44f), ServersPhone.LINES)
        assertEquals(3, phone.lines.size)
        ServersPhone.LINES.forEachIndexed { i, (y, w) ->
            assertNear(Offset(26f + 28f * .24f, 18f + 48f * y), SceneProbe.start(phone.lines[i]), "line $i start")
            assertNear(Offset(26f + 28f * (.24f + w), 18f + 48f * y), SceneProbe.end(phone.lines[i]), "line $i end")
        }
    }

    @Test
    fun `B-KIT-O29-071 the laptop's base reaches past its screen with a hinge notch`() {
        assertNear(40f * .14f, laptop.radius, "corner = height x .14")
        assertNear(Offset(112f - 56f * .12f, 65f), SceneProbe.start(laptop.base), "base start")
        assertNear(Offset(168f + 56f * .12f, 65f), SceneProbe.end(laptop.base), "base end")
        assertNear(Offset(140f - 56f * .12f, 65f), SceneProbe.start(laptop.hinge), "hinge start")
        assertNear(Offset(140f + 56f * .12f, 65f), SceneProbe.end(laptop.hinge), "hinge end")
    }

    @Test
    fun `B-KIT-O29-072 the portal mark keeps the mark svg's own coordinates`() {
        assertNear(Offset(61f, 31f), SceneProbe.start(ServersPortal.upper), "upper start")
        assertNear(Offset(31f, 64f), SceneProbe.end(ServersPortal.upper), "upper end")
        assertNear(Offset(77f, 44f), SceneProbe.start(ServersPortal.lower), "lower start")
        assertNear(Offset(44f, 77f), SceneProbe.end(ServersPortal.lower), "lower end")
    }

    private fun draw(block: (Canvas) -> Unit): SceneProbe.Pixels {
        val image = ImageBitmap(200, 120)
        block(Canvas(image))
        return SceneProbe.Pixels(image.toPixelMap(), 1f)
    }

    @Test
    fun `B-KIT-O29-073 each part draws itself in from t and stays put at 1`() {
        val p = SceneProbe.palette
        val done = draw { phone.paint(it, p, 1f); laptop.paint(it, p, 1f) }
        assertColor(muted, done.at(26f, 42f), "phone outline (muted)")
        assertColor(muted, done.at(140f, 65f), "laptop base (muted)")
        assertColor(line, done.at(30f + 4f, 18f + 48f * .34f), "first screen line (line role)", .1f)
        val none0 = draw { phone.paint(it, p, 0f); laptop.paint(it, p, 0f) }
        assertColor(none, none0.at(26f, 42f), "nothing at t=0")
        val lit = draw { phone.paint(it, p, 1f, screen = accent) }
        assertColor(accent, lit.at(30f + 4f, 18f + 48f * .34f), "screen lines in the given colour", .1f)
    }

    @Test
    fun `B-KIT-O29-074 the portal lands its dot in the middle of the mark`() {
        val px = draw { ServersPortal.paint(it, Offset(100f, 60f), 60f, muted, 1f, dot = 1f, dotColor = accent) }
        assertColor(accent, px.at(100f, 60f), "dot")
        assertColor(muted, px.at(100f, 37f), "upper bracket")
        val empty = draw { ServersPortal.paint(it, Offset(100f, 60f), 60f, muted, 0f) }
        assertColor(none, empty.at(100f, 37f), "nothing at t=0")
    }

    @Test
    fun `B-KIT-O29-075 a sparkle is a plus of two strokes`() {
        val px = draw { paintSparkle(it, Offset(100f, 60f), 5f, SceneDraw.pen(accent, SceneDraw.HAIRLINE)) }
        assertColor(accent, px.at(100f, 56f), "vertical")
        assertColor(accent, px.at(104f, 60f), "horizontal")
        assertColor(none, px.at(103f, 57f), "no diagonal")
    }
}
