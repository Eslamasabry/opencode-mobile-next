package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The translation pipeline's length check (native/tools/strings/README.md): English made 40 % longer with accents (the
 * pseudo-locale of `:tools:strings` `PseudoLocale`, copied here because no module may depend on a tool) must not clip in
 * the kit parts that carry short action labels, at phone width. A translation that is longer than that is caught by
 * `TranslationQualityTest`'s label budget; this proves the parts can hold the budget.
 */
@OptIn(ExperimentalTestApi::class)
class PseudoLocaleOverflowTest {
    private val labels =
        listOf("Try again", "Add server", "New conversation", "Delete this conversation", "Open project files", "Allow once", "Stop all agents")

    private val swaps = mapOf('a' to 'à', 'e' to 'é', 'i' to 'î', 'o' to 'ö', 'u' to 'ü', 'c' to 'ç', 'n' to 'ñ', 's' to 'š', 'y' to 'ý')

    private fun expand(text: String): String {
        val grown = text.map { swaps[it] ?: it }.joinToString("")
        val extra = (text.count { it.isLetter() } * 0.4).toInt()
        return if (extra > 0) grown + " " + "ŵŵŵŵŵŵŵŵ".repeat(extra / 8 + 1).take(extra) else grown
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(360, 760) { content() }
            }
        }
    }

    @Test
    fun `a full-width button holds a label 40 percent longer than the English without clipping it`() = runComposeUiTest {
        var shown by mutableStateOf("")
        setContent { Themed { KitButton(shown.ifEmpty { "x" }, onClick = {}, fullWidth = true) } }
        for (label in labels) {
            val long = expand(label)
            shown = long
            waitForIdle()
            val node = onNodeWithText(long, useUnmergedTree = true).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
            assertTrue(!results.first().hasVisualOverflow, "the button clips \"$long\" (English \"$label\")")
            assertTrue(node.boundsInRoot.right <= 360f && node.boundsInRoot.left >= 0f, "\"$long\" leaves the 360 dp screen: ${node.boundsInRoot}")
        }
    }
}
