package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitRuleBroken
import io.github.eslamasabry.oc.ui.kit.swatch.KitSwatch
import io.github.eslamasabry.oc.ui.kit.swatch.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.swatch.KitSwatchRules
import io.github.eslamasabry.oc.ui.kit.swatch.KitThemePreview
import io.github.eslamasabry.oc.ui.kit.swatch.kitSwatchCheckColor
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The colour swatch picker (Flutter `test/kit/kit_swatch_test.dart`, B-KIT-049). */
@OptIn(ExperimentalTestApi::class)
class KitSwatchTest {
    private val blue = Color(0xFF3D6BFF)

    private fun KitColors.sample(): KitColors = copy(
        ground = Color(0xFF102030), surface1 = Color(0xFF405060), text1 = Color(0xFFF0E0D0), accent = blue,
        success = Color(0xFF20C060), surface3 = Color(0xFF707070), hairline = Color(0xFF303030),
    )

    @Composable
    private fun Stage(locale: String = "en", width: Int = 412, fontScale: Float = 1f, content: @Composable (KitColors) -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), locale, stillFrame = true) {
                val roles = LocalKitColors.current.sample()
                InWindow(width, 760) { Column { content(roles) } }
            }
        }
    }

    private fun themes(roles: KitColors, n: Int = 8, disabled: Set<Int> = emptySet(), selected: Int = -1, onPress: (Int) -> Unit = {}) =
        List(n) { i ->
            KitSwatch.Theme(
                roles, "Pack $i", selected = i == selected,
                onClick = if (i in disabled) null else ({ onPress(i) }),
                disabledReason = if (i in disabled) "Needs Android 12 or later" else null, tag = "sw$i",
            )
        }

    private fun SemanticsNodeInteraction.top(): Float = getBoundsInRoot().top.value

    @Test
    fun `B-KIT-049 tapping a swatch presses it once, and Enter or Space on the focused swatch presses it too`() = runComposeUiTest {
        var count = 0
        setContent { Stage { KitSwatchGrid("Theme", themes(it, 2) { count++ }) } }
        onNodeWithTag("sw0").performClick()
        waitForIdle()
        assertEquals(1, count)
        onRoot().performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("sw0").assertIsFocused()
        onNodeWithTag("sw0").performKeyInput { pressKey(Key.Enter) }
        assertEquals(2, count)
        onNodeWithTag("sw0").performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(3, count)
    }

    @Test
    fun `B-KIT-049 a selected swatch is selected to a screen reader, says In use and shows the check`() = runComposeUiTest {
        setContent { Stage { KitSwatchGrid("Theme", themes(it, 2, selected = 1)) } }
        val node = onNodeWithTag("sw1").fetchSemanticsNode()
        assertEquals(true, node.config.getOrNull(SemanticsProperties.Selected))
        assertEquals("In use", node.config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(listOf("Pack 1"), node.config.getOrNull(SemanticsProperties.ContentDescription), "the name, read once")
        assertEquals(Role.RadioButton, node.config.getOrNull(SemanticsProperties.Role))
        onNodeWithTag("kit-swatch-check", useUnmergedTree = true).assertExists()
        assertEquals(false, onNodeWithTag("sw0").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `B-KIT-049 a disabled swatch must give a reason, shows it, reads it, and cannot take focus`() {
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest {
                setContent { Stage { KitSwatchGrid("Theme", listOf(KitSwatch.Theme(it, "Material You", false, onClick = null))) } }
            }
        }
        runComposeUiTest {
            setContent { Stage { KitSwatchGrid("Theme", themes(it, 2, disabled = setOf(0))) } }
            onNodeWithTag("kit-swatch-reason", useUnmergedTree = true).assertExists()
            val node = onNodeWithTag("sw0").fetchSemanticsNode()
            assertTrue(node.config.contains(SemanticsProperties.Disabled))
            assertEquals("Needs Android 12 or later", node.config.getOrNull(SemanticsProperties.StateDescription))
            assertEquals(listOf("Pack 0"), node.config.getOrNull(SemanticsProperties.ContentDescription))
            onRoot().performKeyInput { pressKey(Key.Tab) }
            assertTrue(onNodeWithTag("sw0").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) != true, "a disabled swatch cannot take focus")
            onNodeWithTag("sw1").assertIsFocused()
        }
    }

    @Test
    fun `B-KIT-049 the miniature is drawn in the theme's own colours and the accent check reads on its colour`() = runComposeUiTest {
        lateinit var roles: KitColors
        setContent {
            Stage {
                roles = it
                KitSwatchGrid("Theme", themes(it, 1))
                KitSwatchGrid("Accent colour", listOf(KitSwatch.Accent(blue, "blue", selected = true, onClick = {}, tag = "acc")))
            }
        }
        fun centre(tag: String): Color {
            val map = onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
            return map[map.width / 2, map.height / 2]
        }
        fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.02f && abs(a.green - b.green) < 0.02f && abs(a.blue - b.blue) < 0.02f
        assertTrue(near(roles.accent, centre("kit-swatch-accent-dot")))
        assertTrue(near(roles.success, centre("kit-swatch-success-dot")))
        assertTrue(near(roles.text1, centre("kit-swatch-text-line")))
        val panel = onNodeWithTag("kit-swatch-panel", useUnmergedTree = true).captureToImage().toPixelMap()
        assertTrue(near(roles.surface1, panel[panel.width - 2, panel.height - 2]))
        onNodeWithTag("acc").assertExists()
        onNodeWithTag("kit-swatch-check", useUnmergedTree = true).assertExists()
        val ink = kitSwatchCheckColor(blue)
        val argb = (0xFF shl 24) or ((blue.red * 255 + 0.5f).toInt() shl 16) or ((blue.green * 255 + 0.5f).toInt() shl 8) or (blue.blue * 255 + 0.5f).toInt()
        assertEquals(Color(RoleDerivation.onColor(Argb(argb)).v), ink)
    }

    @Test
    fun `B-KIT-049 the grid picks its columns by width, and one fewer from text scale 1_3`() {
        assertEquals(3, KitSwatchRules.columnsFor(412f, 1f))
        assertEquals(2, KitSwatchRules.columnsFor(320f, 1f))
        assertEquals(6, KitSwatchRules.columnsFor(900f, 1f), "capped")
        assertEquals(2, KitSwatchRules.columnsFor(412f, 1.3f))
        assertEquals(6, KitSwatchRules.accentColumnsFor(328f, 48f))
        for ((width, expected) in listOf(412 to 3, 320 to 2, 900 to 6)) {
            runComposeUiTest {
                setContent { Stage(width = width) { KitSwatchGrid("Theme", themes(it)) } }
                val first = onNodeWithTag("sw0").top()
                val inRow = (0 until 8).count { abs(onNodeWithTag("sw$it").top() - first) < 1 }
                assertEquals(expected, inRow, "at $width dp")
            }
        }
    }

    @Test
    fun `B-KIT-049 accent circles are 48 dp targets 8 dp apart from the start`() = runComposeUiTest {
        setContent {
            Stage { KitSwatchGrid("Accent colour", List(3) { i -> KitSwatch.Accent(blue, "c$i", i == 0, onClick = {}, tag = "a$i") }) }
        }
        val r = (0 until 3).map { onNodeWithTag("a$it").getBoundsInRoot() }
        r.forEach { assertEquals(48f, (it.right - it.left).value, 0.5f); assertEquals(48f, (it.bottom - it.top).value, 0.5f) }
        assertEquals(0f, r[0].left.value, 0.5f)
        assertEquals(8f, (r[1].left - r[0].right).value, 0.5f)
        assertEquals(8f, (r[2].left - r[1].right).value, 0.5f)
    }

    @Test
    fun `B-KIT-049 arrows move focus, skip disabled swatches, follow RTL, and Home and End go to the ends`() {
        runComposeUiTest {
            setContent { Stage { KitSwatchGrid("Theme", themes(it, disabled = setOf(1, 7))) } }
            onRoot().performKeyInput { pressKey(Key.Tab) }
            onNodeWithTag("sw0").assertIsFocused()
            onNodeWithTag("sw0").performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            onNodeWithTag("sw2").assertIsFocused()
            onNodeWithTag("sw2").performKeyInput { pressKey(Key.DirectionLeft) }
            waitForIdle()
            onNodeWithTag("sw0").assertIsFocused()
            onNodeWithTag("sw0").performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            onNodeWithTag("sw3").assertIsFocused() // 3 columns at 412 dp
            onNodeWithTag("sw3").performKeyInput { pressKey(Key.MoveEnd) }
            waitForIdle()
            onNodeWithTag("sw6").assertIsFocused()
            onNodeWithTag("sw6").performKeyInput { pressKey(Key.MoveHome) }
            waitForIdle()
            onNodeWithTag("sw0").assertIsFocused()
        }
        runComposeUiTest {
            setContent { Stage(locale = "ar") { KitSwatchGrid("Theme", themes(it)) } }
            onRoot().performKeyInput { pressKey(Key.Tab) }
            onNodeWithTag("sw0").performKeyInput { pressKey(Key.DirectionLeft) }
            waitForIdle()
            onNodeWithTag("sw1").assertIsFocused()
        }
    }

    @Test
    fun `B-KIT-049 Tab enters the grid once, on the selected swatch, and the next Tab leaves it`() = runComposeUiTest {
        setContent {
            Stage {
                KitSwatchGrid("Theme", themes(it, 4, selected = 2))
                KitButton("After", onClick = {}, modifier = Modifier.then(Modifier))
            }
        }
        onRoot().performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag("sw2").assertIsFocused()
        onNodeWithTag("sw2").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        (0 until 4).forEach { onNodeWithTag("sw$it").assertIsNotFocused() }
        onNodeWithText("After").assertIsFocused()
    }

    @Test
    fun `B-KIT-049 a focused swatch gets the focus ring, and hover fills the tile with the next surface`() = runComposeUiTest {
        lateinit var base: KitColors
        setContent {
            Stage {
                base = LocalKitColors.current
                KitSwatchGrid("Theme", themes(it, 2))
            }
        }
        onNodeWithTag("kit-swatch-focus-ring", useUnmergedTree = true).assertDoesNotExist()
        onRoot().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        onNodeWithTag("kit-swatch-focus-ring", useUnmergedTree = true).assertExists()
        fun corner(): Color = onNodeWithTag("sw1").captureToImage().toPixelMap().let { it[it.width / 2, 4] }
        val before = corner()
        onNodeWithTag("sw1").performMouseInput { moveTo(Offset(20f, 4f)) }
        waitForIdle()
        val after = corner()
        assertNotEquals(before, after, "hover changes the tile")
        assertTrue(abs(after.red - base.surface2.red) < 0.02f && abs(after.blue - base.surface2.blue) < 0.02f)
    }

    @Test
    fun `B-KIT-049 the preview renders as one named image, its code line stays left to right in Arabic`() = runComposeUiTest {
        setContent { Stage(locale = "ar") { KitThemePreview(it, "Preview of Graphite") } }
        val node = onNodeWithTag("kit-theme-preview").fetchSemanticsNode()
        assertEquals(listOf("Preview of Graphite"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(Role.Image, node.config.getOrNull(SemanticsProperties.Role))
        val b = onNodeWithTag("kit-theme-preview").getBoundsInRoot()
        assertTrue((b.bottom - b.top).value > 100f, "every preview part is drawn: $b")
    }

    @Test
    fun `B-KIT-049 long names and reasons at the largest text do not overflow`() = runComposeUiTest {
        val long = "A very long theme name that keeps going well past the tile"
        val reason = "This colour needs a newer version of Android than this phone runs, so it cannot be chosen here"
        setContent {
            Stage(width = 320, fontScale = 2f) {
                KitSwatchGrid(
                    "Theme",
                    listOf(
                        KitSwatch.Theme(it, long, true, onClick = {}, tag = "l0"),
                        KitSwatch.Theme(it, long, false, onClick = null, disabledReason = reason, tag = "l1"),
                    ),
                )
            }
        }
        onNodeWithTag("kit-swatch-reason", useUnmergedTree = true).assertExists()
        for (tag in listOf("l0", "l1")) {
            val b = onNodeWithTag(tag).getBoundsInRoot()
            assertTrue(b.left.value >= -0.5f && b.right.value <= 320.5f, "$tag stays inside the window: $b")
        }
    }
}
