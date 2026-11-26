package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * B-603: Desktop test picture harness loads CJK fallback fonts so Japanese (`日本語`) and
 * Simplified Chinese (`简体中文`) render distinct real glyphs instead of empty tofu boxes.
 */
@OptIn(ExperimentalTestApi::class)
class LanguageCjkFontsTest {
    @Test
    fun `B-603 Skia font manager resolves real glyphs for Japanese and Chinese language names`() {
        val mgr = FontMgr.default
        for (label in listOf("日本語", "简体中文")) {
            for (ch in label) {
                val cp = ch.code
                val face = mgr.matchFamilyStyleCharacter(null, FontStyle.NORMAL, null, cp)
                assertNotNull(
                    face,
                    "Desktop test fontconfig lacks a CJK fallback font for '$ch' (U+${cp.toString(16).uppercase()})",
                )
                face.use { tf ->
                    assertTrue(
                        tf.getUTF32Glyph(cp).toInt() != 0,
                        "Matched font '${tf.familyName}' returned .notdef for '$ch'",
                    )
                }
            }
        }
    }

    @Test
    fun `B-603 Language screen renders distinct non-tofu CJK glyphs for Japanese and Chinese`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val ui =
                SettingsUi(
                    stack = persistentListOf(SettingsPage.Hub, SettingsPage.Language),
                    language = AppLanguage.English,
                )
            runDesktopComposeUiTest(360, 915) {
                setContent {
                    SettingsThemed(dark = true, width = 360f) {
                        SettingsScreen(ui, RecordingActions())
                    }
                }
                waitForIdle()
                val jpMasks = glyphInkMasks("日本語")
                val zhMasks = glyphInkMasks("简体中文")
                assertDistinctGlyphs("日本語", jpMasks)
                assertDistinctGlyphs("简体中文", zhMasks)
                val crossDiff = maskDiffRatio(jpMasks.first(), zhMasks.first())
                assertTrue(
                    crossDiff >= MIN_GLYPH_DIFF,
                    "First glyph of 日本語 and 简体中文 look identical (tofu): diff=$crossDiff",
                )
            }
        } finally {
            Locale.setDefault(prev)
        }
    }

    private fun ComposeUiTest.glyphInkMasks(label: String): List<BooleanArray> {
        val target = onNodeWithText(label, useUnmergedTree = true)
        target.performScrollTo()
        waitForIdle()
        val px = onRoot().captureToImage().toPixelMap()
        val node = target.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val origin = node.boundsInRoot.topLeft
        return label.indices.map { i ->
            val box = layout.getBoundingBox(i)
            val rect =
                Rect(
                    left = origin.x + box.left,
                    top = origin.y + box.top,
                    right = origin.x + box.right,
                    bottom = origin.y + box.bottom,
                )
            val mask = sampleInkMask(px, rect)
            val ink = mask.count { it }
            assertTrue(
                ink >= MIN_INK_CELLS,
                "Glyph '${label[i]}' of '$label' at $rect drew too little ink ($ink cells)",
            )
            mask
        }
    }

    private fun sampleInkMask(px: PixelMap, rect: Rect): BooleanArray {
        val x0 = floor(rect.left).toInt().coerceIn(0, px.width - 1)
        val x1 = ceil(rect.right).toInt().coerceIn(x0 + 1, px.width)
        val y0 = floor(rect.top).toInt().coerceIn(0, px.height - 1)
        val y1 = ceil(rect.bottom).toInt().coerceIn(y0 + 1, px.height)
        val bg = minLuminance(px, x0, x1, y0, y1)
        val w = x1 - x0
        val h = y1 - y0
        val out = BooleanArray(GRID * GRID)
        for (gy in 0 until GRID) {
            val sy = (y0 + (gy * h) / GRID).coerceIn(0, px.height - 1)
            for (gx in 0 until GRID) {
                val sx = (x0 + (gx * w) / GRID).coerceIn(0, px.width - 1)
                out[gy * GRID + gx] = abs(luma(px[sx, sy]) - bg) >= INK_DELTA
            }
        }
        return out
    }

    private fun minLuminance(px: PixelMap, x0: Int, x1: Int, y0: Int, y1: Int): Float {
        var best = 1f
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val l = luma(px[x, y])
                if (l < best) best = l
            }
        }
        return best
    }

    private fun assertDistinctGlyphs(label: String, masks: List<BooleanArray>) {
        for (i in 0 until masks.size - 1) {
            val diff = maskDiffRatio(masks[i], masks[i + 1])
            assertTrue(
                diff >= MIN_GLYPH_DIFF,
                "Glyphs '${label[i]}' and '${label[i + 1]}' in '$label' rendered identically (tofu): diff=$diff",
            )
        }
    }

    private fun maskDiffRatio(a: BooleanArray, b: BooleanArray): Double {
        var union = 0
        var diff = 0
        for (i in a.indices) {
            if (a[i] || b[i]) union++
            if (a[i] != b[i]) diff++
        }
        return if (union == 0) 0.0 else diff.toDouble() / union.toDouble()
    }

    private fun luma(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    private companion object {
        const val GRID = 12
        const val MIN_INK_CELLS = 4
        const val INK_DELTA = 0.12f
        const val MIN_GLYPH_DIFF = 0.15
    }
}
