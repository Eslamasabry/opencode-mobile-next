package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwipeAction
import io.github.eslamasabry.oc.ui.kit.row.KitSwipeRow
import kotlin.test.Test

/**
 * The swipe scenes of test/goldens/kit/kit_row_golden_test.dart: the default rows with the first row's swipe held at
 * about 60 % of its width (`_holdSwipe`), and after the act, with the one undo line.
 */
@OptIn(ExperimentalTestApi::class)
class KitSwipeActionGateTest {
    private val swipe = KitSwipeAction(
        id = "session-dismiss-fix-login",
        label = "Archive",
        icon = KitIcons.archive,
        onAct = { true },
        undoMessage = "Archived 'Fix the login bug'",
        onUndo = {},
    )

    private val rows: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitSwipeRow(actionLabel = swipe.label, onAct = {}, icon = swipe.icon) {
                KitRow("Fix the login bug", supporting = "Finished 5h ago", leading = KitRowLeading.Glyph(KitIcons.chat), onClick = {})
            }
            KitRow("Model", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Value("Claude Sonnet 4"), onClick = {})
            KitRow("Project settings", leading = KitRowLeading.Glyph(KitIcons.settings), trailing = KitRowTrailing.Chevron, onClick = {})
        }
    }

    /** `_holdSwipe`: press on the title, move 20 px, then on to 60 % of the row's width, and hold. */
    private val hold: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        onAllNodesWithText("Fix the login bug").onFirst().performTouchInput {
            down(center)
            moveBy(Offset(-20f, 0f))
            moveBy(Offset(-width * 0.6f + 20f, 0f))
        }
    }

    @Test fun kit_swipe_action_revealing() {
        FlutterGoldenGate.check("kit_swipe_action_revealing", wrap = { FlutterCapture(it) }, act = hold, content = rows)
    }

    @Test fun kit_swipe_action_revealing_1280x800() {
        FlutterGoldenGate.check("kit_swipe_action_revealing_1280x800", wrap = { FlutterCapture(it) }, act = hold, content = rows)
    }

    @Test fun kit_swipe_action_acted() {
        FlutterGoldenGate.check("kit_swipe_action_acted", Frame.Screen, wrap = { FlutterCapture(it) }) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.padding(top = 16.dp)) { rows() }
                Box(Modifier.align(Alignment.BottomCenter)) { KitUndoBar(swipe.undoOffer(), onUndo = {}, onTimeout = {}) }
            }
        }
    }
}
