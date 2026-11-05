package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.style.KitRunningFeedLine
import kotlin.test.Test

/**
 * The Signal goldens are whole screens (the Conversations list, a conversation at work) built from parts other
 * orders own (feed items, request cards, step timelines). Each shot is rendered here at the phone window with the
 * kit parts that exist today; the shots that differ only through those parts are waived DEPENDS_ON in waivers/01.tsv.
 */
class KitSignalGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_signal") {
            KitRunningFeedLine(sinceMs = 180_000, nowMs = 180_000, command = "flutter test", runningLabel = "Running", minutesLabel = { "$it min" })
        }
    }

    @Composable private fun homeScene(ar: Boolean) {
        Column {
            KitPageHeading(title = if (ar) "المحادثات" else "Conversations")
            KitRowGroup(label = if (ar) "اليوم" else "Today") {
                KitRow(title = "Push to origin?", supporting = "settings-demo", meta = "2 min ago", onClick = {})
                KitRow(title = "Fix the TV focus ring", supporting = "IPTV_King · Claude Code", meta = "now", onClick = {})
                KitRunningFeedLine(sinceMs = 180_000, nowMs = 180_000, command = "flutter test", runningLabel = "Running", minutesLabel = { "$it min" })
                KitRow(title = "Design review", supporting = "IPTV_King · Codex", meta = "1 h ago", onClick = {})
                KitRow(title = "Widget sizes", supporting = "oc_app", meta = "3 h ago", onClick = {})
            }
            KitRowGroup(label = if (ar) "سابقاً" else "Earlier") {
                KitRow(title = "Arabic search", supporting = "IPTV_King", meta = "yesterday", onClick = {})
            }
        }
    }

    @Composable private fun chatScene() {
        Column(Modifier.padding(vertical = 16.dp)) { // F: test/goldens/kit/kit_signal_golden_test.dart:165
            KitRow(title = "Read lib/ui/tv/tv_grid.dart", supporting = "1 s", onClick = {})
            KitRow(title = "Edit lib/ui/tv/tv_tile.dart", supporting = "+3", onClick = {})
            KitRow(title = "Run flutter test", onClick = {})
            KitRunningFeedLine(sinceMs = 72_000, nowMs = 72_000, command = "flutter test", runningLabel = "Running", minutesLabel = { "$it min" }, secondsLabel = { "$it s" })
        }
    }

    private fun shot(name: String, body: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, style = Style.Signal, content = body)
    }

    @Test fun home() = shot("kit_signal_home") { homeScene(false) }
    @Test fun homeAr() = shot("kit_signal_home_ar") { homeScene(true) }
    @Test fun chat() = shot("kit_signal_chat") { chatScene() }
    @Test fun chatFolded() = shot("kit_signal_chat_folded") { chatScene() }
}
