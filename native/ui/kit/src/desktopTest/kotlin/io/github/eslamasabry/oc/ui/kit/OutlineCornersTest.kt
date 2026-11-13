@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Owner 2026-11-13 ("You are fixing these broken corners everywhere also right?"): a focused multiline field in a sheet
 * under Glass drew its green focus outline along the four sides but not round its four corners. The field clipped its
 * box to one named shape and drew its style's look (fill and outline) on another, so the clip cut the outline off at
 * every corner. Every outlined, focusable or selectable kit part's state outline goes all the way round its corners, in
 * every style, dark and light ([OutlineCorners]: no stored picture, no radius; the rays find the outline or name the
 * gap). Rendered at the phone's 2.625 px/dp.
 */
class OutlineCornersTest {
    private class Case(val name: String, val matcher: SemanticsMatcher, val content: @Composable () -> Unit)

    private val fieldCases =
        listOf(
            Case("focused one-line field", hasSetTextAction()) {
                val focus = remember { FocusRequester() }
                KitField(rememberTextFieldState("example.org"), "Address", focusRequester = focus, labelShown = false)
                LaunchedEffect(Unit) { focus.requestFocus() }
            },
            Case("focused multiline field", hasSetTextAction()) {
                val focus = remember { FocusRequester() }
                KitField(
                    rememberTextFieldState("first line\nsecond line\nthird line"),
                    "Notes",
                    kind = KitFieldKind.Multiline,
                    focusRequester = focus,
                    labelShown = false,
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            },
            Case("field with an error", hasSetTextAction()) {
                KitField(rememberTextFieldState("99999"), "Port", kind = KitFieldKind.Number, error = "Too big", labelShown = false)
            },
        )

    @Test
    fun `a field's focus and error outline goes round all four corners in every style, dark and light`() {
        val gaps = mutableListOf<String>()
        for (style in Style.entries) for (dark in listOf(true, false)) for (case in fieldCases) {
            gaps += check(style, dark, case)
        }
        if (gaps.isNotEmpty()) fail("Outlines cut at the corners (${gaps.size}):\n" + gaps.joinToString("\n"))
    }

    private fun check(style: Style, dark: Boolean, case: Case): List<String> {
        var result = emptyList<String>()
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { Scene(style, dark) { case.content() } }
            waitForIdle()
            val node = onNode(case.matcher).fetchSemanticsNode()
            val b = node.boundsInRoot
            val box = Rect(b.left, b.top, b.right, b.bottom)
            assertTrue(box.height >= 48f * DENSITY - 1f, "${case.name}: the matched node is the field's box (${box.height} px tall)")
            val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            val what = "${style.name} ${if (dark) "dark" else "light"} ${case.name}"
            result = OutlineCorners.gaps(image, box, DENSITY).map { "$what: $it" }
        }
        return result
    }

    @Composable
    private fun Scene(style: Style, dark: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
            KitTheme(
                KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off, style = style),
                FakeKitPlatform(),
                "en",
            ) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground.copy(alpha = 1f)).padding(24.dp)) { content() }
            }
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 720
        const val DENSITY = 2.625f
    }
}
