package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scoreboard 2026-11-07 group F, F1: Flutter's idle message box is ONE row, "+", the words and the round control
 * (`lib/ui/kit/chat/kit_composer.dart:796-808`), with the approval and model chips on a strip ABOVE it
 * (`lib/ui/screens/chat/chat_composer_region.dart:44-77`). Native drew two rows (the field, then + · Model · Send) with
 * the chips inside. Growing words still move the controls under a full-width field.
 */
@OptIn(ExperimentalTestApi::class)
class F1ComposerOneRowTest {
    private class Actions : BasicComposerActions {
        override fun draftChanged(text: String) = Unit
        override suspend fun send(text: String): Boolean = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true
    }

    private fun ui() =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            targetKey = "p/s",
            models = ComposerModels(usesDefault = true),
            filesAvailable = true,
        )

    private val chrome =
        ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false }, rowChips = {
            KitText("Ask first", Modifier.testTag("approval-chip"))
        })

    @Composable
    private fun Scene() {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
        ) {
            Box(Modifier.width(380.dp)) { BasicComposer(ui(), Actions(), chrome) }
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `F1 the idle box is one row and the chips sit on a strip above it`() = runComposeUiTest {
        setContent { Scene() }
        waitForIdle()
        // The tag sits inside the face's S1 padding: the face is 4 dp larger on every side.
        val box = bounds("kit-composer-box").inflate(4f)
        val plus = bounds("chat-attach-button")
        val field = bounds("chat-composer-field")
        val send = bounds("chat-send-button")
        val model = bounds("chat-model-button")
        val approval = bounds("approval-chip")
        // One row: the plus, the words and Send share a line (Flutter `Row(leading, field, trailing)`).
        assertTrue(abs(plus.center.y - send.center.y) < 1f, "plus $plus and Send $send share a row")
        assertTrue(field.top < send.center.y && send.center.y < field.bottom, "the field $field is on Send's row $send")
        assertTrue(plus.right <= field.left + 0.5f && field.right <= send.left + 0.5f, "+ · words · Send in order")
        // One 48 dp row plus the box's 4 dp padding (F: kit_composer.dart:572 minTarget; KitComposerBox S1).
        assertTrue(box.height <= 56f + 0.5f, "the idle box is one row tall, was ${box.height}")
        // The chips: above the box, the approval chip first, the model at the end (F: chat_composer_region.dart:66-73).
        assertTrue(model.bottom <= box.top + 0.5f, "the model chip $model sits above $box")
        assertTrue(approval.bottom <= box.top + 0.5f, "the approval chip $approval sits above $box")
        assertTrue(approval.right <= model.left, "the approval chip $approval leads the model $model")
        assertTrue(abs(model.right - box.right) < 1f, "the model chip ends on the box's edge: $model vs $box")
        // Without voice an empty box shows Send, disabled: nothing to send yet (kit_composer.dart:434).
        onNode(
            hasAnyAncestor(hasTestTag("chat-send-button")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `F1 words past one line take the whole width and move the controls below`() = runComposeUiTest {
        setContent { Scene() }
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field")), useUnmergedTree = true)
            .performTextInput(LONG_WORDS)
        waitForIdle()
        val field = bounds("chat-composer-field")
        val plus = bounds("chat-attach-button")
        val send = bounds("chat-send-button")
        assertTrue(plus.top >= field.bottom - 0.5f, "the plus $plus moved under the words $field")
        assertTrue(send.top >= field.bottom - 0.5f, "Send $send moved under the words $field")
        // The stacked field keeps Flutter's S3 start gap (kit_composer.dart:778-781) and takes the rest of the width.
        assertTrue(field.left - plus.left <= 12f + 0.5f, "the words start on the box's edge: $field vs $plus")
        assertTrue(field.right >= send.left, "the words run over Send's column: $field vs $send")
    }

    private companion object {
        const val LONG_WORDS =
            "Look at the checkout flow again and tell me why the coupon total is stale after a refresh"
    }
}
