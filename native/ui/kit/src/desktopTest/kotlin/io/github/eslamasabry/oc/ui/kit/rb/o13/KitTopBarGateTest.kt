package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitShellControls
import io.github.eslamasabry.oc.ui.kit.frame.KitShellControlsLayout
import io.github.eslamasabry.oc.ui.kit.frame.KitShellTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test

/** test/goldens/kit/kit_top_bar_golden_test.dart: the bar over two body rows, as it sits at the top of a screen. */
class KitTopBarGateTest {
    private val stop = KitLineAction("Stop task", {}, icon = KitIcons.stop)
    private val copy = KitLineAction("Copy link", {}, icon = KitIcons.copy)

    /** The one overflow: a "Rename" menu behind ⋯ (the scene's `_menu`). */
    private val more: @Composable (List<KitLineAction>) -> Unit = { KitIconButton(KitIcons.more, "More actions", {}) }

    private fun screen(bar: @Composable () -> Unit): @Composable () -> Unit = {
        Column {
            bar()
            KitRow("Fix the login redirect", supporting = "Working · 2 min")
            KitRow("Update the docs", supporting = "Done · yesterday")
        }
    }

    @Composable
    private fun controls(
        status: String = "Connected",
        tone: KitConnectionTone = KitConnectionTone.Ok,
        needsYou: Int = 0,
        layout: KitShellControlsLayout = KitShellControlsLayout.Bar,
    ) {
        val sidebar = layout == KitShellControlsLayout.Sidebar
        KitShellControls(
            server = "Laptop",
            serverStatus = status,
            serverTone = tone,
            onServer = {},
            needsYou = needsYou,
            project = if (sidebar) "shopfront" else null,
            onProject = if (sidebar) ({}) else null,
            onSearch = {},
            layout = layout,
        )
    }

    private val default = screen {
        KitTopBar("Fix login", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {}, actions = listOf(stop), more = more)
    }

    private fun shot(name: String, content: @Composable () -> Unit) = FlutterGoldenGate.check(name, content = content)

    @Test fun kit_top_bar_default() { shot("kit_top_bar_default", default) }

    @Test fun kit_top_bar_default_text2() { shot("kit_top_bar_default_text2", default) }

    @Test fun kit_top_bar_default_text2_1280x800() { shot("kit_top_bar_default_text2_1280x800", default) }

    @Test fun kit_top_bar_default_1280x800() {
        shot(
            "kit_top_bar_default_1280x800",
            screen {
                KitTopBar(
                    "Fix login",
                    subtitle = "Working · 2 min",
                    exit = KitTopBarExit.Back,
                    exitLabel = "Back",
                    onExit = {},
                    actions = listOf(stop, copy),
                    more = more,
                )
            },
        )
    }

    @Test fun kit_top_bar_subtitle_working() {
        shot(
            "kit_top_bar_subtitle_working",
            screen {
                // Flutter's scene: subtitleTone progress.
                KitTopBar(
                    "Fix login",
                    subtitle = "Working · 2 min",
                    subtitleTone = StatusTone.Progress,
                    exit = KitTopBarExit.Back,
                    exitLabel = "Back",
                    onExit = {},
                    actions = listOf(stop, copy),
                )
            },
        )
    }

    @Test fun kit_top_bar_needs_you() {
        shot(
            "kit_top_bar_needs_you",
            screen {
                KitTopBar("Fix login", subtitle = "Laptop", exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {}, actions = listOf(stop, copy), needsYou = 1)
            },
        )
    }

    @Test fun kit_top_bar_switcher() {
        shot(
            "kit_top_bar_switcher",
            screen { KitTopBar("shopfront", onTitleTap = {}, titleTapLabel = "Switch project", more = more, needsYou = 2) },
        )
    }

    @Test fun kit_top_bar_brand() { shot("kit_top_bar_brand", screen { KitTopBar("Open Portal", brand = true) }) }

    @Test fun kit_top_bar_close() {
        shot("kit_top_bar_close", screen { KitTopBar("New server", exit = KitTopBarExit.Close, exitLabel = "Close", onExit = {}) })
    }

    @Test fun kit_top_bar_shell_connected() { shot("kit_top_bar_shell_connected", screen { KitShellTopBar { controls() } }) }

    @Test fun kit_top_bar_shell_reconnecting() {
        shot(
            "kit_top_bar_shell_reconnecting",
            screen { KitShellTopBar { controls(status = "Reconnecting", tone = KitConnectionTone.Working) } },
        )
    }

    @Test fun kit_top_bar_shell_needs_you() {
        shot("kit_top_bar_shell_needs_you", screen { KitShellTopBar(trailing = { more(emptyList()) }) { controls(needsYou = 1) } })
    }

    @Test fun kit_top_bar_shell_sidebar_1280x800() {
        shot("kit_top_bar_shell_sidebar_1280x800") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.width(280.dp).padding(16.dp)) { controls(needsYou = 1, layout = KitShellControlsLayout.Sidebar) }
            }
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_top_bar",
            caps = mapOf("KitShellControls" to "fill", "KitTopBar" to "fill"),
            content = default,
        )
    }
}
