package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.SetupCast
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/setup_cast.dart. */
class SetupCastParityTest {
    init {
        SceneProbe.ensure()
    }

    @Test
    fun `B-KIT-O29-020 the portal mark sits in unit space, centred on the origin`() {
        assertNear(Offset.Zero, SetupCast.m(54f, 54f), "mark centre")
        assertNear(Offset(.5f, .5f), SetupCast.m(84f, 84f), "mark corner")
        assertNear(SetupCast.m(61f, 31f), SceneProbe.start(SetupCast.upper), "upper start")
        assertNear(SetupCast.m(31f, 64f), SceneProbe.end(SetupCast.upper), "upper end")
        assertNear(SetupCast.m(77f, 44f), SceneProbe.start(SetupCast.lower), "lower start")
        assertNear(SetupCast.m(44f, 77f), SceneProbe.end(SetupCast.lower), "lower end")
    }

    @Test
    fun `B-KIT-O29-021 prompt, check and phone paths follow the Dart builders`() {
        val prompt = SetupCast.prompt(Offset(66f, 26f), 8f)
        assertNear(Offset(66f, 26f), SceneProbe.start(prompt), "prompt start")
        assertNear(Offset(66f, 34f), SceneProbe.end(prompt), "prompt end")
        assertNear(Offset(70f, 30f), SceneProbe.along(prompt, .5f), "prompt tip", .05f)
        val check = SetupCast.check(Offset(80f, 58f), 15f)
        assertNear(Offset(80f - 15f * .36f, 58f + 15f * .02f), SceneProbe.start(check), "check start")
        assertNear(Offset(80f + 15f * .38f, 58f - 15f * .24f), SceneProbe.end(check), "check end")
        val phone = SetupCast.phone(Rect(54f, 8f, 106f, 112f), 12f)
        assertTrue(phone.getBounds().let { it.left <= 54.01f && it.right >= 105.99f && it.top <= 8.01f && it.bottom >= 111.99f })
    }

    @Test
    fun `B-KIT-O29-022 the cursor blinks twice per loop and shows when still`() {
        assertTrue(SetupCast.cursorOn(SceneProbe.frame(looping = false, loop = .3f)))
        assertTrue(SetupCast.cursorOn(SceneProbe.frame(looping = true, loop = .1f)))
        assertFalse(SetupCast.cursorOn(SceneProbe.frame(looping = true, loop = .3f)))
        assertTrue(SetupCast.cursorOn(SceneProbe.frame(looping = true, loop = .55f)))
        assertFalse(SetupCast.cursorOn(SceneProbe.frame(looping = true, loop = .8f)))
    }

    @Test
    fun `B-KIT-O29-023 orbit starts at the top and turns clockwise`() {
        assertNear(Offset(80f, 4f), SetupCast.orbit(Offset(80f, 60f), 56f, 0f), "top")
        assertNear(Offset(136f, 60f), SetupCast.orbit(Offset(80f, 60f), 56f, .25f), "right")
        assertNear(Offset(80f, 116f), SetupCast.orbit(Offset(80f, 60f), 56f, .5f), "bottom")
    }

    private fun draw(block: (Canvas) -> Unit): SceneProbe.Pixels {
        val image = ImageBitmap(120, 120)
        block(Canvas(image))
        return SceneProbe.Pixels(image.toPixelMap(), 1f)
    }

    @Test
    fun `B-KIT-O29-024 the portal draws at its size with a stroke of a seventh`() {
        // Size 60 at (60, 60): the upper bracket's top runs along y = 60 + 60 * (31 - 54) / 60 = 37.
        val px = draw { SetupCast.portal(it, Offset(60f, 60f), 60f, accent) }
        assertColor(accent, px.at(58f, 37f), "upper top")
        assertColor(accent, px.at(58f, 40.5f), "stroke reaches size/7/2 = 4.3 below the line")
        assertColor(none, px.at(58f, 43f), "and no further")
        val open = draw { SetupCast.portal(it, Offset(60f, 60f), 60f, accent, open = 6f) }
        assertColor(accent, px.at(58f, 37f), "closed")
        assertColor(accent, open.at(52f, 31f), "open moves the upper bracket up-left by open")
        val half = draw { SetupCast.portal(it, Offset(60f, 60f), 60f, accent, upper = 0f) }
        assertColor(none, half.at(58f, 37f), "upper not drawn at 0")
    }

    @Test
    fun `B-KIT-O29-025 a sparkle pops from nothing`() {
        assertColor(none, draw { SetupCast.sparkle(it, Offset(60f, 60f), 10f, accent, pop = 0f) }.at(60f, 60f), "pop 0")
        assertColor(accent, draw { SetupCast.sparkle(it, Offset(60f, 60f), 10f, accent) }.at(60f, 60f), "centre")
        assertColor(accent, draw { SetupCast.sparkle(it, Offset(60f, 60f), 10f, accent) }.at(60f, 55f), "towards the top point")
        assertColor(none, draw { SetupCast.sparkle(it, Offset(60f, 60f), 10f, accent) }.at(54f, 54f), "pinched waist")
    }

    @Test
    fun `B-KIT-O29-026 the cast stays inside its unit box`() {
        assertTrue(Size(1f, 1f).width == 1f)
        val b = SetupCast.upper.getBounds()
        assertTrue(b.left >= -.4f && b.top >= -.4f && b.right <= .4f && b.bottom <= .4f, "upper within the mark: $b")
    }
}
