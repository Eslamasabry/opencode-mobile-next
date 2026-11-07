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
 * test/goldens/kit/kit_transcript_excerpt_golden_test.dart: four remembered messages in a 600 dp box, newest at the
 * bottom, with "Updated 12m ago · Refreshing" under the newest, at 412x915 and 1280x800 and at 2.0 text (dark only).
 *
 * Mismatch: native has no `KitTranscriptExcerpt`; the case stands in with the transcript's own parts (KitPromptBubble,
 * KitMarkdown) laid out as kit_transcript_excerpt.dart:84-140 lays them out (gutter, space2 top and bottom, space4 after
 * a prompt, sectionGap after a reply, newest at the bottom, the caption one step under it).
 */
class KitTranscriptExcerptGateTest {

    private class Excerpt(val text: String, val fromPerson: Boolean)

    private val messages = listOf( // F: test/goldens/kit/kit_transcript_excerpt_golden_test.dart:15-29
        Excerpt("Fix the flaky checkout test", fromPerson = true),
        Excerpt(
            "The checkout test waited on a timer that the payment stub never fired. It now waits for the " +
                "**order confirmed** event instead, and the suite passed ten runs in a row.",
            fromPerson = false,
        ),
        Excerpt("Run the full suite once more", fromPerson = true),
        Excerpt("All 412 tests passed in 3 minutes 20 seconds.", fromPerson = false),
    )

    private val actions = KitMarkdownActions(onLink = {})

    private val excerpt: @Composable () -> Unit = {
        Box(Modifier.height(600.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { // F: test/goldens/kit/kit_transcript_excerpt_golden_test.dart:33
            Column(
                Modifier.widthIn(max = (700 + 2 * 16).dp) // F: lib/ui/kit/chat/kit_transcript_excerpt.dart:93
                    .padding(horizontal = 16.dp, vertical = 8.dp), // F: lib/ui/kit/chat/kit_transcript_excerpt.dart:99-104
            ) {
                messages.forEachIndexed { i, m ->
                    if (m.fromPerson) KitPromptBubble(m.text)
                    else KitMarkdown(KitMdSource.blocks("excerpt-$i", m.text, false), actions)
                    if (i < messages.lastIndex) {
                        Spacer(Modifier.height(if (m.fromPerson) 16.dp else 32.dp)) // F: lib/ui/kit/chat/kit_transcript_excerpt.dart:125-129
                    }
                }
                Spacer(Modifier.height(4.dp)) // F: lib/ui/kit/chat/kit_transcript_excerpt.dart:111
                KitText("Updated 12m ago · Refreshing", role = TypeRole.Caption, tone = TextTone.Tertiary)
            }
        }
    }

    @Test fun transcript_excerpt_loading() {
        FlutterGoldenGate.check("kit_transcript_excerpt_loading", content = excerpt)
        FlutterGoldenGate.check("kit_transcript_excerpt_loading_1280x800", content = excerpt)
        FlutterGoldenGate.check("kit_transcript_excerpt_loading_text2", modes = listOf("dark"), content = excerpt)
        FlutterGoldenGate.check("kit_transcript_excerpt_loading_text2_1280x800", modes = listOf("dark"), content = excerpt)
    }

    @Test fun transcript_excerpt_matrix() {
        FlutterGoldenGate.matrix("part-kit_transcript_excerpt", caps = mapOf("KitTranscriptExcerpt" to "conversation"), content = excerpt)
    }
}
