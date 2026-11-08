@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
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
 * "Jump to latest"; the pill sits bottom centre 8 dp above the composer, a short soft fade (not a block) settles the
 * words under it, and the newest message still rests just above the composer.
 */
class E20S30JumpPillTest {
    private val rows = (0 until 80).map { "Message $it" }

    /** The top edge of the composer's veil: KitScreen's bottom block, whose tag sits inside its 8 dp padding. */
    private fun androidx.compose.ui.test.ComposeUiTest.veilTop() = onNodeWithTag("kit-screen-bottom").getBoundsInRoot().top - 8.dp

    private fun scene(): @androidx.compose.runtime.Composable () -> Unit = {
        KitTheme(appearance = KitAppearance(style = Style.Solid), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            Box(Modifier.size(412.dp, 800.dp)) {
                val source = rememberKitFrostState()
                val state = rememberTranscriptListState()
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    bottom = { Box(Modifier.fillMaxWidth().height(72.dp).testTag("composer")) },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                        KitTranscriptList(state, newestKey = rows.first(), jumpLabel = "Jump to latest", motion = KitMotionLevel.Off) {
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
        // Only the list's own 16 dp padding between them: no extra lane or fade room.
        assertTrue(veil - newest.bottom <= 16.5.dp, "gap ${veil - newest.bottom}")
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
    }

    @Test
    fun `E20-S30 away from the latest the calm pill sits bottom centre 8 dp above the composer over a soft fade`() =
        runComposeUiTest {
            setContent(scene())
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(60)
            waitForIdle()
            val veil = veilTop()
            val face = onNodeWithTag("kit-jump-pill-veiled", useUnmergedTree = true).getBoundsInRoot()
            // Centred (it used to sit at the end of the line).
            val centre = (face.left + face.right) / 2
            assertTrue(abs((centre - 206.dp).value) <= 1f, "pill centre $centre")
            // The calmer 32 dp face, 8 dp clear of the composer's veil.
            assertEquals(32.dp, face.bottom - face.top)
            assertTrue(abs((veil - face.bottom - 8.dp).value) <= .5f, "gap ${veil - face.bottom}")
            // A short fade, never the old lane (minTarget + 8 + 16 dp + the composer, ground all the way down).
            val fade = onNodeWithTag("transcript-jump-fade").getBoundsInRoot()
            assertEquals(JUMP_FADE_DP.dp, fade.bottom - fade.top)
            assertTrue(abs((fade.bottom - veil).value) <= .5f, "fade $fade ends at the composer's veil $veil")
            assertTrue(JUMP_FADE_ALPHA < 1f, "the fade never reaches a solid block")
        }

    @Test
    fun `E20-S30 the pill brings the reader back and leaves at once with motion off`() = runComposeUiTest {
        setContent(scene())
        waitForIdle()
        onNodeWithTag("transcript-list").performScrollToIndex(60)
        waitForIdle()
        onNodeWithTag("jump-to-latest").performClick()
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
        val newest = onNodeWithText("Message 0").getBoundsInRoot()
        assertTrue(newest.bottom <= veilTop())
    }

    @Test
    fun `E20-S30 the pill's lift leaves 8 dp between its face and the composer`() {
        // 48 dp target, 32 dp face: the target's own 8 dp margin is the gap.
        assertEquals(0, jumpPillLift(48))
        assertEquals(8, jumpPillLift(32))
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
