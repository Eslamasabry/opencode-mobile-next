@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Words on the chat's glass (device, Android 15, Glass dark, 2026-11-11) — bottom block, docked jump row, code block,
 * decision-card, and lens/frosted layout parity checks. Sibling shard: [ChatWordsGlassChromeTest].
 */
class ChatWordsGlassTest : ChatWordsGlassHarness() {
    /**
     * Owner 2026-11-14 ("Wtfff": "Jump to latest" sat on a code block's words and bent them; a smear of bent transcript
     * along the message box's top edge): reading history, nothing of the transcript shows in the bottom block (the box
     * and its rim included) or behind the docked jump row, at any scroll offset; it fades out above it in steps.
     */
    @Test
    fun `B-CHAT-COL-01 nothing of the transcript shows in the block or behind the docked jump row, fading above`() {
        for (at in listOf(SCROLLED_TO, SCROLLED_TO + 7)) {
            val rows = RowOptions(scrollTo = at)
            val bright = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true, rows = rows))
            val none = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = false, rows = rows))
            val block = bright.block!!
            val row = assertNotNull(bright.pill, "scrolled to $at: the docked jump row shows")
            val spans = bright.rows.map { it.top..it.bottom }
            assertTrue(
                bright.rows.any { it.bottom > block.top + 40.dp },
                "scrolled to $at: rows lie under the block: $spans block $block",
            )
            // The whole block, its rim and its margins.
            val blockArea = DpArea(0f, block.top.value, W.toFloat(), H.toFloat())
            val (blockMean, blockMax) = diff(bright.image, none.image, blockArea)
            println("scrolled to $at: block mean $blockMean max $blockMax over $block, row $row")
            val mapArea = DpArea(0f, block.top.value - 40f, W.toFloat(), H.toFloat())
            assertTrue(
                blockMean <= WORDS_MEAN && blockMax <= WORDS_MAX,
                "scrolled to $at: the rows show in the bottom block: mean $blockMean, max $blockMax\n" +
                    map(bright.image, none.image, mapArea),
            )
            // The docked row's whole rect: nothing of a row shows there, behind or around its words (the band is
            // cleared).
            val rowArea = DpArea(row.left.value, row.top.value, row.right.value, row.bottom.value)
            val (rowMean, rowMax) = diff(bright.image, none.image, rowArea)
            assertTrue(
                rowMean <= WORDS_MEAN && rowMax <= WORDS_MAX,
                "scrolled to $at: the rows show behind the docked jump row: mean $rowMean, max $rowMax",
            )
            // Above it: whole, then a soft fade in steps, then gone before the row's top.
            val top = row.top.value
            assertFadesOut(bright.image, none.image, FadeProbe(8f, top - 40f, top, "scrolled to $at"))
        }
    }

    /** Owner 2026-11-14: the code block was cut at its words but its outline ran on. A clearing hides everything. */
    @Test
    fun `B-CHAT-COL-02 a code block passing the bottom edge loses its outline with its words`() {
        val rows = RowOptions(outlined = true)
        val bright = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true, rows = rows))
        val none = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = false, rows = rows))
        val block = bright.block!!
        val spans = bright.rows.map { it.top..it.bottom }
        assertTrue(
            bright.rows.any { it.top < block.top && it.bottom > block.top },
            "a code block crosses the block's top: $spans block $block",
        )
        for (x in listOf(12f, 13f, W - 13f, W - 12f)) {
            val (mean, max) = diff(bright.image, none.image, DpArea(x - 1f, block.top.value, x + 1f, H.toFloat()))
            assertTrue(
                mean <= WORDS_MEAN && max <= WORDS_MAX,
                "the code block's outline runs on at $x dp under the block: mean $mean, max $max",
            )
        }
        val aboveArea = DpArea(11f, block.top.value - 120f, 14f, block.top.value - 60f)
        val (above, _) = diff(bright.image, none.image, aboveArea)
        assertTrue(above >= 20f, "the code block's outline shows above the bottom block: $above")
    }

    /**
     * Owner 2026-11-14 ("Why did you unlink jump to latest from the composer it was good already!!"): reading history
     * with a phrase row above the box, the row folds away; at the newest message the chips are back. Since owner
     * decision O2 = D (2026-11-24) the jump control is a docked row above the box while history is read, never a drop
     * of the box.
     */
    @Test
    fun `B-CHAT-COL-03 reading history the phrase row folds and the docked row shows, at the newest chips return`() {
        val away = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true, chips = true))
        println("away: chips ${away.chipRows} joined ${away.joined} row ${away.pill} box ${away.box}")
        assertEquals(0, away.chipRows, "no phrase row is laid out while reading history")
        val row = assertNotNull(away.pill, "the docked row shows while reading history")
        assertTrue(row.bottom <= away.box.top, "the row stands above the box: $row box ${away.box}")
        val latest =
            shot(
                dark(Style.Glass),
                LensPlatform(),
                SceneOptions(bright = true, chips = true, rows = RowOptions(scrollTo = 0)),
            )
        println("latest: chips ${latest.chipRows} joined ${latest.joined} row ${latest.pill}")
        assertEquals(1, latest.chipRows, "the phrase row is back at the newest message")
        assertNull(latest.pill, "no docked row at the newest message")
    }

    @Test
    fun `a bright row never shows behind the Needs your decision label above the box, and the pill stays above the card`() {
        val bright = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true, card = true))
        val none = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = false, card = true))
        val label = bright.label!!
        val spans = bright.rows.map { it.top..it.bottom }
        assertTrue(
            bright.rows.any { it.top < label.bottom && it.bottom > label.top },
            "rows run behind the label: $spans label $label",
        )
        val labelArea = DpArea(label.left.value, label.top.value, label.right.value, label.bottom.value)
        val (mean, max) = diff(bright.image, none.image, labelArea)
        println("label: mean $mean max $max over $label, pill ${bright.pill}, box ${bright.box}")
        assertTrue(
            mean <= NOTICE_MEAN && max <= NOTICE_MAX,
            "bright rows show behind the label: mean $mean, max $max of 255",
        )
        val wordsArea =
            DpArea(
                bright.field.left.value,
                bright.placeholder.top.value,
                bright.field.right.value,
                bright.placeholder.bottom.value,
            )
        val (wordsMean, wordsMax) = diff(bright.image, none.image, wordsArea)
        assertTrue(
            wordsMean <= WORDS_MEAN && wordsMax <= WORDS_MAX,
            "bright rows show behind the placeholder: mean $wordsMean, max $wordsMax",
        )
        bright.pill?.let {
            assertTrue(it.bottom <= label.top + 1.dp, "the pill $it lies below the label's top ${label.top}")
        }
    }

    @Test
    fun `the transcript's resting place, scroll range and the jump pill's place are the same with and without the lens`() {
        // Liquid Glass fades the transcript under the box; the Frosted look (no lens) does not. Both lay the transcript
        // out alike. (The box itself sits differently in the two looks, as it did before the fade: the lens's slab
        // keeps the bar's 6 dp side margin. Its place under the lens is printed to compare across commits.)
        val lens = shot(dark(Style.Glass), LensPlatform(), SceneOptions(bright = true))
        val frosted = shot(dark(Style.Glass, GlassLookId.Frosted), LensPlatform(), SceneOptions(bright = true))
        println("lens:    ${lens.list} pill ${lens.pill} box ${lens.box}")
        println("frosted: ${frosted.list} pill ${frosted.pill} box ${frosted.box}")
        assertEquals(frosted.list, lens.list, "the transcript's scroll state")
        assertEquals(frosted.rows, lens.rows, "the rows' places")
        assertEquals(frosted.pill, lens.pill, "the jump pill's place")
    }

    /**
     * Before/after renders (off unless WORDS_GLASS_OUT names a folder; WORDS_GLASS_TAG names the files): the chat on a
     * white-text transcript scrolled under the message box and under the "waiting to send" line, liquid Glass dark (the
     * desktop's lens is a blur only), and the same small scene in every look that must not change.
     */
    @Test
    fun `words on glass renders`() {
        val out = System.getenv("WORDS_GLASS_OUT")?.let(::File) ?: return
        val tag = System.getenv("WORDS_GLASS_TAG") ?: "render"
        out.mkdirs()
        val nonGlass =
            Style.entries.filter { it != Style.Glass }.map {
                it.name.lowercase() to (dark(it) to (DesktopKitPlatform() as KitPlatform))
            }
        val still =
            nonGlass +
                listOf(
                    "glass-nolens" to (dark(Style.Glass) to (DesktopKitPlatform() as KitPlatform)),
                    "glass-frosted" to (dark(Style.Glass, GlassLookId.Frosted) to (LensPlatform() as KitPlatform)),
                    "glass-lens" to (dark(Style.Glass) to (LensPlatform() as KitPlatform)),
                )
        for ((name, case) in still) {
            val s = shot(case.first, case.second, SceneOptions(bright = true, notice = true))
            ImageIO.write(s.image.toImage(), "png", File(out, "$tag-small-$name.png"))
        }
        runDesktopComposeUiTest((PW * PD).toInt(), (PH * PD).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(PD)) {
                    val appearance =
                        KitAppearance(
                            style = Style.Glass,
                            mode = AppearanceMode.Dark,
                            liveBackground = true,
                            liveStyle = "auroraImage",
                        )
                    KitTheme(appearance, LensPlatform(), "en", stillFrame = true) { PhoneScene() }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(8)
            waitForIdle()
            val img = onNodeWithTag("scene").captureToImage().toAwtImage()
            ImageIO.write(img, "png", File(out, "render-words-chat-$tag.png"))
        }
    }
}
