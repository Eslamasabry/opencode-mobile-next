package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestLook
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.tool.kitWorkIcon
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyFooter
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyMeta
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyNote
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTurn
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTurnNext
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTurnSegment
import io.github.eslamasabry.oc.ui.kitchat.transcript.LocalKitTurn
import io.github.eslamasabry.oc.ui.kitchat.turn.KitLiveSweep
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.test.Test

/**
 * test/goldens/kit/kit_turn_golden_test.dart: every KitTurn phase at 412x915 as a real composition (a prompt, the
 * work line, a reply, a request card or an error notice where the scene has one), followed by the next turn's prompt
 * so the turn's own trailing gap shows (`_scene`, 16 dp gutter); the default (finished, latest) in the 700 dp pane
 * (`_pane`) at 1280x800 and at 2.0 text on both sizes. Frame: `kitGalleryPart` ([FlutterGoldenGate.Frame.Part]).
 *
 * Native KitTurn is one row of a turn; a Flutter turn is drawn as its rows ([Turn]): the prompt row ([KitTurnSegment.
 * First]), one row per block, the phase/live line and the footer, each row telling the next one's kind so the gaps are
 * Flutter's (16 under the prompt, 12 between blocks and before the phase line, 4 above the footer, 24 under a turn,
 * 8 under the latest). The transcript parts carry the 16 dp gutter; the work line, markdown, the card, the notice and
 * the phase lines get it from the scene ([Gutter]).
 *
 * Mismatch: no native KitTurn phase model (starting/running/waitingForYou/finished/stopped/interrupted/failed) —
 *   the phase line, footer and live line are composed by hand per Flutter's `_TurnFrame.build` rules.
 * Mismatch: starting — Flutter's KitSince line ("Starting the model…", "Still waiting for the model · 9 s" after
 *   the fake clock's 9 s) has no native counterpart; the words are typed as a KitText (secondary).
 * Mismatch: running_live — Flutter's `_KitTurnLiveLine` (accent live dot, "Writing · 6 s", the sweep) has no native
 *   part; stands in as KitLiveSweep over a KitText in the accent tone, with no dot.
 * Mismatch: highlighted — the find band (surface1, panel radius, 8 dp outset) has no native part; not drawn.
 * Mismatch: waiting_for_you — Flutter's KitRequestCard.ask (who "fox", server "shopfront", ifIgnored line,
 *   decision reason) stands in as KitPermissionCard in the Question look; the 1 min wait is the typed "1 min".
 * Mismatch: the prompt's turn menu (Edit and resend; footer Fork from here, Revert) is not drawn by either side but
 *   has no native long-press menu.
 */
class KitTurnGateTest {
    private val promptWords = "Fix the flaky checkout test. It fails about one run in five on CI."

    private val replyWords =
        "The flakiness comes from `CheckoutBloc` racing the price refresh: " +
            "`applyCoupon()` awaits the network before the total settles, so the " +
            "assertion sometimes reads the old total. I made the test wait for the " +
            "settled state."

    private val firstWords = "I will read the checkout test and the bloc it drives."

    private val meta = "Sonnet 4.5 · 12k tokens · 10:42"

    /** KitWorkLine.summaryOf(KitWorkCounts(read: 3, edited: 1)). */
    private val summary = "Read 3 files · edited 1 file"

