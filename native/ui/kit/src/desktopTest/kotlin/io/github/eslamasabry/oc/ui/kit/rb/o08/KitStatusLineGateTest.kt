package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The scenes of test/goldens/kit/kit_status_line_golden_test.dart: every state under a stub top bar. */
class KitStatusLineGateTest {
    private fun act(label: String) = KitLineAction(label, {})

    /** The stub top bar: the screen's name where the real bar puts it (`_StubTopBar`). */
    @Composable
    private fun StubTopBar() {
        val tokens = LocalKitTokens.current
        Box(
            Modifier.fillMaxWidth().height(tokens.rowHeightOne.dp).padding(start = tokens.gutter.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            KitText("Laptop", role = TypeRole.Headline)
        }
    }

    private fun screen(line: @Composable () -> Unit): @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            StubTopBar()
            line()
        }
    }

    private val connection = screen {
        KitStatusLine(
            KitStatus(
                KitStatusKind.Connection,
                "Reconnecting to laptop…",
                id = "connection:laptop",
                icon = KitIcons.cloudOff,
                tone = StatusTone.Progress,
                supporting = "2 drafts will send when connected",
                action = act("Servers"),
            ),
        )
    }

    private val slow = screen {
        KitStatusLine(
            "Reconnecting to laptop…",
            icon = KitIcons.cloudOff,
            tone = StatusTone.Progress,
            since = TimeSource.Monotonic.markNow() - 16.seconds,
            onSlow = listOf(act("Retry"), act("Leave it running")),
        )
    }

    private val now = screen {
        KitStatusLine(
            KitStatus(
                KitStatusKind.Work,
                "Working on the login fix",
                id = "team:login-fix",
                icon = KitIcons.agent,
                tone = StatusTone.Progress,
                next = "A reviewer checks it next · about 6 min",
                action = act("Open"),
            ),
        )
    }

    private val risky = screen {
        KitStatusLine(
            KitStatus(KitStatusKind.RiskySwitch, "Auto-approve is on for this chat", icon = KitIcons.shield, action = act("Turn off")),
        )
    }

    private val update = screen {
        KitStatusLine(
            KitStatus(
                KitStatusKind.Update,
                "Update ready",
                icon = KitIcons.download,
                supporting = "Restart the app to use it",
                action = act("Restart"),
                onDismiss = {},
            ),
        )
    }

    private val error = screen {
        KitStatusLine(
            KitStatus(
                KitStatusKind.Info,
                "Couldn’t share this conversation",
                icon = KitIcons.error,
                tone = StatusTone.Failure,
                action = act("Try again"),
                more = listOf(act("Details")),
            ),
        )
    }

    private val countdown = screen {
        KitStatusLine(
            "Laptop isn’t answering",
            icon = KitIcons.cloudOff,
            countdownTo = TimeSource.Monotonic.markNow() + 12400.milliseconds,
            countdownLine = { left -> "Trying again in ${ceil(left.inWholeMilliseconds / 1000.0).toLong()} s" },
            action = act("Try now"),
            more = listOf(act("Details")),
        )
    }

    private val stacked = screen {
        KitStatusLine(
            "Can’t reach the laptop over Tailscale; it may be asleep or offline",
            icon = KitIcons.cloudOff,
            tone = StatusTone.Attention,
            action = act("Wake it"),
            more = listOf(act("Servers")),
        )
    }

    @Test fun kit_status_line_connection() { FlutterGoldenGate.check("kit_status_line_connection", content = connection) }
    @Test fun kit_status_line_slow() { FlutterGoldenGate.check("kit_status_line_slow", content = slow) }
    @Test fun kit_status_line_now() { FlutterGoldenGate.check("kit_status_line_now", content = now) }
    @Test fun kit_status_line_risky() { FlutterGoldenGate.check("kit_status_line_risky", content = risky) }
    @Test fun kit_status_line_update() { FlutterGoldenGate.check("kit_status_line_update", content = update) }
    @Test fun kit_status_line_error() { FlutterGoldenGate.check("kit_status_line_error", content = error) }
    @Test fun kit_status_line_countdown() { FlutterGoldenGate.check("kit_status_line_countdown", content = countdown) }
    @Test fun kit_status_line_stacked() { FlutterGoldenGate.check("kit_status_line_stacked", content = stacked) }
    @Test fun kit_status_line_connection_1280x800() { FlutterGoldenGate.check("kit_status_line_connection_1280x800", content = connection) }
    @Test fun kit_status_line_connection_text2() { FlutterGoldenGate.check("kit_status_line_connection_text2", content = connection) }
    @Test fun kit_status_line_connection_text2_1280x800() { FlutterGoldenGate.check("kit_status_line_connection_text2_1280x800", content = connection) }
    @Test fun kit_status_line_now_text2() { FlutterGoldenGate.check("kit_status_line_now_text2", content = now) }
    @Test fun kit_status_line_now_text2_1280x800() { FlutterGoldenGate.check("kit_status_line_now_text2_1280x800", content = now) }
    @Test fun kit_status_line_countdown_text2() { FlutterGoldenGate.check("kit_status_line_countdown_text2", content = countdown) }
    @Test fun kit_status_line_countdown_text2_1280x800() { FlutterGoldenGate.check("kit_status_line_countdown_text2_1280x800", content = countdown) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_status_line", caps = mapOf("KitStatusLine" to "fill"), content = connection) }
}
