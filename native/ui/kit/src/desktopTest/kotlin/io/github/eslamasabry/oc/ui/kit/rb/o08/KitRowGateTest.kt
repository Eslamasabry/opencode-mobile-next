package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Ignore
import kotlin.test.Test

/** The scenes of test/goldens/kit/kit_row_golden_test.dart and the KitRowMenu scenes of kit_row_parts_golden_test.dart. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitRowGateTest {
    private fun glyph(icon: androidx.compose.ui.graphics.vector.ImageVector) = KitRowLeading.Glyph(icon)

    private val menu = listOf(
        KitMenuItem("Rename", {}, icon = KitIcons.edit),
        KitMenuItem("Delete conversation", {}, icon = KitIcons.delete, destructive = true),
    )

    /** `_defaultRows`: a two-line row, a value row and a chevron row in a group labelled "Conversations". */
    private fun defaults(withMenu: Boolean = false): @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow(
                "Fix the login bug", supporting = "Finished 5h ago", leading = glyph(KitIcons.chat),
                // The swipe's menu twin ("Archive") comes first, as Flutter's _menuWithTwin puts it.
                menu = if (withMenu) listOf(KitMenuItem("Archive", {}, icon = KitIcons.archive)) + menu else emptyList(),
                onClick = {},
            )
            KitRow("Model", leading = glyph(KitIcons.model), trailing = KitRowTrailing.Value("Claude Sonnet 4"), onClick = {})
            KitRow("Project settings", leading = glyph(KitIcons.settings), trailing = KitRowTrailing.Chevron, onClick = {})
        }
    }

    private val disabled: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow("Run tests", leading = glyph(KitIcons.terminal), enabled = false, disabledReason = "Needs a connection to the server", onClick = {})
            KitRow("Open terminal", leading = glyph(KitIcons.terminal), onClick = {})
        }
    }

    private val unavailable: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow(
                "Voice input", leading = glyph(KitIcons.mic), unavailableReason = "Voice needs a model on this phone.",
                enable = KitLineAction("Download", onClick = {}),
            )
            KitRow("Sub-agents", leading = glyph(KitIcons.agent), unavailableReason = "Available on OpenCode 2 servers.")
        }
    }

    private val server: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow("Fix the login bug", leading = glyph(KitIcons.chat), server = "laptop", supporting = "Needs you", onClick = {})
            KitRow("Write release notes", leading = glyph(KitIcons.chat), server = "phone", supporting = "Finished 2m ago", onClick = {})
        }
    }

    private val selected: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow("Fix the login bug", leading = glyph(KitIcons.chat), supporting = "Open · Finished 5h ago", selected = true, onClick = {})
            KitRow("Write release notes", leading = glyph(KitIcons.chat), supporting = "Finished 2m ago", onClick = {})
        }
    }

    private val destructive: @Composable () -> Unit = {
        KitRowGroup(
            label = "Conversations",
            destructive = {
                KitRow("Delete conversation", leading = glyph(KitIcons.delete), leadingTone = io.github.eslamasabry.oc.ui.design.type.TextTone.Primary, onClick = {})
            },
        ) {
            KitRow("Rename", leading = glyph(KitIcons.edit), onClick = {})
            KitRow("Share", leading = glyph(KitIcons.link), onClick = {})
        }
    }

    /** kit_row_parts `_shot`: a ListView padded by the gutter holding one unlabelled group. */
    private val menuRows: @Composable () -> Unit = {
        // KitMaxWidth.list centres the capped group in the padded ListView.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        KitRowGroup {
            KitRow(
                "Conversation", leading = glyph(KitIcons.chat),
                trailing = KitRowTrailing.Menu(
                    "Conversation actions",
                    listOf(KitMenuItem("Rename", {}), KitMenuItem("Share", {}), KitMenuItem("Delete", {}, destructive = true)),
                ),
            )
        }
        }
    }

    @Test fun kit_row_default() { FlutterGoldenGate.check("kit_row_default", content = defaults()) }
    @Test fun kit_row_default_1280x800() { FlutterGoldenGate.check("kit_row_default_1280x800", content = defaults()) }
    @Test fun kit_row_disabled() { FlutterGoldenGate.check("kit_row_disabled", content = disabled) }
    @Test fun kit_row_unavailable() { FlutterGoldenGate.check("kit_row_unavailable", content = unavailable) }
    @Test fun kit_row_server() { FlutterGoldenGate.check("kit_row_server", content = server) }
    @Test fun kit_row_selected() { FlutterGoldenGate.check("kit_row_selected", content = selected) }
    @Test fun kit_row_destructive() { FlutterGoldenGate.check("kit_row_destructive", content = destructive) }

    @Test fun kit_row_hover() {
        FlutterGoldenGate.check("kit_row_hover", act = { onNodeWithText("Model").performMouseInput { moveTo(center) } }, content = defaults())
    }

    @Test fun kit_row_focused() {
        FlutterGoldenGate.check("kit_row_focused", act = { onRoot().performKeyInput { pressKey(Key.Tab); pressKey(Key.Tab) } }, content = defaults())
    }

    @Ignore("blocked: the open menu is a Popup, a second compose root, and FlutterGoldenGate.render captures onRoot() (one root only; :ui:gate, not order 08)")
    @Test fun kit_row_context_menu() {
        FlutterGoldenGate.check(
            "kit_row_context_menu",
            act = { onNodeWithText("Fix the login bug").performTouchInput { longClick() } },
            content = defaults(withMenu = true),
        )
    }

    @Test fun kit_row_menu_default() {
        for (size in listOf("", "_1280x800")) FlutterGoldenGate.check("kit_row_menu_default$size", Frame.Screen, content = menuRows)
    }

    @Ignore("blocked: the open menu is a Popup, a second compose root, and FlutterGoldenGate.render captures onRoot() (one root only; :ui:gate, not order 08)")
    @Test fun kit_row_menu_open() {
        FlutterGoldenGate.check("kit_row_menu_open", Frame.Screen, act = { onNodeWithTag("kit-row-menu", useUnmergedTree = true).performClick() }, content = menuRows)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_row", caps = mapOf("KitRow" to "list", "KitRowGroup" to "list"), content = defaults())
    }
}
