@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        // The list's own 16 dp padding and 8 dp more of the transcript's fade (2026-11-14: the transcript is gone at the
        // block's top, fading out over the 16 dp above it): no extra lane.
        assertTrue(veil - newest.bottom <= 24.5.dp, "gap ${veil - newest.bottom}")
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
    }

    @Test
    fun `E20-S30 away from the latest the calm pill sits bottom centre 8 dp above the composer with no band of ground`() =
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
            // The calmer 32 dp face. Audit 2026-11-14 (finding 25): its 48 dp target sits on the block's first part (the
            // composer), so the face is the target's own 8 dp clear of the composer and no dead band stands above it.
            assertEquals(32.dp, face.bottom - face.top)
            val composer = onNodeWithTag("composer").getBoundsInRoot().top
            assertTrue(abs((composer - face.bottom - 8.dp).value) <= .5f, "gap ${composer - face.bottom}, veil at $veil")
            // 2026-11-14: a page that records its body ends the list itself above the pill (kitScreenListEdges), so no
            // band of ground is laid there any more.
            onNodeWithTag("transcript-jump-fade").assertDoesNotExist()
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

    @Test
    fun `E20-S30 scrolling past a short newest row stays below the away threshold`() = runComposeUiTest {
        lateinit var state: TranscriptListState
        setContent {
            KitTheme(
                appearance = KitAppearance(style = Style.Solid),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 800.dp)) {
                    state = rememberTranscriptListState()
                    KitTranscriptList(
                        state,
                        newestKey = "live",
                        jumpLabel = "Jump to latest",
                        motion = KitMotionLevel.Off,
                    ) {
                        item(key = "live") { Box(Modifier.fillMaxWidth().height(26.dp)) }
                        item(key = "work") { Box(Modifier.fillMaxWidth().height(600.dp)) }
                        items(rows, key = { it }) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        // 120 dp past the 26 dp live line: index 1, offset 94 dp, well below AWAY_DP (480 dp).
        runOnIdle { state.lazy.requestScrollToItem(1, 94) }
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        // Past 480 dp into index 1: now away.
        runOnIdle { state.lazy.requestScrollToItem(1, 500) }
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertExists()
    }

    @Test
    fun `E20-S30 a newer row replacing one below keeps the go-to-newest control`() = runComposeUiTest {
        lateinit var state: TranscriptListState
        var replaced by mutableStateOf(false)
        setContent {
            KitTheme(
                appearance = KitAppearance(style = Style.Solid),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 800.dp)) {
                    state = rememberTranscriptListState()
                    val newestKey = if (replaced) "reply" else "live"
                    KitTranscriptList(
                        state,
                        newestKey = newestKey,
                        jumpLabel = "Jump to latest",
                        motion = KitMotionLevel.Off,
                    ) {
                        if (replaced) {
                            item(key = "reply") { Box(Modifier.fillMaxWidth().height(900.dp)) }
                        } else {
                            item(key = "live") { Box(Modifier.fillMaxWidth().height(26.dp)) }
                        }
                        item(key = "work") { Box(Modifier.fillMaxWidth().height(600.dp)) }
                        items(rows, key = { it }) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle {
            state.lazy.requestScrollToItem(1, 94)
            state.following = false
        }
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
        runOnIdle { replaced = true }
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertExists()
    }

    @Test
    fun `E20-S30 a row growing below the screen brings the go-to-newest control`() = runComposeUiTest {
        lateinit var state: TranscriptListState
        var replyHeight by mutableStateOf(40.dp)
        setContent {
            KitTheme(
                appearance = KitAppearance(style = Style.Solid),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 800.dp)) {
                    state = rememberTranscriptListState()
                    KitTranscriptList(
                        state,
                        newestKey = "live",
                        jumpLabel = "Jump to latest",
                        motion = KitMotionLevel.Off,
                    ) {
                        item(key = "live") { Box(Modifier.fillMaxWidth().height(26.dp)) }
                        item(key = "reply") { Box(Modifier.fillMaxWidth().height(replyHeight)) }
                        item(key = "work") { Box(Modifier.fillMaxWidth().height(600.dp)) }
                        items(rows, key = { it }) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle {
            state.lazy.requestScrollToItem(2, 40)
            state.following = false
        }
        waitForIdle()
        runOnIdle { replyHeight = 900.dp }
        waitForIdle()
        onNodeWithTag("jump-to-latest").assertExists()
    }
}
