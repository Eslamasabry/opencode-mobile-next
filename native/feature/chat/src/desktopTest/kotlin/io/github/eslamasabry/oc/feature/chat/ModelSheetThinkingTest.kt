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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertCountEquals
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
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
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
        val calls = mutableListOf<String>()

        override fun draftChanged(text: String) {
            calls += "draftChanged"
        }

        override suspend fun send(text: String): Boolean = true

        override fun loadModels() {
            calls += "loadModels"
        }

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true

        override fun attachFile() {
            calls += "attachFile"
        }

        override fun removeAttachment(index: Int) {
            calls += "removeAttachment"
        }

        override fun dismissAttachmentFailure() {
            calls += "dismissAttachmentFailure"
        }

        override suspend fun selectAgent(agent: String): Boolean = true

        override fun mentionFile(path: String) {
            calls += "mentionFile"
        }

        override fun attach(kind: io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind) {
            calls += "attach"
        }

        override fun retrySave() {
            calls += "retrySave"
        }
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
        kitUiTest(360, 800) {
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
        kitUiTest(360, 800) {
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
        kitUiTest(360, 800) {
            setContent { Scene(1f, ComposerModels(choices = fillers + plain, selected = plain)) }
            onNodeWithContentDescription("Model").performClick()
            waitForIdle()
            onNodeWithTag("chat-model-search").assertIsDisplayed()
            onAllNodesWithText("Thinking").assertCountEquals(0)
        }

    private fun noChipCut(fontScale: Float) =
        kitUiTest(360, 800) {
            setContent { Scene(fontScale, withThinker()) }
            onNodeWithContentDescription("Model").performClick()
            waitForIdle()
            for (word in listOf("Default", "Low", "Medium", "High", "Extra high", "Max")) {
                val b = onNodeWithContentDescription(word).fetchSemanticsNode().boundsInRoot
                assertTrue(
                    b.left >= 0f && b.right <= 360f,
                    "the $word chip is whole inside 360 dp at text $fontScale: $b",
                )
            }
        }

    @Test
    fun `B-CHAT-073 no Thinking chip is cut at 360 dp, text 1_0`() = noChipCut(1f)

    @Test
    fun `B-CHAT-073 no Thinking chip is cut at 360 dp, text 1_3`() = noChipCut(1.3f)

    /** The sheet at 360 dp wide, density 3, dark, text 1.0 and 1.3, written to build/model-sheet-render for review. */
    @Test
    fun `B-CHAT-073 render the model sheet at 360 dp, text 1_0 and 1_3`() {
        for (scale in listOf(1.0f, 1.3f)) {
            kitUiTest(1080, 2200) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(3f, scale)) {
                        KitTheme(
                            platform = DesktopKitPlatform(),
                            appearance =
                                KitAppearance(
                                    mode = io.github.eslamasabry.oc.ui.design.effects.AppearanceMode.Dark,
                                    motion = MotionLevel.Off,
                                ),
                            stillFrame = true,
                            localeTag = "en",
                        ) {
                            KitSheetHost { BasicComposer(ui(withThinker()), Actions(), chrome) }
                        }
                    }
                }
                onNodeWithContentDescription("Model").performClick()
                waitForIdle()
                val image = onRoot().captureToImage().toAwtImage()
                val file = java.io.File("build/model-sheet-render/sheet-dark-360-text-$scale.png")
                file.parentFile.mkdirs()
                javax.imageio.ImageIO.write(image, "png", file)
                assertEquals(1080, image.width)
            }
        }
    }
}
