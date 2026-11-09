package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.choice.SELECTED_FILL_ALPHA
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitStatusPill
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.row.CHOSEN_ROW_TINT
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTag
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTagTone
import io.github.eslamasabry.oc.ui.kit.status.glyphFor
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Guards for audit 2026-11-09 classes C03, C04, C11, C14 and C17 (docs/native/qa/audit-fix-d-2026-11-09):
 * C03 no status capsule, C04 no selected slab, C11 no empty-ring notice glyph, C14 no empty leading slot, C17 menus
 * show glyphs on every item or on none.
 */
@OptIn(ExperimentalTestApi::class)
class AuditClassGuardDTest {
    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(motion = MotionLevel.Off),
            platform = FakeKitPlatform(),
            localeTag = "en",
            content = content,
        )
    }

    private fun same(a: Color, b: Color) =
        abs(a.red - b.red) < .02f && abs(a.green - b.green) < .02f && abs(a.blue - b.blue) < .02f

    /** The strip along the top of the node tagged [tag] (clear of any word) paints the page's own colour: no capsule. */
    private fun assertNoCapsule(tag: String, content: @Composable () -> Unit) =
        runDesktopComposeUiTest(412, 400) {
            setContent { Theme { Box(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp)) { content() } } } }
            waitForIdle()
            val node = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
            val pixels = onRoot().captureToImage().toPixelMap()
            val page = pixels[2, 2]
            val b = node.boundsInRoot
            val x = b.center.x.roundToInt()
            // The pill is 32 dp tall inside a 48 dp target; look 13 dp above its centre, inside the old capsule.
            val y = (b.center.y - 13 * density.density).roundToInt().coerceAtLeast(0)
            assertTrue(same(pixels[x, y], page), "'$tag' paints a capsule (C03): ${pixels[x, y]} over page $page")
        }

    @Test
    fun `C03 a fact chip is a dot and a word, never a capsule`() {
        listOf(KitChipTone.Neutral, KitChipTone.Attention, KitChipTone.Active).forEach { tone ->
            assertNoCapsule("kit-chip-plain") { KitChip("Critical", tone = tone) }
        }
    }

    @Test
    fun `C03 a status tag is a plain word`() {
        KitStatusTagTone.entries.forEach { tone ->
            runDesktopComposeUiTest(412, 300) {
                setContent { Theme { Box(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp)) { KitStatusTag("Needs you", tone) } } } }
                waitForIdle()
                val pixels = onRoot().captureToImage().toPixelMap()
                val page = pixels[2, 2]
                // The strip just inside the old capsule's left edge, above the first letter.
                val x = (24 * density.density).roundToInt() + 1
                val y = (24 * density.density).roundToInt() + 1
                assertTrue(same(pixels[x, y], page), "the tag $tone paints a capsule (C03)")
            }
        }
    }

    @Test
    fun `C03 the server status line paints no capsule`() {
        assertNoCapsule("kit-status-pill") {
            KitStatusPill("Laptop", KitConnectionTone.Ok, "Laptop, connected", onClick = {}, word = null)
        }
    }

    @Test
    fun `C04 a chosen row's wash is a breath, and its mark is a bare trailing check`() {
        assertTrue(SELECTED_FILL_ALPHA <= .08f, "a chosen choice row is at most a very light tint")
        assertTrue(CHOSEN_ROW_TINT <= .08f, "a chosen row is at most a very light tint")
        runDesktopComposeUiTest(412, 400) {
            setContent {
                Theme {
                    Column {
                        KitChoiceRow("One", selected = true, mark = KitChoiceMark.Radio, onClick = {})
                        KitChoiceRow("Two", selected = false, mark = KitChoiceMark.Radio, onClick = {})
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-choice-tick", useUnmergedTree = true).assertCountEquals(1)
        }
        runDesktopComposeUiTest(412, 400) {
            setContent { Theme { KitRow("Main", selected = true, onClick = {}) } }
            waitForIdle()
            onNodeWithTag("kit-row-check", useUnmergedTree = true)
        }
    }

    /** The wash's left and right edge on the row's top strip (clear of words) and the count of accent-coloured check pixels. */
    private class Look(val left: Int, val right: Int, val accentPixels: Int)

    private fun selectedLook(dark: Boolean, content: @Composable () -> Unit): Look {
        var look = Look(0, 0, 0)
        var accent = Color.Unspecified
        runDesktopComposeUiTest(412, 400) {
            setContent {
                KitTheme(
                    appearance = KitAppearance(
                        mode = if (dark) io.github.eslamasabry.oc.ui.design.effects.AppearanceMode.Dark else io.github.eslamasabry.oc.ui.design.effects.AppearanceMode.Light,
                        motion = MotionLevel.Off,
                    ),
                    platform = FakeKitPlatform(),
                    localeTag = "en",
                ) {
                    val colors = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current
                    accent = colors.accent
                    Box(Modifier.fillMaxSize().background(colors.ground)) { Box(Modifier.testTag("sel")) { content() } }
                }
            }
            waitForIdle()
            val b = onNodeWithTag("sel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val px = onRoot().captureToImage().toPixelMap()
            val page = px[2, 2]
            val y = (b.top + 2 * density.density).roundToInt()
            val xs = (0 until px.width).filter { !same(px[it, y], page) }
            var count = 0
            for (x in (b.right - 56 * density.density).roundToInt() until b.right.roundToInt())
                for (yy in b.top.roundToInt() until b.bottom.roundToInt())
                    if (abs(px[x, yy].red - accent.red) < .1f && abs(px[x, yy].green - accent.green) < .1f && abs(px[x, yy].blue - accent.blue) < .1f) count++
            look = Look(xs.minOrNull() ?: -1, xs.maxOrNull() ?: -1, count)
        }
        return look
    }

    @Test
    fun `C04 a chosen choice row and a chosen KitRow share one wash shape and one check colour`() {
        listOf(false, true).forEach { dark ->
            val choice = selectedLook(dark) { KitChoiceRow("Claude", selected = true, onClick = {}) }
            val row = selectedLook(dark) { KitRow("Main", selected = true, onClick = {}) }
            assertTrue(choice.left > 0 && choice.right > 0, "the choice row washes (dark=$dark)")
            assertEquals(row.left, choice.left, "same wash start (dark=$dark)")
            assertEquals(row.right, choice.right, "same wash end (dark=$dark)")
            assertTrue(choice.accentPixels > 0, "the choice row's check is the accent (dark=$dark)")
            assertTrue(row.accentPixels > 0, "the KitRow's check is the accent (dark=$dark)")
        }
    }

    @Test
    fun `C11 no notice or empty state draws the empty ring`() {
        assertSame(KitIcons.info, glyphFor(StatusTone.Neutral))
        val offenders =
            File("src/commonMain").walkTopDown().filter { it.extension == "kt" && it.name != "KitIconBuild.kt" && it.name != "KitIcons.kt" }
                .filter { f -> f.readLines().any { l -> "KitIcons.radioEmpty" in l && !l.trim().startsWith("//") && !l.trim().startsWith("*") } }
                .map { it.name }.toList()
        assertEquals(emptyList(), offenders, "the empty ring reads as a radio button (C11)")
    }

    @Test
    fun `C14 a provider logo with no mark gives its slot back`() {
        runDesktopComposeUiTest(412, 300) {
            setContent {
                Theme {
                    CompositionLocalProvider(LocalKitLogoLoader provides KitLogoLoader { _, _ -> null }) {
                        Row {
                            Box(Modifier.testTag("logo")) { KitProviderLogo("nobody", "Nobody") }
                        }
                    }
                }
            }
            waitForIdle()
            val w = onNodeWithTag("logo").fetchSemanticsNode().size.width
            assertEquals(0, w, "an absent mark keeps no leading indent (C14)")
        }
    }

    private fun menuGlyphs(items: List<KitMenuItem>): Int {
        var count = -1
        runDesktopComposeUiTest(412, 600) {
            setContent { Theme { KitSheetHost { Box(Modifier.fillMaxSize()) { KitMenu(expanded = true, items = items, onDismissRequest = {}, anchor = { Box(Modifier.padding(8.dp)) }) } } } }
            waitForIdle()
            count = onAllNodesWithTag("kit-menu-glyph", useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return count
    }

    @Test
    fun `C17 a menu shows a glyph on every item or on none`() {
        val some = listOf(KitMenuItem("Open", {}, KitIcons.file), KitMenuItem("Copy", {}, KitIcons.copy), KitMenuItem("Plain", {}))
        assertEquals(0, menuGlyphs(some), "a half-iconed menu shows no glyphs")
        val all = some.map { it.copy(icon = it.icon ?: KitIcons.info) }
        assertEquals(3, menuGlyphs(all), "a fully iconed menu shows all three in one column")
    }
}
