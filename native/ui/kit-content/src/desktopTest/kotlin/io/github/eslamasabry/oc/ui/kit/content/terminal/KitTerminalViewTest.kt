package io.github.eslamasabry.oc.ui.kit.content.terminal

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.ContentFakePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules of the terminal's two forms, from lib/ui/kit/kit_terminal_view.dart: which output lines show, the theme's
 * ANSI palette, the live grid's wrapping and cursor, and when the key bar shows and goes compact.
 */
@OptIn(ExperimentalTestApi::class)
class KitTerminalViewTest {
    private val colors = FlutterKitRoles.graphiteDark.toKitColors()

    private fun colorAt(text: androidx.compose.ui.text.AnnotatedString, index: Int) =
        text.spanStyles.lastOrNull { index >= it.start && index < it.end }?.item?.color

    @Test
    fun outputShowsTheTailFirstThenTheRestInPlace() {
        val tail = KitTerminalOutputWindow.of(total = 23, tailLines = 12, all = false)
        assertEquals(11, tail.start)
        assertEquals(11, tail.earlier) // "Show 11 earlier lines", as the Flutter golden
        assertFalse(tail.capped)
        val all = KitTerminalOutputWindow.of(total = 23, tailLines = 12, all = true)
        assertEquals(0, all.start)
        assertFalse(all.canExpand)
    }

    @Test
    fun aTooLongOutputExpandsOnlyToTheCapAndOffersOpenAll() {
        val tail = KitTerminalOutputWindow.of(total = 2500, tailLines = 12, all = false)
        assertEquals(2000 - 12, tail.earlier) // back to the cap, not the whole output (COPY-17)
        assertFalse(tail.capped)
        val all = KitTerminalOutputWindow.of(total = 2500, tailLines = 12, all = true)
        assertEquals(500, all.start)
        assertTrue(all.capped)
    }

    @Test
    fun shortOutputHasNothingEarlier() {
        val w = KitTerminalOutputWindow.of(total = 3, tailLines = 40, all = false)
        assertEquals(0, w.start)
        assertFalse(w.canExpand)
    }

    @Test
    fun ansiColoursFollowThemeOfNotAlarmColours() {
        // themeOf: blue and cyan are codeType, yellow codeString, magenta codeKeyword, red text1, black surface3.
        assertEquals(colors.codeType, KitTerminalSpans.ansiColor(4, colors))
        assertEquals(colors.codeType, KitTerminalSpans.ansiColor(6, colors))
        assertEquals(colors.codeString, KitTerminalSpans.ansiColor(3, colors))
        assertEquals(colors.codeKeyword, KitTerminalSpans.ansiColor(5, colors))
        assertEquals(colors.text1, KitTerminalSpans.ansiColor(1, colors))
        assertEquals(colors.surface3, KitTerminalSpans.ansiColor(0, colors))
        assertEquals(colors.text3, KitTerminalSpans.ansiColor(8, colors))
        assertEquals(colors.text2, KitTerminalSpans.ansiColor(7, colors))
    }

    @Test
    fun anOutputLineKeepsItsOwnColoursAndFaintIsText3() {
        val line = KitTerminalSpans.line("\u001b[32m14 passed\u001b[0m, \u001b[33m1 skipped\u001b[0m", colors) { it }
        assertEquals("14 passed, 1 skipped", line.text)
        assertEquals(colors.success, colorAt(line, 0))
        assertEquals(colors.codeString, colorAt(line, line.text.indexOf("1 skipped")))
        val faint = KitTerminalSpans.line("\u001b[2mTest run took 6.1 s\u001b[0m", colors) { it }
        assertEquals(colors.text3, colorAt(faint, 0))
    }

    @Test
    fun aSecretInAColouredLineIsMaskedAndLosesItsColours() {
        val line = KitTerminalSpans.line("token \u001b[1mabc\u001b[0m", colors) { it.replace("abc", "•••") }
        assertEquals("token •••", line.text)
    }

    @Test
    fun theLiveGridWrapsAtItsEdgeAndPutsTheCursorWhereTheTextEnds() {
        val grid = KitTerminalGrid.of("\u001b[32mabcdef\u001b[0m\r\n$ ", columns = 4, colors = colors)
        assertEquals(listOf("abcd", "ef", "$ "), grid.rows.map { it.text })
        assertEquals(2, grid.cursorRow)
        assertEquals(2, grid.cursorColumn)
        assertEquals(colors.success, colorAt(grid.rows[1], 0))
    }

    @Test
    fun theKeyBarGoesCompactWhenTheWindowAboveTheKeyboardIsShort() {
        assertTrue(kitTerminalCompactKeys(412f, 0f)) // a phone in landscape
        assertTrue(kitTerminalCompactKeys(915f, 600f))
        assertFalse(kitTerminalCompactKeys(915f, 0f))
    }

    @Test
    fun aWideDesktopWindowTakesHardwareKeysOnly() {
        assertFalse(kitTerminalShowsKeyBar(hardwareKeysOnly = true, windowWidthDp = 1280f))
        assertTrue(kitTerminalShowsKeyBar(hardwareKeysOnly = true, windowWidthDp = 800f))
        assertTrue(kitTerminalShowsKeyBar(hardwareKeysOnly = false, windowWidthDp = 1280f))
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), ContentFakePlatform(), "en", stillFrame = true, content = content)
        }
    }

    @Test
    fun aKeyCapFillsItsWholeSlotNotJustItsLabel() = runComposeUiTest {
        // Dart `_Key`: Padding → DecoratedBox → Center, so the surface3 cap covers the 48 dp key, not the face only.
        var cap = androidx.compose.ui.graphics.Color.Unspecified
        setContent {
            Themed {
                cap = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.surface3
                Box(Modifier.width(412.dp).testTag("bar")) {
                    TerminalKeyBar(controller = TerminalKeyBarController(), onKey = { _, _, _ -> })
                }
            }
        }
        val image = onNodeWithTag("bar").captureToImage().toPixelMap()
        // 24 dp into Esc's slot, 6 dp below its top (the bar's 4 dp padding + 2): above the label, inside the cap.
        assertEquals(cap, image[24 * 2, (4 + 6) * 2])
    }

    @Test
    fun theLiveGridIsOneNodeNamedForAScreenReader() = runComposeUiTest {
        setContent {
            Themed {
                Box(Modifier.size(412.dp, 400.dp)) {
                    KitTerminalScreen("\u001b[32mdev@build-server\u001b[0m$ ls --color\r\n", "Terminal · build-server", Modifier.fillMaxSize())
                }
            }
        }
        onAllNodesWithText("ls --color", substring = true, useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithContentDescription("Terminal · build-server").assertCountEquals(1)
    }

    @Test
    fun inArabicTheKeyBarStaysCentredOnAWideWindow() = runComposeUiTest {
        // Dart lays the keys out left to right and centred in every language; the band must not push them to its
        // start (the right edge in Arabic).
        setContent {
            Themed {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
                    LocalDensity provides Density(1f, 1f), // the test window is 1024 px wide
                ) {
                    Box(Modifier.width(1000.dp)) {
                        TerminalKeyBar(controller = TerminalKeyBarController(), onKey = { _, _, _ -> })
                    }
                }
            }
        }
        val keys = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNodes()
        val left = keys.minOf { it.boundsInRoot.left }
        val right = keys.maxOf { it.boundsInRoot.right }
        val width = 1000f
        assertTrue(kotlin.math.abs(left - (width - right)) <= 2f, "keys span $left..$right of $width: not centred")
    }
}

