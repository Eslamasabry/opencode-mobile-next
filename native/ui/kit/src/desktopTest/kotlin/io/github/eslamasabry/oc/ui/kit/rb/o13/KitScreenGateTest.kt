package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlin.test.Test

/** test/goldens/kit/kit_screen_golden_test.dart: a page of six rows under a bar, in its states and panes. */
class KitScreenGateTest {
    private val connection =
        KitStatus(kind = KitStatusKind.Connection, id = "connection:laptop", icon = KitIcons.cloudOff, message = "Reconnecting to laptop…")

    private fun rows(prefix: String = "Conversation"): @Composable () -> Unit = {
        // The Dart scene pads the list by KitScreen.padding (the gutter) and KitRow adds its own, so its words start at
        // 32 dp; the alignment gate holds row words to the page's 16 dp edge, so the native scene keeps one gutter.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for (i in 1..6) KitRow("$prefix $i", supporting = "Updated $i min ago", onClick = {})
        }
    }

    private val save: @Composable () -> Unit = { KitButton("Start a task", {}) }

    /** The pages are pushed (exit auto = Back), except the detail pane's bar (exit none). */
    private fun bar(title: String): @Composable () -> Unit = { KitTopBar(title, exit = KitTopBarExit.Back, exitLabel = "Back", onExit = {}) }

    private val emptyDetail: @Composable () -> Unit = { KitStateView(KitStateKind.Empty, "Pick a conversation", icon = KitIcons.chat) }

    private val detail: @Composable () -> Unit = {
        KitScreen(topBar = { KitTopBar("Conversation 2") }, width = KitScreenWidth.Full) {
            Column(Modifier.padding(horizontal = FlutterKitTokens.GUTTER.dp)) {
                KitText("The login fix is ready for review.")
                KitText("Two files changed; the tests pass.")
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) =
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, content = content)

    private val default: @Composable () -> Unit = {
        KitScreen(topBar = bar("Conversations"), width = KitScreenWidth.Reading, bottom = save, body = rows())
    }

    @Test fun kit_screen_default() { shot("kit_screen_default", default) }

    @Test fun kit_screen_default_1280x800() { shot("kit_screen_default_1280x800", default) }

    @Test fun kit_screen_loading() {
        shot("kit_screen_loading") {
            KitScreen(topBar = bar("Conversations"), loading = true, loadingLabel = "Loading conversations", body = rows())
        }
    }

    @Test fun kit_screen_status() { shot("kit_screen_status") { KitScreen(topBar = bar("Conversations"), status = connection, body = rows()) } }

    @Test fun kit_screen_search() {
        shot("kit_screen_search") {
            KitScreen(topBar = bar("Conversations"), search = { KitSearchField("Search conversations", {}) }, body = rows())
        }
    }

    // The keyboard shot raises a 300 dp inset; the desktop gate has no keyboard, so this renders the resting page.
    @Test fun kit_screen_keyboard() { shot("kit_screen_keyboard") { KitScreen(topBar = bar("New task"), bottom = save, body = rows()) } }

    @Test fun kit_screen_two_pane_empty_1280x800() {
        shot("kit_screen_two_pane_empty_1280x800") {
            KitPaneScreen(list = rows(), detail = null, emptyDetail = emptyDetail, topBar = bar("Conversations"), bottom = save)
        }
    }

    @Test fun kit_screen_two_pane_selected_1280x800() {
        shot("kit_screen_two_pane_selected_1280x800") {
            KitPaneScreen(list = rows(), detail = detail, emptyDetail = emptyDetail, topBar = bar("Conversations"), bottom = save)
        }
    }

    @Test fun kit_screen_three_pane_1280x800() {
        shot("kit_screen_three_pane_1280x800") {
            KitPaneScreen(
                list = rows(),
                detail = detail,
                emptyDetail = emptyDetail,
                side = rows("Changed file"),
                topBar = bar("Conversations"),
                bottom = save,
            )
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_screen", caps = mapOf("KitScreen" to "fill"), frame = FlutterGoldenGate.Frame.Screen, content = default)
    }
}
