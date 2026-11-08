package io.github.eslamasabry.oc.feature.chat.rb.o42

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Real native chat, using the scene words from Flutter chat_states_golden_test.dart and capture/fixtures.dart. The
 * chat is the window's root in those goldens, so the bar has no Back (Flutter `KitTopBarExit.auto`).
 */
class ChatGateTest {
    private val scene = ChatScene()
    private fun state() = scene.live()

    private fun shot(name: String, state: ChatUi, facts: TurnFactsUi? = null) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) {
            KitStandaloneRedaction { if (facts == null) scene.Screen(state) else scene.Screen(state, facts = facts) }
        }
    }

    private fun loadingState() = state().copy(phase = ChatPhase.Loading, rows = persistentListOf())
    private fun failedState() = state().copy(phase = ChatPhase.Failed(Failure.Unexpected()), rows = persistentListOf())
    private fun findState() = state().copy(find = FindUi("total", 2, 0, null, 1, false))

    // chat-1: the turn read three files and edited one (chat_states_golden_test.dart:262-297).
    @Test fun transcript() = shot("chat_transcript_turn", scene.liveWithWork(), scene.workFacts())
    @Test fun transcriptWide() = shot("chat_transcript_turn_1280x800", scene.liveWithWork(), scene.workFacts())
    // Flutter keeps the composer under Find (chat_composer_region.dart:279). The guard still rejects the composer
    // field's 24 dp target (:ui:kit-chat KitComposerBox, scoreboard F2), as it rejects the bar on the other shots;
    // the picture is rendered and measured all the same.
    @Test fun find() = shot("chat_find_open", findState())
    @Test fun loading() = shot("chat_loading", loadingState())
    @Test fun failed() = shot("chat_load_error", failedState())

    // E7 gate cases (scoreboard 2026-11-07): the rest of chat_states_golden_test.dart's screen states. Their guard
    // findings are kit parts' (the composer field's 24 dp target, F2; the reply footer's More 0.5 dp past the window
    // edge, E6; the bar's project line), as on the shots above; the pictures are measured all the same.
    @Test fun empty() = shot("chat_empty", state().copy(rows = persistentListOf()))
    @Test fun disconnected() = shot("chat_disconnected", state().copy(status = ChatStatusUi("offline", ChatStatusKind.Offline)))

    /**
     * `chat · model error in a reply` (chat_states_golden_test.dart:213-246): the reply failed at once with the
     * server's ProviderModelNotFoundError for openai/gpt-5.6, so the turn has its prompt and a failed footer.
     */
    @Test fun modelError() = shot("chat_model_error", modelErrorState(), modelErrorFacts())

    private fun modelErrorState() = state().copy(
        rows = persistentListOf(
            FooterRow(
                "footer:msg_user", "msg_user", failed = true,
                reason = "ProviderModelNotFoundError: Model not found: openai/gpt-5.6. Did you mean: gpt-5.6-pro?\n" +
                    "    at <anonymous> (/\$bunfs/root/chunk.js:439:1)",
                errorKind = MessageErrorKind.ModelNotFound,
            ),
            prompt("msg_user", "Fix the flaky checkout test"),
        ),
    )

    private fun modelErrorFacts() =
        TurnFactsUi(reading = TranscriptReadingUi(metadata = mapOf("msg_user" to TurnMetadataUi(listOf("GPT-5.6"), 0, 0.0, true))))
}
