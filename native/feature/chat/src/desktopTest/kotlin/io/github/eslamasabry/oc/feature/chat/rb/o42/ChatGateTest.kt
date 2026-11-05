package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.*
import io.github.eslamasabry.oc.feature.chat.composer.*
import io.github.eslamasabry.oc.feature.chat.composer.presentation.*
import io.github.eslamasabry.oc.feature.chat.header.presentation.*
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.*
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Real native chat, using the scene words from Flutter chat_states_golden_test.dart and capture/fixtures.dart. */
class ChatGateTest {
    private val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
    private val headerActions = object : ChatHeaderActions {
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
    private val host = object : ComposerHost {
        @Composable override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
            BasicComposer(BasicComposerUi(available = true, sending = false, steers = false, targetKey = "checkout"), composerActions, chrome, modifier)
        }
    }
    private val text = "The flakiness comes from `CheckoutBloc` racing the price refresh: `applyCoupon()` awaits the network before the cart total has settled, so the assertion sometimes reads the stale total.\n\nI made the test wait on the settled state instead of a fixed delay:"
    private fun state() = ChatUi.loading(Ref).copy(
        title = "Fix flaky checkout test", phase = ChatPhase.Live,
        rows = persistentListOf(footer("msg_user"), block("msg_user", "msg_assistant", "part_intro", 0, text), prompt("msg_user", "Fix the flaky checkout test")),
    )
    private fun shot(name: String, state: ChatUi) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) {
            KitStandaloneRedaction {
                ChatScreen(state, actions, host,
                    header = ChatHeaderSlot(ChatHeaderUi(title = state.title, projectName = "shopfront"), headerActions),
                    facts = TurnFactsUi(reading = TranscriptReadingUi(metadata = mapOf("msg_user" to TurnMetadataUi(listOf("Sonnet 4"), 0, 0.0, true)))),
                )
            }
        }
    }
    @Test fun transcript() = shot("chat_transcript_turn", state())
    @Test fun transcriptWide() = shot("chat_transcript_turn_1280x800", state())
    @Test fun find() = shot("chat_find_open", state().copy(find = FindUi("total", 2, 0, null, 1, false)))
    @Test fun loading() = shot("chat_loading", state().copy(phase = ChatPhase.Loading, rows = persistentListOf()))
    @Test fun failed() = shot("chat_load_error", state().copy(phase = ChatPhase.Failed(Failure.Unexpected()), rows = persistentListOf()))
}
