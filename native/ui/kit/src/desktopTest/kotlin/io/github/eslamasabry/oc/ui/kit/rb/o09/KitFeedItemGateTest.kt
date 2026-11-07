package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.row.KitFeedAgent
import io.github.eslamasabry.oc.ui.kit.row.KitFeedItem
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import kotlin.test.Test

/** test/goldens/kit/kit_feed_item_golden_test.dart `_part` (five conversations) and `_disabled` (read-only). */
class KitFeedItemGateTest {
    private val part: @Composable () -> Unit = {
        KitRowGroup {
            KitFeedItem("Fix the login bug", "alpha", "2 h ago", KitRowState("Needs you", KitRowStateTone.NeedsYou), {})
            KitFeedItem("Add dark mode", "beta", "now", KitRowState("Working", KitRowStateTone.Running), {})
            KitFeedItem("Explain the build", "gamma", "3 h ago", KitRowState("Done", KitRowStateTone.Quiet), {}, done = true)
            KitFeedItem(
                "Rename the settings page and move its tests next to it", "alpha", "3 d ago", null, {},
                agent = KitFeedAgent("", "Claude Code"),
            )
            KitFeedItem("Plan the release notes", "gamma", "Sep 24", KitRowState("Failed", KitRowStateTone.Quiet), {})
        }
    }

    private val disabled: @Composable () -> Unit = {
        KitRowGroup { KitFeedItem("Fix the login bug", "alpha", "2 h ago", KitRowState("Working", KitRowStateTone.Running), null) }
    }

    @Test fun kit_feed_item_default() { FlutterGoldenGate.check("kit_feed_item_default", wrap = { FlutterCapture(it) }, content = part) }
    @Test fun kit_feed_item_default_1280x800() { FlutterGoldenGate.check("kit_feed_item_default_1280x800", wrap = { FlutterCapture(it) }, content = part) }
    @Test fun kit_feed_item_default_text2() {
        FlutterGoldenGate.check("kit_feed_item_default_text2", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = part)
    }
    @Test fun kit_feed_item_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_feed_item_default_text2_1280x800", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = part)
    }
    @Test fun kit_feed_item_disabled() { FlutterGoldenGate.check("kit_feed_item_disabled", wrap = { FlutterCapture(it) }, content = disabled) }
    @Test fun kit_feed_item_disabled_1280x800() { FlutterGoldenGate.check("kit_feed_item_disabled_1280x800", wrap = { FlutterCapture(it) }, content = disabled) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_feed_item", caps = mapOf("KitFeedItem" to "list"), wrap = { FlutterCapture(it) }, content = part) }
}
