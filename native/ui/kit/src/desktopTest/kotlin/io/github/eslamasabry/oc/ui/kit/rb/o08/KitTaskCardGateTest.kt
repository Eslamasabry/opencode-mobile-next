package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.team.KitPriority
import io.github.eslamasabry.oc.ui.kit.team.KitTaskCard
import io.github.eslamasabry.oc.ui.kit.team.KitTaskFlag
import io.github.eslamasabry.oc.ui.kit.team.KitTaskFlagKind
import io.github.eslamasabry.oc.ui.kit.team.KitTaskMeta
import io.github.eslamasabry.oc.ui.kit.team.KitTaskState
import kotlin.test.Test

/** The scenes of test/goldens/kit/kit_task_card_golden_test.dart: one card per scene in a 320 dp lane, 16 dp from the start. */
class KitTaskCardGateTest {
    private val meta = listOf(
        KitTaskMeta("High", priority = KitPriority.High, strong = true),
        KitTaskMeta("Bug", icon = KitIcons.bug),
        KitTaskMeta("fox"),
        KitTaskMeta("12 min ago"),
    )

    private fun move(reason: String? = null) =
        KitAction("Move or change", {}, enabled = reason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled, icon = KitIcons.swap)

    /** `_lane`: 16 dp side padding, top-start, 320 dp wide. */
    private fun lane(card: @Composable () -> Unit): @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopStart) {
            Box(Modifier.width(320.dp)) { card() }
        }
    }

    private val default = lane {
        KitTaskCard("Fix the sync engine dropping queued messages", KitTaskState.Working, {}, meta = meta, action = move())
    }

    @Test fun kit_task_card_default() { FlutterGoldenGate.check("kit_task_card_default", wrap = { FlutterCapture(it) }, content = default) }
    @Test fun kit_task_card_default_1280x800() { FlutterGoldenGate.check("kit_task_card_default_1280x800", wrap = { FlutterCapture(it) }, content = default) }
    @Test fun kit_task_card_default_text2() { FlutterGoldenGate.check("kit_task_card_default_text2", wrap = { FlutterCapture(it) }, content = default) }
    @Test fun kit_task_card_default_text2_1280x800() { FlutterGoldenGate.check("kit_task_card_default_text2_1280x800", wrap = { FlutterCapture(it) }, content = default) }

    @Test fun kit_task_card_needs_you() {
        FlutterGoldenGate.check("kit_task_card_needs_you", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Merge the onboarding branch", KitTaskState.NeedsYou, {},
                meta = listOf(KitTaskMeta("fox"), KitTaskMeta("3 min ago")),
                flag = KitTaskFlag(KitTaskFlagKind.NeedsYou, "Approve the merge"),
                action = move(),
            )
        })
    }

    @Test fun kit_task_card_blocked() {
        FlutterGoldenGate.check("kit_task_card_blocked", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Ship the settings redesign", KitTaskState.Waiting, {},
                meta = listOf(KitTaskMeta("Urgent", priority = KitPriority.Urgent, strong = true), KitTaskMeta("2 h ago")),
                flag = KitTaskFlag(KitTaskFlagKind.Blocked, "Blocked by Sync engine"),
                action = move(),
            )
        })
    }

    @Test fun kit_task_card_failed() {
        FlutterGoldenGate.check("kit_task_card_failed", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Upgrade the build tools", KitTaskState.Failed, {},
                meta = listOf(KitTaskMeta("owl"), KitTaskMeta("1 h ago")),
                flag = KitTaskFlag(KitTaskFlagKind.Failed, "Stopped with an error"),
                action = move(),
            )
        })
    }

    @Test fun kit_task_card_stopped() {
        FlutterGoldenGate.check("kit_task_card_stopped", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Try the old cache layout", KitTaskState.Stopped, {},
                meta = listOf(KitTaskMeta("Someday", priority = KitPriority.Someday), KitTaskMeta("2 d ago")),
                flag = KitTaskFlag(KitTaskFlagKind.Stopped, "Cancelled"),
                action = move(),
            )
        })
    }

    @Test fun kit_task_card_done() {
        FlutterGoldenGate.check("kit_task_card_done", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Write the release notes", KitTaskState.Done, {},
                meta = listOf(
                    KitTaskMeta("Low", priority = KitPriority.Low),
                    KitTaskMeta("Feature", icon = KitIcons.sparkle),
                    KitTaskMeta("1 d ago"),
                ),
                flag = KitTaskFlag(KitTaskFlagKind.Info, "In Onboarding", KitIcons.layers),
                action = move(),
            )
        })
    }

    @Test fun kit_task_card_moving() {
        FlutterGoldenGate.check("kit_task_card_moving", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Fix the sync engine dropping queued messages", KitTaskState.Working, {},
                meta = meta,
                receipt = KitReceiptState.Sending,
                action = move(reason = "Moving to Review…"),
            )
        })
    }

    @Test fun kit_task_card_not_confirmed() {
        FlutterGoldenGate.check("kit_task_card_not_confirmed", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard(
                "Fix the sync engine dropping queued messages", KitTaskState.Working, {},
                meta = meta,
                receipt = KitReceiptState.Sent,
                onReceiptRetry = {},
                action = move(),
            )
        })
    }

    @Test fun kit_task_card_read_only() {
        FlutterGoldenGate.check("kit_task_card_read_only", wrap = { FlutterCapture(it) }, content = lane {
            KitTaskCard("Fix the sync engine dropping queued messages", KitTaskState.Working, {}, meta = meta)
        })
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_task_card", caps = mapOf("KitPriorityGlyph" to "glyph", "KitTaskCard" to "lane"), content = default)
    }
}
