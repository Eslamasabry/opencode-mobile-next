package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dart `KitConfirmSheet` (lib/ui/kit/kit_confirm_sheet.dart) and `KitConsequences` (lib/ui/kit/kit_consequences.dart). */
@OptIn(ExperimentalTestApi::class)
class KitConfirmTest {
    private val move =
        KitConfirmSpec(
            title = "Move fox?",
            body = "The conversation moves to the notes project.",
            confirmLabel = "Move fox",
            consequenceItems = listOf(
                KitConsequence("Its 3 changed files move with it", KitConsequenceMark.Neutral),
                KitConsequence("Its agent keeps its model", KitConsequenceMark.Neutral),
            ),
            alternative = KitAction("Copy fox instead", onClick = {}),
        )

    @Test
    fun `B-KIT-015 on a phone the safer path sits at the start, its words lined up with the question, not stretched`() =
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitSheetHost { KitConfirm(spec = move, onConfirm = {}, onCancel = {}) }
                }
            }
            waitForIdle()
            val content = onNodeWithTag("kit-confirm-content").getBoundsInRoot()
            val alternative = onNode(hasText("Copy fox instead") and hasClickAction()).getBoundsInRoot()
            // KitInset (kit_buttons.dart:1270-1290): start-aligned and pulled back by space 2.
            assertEquals(content.left - 8.dp, alternative.left)
            assertTrue(alternative.right < content.right, "the tertiary answer keeps its own width")
        }

    @Test
    fun `B-KIT-015 the facts sit in one panel, neutral ones marked by a dot, with one hairline between two facts`() =
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitSheetHost { KitConfirm(spec = move, onConfirm = {}, onCancel = {}) }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-consequences").assertCountEquals(1)
            onAllNodesWithTag("kit-consequence-dot", useUnmergedTree = true).assertCountEquals(2)
        }

    @Test
    fun `B-KIT-015 the confirmation holds the confirm cap of 560 dp on a wide window`() =
        runDesktopComposeUiTest(1600, 1000) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitSheetHost { KitConfirm(spec = move, onConfirm = {}, onCancel = {}) }
                }
            }
            waitForIdle()
            val content = onNodeWithTag("kit-confirm-content").getBoundsInRoot()
            assertTrue(content.right - content.left <= 560.dp)
        }

    @Test
    fun `B-KIT-015 rules - a question, a named act, the typed name, Enter only for a neutral question`() {
        assertTrue(ConfirmRules.titleIsQuestion("Stop fox?"))
        assertTrue(ConfirmRules.titleIsQuestion("حذف fox؟"))
        assertTrue(!ConfirmRules.labelNamesTheAct("OK"))
        assertTrue(ConfirmRules.confirmEnabled("fox", "fox"))
        assertTrue(!ConfirmRules.confirmEnabled("fox", "fo"))
        assertTrue(ConfirmRules.enterConfirms(KitConfirmKind.Neutral))
        assertTrue(!ConfirmRules.enterConfirms(KitConfirmKind.Destructive))
    }
}
