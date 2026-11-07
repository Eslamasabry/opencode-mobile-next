package io.github.eslamasabry.oc.ui.kitchat.rb.o16

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStop
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopState
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import kotlin.test.Test

/**
 * The 'table' scene of test/goldens/kit/kit_markdown_golden_test.dart (golden `kit_markdown_table`): `_table` on the
 * markdown gallery's ground (scrolled, gutter all round, top start, at most 700 dp), at 412x915 and 1280x800.
 */
class KitMarkdownTableGateTest {
    private val actions = KitMarkdownActions(onLink = {})


    private val table = """
        | Server | Address | Protocol | Status | Last seen | Sessions |
        | --- | --- | --- | :---: | --- | ---: |
        | Phone | `127.0.0.1:4096` | OpenCode 1 | online | just now | 12 |
        | Workstation | `100.64.0.4:4097` | OpenCode 2 | online | 2 min ago | 48 |
        | Build box | `100.64.0.9:4097` | OpenCode 2 | offline | yesterday | 3 |
    """.trimIndent() + "\n" // F: test/goldens/kit/kit_markdown_golden_test.dart:62-68

    /** The markdown gallery's `_onGround` (kit_markdown_golden_test.dart:23-39): scrolled, gutter all round, top start, at most 700 dp. */
    private val tableScene: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { // F: test/goldens/kit/kit_markdown_golden_test.dart:28-29
            Box(Modifier.widthIn(max = 700.dp).align(Alignment.TopStart)) { // F: test/goldens/kit/kit_markdown_golden_test.dart:31-34
                KitMarkdown(KitMdSource.blocks("table", table, false), actions)
            }
        }
    }

    @Test fun markdown_table() {
        FlutterGoldenGate.check("kit_markdown_table", frame = FlutterGoldenGate.Frame.Screen, content = tableScene)
        FlutterGoldenGate.check("kit_markdown_table_1280x800", frame = FlutterGoldenGate.Frame.Screen, content = tableScene)
    }
}
