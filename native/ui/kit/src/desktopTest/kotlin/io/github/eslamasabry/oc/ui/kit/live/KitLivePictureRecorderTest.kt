package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlassPicture
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlassPictureHolder
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Black-frame (release blocker, Galaxy S22 2026-11-16): Home → + and Close with a live background (Silk; Glass or
 * Solid) showed 1 to 6 fully black frames; with Background Off, none. While a page is pushed or popped two framed
 * pages show the theme root's one shared live picture (`KitGlassPictures`). Every page used to record that one layer
 * on each of its draws, so during the slide the layer was recorded twice a frame, by two pages, each with its own
 * content.
 *
 * Desktop Skia does not show the device's black pixels, so these tests pin the ownership rule instead: the shared
 * picture has exactly one recorder at a time; every other page only draws the recording; when the recorder leaves, a
 * page that stays takes the recording over, and the picture is never released while a page still shows it.
 */
@OptIn(ExperimentalTestApi::class)
class KitLivePictureRecorderTest {
    private fun appearance(style: Style) =
        KitAppearance(
            style = style,
            mode = AppearanceMode.Dark,
            motion = MotionLevel.Full,
            liveBackground = true,
            liveStyle = "mesh",
        )

    private val platform =
        FakeKitPlatform(frost = true, liveShaders = { source -> DesktopLiveShader.compile(source) })

    /** One recording: the frame it was made in, the picture and the page that made it. */
    private data class Recording(val frame: Int, val picture: KitGlassPicture, val by: KitGlassPictureHolder)

