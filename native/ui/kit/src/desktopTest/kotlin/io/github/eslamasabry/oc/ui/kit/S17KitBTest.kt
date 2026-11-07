package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scoreboard 2026-11-07 group A (orders 08, 11, 15) in `:ui:kit`: the measured gaps against Flutter's goldens, each
 * as a rule that fails on the code before its fix.
 */
@OptIn(ExperimentalTestApi::class)
class S17KitBTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    // A7 (kit_sheet_default: the sheet was 48 px taller) -----------------------------------------------------------

    @Test
    fun `A7 a row with its own padding lays out as given, as KitRow padding does in kit_row_dart`() = runComposeUiTest {
        setContent {
            Themed {
                Column(Modifier.fillMaxWidth()) {
                    KitRow(
                        "English",
                        Modifier.testTag("own"),
                        supporting = "The phone’s language",
                        leading = KitRowLeading.Glyph(KitIcons.check, current = true),
                        padding = PaddingValues(vertical = 8.dp),
                        onClick = {},
                    )
                }
            }
        }
        val row = onNodeWithTag("own").getBoundsInRoot()
        // Two lines: the 64 dp minimum holds, the vertical 8 sits inside it and nothing is added outside.
        assertEquals(64f, (row.bottom - row.top).value, 0.5f, "a two-line row with padding 8 is 64 dp, not 80")
        // No rails: the leading slot starts at the row's own edge.
        assertEquals(0f, row.left.value, 0.5f)
        val title = onNodeWithText("English").getBoundsInRoot()
        assertTrue(title.left.value < 16f + 28f + 12f + 0.5f, "the words start at tile + gap, without the 16 dp rail (at ${title.left})")
    }

    // A9 (kit_phrase_chips_default: the chips started at x 0 where Flutter's are centred) ------------------------

    @Composable
    private fun Chips() {
        KitPhraseChips(
            phrases = listOf("Continue", "Run the tests", "Commit this"),
            label = "Quick phrases",
            insertLabel = { "Send: $it" },
            menuLabel = { "Phrase options" },
            hideLabel = "Hide",
            onChoose = {},
            onHide = {},
            modifier = Modifier.testTag("chips"),
        )
    }

    @Test
    fun `A9 the phrase row is as wide as its chips, so a centring parent centres it`() = runComposeUiTest {
        setContent { Themed { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Chips() } } }
        val row = onNodeWithTag("chips").getBoundsInRoot()
        assertTrue(row.left.value > 8f, "the row is centred, not at x 0 (left ${row.left})")
        assertEquals(412f - row.right.value, row.left.value, 1f, "equal room on both sides")
    }

    @Test
    fun `A9 in a start-aligned column, as the composer holds it, the phrase row starts on the edge`() = runComposeUiTest {
        setContent { Themed { Column(Modifier.fillMaxWidth()) { Chips() } } }
        assertEquals(0f, onNodeWithTag("chips").getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `A7 an Arabic row title in an English list starts on the reading edge, as Flutter's ambient direction does`() = runComposeUiTest {
        setContent {
            Themed {
                Column(Modifier.fillMaxWidth()) {
                    KitRow("English", supporting = "The phone’s language", leading = KitRowLeading.Glyph(KitIcons.check), onClick = {})
                    KitRow("العربية", supporting = "Arabic", leading = KitRowLeading.Glyph(KitIcons.info), onClick = {})
                }
            }
        }
        val latin = onNodeWithText("English").getBoundsInRoot().left.value
        val arabic = onNodeWithText("العربية").getBoundsInRoot()
        // The title box spans the words column; its glyphs sit at its left. The box starts where the Latin title does.
        assertEquals(latin, arabic.left.value, 0.5f)
        val layout = textLayoutOf("العربية")
        assertTrue(layout.getLineLeft(0) < 1f, "the Arabic glyphs start at the left of the title box (line left ${layout.getLineLeft(0)})")
    }

    // A6 (kit_notice_offer: the action sat under the sentence as a filled button) ----------------------------------

    @Test
    fun `A6 an offer that fits is one line - sentence, the action as words, and the close`() = runComposeUiTest {
        setContent {
            Themed {
                io.github.eslamasabry.oc.ui.kit.state.KitOfferNotice(
                    "Pin it to Work?",
                    io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction("Pin", onClick = {}),
                    io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction("Not now", onClick = {}),
                )
            }
        }
        val words = onNodeWithText("Pin it to Work?").getBoundsInRoot()
        val pin = onNodeWithText("Pin").getBoundsInRoot()
        val centre = { b: androidx.compose.ui.unit.DpRect -> (b.top + b.bottom).value / 2 }
        assertEquals(centre(words), centre(pin), 1.5f, "the action is on the sentence's line")
        assertTrue(pin.left >= words.right, "after the sentence")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textLayoutOf(text: String): androidx.compose.ui.text.TextLayoutResult {
        val out = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        onNodeWithText(text).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(out)
        return out.first()
    }
}
