@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Words on the chat's glass (device, Android 15, Glass dark, 2026-11-11) — request-card pill, phrase chips, top bar,
 * status notice line, and recording-clear checks. Sibling shard: [ChatWordsGlassTest].
 */
class ChatWordsGlassChromeTest : ChatWordsGlassHarness() {
    /**
     * A decision waiting above the box: the pill stays clear of the card, not joined, in the faded zone above the
     * block, with nothing of the transcript behind it.
     */
    @Test
    fun `B-CHAT-COL-04 with a request card above the box the pill stands apart over no words`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true, card = true))
        val none = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = false, card = true))
        val face = assertNotNull(bright.pillFace, "the pill shows")
        assertEquals(0, bright.joined, "the pill is not joined to the box under the card")
        assertTrue(face.bottom <= bright.block!!.top, "the pill sits above the block: $face block ${bright.block}")
        val area = DpArea(face.left.value - 4f, face.top.value - 4f, face.right.value + 4f, face.bottom.value + 4f)
        val (mean, max) = diff(bright.image, none.image, area)
        println("pill over card: mean $mean max $max at $face")
        val mapArea =
            DpArea(
                face.left.value - 16f,
                face.top.value - 24f,
                face.right.value + 16f,
                bright.block!!.top.value + 8f,
            )
        assertTrue(
            mean <= WORDS_MEAN && max <= WORDS_MAX,
            "the rows show behind the pill: mean $mean, max $max (none: ${none.pillFace})\n" +
                map(bright.image, none.image, mapArea),
        )
        assertFadesOut(bright.image, none.image, FadeProbe(8f, face.top.value - 40f, face.top.value, "over the card"))
    }

    /**
     * The phrase row shows only near the newest message (reading history it folds away, B-CHAT-COL-03), so the scene is
     * there: a tall newest message drawn 120 dp past its resting place (not away from the newest, the chips stay), its
     * lower part under the chips.
     */
    @Test
    fun `a bright row never shows behind the phrase chips above the box`() {
        val rows = RowOptions(scrollTo = 0, tall = true, nudgeDp = 120)
        val optsBright = SceneOptions(bright = true, chips = true, rows = rows)
        val optsNone = SceneOptions(bright = false, chips = true, rows = rows)
        val bright = shot(dark(Style.Glass), LensPlatform(), optsBright)
        val none = shot(dark(Style.Glass), LensPlatform(), optsNone)
        val chip = assertNotNull(bright.chip, "near the newest message the phrase row shows: ${bright.list}")
        assertTrue(chip.top < bright.box.top, "the chips lie above the box: $chip box ${bright.box}")
        val spans = bright.rows.map { it.top..it.bottom }
        assertTrue(
            bright.rows.any { it.top < chip.top && it.bottom > chip.bottom },
            "the newest row runs under the chips: $spans chips $chip (${bright.list})",
        )
        val area = DpArea(chip.left.value + 6f, chip.top.value + 4f, chip.left.value + 60f, chip.bottom.value - 8f)
        val (mean, max) = diff(bright.image, none.image, area)
        println("chips: mean $mean max $max over $chip")
        assertTrue(
            mean <= WORDS_MEAN && max <= WORDS_MAX,
            "bright rows show behind the phrase chip: mean $mean, max $max of 255",
        )
    }

    /**
     * Owner 2026-11-14: transcript text ran up under the bar and showed inside its lower edge as a blurred band.
     * Nothing of the transcript shows under the status bar or the bar, its rim included; it dissolves in below.
     */
    @Test
    fun `a bright row never shows under the status bar or the chat bar, its rim included`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true))
        val none = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = false))
        val bar = bright.bar
        val spans = bright.rows.map { it.top..it.bottom }
        assertTrue(bright.rows.any { it.top < bar.top }, "rows run under the bar: $spans bar $bar")
        val (mean, max) = diff(bright.image, none.image, DpArea(0f, 0f, W.toFloat(), bar.bottom.value))
        println("bar: mean $mean max $max, bar $bar")
        val mapArea = DpArea(0f, 0f, bar.right.value, bar.bottom.value + 16f)
        assertTrue(
            mean <= WORDS_MEAN && max <= WORDS_MAX,
            "bright rows show under the chrome: mean $mean, max $max of 255\n" +
                map(bright.image, none.image, mapArea),
        )
    }

    @Test
    fun `a bright row never shows inside the waiting to send line under the bar`() {
        // With the page under the status bar (24 dp inset) and without (a window whose status bar the system draws: no
        // top inset, as on the 2026-11-11 emulator, where the line was crossed by sharp transcript text).
        for (statusDp in listOf(STATUS_DP, 0)) {
            val optsBright = SceneOptions(bright = true, notice = true, statusDp = statusDp)
            val optsNone = SceneOptions(bright = false, notice = true, statusDp = statusDp)
            val bright = shot(dark(Style.Glass), LensPlatform(), optsBright)
            val none = shot(dark(Style.Glass), LensPlatform(), optsNone)
            val line = bright.status!!
            assertTrue(line.bottom - line.top > 8.dp, "status $statusDp dp: the line shows: $line")
            val spans = bright.rows.map { it.top..it.bottom }
            assertTrue(
                bright.rows.any { it.top < line.bottom },
                "status $statusDp dp: rows scrolled under the line: $spans line $line",
            )
            val area = DpArea(line.left.value, line.top.value, line.right.value, line.bottom.value)
            val (mean, max) = diff(bright.image, none.image, area)
            println("notice (status $statusDp dp): mean $mean max $max over $line")
            assertTrue(
                mean <= NOTICE_MEAN && max <= NOTICE_MAX,
                "status $statusDp dp: bright rows show inside the notice line: mean $mean, max $max of 255",
            )
        }
    }

    /**
     * Owner 2026-11-14: a clearing hides everything it covers. In every style the bottom block's glass (and the pill's)
     * sees the screen's recording cleared as the page shows it, never the whole recording.
     */
    @Test
    fun `in every style the bottom block sees the recording cleared as the page shows it`() {
        val desktop: KitPlatform = DesktopKitPlatform()
        val lens: KitPlatform = LensPlatform()
        val cases =
            Style.entries.map { "$it" to (dark(it) to desktop) } +
                listOf(
                    "Glass, Frosted look" to (dark(Style.Glass, GlassLookId.Frosted) to lens),
                    "Glass, lens" to (dark(Style.Glass) to lens),
                )
        for ((what, case) in cases) {
            val s = shot(case.first, case.second, SceneOptions(bright = true, notice = true))
            assertTrue(s.bottomSawSome && !s.bottomSawSource, "$what: the bottom block saw the whole recording")
        }
    }

    /**
     * Before/after renders of the top bar and of the "Needs your decision" label (off unless WORDS_GLASS_OUT names a
     * folder; WORDS_GLASS_TAG is "before" or "after"): the small scene, white rows scrolled under the bar and under a
     * card's label above the message box, liquid Glass dark.
     */
    @Test
    fun `words on glass renders - top bar and the decision label`() {
        val out = System.getenv("WORDS_GLASS_OUT")?.let(::File) ?: return
        val tag = System.getenv("WORDS_GLASS_TAG") ?: "render"
        out.mkdirs()
        val top = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true))
        ImageIO.write(top.image.toImage(), "png", File(out, "render-words-top-chat-$tag.png"))
        val card = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true, card = true))
        ImageIO.write(card.image.toImage(), "png", File(out, "render-words-needs-decision-$tag.png"))
    }
}
