package io.github.eslamasabry.oc.feature.chatcomposer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ApprovalMode
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerChip
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerNote
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerUi
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EffortWord
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelLabel
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ReadOnlyReason
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Audit capture only: the composer screen in the states its model allows. Nothing is asserted. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val sonnet = ComposerChip.Model(ModelLabel.Named("Sonnet 5"), isNew = true, effort = EffortWord.Known.High)
    private val ask = ComposerChip.Approval(ApprovalMode.AsksFirst)

    private fun shot(state: String, ui: ComposerUi, text: String = "", setup: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {}) {
        Audit.shoot("chat-composer", "composer", state, setup = setup) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Field(ui, text)
            }
        }
    }

    @Composable
    private fun Field(ui: ComposerUi, text: String) {
        val field = rememberTextFieldState(text)
        ComposerScreen(ui, RecordingActions(), field)
    }

    @Test
    fun idleStates() = Audit.guard("chat-composer__composer__idle__build") {
        shot("idle-empty-mic", ComposerUi(button = FieldButton.Mic, chips = persistentListOf(sonnet, ask)))
        shot("idle-text-send", ComposerUi(button = FieldButton.Send, chips = persistentListOf(sonnet, ask)), "Fix the failing login test and run it again")
        shot(
            "idle-long-text",
            ComposerUi(button = FieldButton.Send, chips = persistentListOf(sonnet, ask)),
            "Please read the whole checkout flow, then explain why the price refresh races the coupon step, " +
                "propose two different fixes with their trade-offs, and only after that change any file. " +
                "Keep the diff small and add a regression test that fails without the fix.\n\nAlso: do not touch the CI config.",
        )
        shot(
            "idle-long-text-editor",
            ComposerUi(button = FieldButton.Send, chips = persistentListOf(sonnet, ask)),
            "Line one\nLine two\nLine three\nLine four\nLine five\nLine six\nLine seven\nLine eight",
            setup = {
                onNodeWithTag("composer-open-editor").performClick()
                waitForIdle()
                mainClock.advanceTimeBy(800)
            },
        )
        shot("auto-approve", ComposerUi(button = FieldButton.Mic, chips = persistentListOf(sonnet, ComposerChip.Approval(ApprovalMode.AutoApprove))))
        shot("auto-approve-paused", ComposerUi(button = FieldButton.Send, chips = persistentListOf(sonnet, ComposerChip.Approval(ApprovalMode.Paused))), "Continue")
    }

    @Test
    fun modelChipStates() = Audit.guard("chat-composer__composer__model__build") {
        shot("model-loading", ComposerUi(button = FieldButton.Mic, chips = persistentListOf(ComposerChip.Model(ModelLabel.Loading, false, null), ask)))
        shot("model-none-signed-in", ComposerUi(button = FieldButton.CannotSendYet, chips = persistentListOf(ComposerChip.Model(ModelLabel.NoModelSignedIn, false, null), ask)), "Hello")
        shot(
            "model-long-name-effort-other",
            ComposerUi(
                button = FieldButton.Send,
                chips = persistentListOf(
                    ComposerChip.Model(ModelLabel.Named("Anthropic Claude Sonnet 5 Extended Context Preview"), false, EffortWord.Other("ultra-deep-thinking")),
                    ask,
                ),
            ),
            "Hello",
        )
        shot(
            "note-next-turns-model",
            ComposerUi(button = FieldButton.Mic, chips = persistentListOf(sonnet, ask), note = ComposerNote.NextTurnsModel(ModelLabel.Named("GPT-6 Sol"))),
        )
    }

    @Test
    fun runningStates() = Audit.guard("chat-composer__composer__running__build") {
        shot(
            "running-steer",
            ComposerUi(button = FieldButton.AddToTurn, chips = persistentListOf(sonnet, ask, ComposerChip.Stop(stuck = false)), glow = true),
            "Also check the docs",
        )
        shot(
            "running-stuck-stop-and-send",
            ComposerUi(button = FieldButton.StopAndSend, chips = persistentListOf(sonnet, ComposerChip.Approval(ApprovalMode.Paused), ComposerChip.Stop(stuck = true))),
            "Start over",
        )
        shot("running-empty", ComposerUi(button = FieldButton.None, chips = persistentListOf(sonnet, ask, ComposerChip.Stop(stuck = false)), glow = true))
        shot("sending", ComposerUi(button = FieldButton.Sending, chips = persistentListOf(sonnet, ask)), "Ship it")
    }

    @Test
    fun problemStates() = Audit.guard("chat-composer__composer__problem__build") {
        shot(
            "offline-send-when-online",
            ComposerUi(button = FieldButton.SendWhenOnline, chips = persistentListOf(sonnet, ask)),
            "Ship it when I am back online",
        )
        shot(
            "send-failed",
            ComposerUi(button = FieldButton.Send, chips = persistentListOf(sonnet, ask), note = ComposerNote.SendFailed),
            "Ship it",
        )
        shot(
            "read-only-sub-agent",
            ComposerUi(button = FieldButton.None, note = ComposerNote.ReadOnly(ReadOnlyReason.SubAgent), fieldEnabled = false),
        )
    }
}
