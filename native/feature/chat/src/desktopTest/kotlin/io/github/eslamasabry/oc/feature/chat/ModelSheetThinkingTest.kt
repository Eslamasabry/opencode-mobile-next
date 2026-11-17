package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-CHAT-073 (owner, build 10077): in "Choose a model" the Thinking levels come first, under the title and above the
 * search field, and stay where they are while the list scrolls; a model without levels has no Thinking row; and no
 * level chip is cut at the screen's edge at 360 dp, text 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class ModelSheetThinkingTest {
    private val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private val levels = listOf("low", "medium", "high", "xhigh", "max")
    private val thinker = ComposerModel("prov", "think", "Thinker", supportsAttachments = true, variants = levels)
    private val plain = ComposerModel("prov", "plain", "Plain", supportsAttachments = true)
    private val fillers = (1..9).map { ComposerModel("prov", "m$it", "Model $it", supportsAttachments = true) }

    private class Actions : BasicComposerActions {
        override fun draftChanged(text: String) {}

        override suspend fun send(text: String): Boolean = true

        override fun loadModels() {}

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true

        override fun attachFile() {}

        override fun removeAttachment(index: Int) {}

        override fun dismissAttachmentFailure() {}

        override suspend fun selectAgent(agent: String): Boolean = true

        override fun mentionFile(path: String) {}

        override fun attach(kind: io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind) {}

        override fun retrySave() {}
    }

    private fun ui(models: ComposerModels) =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(),
            targetKey = "p/s",
            models = models,
            pickingFile = false,
            attachmentFailure = null,
            filesAvailable = true,
        )

    @Composable
    private fun Scene(fontScale: Float, models: ComposerModels) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
                localeTag = "en",
            ) {
                KitSheetHost { BasicComposer(ui(models), Actions(), chrome) }
            }
        }
    }

    private fun withThinker() =
        ComposerModels(choices = fillers + thinker, selected = thinker, variant = "high")

    @Test
    fun `B-CHAT-073 Thinking stands above the search field and the model list`() =
        runDesktopComposeUiTest(360, 800) {
            setContent { Scene(1f, withThinker()) }
            onNodeWithContentDescription("Model").performClick()
            waitForIdle()
            val thinking = onNodeWithText("Thinking").fetchSemanticsNode().boundsInRoot
            val search = onNodeWithTag("chat-model-search").fetchSemanticsNode().boundsInRoot
            val list = onNodeWithTag("chat-model-list").fetchSemanticsNode().boundsInRoot
            assertTrue(thinking.bottom <= search.top, "Thinking ($thinking) is above the search field ($search)")
            assertTrue(thinking.bottom <= list.top, "Thinking is above the list")
        }

    @Test
    fun `B-CHAT-073 Thinking stays where it is while the model list scrolls`() =
        runDesktopComposeUiTest(360, 800) {
            setContent { Scene(1f, withThinker()) }
            onNodeWithContentDescription("Model").performClick()
            waitForIdle()
            val before = onNodeWithText("Thinking").fetchSemanticsNode().boundsInRoot
            onNodeWithTag("chat-model-list").performScrollToIndex(8)
            waitForIdle()
            onNodeWithText("Thinking").assertIsDisplayed()
            assertEquals(before, onNodeWithText("Thinking").fetchSemanticsNode().boundsInRoot)
        }

    @Test
    fun `B-CHAT-073 a model without thinking levels has no Thinking row`() =
        runDesktopComposeUiTest(360, 800) {
            setContent { Scene(1f, ComposerModels(choices = fillers + plain, selected = plain)) }
            onNodeWithContentDescription("Model").performClick()
            waitForIdle()
            onNodeWithTag("chat-model-search").assertIsDisplayed()
            onAllNodesWithText("Thinking").assertCountEquals(0)
        }

    private fun noChipCut(fontScale: Float) =
        runDesktopComposeUiTest(360, 800) {
            setContent { Scene(fontScale, withThinker()) }
            onNodeWithContentDescription("Model").performClick()
            waitForIdle()
            for (word in listOf("Default", "Low", "Medium", "High", "Extra high", "Max")) {
                val b = onNodeWithContentDescription(word).fetchSemanticsNode().boundsInRoot
                assertTrue(b.left >= 0f && b.right <= 360f, "the $word chip is whole inside 360 dp at text $fontScale: $b")
            }
        }

    @Test
    fun `B-CHAT-073 no Thinking chip is cut at 360 dp, text 1_0`() = noChipCut(1f)

    @Test
    fun `B-CHAT-073 no Thinking chip is cut at 360 dp, text 1_3`() = noChipCut(1.3f)
}
