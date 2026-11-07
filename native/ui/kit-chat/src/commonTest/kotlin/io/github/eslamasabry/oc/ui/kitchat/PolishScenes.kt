package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeChunk
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTray
import io.github.eslamasabry.oc.ui.kitchat.composer.KitTrayAction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitTrayKind
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState

/*
 * S8 polish scenes (auditor sweep of a2e7d3fa5): the transcript parts on the page's own 16 dp edge, as the chat draws
 * them (no page padding of their own: each part carries the conversation column's S4 sides).
 */

@Composable
private fun Edge(tag: String, content: @Composable () -> Unit) {
    Column(
        Modifier.testTag(tag).fillMaxWidth().background(LocalKitColors.current.ground).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** The `ls -la` listing and the `demo.py` block from the audit: lines wider than the phone, with Copy and Wrap. */
val polishListing: String =
    """
    total 48
    drwxr-xr-x  6 dev staff   192 Nov  7 09:12 .
    -rw-r--r--  1 dev staff  2210 Nov  7 09:12 pubspec.yaml
    -rw-r--r--  1 dev staff 14982 Nov  7 09:10 analysis_options.yaml
    """.trimIndent()

val polishPython: String =
    """
    def ratio(positives, negatives):
        return len(positives) / max(1, len(positives) + len(negatives))  # share of passing checks
    """.trimIndent()

/** P-001: long lines run the full width under Copy and Wrap (both 48 dp icon buttons on the first line). */
@Composable
fun PolishCodeScene() {
    Edge("polish-code") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KitCodeBlock(polishListing, KitCodeKind.Output, copyLabel = "Copy", onCopy = {})
            KitCodeBlock(polishPython, KitCodeKind.Code, language = CodeLanguage.Python, copyLabel = "Copy", onCopy = {})
            KitCodeBlock(polishPython, KitCodeKind.Code, language = CodeLanguage.Python, wrap = true, copyLabel = "Copy", onCopy = {})
            KitCodeBlock("print(1)", KitCodeKind.Code, copyLabel = "Copy", onCopy = {})
        }
    }
}

/** P-002: one fence as three rows; the middle one still revealing, the others settled. One box, one style. */
@Composable
fun PolishChunkScene() {
    val registry = androidx.compose.runtime.remember { RevealRegistry() }
    val lines = (1..9).map { "    total += len(positives), len(negatives)  # row $it" }
    Edge("polish-chunks") {
        Column {
            KitCodeChunk(lines.subList(0, 3).joinToString("\n"), continued = false, continues = true, Modifier.padding(horizontal = 16.dp), copyLabel = "Copy", onCopy = {})
            KitReplyBlock(
                lines.subList(3, 6).joinToString("\n"), KitBlockKind.Code, live = false, streamed = false,
                revealKey = "m", revealGroup = "g", registry = registry, motion = KitMotionLevel.Off,
                continued = true, continues = true,
            )
            KitCodeChunk(lines.subList(6, 9).joinToString("\n"), continued = true, continues = false, Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** The app's + tray choices, in its own words (P-004). */
val polishTray: List<KitTrayAction> =
    listOf(
        KitTrayAction("Attach file", KitTrayKind.File) {},
        KitTrayAction("Photo library", KitTrayKind.Photo) {},
        KitTrayAction("Take photo", KitTrayKind.Camera) {},
        KitTrayAction("Paste image from clipboard", KitTrayKind.Clipboard) {},
        KitTrayAction("Reuse a prompt", KitTrayKind.History) {},
        KitTrayAction("Commands", KitTrayKind.Commands) {},
        KitTrayAction("Saved prompts", KitTrayKind.Saved) {},
        KitTrayAction("Send later or repeat", KitTrayKind.Schedule) {},
    )

/** P-004: distinct glyphs, centred labels with 4 dp sides. */
@Composable
fun PolishTrayScene() {
    Edge("polish-tray") { KitComposerTray(polishTray, Modifier.padding(horizontal = 16.dp)) }
}

/** P-011: an opened work line: one 16 dp edge, one icon column, one text edge. */
@Composable
fun PolishWorkScene() {
    Edge("polish-work") {
        KitWorkLine("Read 2 files · ran 1 command", "Done", KitWorkState.Done, expanded = true, onToggle = {}) {
            val steps =
                listOf(
                    KitToolView("s1", KitToolKind.Read, "Read", "lib/main.dart", null, KitToolStatus.Done, "0.4s", null, null, "Done"),
                    KitToolView("s2", KitToolKind.Shell, "Ran", "flutter test", null, KitToolStatus.Done, "3.1s", null, null, "Done"),
                    KitToolView("s3", KitToolKind.Edit, "Edited", "lib/list.dart", null, KitToolStatus.Done, "0.2s", 4, 1, "Done"),
                )
            // E20-S25: the opened work line draws the rail itself; each step only takes its place on it.
            steps.forEach { v -> KitToolRow(v, opened = false, onToggle = {}, body = {}) }
        }
    }
}
