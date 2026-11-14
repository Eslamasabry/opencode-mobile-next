package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveTone
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveFor
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveMinutes
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveNow
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveSeconds
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveThinking
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWorking
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWriting
import org.jetbrains.compose.resources.stringResource

/** What the running turn's live line says: the line, what a screen reader hears, its tone and the muted step line. */
internal data class LiveLineWords(
    val words: String,
    val spoken: String,
    val tone: KitTurnLiveTone,
    val step: String? = null,
)

/** Under this the line says only what the turn is doing (Dart `KitTurnLive.showElapsedAfter`). */
internal const val LIVE_SHOW_ELAPSED_AFTER_MS: Long = 5_000

/**
 * The running turn's live line in words (Flutter `_KitTurnLiveLine`, kit_turn.dart:886-1006, fed by
 * chat_transcript.dart:290-375): what the turn is doing — the step in hand in plain words ("Running npm test"),
 * "Writing" while reply words stream, else "Thinking" — and, after five seconds, for how long ("Running npm test · 12
 * s"). While a request card waits for the person there is no live line (null): the card says it; a stop on its way,
 * a retry, a stall or a stuck command replace the words. The muted step line is the newest line a running
 * command printed.
 */
@Composable
internal fun liveLineWords(
    live: LiveTurnUi,
    listWords: ChatListWords,
    notice: LiveNoticeUi?,
    work: TurnWork?,
    writing: Boolean,
    elapsedMs: Long?,
): LiveLineWords? {
    val mask = rememberKitMask()
    // A stop or a note about the turn replaces what it is doing, without the time (F: kit_turn.dart:920-937).
    val held =
        when {
            live.stopping -> LiveLineWords(listWords.stopping, listWords.stopping, KitTurnLiveTone.Quiet)
            live.stoppingAfterStep ->
                LiveLineWords(listWords.stoppingAfterStep, listWords.stoppingAfterStep, KitTurnLiveTone.Working)
            notice != null -> {
                val words = liveNoticeWords(notice)
                val tone =
                    when (notice) {
                        is LiveNoticeUi.StuckCommand,
                        is LiveNoticeUi.ToolWait -> KitTurnLiveTone.WaitingForYou
                        is LiveNoticeUi.Retry,
                        is LiveNoticeUi.Stall -> KitTurnLiveTone.Quiet
                        else -> KitTurnLiveTone.Working
                    }
                LiveLineWords(words, words, tone, step = retryWhyWords(notice))
            }
            else -> null
        }
    if (held != null) return held
    // Review #12: a request card waiting on the person says so itself; the live line adds no second "Waiting for
    // you" (RunTailRules: the live line is drawn only while the run works or stops).
    if (work?.state == WorkLineState.WaitingForYou) return null
    val now = work?.now
    val words =
        when {
            // D1 "steps only" (2026-11-14): the step in hand is in view on the turn's step list with its light and its
            // time, so the live line says that the agent works and for how long, never the same step a second time.
            now != null && work != null -> stringResource(Res.string.kitTurnLiveWorking)
            writing -> stringResource(Res.string.kitTurnLiveWriting)
            else -> stringResource(Res.string.kitTurnLiveThinking)
        }
    val elapsed = elapsedMs?.coerceAtLeast(0)
    val line =
        if (elapsed == null || elapsed < LIVE_SHOW_ELAPSED_AFTER_MS) stringResource(Res.string.kitTurnLiveNow, words)
        else {
            val seconds = (elapsed / 1_000).toInt()
            val time =
                if (seconds < 60) stringResource(Res.string.kitTurnLiveSeconds, seconds)
                else stringResource(Res.string.kitTurnLiveMinutes, seconds / 60, seconds % 60)
            // "12 s" never breaks between the number and its unit (F: kit_turn.dart:913-918).
            stringResource(Res.string.kitTurnLiveFor, words, time.replace(' ', ' '))
        }
    // F: chat_transcript.dart:310-323 the newest line a running command printed rides in the muted second slot.
    val printed =
        now?.takeIf { it.facts.kind == ToolKind.Shell && it.state == StepState.Running }
            ?.output?.text
            ?.lineSequence()
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotEmpty() }
            ?.let(mask)
    return LiveLineWords(line, words, KitTurnLiveTone.Working, printed)
}
