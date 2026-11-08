package io.github.eslamasabry.oc.feature.chat.rb.o42

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.footer
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * The rest of Flutter's chat_states_golden_test.dart on the real native chat, with the scene of [ChatScene]. Those
 * states use the plain turn of `_turn()` (chat_states_golden_test.dart:52-81): the prompt and the two-paragraph
 * intro, no tools, so there is no work line or changed file.
 */
class ChatStatesGateTest {
    private val scene = ChatScene()

    private fun shot(name: String, state: ChatUi, facts: TurnFactsUi = plainFacts) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) {
            KitStandaloneRedaction { scene.Screen(state, facts = facts) }
        }
    }

    /** `_turn()`: newest first, the footer, the intro's two paragraphs, the prompt. */
    private fun plainTurn(): ChatUi = scene.live().copy(
        rows = persistentListOf(
            footer(TURN),
            block(TURN, ASSISTANT, "part_intro", SPLIT, ChatScene.REPLY.substring(SPLIT)),
            block(TURN, ASSISTANT, "part_intro", 0, ChatScene.REPLY.substring(0, SPLIT).trimEnd()),
            prompt(TURN, "Fix the flaky checkout test"),
        ),
    )

    private val plainFacts = TurnFactsUi(
        reading = TranscriptReadingUi(
            metadata = mapOf(TURN to TurnMetadataUi(listOf("anthropic/claude-sonnet-4"), 0, 0.0, true, "Sonnet 4")),
        ),
    )

    /**
     * chat_states_golden_test.dart:203-224: "Run the full test suite" was sent, the server answered 503 and the send
     * is not confirmed yet ("Not confirmed yet").
     */
    @Test fun sendError() = shot(
        "chat_send_error",
        plainTurn().copy(
            pending = persistentListOf(PendingSend(1L, "Run the full test suite", PendingState.Unconfirmed)),
        ),
    )

    /**
     * chat_states_golden_test.dart:226-260: the reply failed at once with "Model not found: openai/gpt-5.6", no
     * words; the turn offers the way on (the suggested model, or choose another).
     */
    @Test fun modelError() = shot(
        "chat_model_error",
        scene.live().copy(
            rows = persistentListOf(
                FooterRow(
                    "footer:$TURN", TURN, failed = true,
                    reason = "ProviderModelNotFoundError: Model not found: openai/gpt-5.6. Did you mean: gpt-5.6-pro?",
                    errorKind = MessageErrorKind.ModelNotFound,
                ),
                prompt(TURN, "Fix the flaky checkout test"),
            ),
        ),
        facts = TurnFactsUi(),
    )

    private companion object {
        const val TURN = "msg_user"
        const val ASSISTANT = "msg_assistant"
        val SPLIT = ChatScene.REPLY.indexOf("I made")
    }
}
