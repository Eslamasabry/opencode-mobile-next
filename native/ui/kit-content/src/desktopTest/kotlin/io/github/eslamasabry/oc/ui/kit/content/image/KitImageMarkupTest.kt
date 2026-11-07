package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** KitImageMarkup's geometry and result (lib/ui/kit/kit_image_markup.dart). */
@OptIn(ExperimentalResourceApi::class)
class KitImageMarkupTest {
    private val full = Rect(0f, 0f, 800f, 600f)

    @Test fun `the picture fits the room and centres`() {
        val (scale, origin) = MarkupGeometry.fit(full, Size(400f, 600f))
        assertEquals(0.5f, scale)
        assertEquals(Offset(0f, 150f), origin)
    }

    @Test fun `a corner never crosses the picture or shrinks the crop under 48 dp`() {
        val r = MarkupGeometry.dragCorner(full, full, 0, Offset(-50f, -50f), 1f, 48f)
        assertEquals(full, r) // already at the edge
        val small = MarkupGeometry.dragCorner(full, full, 3, Offset(-900f, -900f), 0.5f, 48f)
        assertEquals(96f, small.width) // 48 dp at half scale is 96 picture px
        assertEquals(96f, small.height)
    }

    @Test fun `the crop moves only inside the picture`() {
        val crop = Rect(100f, 100f, 300f, 300f)
        assertEquals(Rect(0f, 0f, 200f, 200f), MarkupGeometry.moveCrop(crop, full, Offset(-500f, -500f), 1f))
        assertEquals(Rect(600f, 400f, 800f, 600f), MarkupGeometry.moveCrop(crop, full, Offset(900f, 900f), 1f))
    }

    @Test fun `the result is the cropped picture at its own resolution`() {
        val image = ImageBitmap(800, 600)
        val stroke = MarkupStroke(Color.Red, 4f, Offset(10f, 10f)).apply { points.add(Offset(150f, 90f)) }
        val png = renderMarkup(image, Rect(0f, 0f, 200f, 100f), listOf(stroke))
        assertTrue(png.size <= KIT_MARKUP_MAX_BYTES)
        val back = png.decodeToImageBitmap()
        assertEquals(200, back.width)
        assertEquals(100, back.height)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun `done with nothing drawn hands back the original, a stroke hands back a new picture`() = runComposeUiTest {
        val original = run {
            val image = ImageBitmap(40, 30)
            encodeMarkupPng(image)
        }
        val results = mutableListOf<KitMarkupResult?>()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitImageMarkup(original, "shot.png", onResult = { results += it })
            }
        }
        waitForIdle()
        onNodeWithText("Done").performClick()
        waitForIdle()
        assertEquals(false, results.single()!!.changed)
        assertTrue(results.single()!!.bytes.contentEquals(original))
        onNodeWithTag(KitMarkupTags.CANVAS).performTouchInput {
            down(center)
            moveBy(Offset(30f, 10f))
            up()
        }
        waitForIdle()
        onNodeWithText("Send").performClick()
        waitForIdle()
        val sent = results.last()!!
        assertTrue(sent.changed && sent.send)
        assertEquals(40, sent.bytes.decodeToImageBitmap().width)
    }
}
