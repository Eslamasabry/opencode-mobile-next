package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.STATE_ESCALATE_AFTER
import kotlin.test.Test
import kotlin.time.TimeSource

/** The scenes of test/goldens/kit/kit_state_view_golden_test.dart, with the Dart test's exact inputs. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, RedactorApi::class)
class KitStateViewGateTest {
    private fun act(label: String) = KitAction(label, onClick = {})

    private val details =
        Redacted("GET /api/permission failed: 500\nFormatException: Unexpected character (at character 1)")

    /** A page state fills the window's height, as it does in a real body (`_page`: the height less 64). */
    private fun page(h: Int, content: @Composable () -> Unit): @Composable () -> Unit = {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height((h - 64).dp)) { content() }
    }

    private val empty: @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Empty,
            "No worktrees yet",
            body = "A worktree lets an agent work on a branch without touching yours.",
            icon = KitIcons.branch,
            primary = act("Create worktree"),
        )
    }

    private fun errorOther(size: KitStateSize = KitStateSize.Page): @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Error,
            "Couldn’t load saved permissions",
            body = "The server answered with something the app can’t read.",
            failure = Failure.UnreadableReply(details),
            onRetry = {},
            retryLabel = "Try again",
            onReport = {},
            size = size,
        )
    }

    private fun missingEnable(size: KitStateSize = KitStateSize.Inline): @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Missing,
            "Voice needs a model",
            body = "Voice runs on this phone once a model is downloaded.",
            primary = act("Download voice model"),
            cost = listOf("About 208 MB", "about 4 min", "uses Wi-Fi"),
            size = size,
        )
    }

    private fun calm(size: KitStateSize = KitStateSize.Page): @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Calm,
            "Nothing running",
            body = "Start a conversation, or open one to pick up where you left off.",
            icon = KitIcons.chat,
            primary = if (size == KitStateSize.Page) act("New conversation") else null,
            tertiary = listOf(act("Show all conversations")),
            size = size,
        )
    }

    private val loading: @Composable () -> Unit = {
        KitStateView(KitStateKind.Loading, "Loading conversations…", icon = KitIcons.sync, progress = KitProgress.Waiting())
    }

    private val working: @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Working,
            "Installing OpenCode…",
            icon = KitIcons.download,
            progress = KitProgress.Staged(step = 3, of = 5, label = "Installing packages"),
        )
    }

    private val errorNetwork: @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Error,
            "Couldn’t reach laptop",
            body = "Check that the server is running and this phone is online.",
            failure = Failure.TimedOut(Redacted("GET http://192.168.1.20:4096/session timed out after 10 s")),
            onRetry = {},
            retryLabel = "Try again",
            onSwitchServer = {},
            switchServerLabel = "Switch server",
        )
    }

    private val missingExplains: @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Missing,
            "Terminal needs a server on this phone",
            body = "The terminal runs where the server runs. Laptop servers offer it from the Servers page.",
        )
    }

    private val slow: @Composable () -> Unit = {
        KitStateView(
            KitStateKind.Loading,
            "Starting OpenCode…",
            body = "This takes a few seconds.",
            icon = KitIcons.sync,
            progress = KitProgress.Waiting(),
            since = TimeSource.Monotonic.markNow() - STATE_ESCALATE_AFTER * 2,
            onSlow = listOf(act("Try again"), act("Restart")),
        )
    }

    @Test fun kit_state_view_loading() { FlutterGoldenGate.check("kit_state_view_loading", content = page(915, loading)) }
    @Test fun kit_state_view_working() { FlutterGoldenGate.check("kit_state_view_working", content = page(915, working)) }
    @Test fun kit_state_view_empty() { FlutterGoldenGate.check("kit_state_view_empty", content = page(915, empty)) }
    @Test fun kit_state_view_error_network() { FlutterGoldenGate.check("kit_state_view_error_network", content = page(915, errorNetwork)) }
    @Test fun kit_state_view_error_other() { FlutterGoldenGate.check("kit_state_view_error_other", content = page(915, errorOther())) }
    @Test fun kit_state_view_calm() { FlutterGoldenGate.check("kit_state_view_calm", content = page(915, calm())) }
    @Test fun kit_state_view_missing_explains() { FlutterGoldenGate.check("kit_state_view_missing_explains", content = page(915, missingExplains)) }
    @Test fun kit_state_view_missing_enable() { FlutterGoldenGate.check("kit_state_view_missing_enable", content = page(915, missingEnable(KitStateSize.Page))) }
    @Test fun kit_state_view_slow() { FlutterGoldenGate.check("kit_state_view_slow", content = page(915, slow)) }
    @Test fun kit_state_view_error_other_inline() { FlutterGoldenGate.check("kit_state_view_error_other_inline", content = errorOther(KitStateSize.Inline)) }
    @Test fun kit_state_view_calm_inline() { FlutterGoldenGate.check("kit_state_view_calm_inline", content = calm(KitStateSize.Inline)) }
    @Test fun kit_state_view_calm_text2() { FlutterGoldenGate.check("kit_state_view_calm_text2", content = page(915, calm())) }
    @Test fun kit_state_view_missing_enable_inline() { FlutterGoldenGate.check("kit_state_view_missing_enable_inline", content = missingEnable()) }
    @Test fun kit_state_view_error_other_text2() { FlutterGoldenGate.check("kit_state_view_error_other_text2", content = page(915, errorOther())) }
    @Test fun kit_state_view_empty_1280x800() { FlutterGoldenGate.check("kit_state_view_empty_1280x800", content = page(800, empty)) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_state_view", caps = mapOf("KitStateView" to "state"), content = errorOther(KitStateSize.Inline)) }
}
