package io.github.eslamasabry.oc.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerPromptEditor
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowHost
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/**
 * 2026-11-08, native preview merge41 on the owner's phone: in a chat opened from its row, Prompt tools › Prompt editor
 * crashed the app. The chat page grows from the row (KitGrowPage), and the editor's full-screen overlay is a separate
 * window whose bar title joined the page's shared-element transition. The editor here is built from the same kit parts
 * as `:feature:chat-composer`'s PromptEditorPage (which this module may not depend on): KitPageOverlay, KitScreen,
 * KitTopBar, KitField.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class PromptEditorInGrownChatTest {
    private class Actions : BasicComposerActions {
        override fun draftChanged(text: String) {}

        override suspend fun send(text: String): Boolean = true

        override fun attachFile() {}

        override fun attach(kind: AttachKind) {}
    }

    private object OverlayEditor : ComposerPromptEditor {
        @Composable
        override fun Editor(visible: Boolean, field: TextFieldState, onClose: () -> Unit) {
            KitPageOverlay(visible, "Prompt editor", onClose) {
                KitScreen(
                    topBar = {
                        KitTopBar("Prompt editor", exit = KitTopBarExit.Close, exitLabel = "Close", onExit = onClose)
                    },
                    bottom = { KitActionBlock(primary = KitAction("Use in draft", onClose)) },
                ) {
                    KitField(state = field, label = "Prompt", kind = KitFieldKind.Multiline)
                }
            }
        }
    }

    private class Host(
        override val sharedTransitionScope: SharedTransitionScope,
        override val animatedVisibilityScope: AnimatedVisibilityScope,
    ) : KitGrowHost {
        override val transformEnabled: Boolean = true
        override val opening: Boolean = true
    }

    /** The chat entry as the shell shows it after a row opened it: a grown page with its own bar title. */
    @Composable
    private fun GrownChat(content: @Composable () -> Unit) {
        SharedTransitionLayout {
            val shared = this
            AnimatedVisibility(visible = true) {
                val host = remember { Host(shared, this) }
                CompositionLocalProvider(LocalKitGrowHost provides host) {
                    KitGrowPage(growKey = "p/s") {
                        KitScreen(topBar = { KitTopBar("Chat title") }, bottom = content) {}
                    }
                }
            }
        }
    }

    private fun ui() =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(ready = true),
            targetKey = "p/s",
            filesAvailable = true,
            attachKinds = setOf(AttachKind.Photos),
            attachmentsSupported = true,
        )

    @Test
    fun `opening the prompt editor from Prompt tools in a chat opened from its row`() = runComposeUiTest {
        val actions = Actions()
        val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(),
                stillFrame = true,
                localeTag = "en",
            ) {
                KitSheetHost {
                    GrownChat { BasicComposer(ui(), actions, chrome, promptEditor = OverlayEditor) }
                }
            }
        }
        onNodeWithContentDescription("Attach and more").performClick()
        onNodeWithText("More").performClick()
        onNodeWithText("Prompt editor").assertIsDisplayed().performClick()
        waitForIdle()

        // The editor is open over the chat, and Use in draft closes it again.
        onNodeWithText("Use in draft").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithText("Use in draft").assertDoesNotExist()
        onNodeWithText("Chat title").assertIsDisplayed()
    }
}