    /**
     * Framed pages of one size on top of each other, as during a push or a pop; [shown] says which are composed and
     * [drawn] (read in the draw) which of those are drawn: a page kept composed under another but no longer drawn.
     */
    @Composable
    private fun Pages(shown: List<Boolean>, drawn: () -> List<Boolean> = { shown }, seen: (KitLiveShare?) -> Unit) {
        Box(Modifier.fillMaxSize()) {
            shown.forEachIndexed { i, on ->
                if (on) {
                    key(i) {
                        Box(Modifier.fillMaxSize().drawWithContent { if (drawn()[i]) drawContent() }) {
                            KitScreen(page = true) {
                                seen(LocalKitLiveShare.current)
                                KitText("Page $i")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `B-KIT-LIVE-054 two pages showing the shared live picture record it once a frame, always by one page`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            runDesktopComposeUiTest(200, 400) {
                var share: KitLiveShare? = null
                var frame = 0
                val log = ArrayList<Recording>()
                mainClock.autoAdvance = false
                setContent {
                    KitTheme(appearance(style), platform, "en") {
                        Pages(listOf(true, true)) { share = it }
                        SideEffect { share?.pictures?.onRecord = { p, by -> log += Recording(frame, p, by) } }
                    }
                }
                repeat(60) {
                    frame++
                    mainClock.advanceTimeByFrame()
                }
                val pictures = assertNotNull(share).pictures
                assertEquals(1, pictures.count, "$style: one picture for two pages of one size")
                assertTrue(log.isNotEmpty(), "$style: the picture was recorded")
                for ((f, inFrame) in log.groupBy { it.frame }) {
                    val pages = inFrame.map { it.by }.toSet().size
                    assertEquals(1, inFrame.size, "$style: frame $f recorded it ${inFrame.size} times ($pages pages)")
                }
                assertEquals(1, log.map { it.by }.toSet().size, "$style: one page records it while both show it")
                val picture = log.first().picture
                assertEquals(2, picture.users, "$style: both pages hold the picture")
                assertSame(log.first().by, picture.recorder, "$style: the recorder is the page that records")
            }
        }
    }

    @Test
    fun `B-KIT-LIVE-055 the page that stays takes the recording over and the picture is never released while shown`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            runDesktopComposeUiTest(200, 400) {
                var share: KitLiveShare? = null
                var frame = 0
                val log = ArrayList<Recording>()
                // A push: the covered page first, then the arriving one; then the covered page leaves (the push
                // settles) while the arriving one stays.
                var shown by mutableStateOf(listOf(true, false))
                mainClock.autoAdvance = false
                setContent {
                    KitTheme(appearance(style), platform, "en") {
                        Pages(shown) { share = it }
                        SideEffect { share?.pictures?.onRecord = { p, by -> log += Recording(frame, p, by) } }
                    }
                }
                fun frames(n: Int) = repeat(n) {
                    frame++
                    mainClock.advanceTimeByFrame()
                }
                frames(10)
                val first = log.last().by
                val picture = log.last().picture
                val layer = assertNotNull(picture.layer)
                shown = listOf(true, true)
                frames(20)
                assertEquals(2, picture.users, "$style: both pages hold the one picture during the push")
                assertTrue(log.all { it.by === first }, "$style: the arriving page only draws the recording")
                // The covered page leaves while the arriving one stays.
                val before = log.size
                shown = listOf(false, true)
                frames(1)
                assertFalse(layer.isReleased, "$style: the picture is not released while a page still shows it")
                assertSame(picture, share!!.pictures.at(layer.size), "$style: the same picture stays")
                assertEquals(1, picture.users, "$style: one page holds it")
                val takeover = log.drop(before)
                assertTrue(takeover.isNotEmpty(), "$style: the page that stays records in the frame the other left")
                assertTrue(takeover.none { it.by === first }, "$style: the page that left records nothing")
                assertSame(picture.holders.single(), picture.recorder, "$style: the page that stays records now")
                assertSame(layer, picture.layer, "$style: the same layer, recorded again by the page that stays")
                frames(20)
                assertTrue(log.drop(before).all { it.by === picture.recorder }, "$style: then only it records")
            }
        }
    }

    @Test
    fun `B-KIT-LIVE-056 a recorder no longer drawn hands the recording on and gets it back on the pop`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            runDesktopComposeUiTest(200, 400) {
                var share: KitLiveShare? = null
                var frame = 0
                val log = ArrayList<Recording>()
                var shown by mutableStateOf(listOf(true, false))
                var drawn by mutableStateOf(listOf(true, true))
                mainClock.autoAdvance = false
                setContent {
                    KitTheme(appearance(style), platform, "en") {
                        Pages(shown, { drawn }) { share = it }
                        SideEffect { share?.pictures?.onRecord = { p, by -> log += Recording(frame, p, by) } }
                    }
                }
                fun frames(n: Int) = repeat(n) {
                    frame++
                    mainClock.advanceTimeByFrame()
                }
                frames(10)
                val home = log.last().by
                val picture = log.last().picture
                // The push: the page on top arrives, then the covered page stays composed but is no longer drawn.
                shown = listOf(true, true)
                frames(20)
                drawn = listOf(false, true)
                frames(5)
                val before = log.size
                frames(60)
                val after = log.drop(before)
                // 60 frames are about a second: the picture keeps stepping (LIVE_LOOK_FPS steps), once a frame.
                assertTrue(after.size >= LIVE_LOOK_FPS - 3, "$style: the picture stopped stepping (${after.size})")
                for ((f, inFrame) in after.groupBy { it.frame }) {
                    assertEquals(1, inFrame.size, "$style: frame $f recorded twice")
                }
                // Who records then depends on the platform (Android re-records an invalidated layer its parent does not
                // draw): either way the picture steps under the page on top, once a frame.
                val byHome = after.count { it.by === home }
                println("B-KIT-LIVE-056 $style: not drawn, $byHome of ${after.size} recorded by the covered page")
                assertEquals(2, picture.users, "$style: both pages still hold the one picture")
                assertTrue(log.all { it.picture === picture }, "$style: one picture throughout")
                // The pop: the covered page is drawn again and the page on top leaves.
                drawn = listOf(true, true)
                shown = listOf(true, false)
                frames(2)
                val popped = log.size
                frames(60)
                val back = log.drop(popped)
                assertTrue(back.size >= LIVE_LOOK_FPS - 3, "$style: after the pop it stopped stepping (${back.size})")
                assertTrue(back.all { it.by === home }, "$style: after the pop the covered page records again")
                assertSame(home, picture.recorder, "$style: the covered page is the recorder again")
            }
        }
    }
}
