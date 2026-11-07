package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitConnectionTone
import io.github.eslamasabry.oc.ui.kit.frame.KitShellControls
import io.github.eslamasabry.oc.ui.kit.frame.KitShellControlsLayout
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitNavDestination
import io.github.eslamasabry.oc.ui.kit.nav.KitNavRail
import io.github.eslamasabry.oc.ui.kit.nav.KitSidebar
import kotlin.test.Test

/** test/goldens/kit/kit_nav_golden_test.dart: the phone without a bar, the rail and the sidebar. */
class KitNavGateTest {
    /** `_List`: body rows on the gutter, space3 above and below each. */
    private fun list(count: Int, prefix: String): @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FlutterKitTokens.GUTTER.dp)) {
            for (i in 1..count) {
                KitText("$prefix $i: the quick brown fox jumps over.", Modifier.padding(vertical = Space.S3.dp))
            }
        }
    }

    private fun destinations(inbox: Int = 0) =
        listOf(
            KitNavDestination(
                "work",
                "Work",
                KitIcons.workspace,
                selectedIcon = KitIcons.workspaceSelected,
                pane = list(8, "Conversation"),
            ),
            KitNavDestination("inbox", "Inbox", KitIcons.activity, needsYou = inbox, selectedIcon = KitIcons.activitySelected),
            KitNavDestination("project", "Project", KitIcons.files, selectedIcon = KitIcons.filesSelected),
            KitNavDestination("settings", "Settings", KitIcons.settings),
        )

    // A phone draws no navigation (kit_nav.dart KitNavLayout.none): the content alone.
    @Test fun kit_nav_phone_no_bar() {
        FlutterGoldenGate.check("kit_nav_phone_no_bar", frame = FlutterGoldenGate.Frame.Screen, content = list(12, "Row"))
    }

    private val railPart: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(Space.S2.dp), contentAlignment = Alignment.TopStart) {
            KitNavRail(destinations(inbox = 1), selectedId = "work", onSelect = {})
        }
    }

    @Test fun kit_nav_rail_part() { FlutterGoldenGate.check("kit_nav_rail_part", frame = FlutterGoldenGate.Frame.Screen, content = railPart) }

    @Test fun kit_nav_rail_needs_you_1280x800() {
        FlutterGoldenGate.check("kit_nav_rail_needs_you_1280x800", frame = FlutterGoldenGate.Frame.Screen) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().padding(Space.S2.dp)) {
                    KitNavRail(destinations(inbox = 1), selectedId = "work", onSelect = {})
                }
                Box(Modifier.weight(1f)) { list(12, "Row")() }
            }
        }
    }

    private val sidebar: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            KitSidebar(
                destinations(inbox = 3),
                selectedId = "work",
                onSelect = {},
                header = {
                    KitShellControls(
                        server = "phone",
                        serverStatus = "Connected",
                        serverTone = KitConnectionTone.Ok,
                        onServer = {},
                        project = "opencode",
                        onProject = {},
                        onSearch = {},
                        layout = KitShellControlsLayout.Sidebar,
                    )
                },
                primary = KitAction("New conversation", {}),
            )
            Box(Modifier.weight(1f)) { list(12, "Row")() }
        }
    }

    @Test fun kit_nav_sidebar_default_1280x800() {
        FlutterGoldenGate.check("kit_nav_sidebar_default_1280x800", frame = FlutterGoldenGate.Frame.Screen, content = sidebar)
    }

    @Test fun kit_nav_sidebar_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_nav_sidebar_default_text2_1280x800", frame = FlutterGoldenGate.Frame.Screen, content = sidebar)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_nav",
            caps = mapOf("KitNav" to "fill", "KitNavRail" to "fill"),
            frame = FlutterGoldenGate.Frame.Screen,
            content = railPart,
        )
    }
}
