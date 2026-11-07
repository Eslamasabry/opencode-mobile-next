package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_tool_row_golden_test.dart: a line of the reply, the steps, the reply's next
 * line (kitGalleryPart, so [FlutterGoldenGate.Frame.Part]); each state at 412x915, the default at 1280x800 and at
 * text 2.0 (412x915 and 1280x800). The default at 412x915 and 1.0 text has no golden in 14.gate.tsv.
 *
 * The Dart words come from the scene or from its English strings: durations are `kitToolTookSeconds` ("2 seconds"),
 * status words `kitToolRunning` … (the native row is given them as [KitToolView.statusLabel]).
 *
 * Mismatch: the Dart scene pads the whole column 16 dp at the sides and its rows carry no gutter; the native
 * [KitToolRow] carries the 16 dp page gutter itself (ConversationColumn), so only the two reply lines take the scene's
 * 16 dp here and the rows sit at the same x as in Dart.
 */
class KitToolRowGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) { it() }
    }

    private class Step(val view: KitToolView, val opened: Boolean = false, val body: (@Composable () -> Unit)? = null, val onOpen: (() -> Unit)? = null)

    private fun view(
        kind: KitToolKind,
        title: String,
        status: KitToolStatus,
        statusLabel: String,
        subject: String? = null,
        detail: String? = null,
        duration: String? = null,
        added: Int? = null,
        removed: Int? = null,
    ) = KitToolView(title, kind, title, subject, detail, status, duration, added, removed, statusLabel)

    /** Dart `_scene`: a reply's prose, the steps under it, and the reply's next line. */
    private fun scene(steps: List<Step>): @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            KitText(
                "I will look at how checkout settles, then fix the flaky test.",
                Modifier.padding(horizontal = 16.dp), // F: test/goldens/kit/kit_tool_row_golden_test.dart:24
            )
            Spacer(Modifier.height(8.dp)) // F: test/goldens/kit/kit_tool_row_golden_test.dart:32
            for (step in steps) KitToolRow(step.view, opened = step.opened, onToggle = {}, body = step.body, onOpen = step.onOpen)
            Spacer(Modifier.height(8.dp)) // F: test/goldens/kit/kit_tool_row_golden_test.dart:34
            KitText(
                "The test passes now; the change is one line.",
                Modifier.padding(horizontal = 16.dp), // F: test/goldens/kit/kit_tool_row_golden_test.dart:24
            )
        }
    }

    // F: test/goldens/kit/kit_tool_row_golden_test.dart:40
    private val read =
        Step(view(KitToolKind.Read, "Read", KitToolStatus.Done, "Done", subject = "test/checkout/checkout_test.dart", duration = "2 seconds"))

    // F: test/goldens/kit/kit_tool_row_golden_test.dart:48
    private val output =
        (1..30).joinToString("\n") { i -> if (i == 30) "All tests passed!" else "00:0${i / 10} +$i: checkout step $i" }

    private val shellOpen =
        Step(
            view(
                KitToolKind.Shell,
                "Ran flutter test",
                KitToolStatus.Done,
                "Done",
                detail = "Exit code 0 · passed",
                duration = "42 seconds",
            ),
            opened = true,
            body = {
                KitMarkdown(
                    KitMdSource.blocks("note", "Running the checkout suite to confirm the fix.", streaming = false),
                    KitMarkdownActions(onLink = {}),
                )
                KitCodeBlock(output, KitCodeKind.Output, moreLabel = { "Show all 30 lines" }, onMore = {})
            },
        )

    private val editOpen =
        Step(
            view(
                KitToolKind.Edit,
                "Edited",
                KitToolStatus.Done,
                "Done",
                subject = "test/checkout/checkout_test.dart",
                duration = "1 second",
                added = 4,
                removed = 1,
            ),
            opened = true,
            body = {
                KitDiffView(
                    DiffModel.fromBeforeAfter(
                        "test/checkout/checkout_test.dart",
                        listOf(
                            "await tester.pump();",
                            "expect(find.text(r'\$42.00'), findsOneWidget);",
                        ).joinToString("\n"),
                        listOf(
                            "await tester.pumpUntil(",
                            "  () => bloc.state is CheckoutSettled,",
                            "  timeout: const Duration(seconds: 5),",
                            ");",
                            "expect(find.text(r'\$42.00'), findsOneWidget);",
                        ).joinToString("\n"),
                    ),
                    hunkLabel = { _, _, start, end -> "Lines $start–$end" },
                    gapLabel = { hidden -> if (hidden == 1) "… 1 line" else "… $hidden lines" },
                )
            },
        )

    private val failed =
        Step(
            view(
                KitToolKind.Shell,
                "Ran flutter test",
                KitToolStatus.Failed,
                "Failed",
                detail = "Exit code 1 · 2 tests failed",
                duration = "38 seconds",
            ),
        )

    private val agentTask = "Find where checkout totals are computed and what awaits them"

    private fun shot(state: String, steps: List<Step>) {
        FlutterGoldenGate.check("kit_tool_row_$state", wrap = wrap, content = scene(steps))
    }

    @Test fun running_and_waiting() {
        // F: test/goldens/kit/kit_tool_row_golden_test.dart:56
        shot("running", listOf(read, Step(view(KitToolKind.Shell, "Running flutter test", KitToolStatus.Running, "Running", detail = "test/checkout_test.dart"))))
        // F: test/goldens/kit/kit_tool_row_golden_test.dart:65
        shot("waiting_for_you", listOf(read, Step(view(KitToolKind.Shell, "Run flutter test", KitToolStatus.WaitingForYou, "Waiting for you", detail = "Needs your permission"))))
    }

    /**
     * Mismatch (done_open): the Dart row draws its `note` (a secondary-role KitMarkdown) above the body itself; native
     * has no note slot and KitMarkdown has no secondary role, so the note is the body's first part in the body tone.
     */
    @Test fun done_states() {
        // F: test/goldens/kit/kit_tool_row_golden_test.dart:74
        shot("done", listOf(read, Step(view(KitToolKind.Search, "Searched for applyCoupon", KitToolStatus.Done, "Done", detail = "3 matches", duration = "1 second"))))
        shot("done_open", listOf(read, shellOpen))
        shot("edit_open", listOf(editOpen))
    }

    /**
     * Mismatch (failed_retry): native [KitToolRow] has no retry action (Dart `onRetry` adds "Try again" under a failed
     * step); the shot draws the failed row alone.
     *
     * Mismatch (not_run): native [KitToolStatus] has no NotRun; the nearest is Pending (an empty mark and the
     * caller's words "Not run").
     */
    @Test fun failed_and_not_run() {
        shot("failed", listOf(read, failed))
        shot("failed_retry", listOf(read, failed))
        // F: test/goldens/kit/kit_tool_row_golden_test.dart:154
        shot("not_run", listOf(read, Step(view(KitToolKind.Edit, "Edit", KitToolStatus.Pending, "Not run", subject = "lib/checkout/checkout_bloc.dart"))))
    }

    /**
     * Mismatch: native has no `KitToolRow.agent` line ("Delegated to explore · Running for 3 minutes" over the task,
     * opening the agent's conversation, its elapsed time from `startedAt`). The nearest is a SubAgent step with the
     * task as its detail, the elapsed words as its duration and [KitToolRow]'s onOpen.
     */
    @Test fun agent() {
        // F: test/goldens/kit/kit_tool_row_golden_test.dart:162
        shot("agent", listOf(Step(view(KitToolKind.SubAgent, "Delegated to explore", KitToolStatus.Running, "Running", detail = agentTask, duration = "for 3 minutes"), onOpen = {})))
        // F: test/goldens/kit/kit_tool_row_golden_test.dart:171
        shot("agent_done", listOf(Step(view(KitToolKind.SubAgent, "Delegated to explore", KitToolStatus.Done, "Done", detail = agentTask), onOpen = {})))
    }

    // F: test/goldens/kit/kit_tool_row_golden_test.dart:180
    private val default =
        listOf(read, Step(view(KitToolKind.List, "Listed", KitToolStatus.Done, "Done", subject = "lib/checkout/")))

    @Test fun default_sizes() {
        for (suffix in listOf("_1280x800", "_text2", "_text2_1280x800")) {
            FlutterGoldenGate.check("kit_tool_row_default$suffix", wrap = wrap, content = scene(default))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_tool_row", caps = mapOf("KitToolRow" to "conversation"), wrap = wrap, content = scene(default))
    }
}