    private val actions = KitMarkdownActions(onLink = {})

    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        FlutterCapture { KitChatEnvironment(Redactor()).Provide { content() } }
    }

    /** One part of a turn and what kind of row it is (the row before it takes its gap from this). */
    private class Part(val kind: KitTurnNext, val content: @Composable () -> Unit)

    /** A Flutter KitTurn as native rows: first, middles, last (or one whole row). */
    @Composable private fun Turn(latest: Boolean, parts: List<Part>) {
        parts.forEachIndexed { i, part ->
            val segment = when {
                parts.size == 1 -> KitTurnSegment.Whole
                i == 0 -> KitTurnSegment.First
                i == parts.lastIndex -> KitTurnSegment.Last
                else -> KitTurnSegment.Middle
            }
            val next = parts.getOrNull(i + 1)?.kind ?: KitTurnNext.Block
            KitTurn(segment, latest = latest, next = next) { part.content() }
        }
    }

    @Composable private fun Gutter(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() } // F: test/goldens/kit/kit_turn_golden_test.dart:123
    }

    /** `_scene`: the turn, then the next turn's prompt (a bare KitMessage.prompt, no row padding). */
    @Composable private fun Scene(turn: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth()) {
            turn()
            CompositionLocalProvider(LocalKitTurn provides true) { KitPromptBubble("And run it ten times.") }
        }
    }

    /** `_pane`: top centre, at most KitLayout.paneDetailMaxWidth (700 dp). */
    @Composable private fun Pane(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 700.dp)) { content() } // F: test/goldens/kit/kit_turn_golden_test.dart:140
        }
    }

    private val prompt = Part(KitTurnNext.Block) { KitPromptBubble(promptWords) }
    private val reply = Part(KitTurnNext.Block) { Gutter { KitMarkdown(KitMdSource.blocks("reply", replyWords, false), actions) } }
    private val first = Part(KitTurnNext.Block) { Gutter { KitMarkdown(KitMdSource.blocks("first", firstWords, false), actions) } }

    private fun line(state: KitWorkState, stateWords: String?, now: String? = null) = Part(KitTurnNext.Block) {
        Gutter {
            KitWorkLine(
                label = summary,
                stateWords = stateWords,
                state = state,
                expanded = state.opensByDefault,
                onToggle = {},
                icon = kitWorkIcon(read = 3, edited = 1),
                now = now,
            ) {
                KitToolRow(
                    KitToolView("read", KitToolKind.Read, "Read", "test/checkout_test.dart", null, KitToolStatus.Done, null, null, null, ""),
                    opened = false,
                    onToggle = {},
                )
                KitToolRow(
                    KitToolView("edit", KitToolKind.Edit, "Edited", "test/checkout_test.dart", null, KitToolStatus.Done, null, 6, 2, ""),
                    opened = false,
                    onToggle = {},
                )
            }
        }
    }

    private fun phase(words: String) = Part(KitTurnNext.Line) {
        Gutter { KitText(words, role = TypeRole.Secondary, tone = TextTone.Secondary) }
    }

    /** `_footer`: Copy and More; the meta only on the latest turn; a stopped turn's note leads it. */
    private fun footer(latest: Boolean, note: String? = null) = Part(KitTurnNext.Footer) {
        KitReplyFooter(
            copyLabel = "Copy reply",
            moreLabel = "More for this reply",
            onCopy = {},
            onMore = {},
        ) {
            if (note != null) KitReplyNote(note)
            if (latest) KitReplyMeta(meta)
        }
    }

    private val permission = Part(KitTurnNext.Block) {
        Gutter {
            KitPermissionCard(
                title = "Run this command?",
                summary = "To check the fix",
                subject = null,
                targets = listOf(KitRequestTarget("flutter test test/checkout_test.dart")),
                allowOnceLabel = "Run once",
                alwaysLabel = null,
                denyLabel = "Don't run",
                onAllowOnce = {},
                onAlways = null,
                onDeny = {},
                look = KitRequestLook.Question,
                waitingAge = "1 min",
                onDetails = {},
            )
        }
    }

    @Composable private fun Default() {
        Pane {
            Scene { Turn(latest = true, parts = listOf(prompt, line(KitWorkState.Done, null), reply, footer(latest = true))) }
        }
    }

    @Composable private fun Starting() = Scene { Turn(latest = false, parts = listOf(prompt, phase("Starting the model…"))) }

    @Composable private fun StartingSlow() = Scene {
        Turn(latest = false, parts = listOf(prompt, phase("Still waiting for the model · 9 s")))
    }

    @Composable private fun Running() = Scene {
        Turn(latest = true, parts = listOf(prompt, first, line(KitWorkState.Running, "Working", now = "Editing test/checkout_test.dart")))
    }

    @Composable private fun RunningLive() = Scene {
        val live = Part(KitTurnNext.Line) {
            Gutter {
                KitLiveSweep(working = true) {
                    KitText("Writing · 6 s", role = TypeRole.Secondary, tone = TextTone.Accent)
                }
            }
        }
        Turn(latest = true, parts = listOf(prompt, first, line(KitWorkState.Running, "Working", now = "Editing test/checkout_test.dart"), live))
    }

    @Composable private fun WaitingForYou() = Scene {
        Turn(latest = true, parts = listOf(prompt, first, line(KitWorkState.WaitingForYou, "Waiting for you"), permission))
    }

    @Composable private fun Finished() = Scene {
        Turn(latest = false, parts = listOf(prompt, line(KitWorkState.Done, null), reply, footer(latest = false)))
    }

    @Composable private fun FinishedLatest() = Default()

    @Composable private fun Stopped() = Scene {
        Turn(latest = true, parts = listOf(prompt, first, line(KitWorkState.Stopped, "Stopped"), footer(latest = true, note = "You stopped this reply.")))
    }

    @Composable private fun Interrupted() = Scene {
        Turn(
            latest = true,
            parts = listOf(
                prompt,
                first,
                line(KitWorkState.EndedFailed, "Didn't finish"),
                phase("The connection dropped before this reply finished."),
                footer(latest = true),
            ),
        )
    }

    @Composable private fun Failed() = Scene {
        val notice = Part(KitTurnNext.Block) {
            Gutter { KitErrorNotice("The model provider stopped answering.", retry = KitLineAction("Try again", {})) }
        }
        Turn(latest = true, parts = listOf(prompt, first, line(KitWorkState.EndedFailed, "Didn't finish"), notice, footer(latest = true)))
    }

    @Composable private fun Highlighted() = Scene {
        Turn(latest = false, parts = listOf(prompt, line(KitWorkState.Done, null), reply, footer(latest = false)))
    }

    @Test fun kit_turn_default() {
        FlutterGoldenGate.check("kit_turn_default_1280x800", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_turn_default_text2", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_turn_default_text2_1280x800", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_turn_finished_latest", wrap = wrap) { FinishedLatest() }
    }

    @Test fun kit_turn_working() {
        FlutterGoldenGate.check("kit_turn_starting", wrap = wrap) { Starting() }
        FlutterGoldenGate.check("kit_turn_starting_slow", wrap = wrap) { StartingSlow() }
        FlutterGoldenGate.check("kit_turn_running", wrap = wrap) { Running() }
        FlutterGoldenGate.check("kit_turn_running_live", wrap = wrap) { RunningLive() }
        FlutterGoldenGate.check("kit_turn_waiting_for_you", wrap = wrap) { WaitingForYou() }
    }

    @Test fun kit_turn_ended() {
        FlutterGoldenGate.check("kit_turn_finished", wrap = wrap) { Finished() }
        FlutterGoldenGate.check("kit_turn_stopped", wrap = wrap) { Stopped() }
        FlutterGoldenGate.check("kit_turn_interrupted", wrap = wrap) { Interrupted() }
        FlutterGoldenGate.check("kit_turn_failed", wrap = wrap) { Failed() }
        FlutterGoldenGate.check("kit_turn_highlighted", wrap = wrap) { Highlighted() }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_turn", caps = mapOf("KitTurn" to "conversation"), wrap = wrap) { Default() }
    }
}
