package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** QA wave 4 B12: in Arabic, the user bubble and the thinking text kept right to left for English words. */
@OptIn(ExperimentalTestApi::class)
class PromptDirectionTest {
    private fun androidx.compose.ui.test.ComposeUiTest.direction(text: String): ResolvedTextDirection {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first().getParagraphDirection(0)
    }

    @Test
    fun `B12 B-CHAT-090 an English prompt and thought read left to right in an Arabic interface`() = runComposeUiTest {
        setContent {
            ChatTestTheme(localeTag = "ar") {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column {
                        KitPromptBubble("Answer in one line.")
                        KitQuietLine("Thinking about the answer.")
                        KitPromptBubble("أجب بسطر واحد.")
                    }
                }
            }
        }
        assertEquals(ResolvedTextDirection.Ltr, direction("Answer in one line."))
        assertEquals(ResolvedTextDirection.Ltr, direction("Thinking about the answer."))
        assertEquals(ResolvedTextDirection.Rtl, direction("أجب بسطر واحد."))
    }
}
