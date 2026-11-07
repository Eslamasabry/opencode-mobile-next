package io.github.eslamasabry.oc.feature.chat.rb.o42

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Real native chat, using the scene words from Flutter chat_states_golden_test.dart and capture/fixtures.dart. The
 * chat is the window's root in those goldens, so the bar has no Back (Flutter `KitTopBarExit.auto`).
 */
class ChatGateTest {
    private val scene = ChatScene()
    private fun state() = scene.live()

    private fun shot(name: String, state: ChatUi) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen) {
            KitStandaloneRedaction { scene.Screen(state) }
        }
    }

    private fun loadingState() = state().copy(phase = ChatPhase.Loading, rows = persistentListOf())
    private fun failedState() = state().copy(phase = ChatPhase.Failed(Failure.Unexpected()), rows = persistentListOf())
    private fun findState() = state().copy(find = FindUi("total", 2, 0, null, 1, false))

    @Test fun transcript() = shot("chat_transcript_turn", state())
    @Test fun transcriptWide() = shot("chat_transcript_turn_1280x800", state())
    // Flutter keeps the composer under Find (chat_composer_region.dart:279); its field is a 24 dp target in
    // :ui:kit-chat KitComposerBox (scoreboard F2), which the guards reject on this shot. Runs once that lands.
    @Ignore @Test fun find() = shot("chat_find_open", findState())
    @Test fun loading() = shot("chat_loading", loadingState())
    @Test fun failed() = shot("chat_load_error", failedState())

    // E7 gate cases (scoreboard 2026-11-07): the rest of chat_states_golden_test.dart's screen states.
    // Both wait for kit parts only: the composer field's 24 dp target (F2, :ui:kit-chat), the reply footer's More
    // 0.5 dp past the window edge (E6, :ui:kit-chat KitReplyFooter) and the bar's 1 dp project-line overlap (:ui:kit).
    @Ignore @Test fun empty() = shot("chat_empty", state().copy(rows = persistentListOf()))
    @Ignore @Test fun disconnected() = shot("chat_disconnected", state().copy(status = ChatStatusUi("offline", ChatStatusKind.Offline)))
}
