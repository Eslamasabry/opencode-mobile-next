package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.Ref
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.footer
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContinueChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import kotlinx.collections.immutable.persistentListOf

/**
 * The chat scene of Flutter's chat goldens (test/goldens/chat_states_golden_test.dart and capture/fixtures.dart),
 * on the real native screen: "Fix flaky checkout test" in the project "shopfront", one prompt and one reply.
 */
internal class ChatScene(val nav: RecordingNavigator = RecordingNavigator()) {
    val actions = ChatPresenter(FakeChatWorld(), nav, Immediate)
    val headerActions = object : ChatHeaderActions {
        override fun openMenu() {}
        override fun closeMenu() {}
        override fun choose(action: HeaderAction) {}
        override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) {}
        override fun renameRequested() {}
        override fun renameDone(title: String?) {}
        override fun closeSheet() {}
        override fun continueWith(choice: ContinueChoice) {}
        override fun retryContinueWith() {}
        override fun confirm() {}
        override fun dismissConfirm() {}
        override fun dismissFailure() {}
        override fun clipDone(id: Long) {}
    }
    private val composerActions = object : BasicComposerActions {
        override fun draftChanged(text: String) {}
        override suspend fun send(text: String) = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
    }
    val host = object : ComposerHost {
        @Composable override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
            BasicComposer(BasicComposerUi(available = true, sending = false, steers = false, targetKey = "checkout"), composerActions, chrome, modifier)
        }
    }

    fun live(): ChatUi = ChatUi.loading(Ref).copy(
        title = "Fix flaky checkout test", phase = ChatPhase.Live,
        rows = persistentListOf(footer("msg_user"), block("msg_user", "msg_assistant", "part_intro", 0, REPLY), prompt("msg_user", "Fix the flaky checkout test")),
    )

    /** The screen as Flutter's golden has it: the chat at its window's root (no Back), in "shopfront". */
    @Composable
    fun Screen(state: ChatUi, canGoBack: Boolean = false, branch: String? = null) {
        ChatScreen(
            state, actions, host,
            header = ChatHeaderSlot(ChatHeaderUi(title = state.title, projectName = "shopfront", branch = branch), headerActions),
            facts = TurnFactsUi(reading = TranscriptReadingUi(metadata = mapOf("msg_user" to TurnMetadataUi(listOf("Sonnet 4"), 0, 0.0, true)))),
            canGoBack = canGoBack,
        )
    }

    companion object {
        const val REPLY: String =
            "The flakiness comes from `CheckoutBloc` racing the price refresh: `applyCoupon()` awaits the network " +
                "before the cart total has settled, so the assertion sometimes reads the stale total.\n\n" +
                "I made the test wait on the settled state instead of a fixed delay:"
    }
}
