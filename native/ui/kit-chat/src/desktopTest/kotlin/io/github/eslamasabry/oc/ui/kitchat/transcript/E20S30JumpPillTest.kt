@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.ChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.VeilLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.composerFaceAlpha
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E20-S30 (owner phone screenshot 2026-11-07, "Big gap not required … why not translucent"): no solid lane behind
 * "Jump to latest", and the newest message still rests just above the composer. Since owner decision O2 = D
 * (2026-11-24) the control is a docked row in the live line's place: the whole width, one target tall, its bottom
 * one list padding above the composer's room, shown exactly while the person does not follow the newest message.
 */
class E20S30JumpPillTest {
    private val rows = (0 until 80).map { "Message $it" }

    /** The scene's list state, hoisted out of the composition so a test sets the person's choice by hand. */
    private lateinit var list: TranscriptListState

    /** The top edge of the composer's veil: KitScreen's bottom block, whose tag sits inside its 8 dp padding. */
    private fun androidx.compose.ui.test.ComposeUiTest.veilTop() = onNodeWithTag("kit-screen-bottom").getBoundsInRoot().top - 8.dp

    private fun scene(): @Composable () -> Unit = {
        KitTheme(appearance = KitAppearance(style = Style.Solid), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            Box(Modifier.size(412.dp, 800.dp)) {
                val source = rememberKitFrostState()
                list = rememberTranscriptListState()
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    bottom = { Box(Modifier.fillMaxWidth().height(72.dp).testTag("composer")) },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                        KitTranscriptList(
                            list,
                            newestKey = rows.first(),
                            jumpLabel = "Jump to latest",
                            motion = KitMotionLevel.Off,
                        ) {
                            items(rows, key = { it }, contentType = { "row" }) { KitText(it) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `E20-S30 the newest message rests just above the composer and no pill or fade shows`() = runComposeUiTest {
        setContent(scene())
        waitForIdle()
        val newest = onNodeWithText("Message 0").getBoundsInRoot()
        val veil = veilTop()
        assertTrue(newest.bottom <= veil, "newest $newest under the composer's veil at $veil")
        // The list's own 16 dp padding and 8 dp more of the transcript's fade (2026-11-14: the transcript is gone at the
        // block's top, fading out over the 16 dp above it): no extra lane.
        assertTrue(veil - newest.bottom <= 24.5.dp, "gap ${veil - newest.bottom}")
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
    }

    /** The list at row [index]: a scroll from code keeps following, so the person's choice is set by hand. */
    private fun androidx.compose.ui.test.ComposeUiTest.readOlder(index: Int) {
        onNodeWithTag("transcript-list").performScrollToIndex(index)
        runOnUiThread { list.following = false }
        waitForIdle()
    }

    @Test
    fun `E20-S30 away from the latest the docked row spans the width above the composer, no band of ground`() =
        runComposeUiTest {
            setContent(scene())
            waitForIdle()
            readOlder(60)
            val veil = veilTop()
            val row = onNodeWithTag("jump-to-latest").getBoundsInRoot()
            // Full width and one target tall (it used to be a 32 dp pill, centred).
            assertEquals(412.dp, row.width, "the row's width")
            assertEquals(48.dp, row.height, "the row's height")
            // The list's own 16 dp padding and the chrome's 8 dp lead stand between the row and the composer's room.
            assertTrue(abs((veil - row.bottom - 24.dp).value) <= 1f, "gap ${veil - row.bottom}, veil at $veil")
            // 2026-11-14: a page that records its body ends the list itself above the row (kitScreenListEdges), so no
            // band of ground is laid there.
            onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
        }

    @Test
    fun `E20-S30 the row brings the reader back and leaves at once with motion off`() = runComposeUiTest {
        setContent(scene())
        waitForIdle()
        readOlder(60)
        onNodeWithTag("jump-to-latest").performClick()
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
        val newest = onNodeWithText("Message 0").getBoundsInRoot()
        assertTrue(newest.bottom <= veilTop())
    }

    @Test
    fun `E20-S30 the composer face is as translucent as its veil except under Glass or with no veil`() {
        val blur = ChromeVeil(VeilLook.Blur, 12.5f, .76f)
        assertEquals(.76f, composerFaceAlpha(blur, Style.Solid))
        assertEquals(.76f, composerFaceAlpha(blur, Style.Brutal))
        assertEquals(1f, composerFaceAlpha(blur, Style.Glass))
        assertEquals(1f, composerFaceAlpha(null, Style.Solid))
        assertEquals(1f, composerFaceAlpha(ChromeVeil(VeilLook.Opaque, 0f, 1f), Style.Soft))
    }
}
