package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.icon.KitStatusIcon
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitMessageTimestamp
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitThoughtBlock
import io.github.eslamasabry.oc.ui.kitchat.transcript.LocalKitTurn
import kotlin.test.Test

/**
 * test/goldens/kit/kit_message_golden_test.dart: every KitMessage state at 412x915 inside a short transcript
 * (`_scene`: 16 dp gutter, 16 dp gaps), and the default (a prompt then a reply) in the 700 dp pane (`_pane`) at
 * 412x915 and 1280x800, at 1.0 and 2.0 text. Frame: `kitGalleryPart` ([FlutterGoldenGate.Frame.Part]).
 *
 * Native has no KitMessage: each kind stands in with the closest kit-chat parts. The transcript parts
 * (KitPromptBubble, the timestamp's quiet line, KitThoughtBlock's expand row) carry the 16 dp column gutter
 * themselves, so the scene adds the gutter only to the parts without one (markdown, notices, markers, chips). The
 * scene provides [LocalKitTurn] so the parts drop their own 4 dp row padding; the scene's 16 dp gaps are the only
 * vertical space, as in Flutter.
 *
 * Mismatch: no native KitMessage (prompt/reply/thought/notice/marker kinds); stand-ins below.
 * Mismatch: prompt time — Flutter puts "2:05 PM" as an end-aligned caption (tertiary) 4 dp under the bubble, 8 dp
 *   from its end; native [KitMessageTimestamp] is a start-aligned quiet line (bodyMedium, onSurfaceVariant).
 * Mismatch: prompt menu (Copy message, Edit and resend) — no native long-press menu on KitPromptBubble (not drawn).
 * Mismatch: prompt attachments — Flutter draws read-only KitComposerChips inside the bubble under the words; native
 *   KitPromptBubble takes only photos, so the chips stand in as a KitChipWrap under the bubble.
 * Mismatch: thought — native KitThoughtBlock has no idea glyph before the title and no working ("Thinking…") mark;
 *   its body is markdown in text2 rather than KitMarkdown(role: secondary).
 * Mismatch: notice — no native KitMessage.notice fold (glyph, words, middle-cut mono technical, tertiary action, open
 *   detail); hand-composed from KitIcon/KitText/KitButton with no fold chevron.
 * Mismatch: marker — no native KitMessage.marker (dividers either side of a centred caption, 75 % cap); hand-composed
 *   from KitDivider/KitIcon/KitStatusIcon/KitText.
 */
class KitMessageGateTest {
    private val prompt = "Fix the flaky checkout test. It fails about one run in five on CI."

    private val reply =
        "The flakiness comes from `CheckoutBloc` racing the price refresh: " +
            "`applyCoupon()` awaits the network before the total settles, so the " +
            "assertion sometimes reads the old total. I made the test wait for the " +
            "settled state."

    private val thought =
        "The test awaits `applyCoupon()` but the total is recomputed on the next " +
            "price refresh. Waiting for `CheckoutSettled` removes the race."

    /** DateTime(2026, 9, 27, 14, 5) in the local zone, as Flutter's fixed prompt time. */
    private val time: Long = // F: test/goldens/kit/kit_message_golden_test.dart:45
        java.time.LocalDateTime.of(2026, 9, 27, 14, 5).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val actions = KitMarkdownActions(onLink = {})

    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        FlutterCapture { KitChatEnvironment(Redactor()).Provide { content() } }
    }

    @Composable private fun Gutter(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() } // F: test/goldens/kit/kit_message_golden_test.dart:68
    }

    /** `_scene`: the parts 16 dp apart, 16 dp above and below. */
    @Composable private fun Scene(parts: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitTurn provides true) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp), // F: test/goldens/kit/kit_message_golden_test.dart:68
                verticalArrangement = Arrangement.spacedBy(16.dp), // F: test/goldens/kit/kit_message_golden_test.dart:74
            ) { parts() }
        }
    }

    /** `_pane`: top centre, at most KitLayout.paneDetailMaxWidth (700 dp). */
    @Composable private fun Pane(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 700.dp)) { content() } // F: test/goldens/kit/kit_message_golden_test.dart:85
        }
    }

    @Composable private fun Markdown(key: String, text: String) {
        Gutter { KitMarkdown(KitMdSource.blocks(key, text, false), actions) }
    }

    /** `_promptMessage`: the prompt with its fixed time (and attachments). */
    @Composable private fun PromptMessage(attachments: Boolean = false) {
        Column(Modifier.fillMaxWidth()) {
            KitPromptBubble(prompt)
            if (attachments) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    KitChipWrap {
                        KitChip("checkout_test.dart", icon = KitIcons.file)
                        KitChip("ci-failure.png", icon = KitIcons.image)
                    }
                }
            }
            Spacer(Modifier.height(4.dp)) // F: lib/ui/kit/chat/kit_message.dart:398 (space1)
            KitMessageTimestamp(createdMs = time, nowMs = time)
        }
    }

    @Composable private fun ReplyMessage() = Markdown("reply", reply)

    @Composable private fun Thought(title: String, expanded: Boolean = false) {
        KitThoughtBlock(key = "thought-$title", title = title, text = thought, live = false, expanded = expanded, onExpandedChange = {}, actions = actions)
    }

    @Composable private fun Notice(
        text: String,
        icon: ImageVector = KitIcons.info,
        failed: Boolean = false,
        technical: String? = null,
        action: String? = null,
        detail: String? = null,
    ) {
        val tone = if (failed) TextTone.Primary else TextTone.Secondary
        Gutter {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitIcon(if (failed) KitIcons.error else icon, size = KitIconSize.Small, tone = TextTone.Secondary)
                    Spacer(Modifier.width(8.dp)) // F: lib/ui/kit/chat/kit_message.dart:1173 (space2)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        KitText(text, role = TypeRole.Secondary, tone = tone)
                        if (technical != null) {
                            Spacer(Modifier.width(8.dp)) // F: lib/ui/kit/chat/kit_message.dart:1145 (space2)
                            KitText(technical, role = TypeRole.Mono, tone = tone, maxLines = 1)
                        }
                    }
                    if (action != null) KitButton(action, {}, role = KitButtonRole.Tertiary)
                }
                if (detail != null) KitText(detail, Modifier.padding(start = 28.dp), role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
    }

    @Composable private fun Marker(text: String, icon: ImageVector? = null, working: Boolean = false) {
        Gutter {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { // F: lib/ui/kit/chat/kit_message.dart:1210 (space2)
                KitDivider(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp)) // F: lib/ui/kit/chat/kit_message.dart:1219 (space2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (working) KitStatusIcon(StatusTone.Progress, size = KitIconSize.Tiny)
                    else if (icon != null) KitIcon(icon, size = KitIconSize.Tiny, tone = TextTone.Secondary)
                    if (working || icon != null) Spacer(Modifier.width(4.dp)) // F: lib/ui/kit/chat/kit_message.dart:1229 (space1)
                    KitText(text, role = TypeRole.Caption, tone = TextTone.Secondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.width(8.dp)) // F: lib/ui/kit/chat/kit_message.dart:1242 (space2)
                KitDivider(Modifier.weight(1f))
            }
        }
    }

    @Composable private fun Default() {
        Pane {
            Scene {
                PromptMessage()
                ReplyMessage()
            }
        }
    }

    @Composable private fun Prompt() = Scene {
        PromptMessage()
        KitPromptBubble("And run it ten times.")
    }

    @Composable private fun PromptAttachments() = Scene { PromptMessage(attachments = true) }

    @Composable private fun PromptLong() = Scene {
        KitPromptBubble(
            "Make the coupon test deterministic, run the whole suite twice " +
                "before you commit, and tell me if anything else is flaky.",
        )
        ReplyMessage()
    }

    @Composable private fun Reply() = Scene { ReplyMessage() }

    @Composable private fun ReplyCode() = Scene {
        Markdown(
            "reply-code",
            "Run this, then the longer one:\n\n" +
                "```sh\nflutter test --concurrency=1 test/checkout_test.dart\n```\n\n" +
                "```dart\nfinal total = await bloc.stream.firstWhere((s) => s.settled && s.total > 0);\nexpect(total, 42);\n```",
        )
    }

    @Composable private fun ThoughtFolded() = Scene {
        Thought("Thinking…")
        Thought("Thought for 12 seconds")
        Thought("Reading the checkout bloc")
        ReplyMessage()
    }

    @Composable private fun ThoughtOpen() = Scene {
        Thought("Thought for 12 seconds", expanded = true)
        ReplyMessage()
    }

    @Composable private fun Notices() = Scene {
        Notice("Context added")
        Notice("Skill loaded", icon = KitIcons.sparkle, technical = "flutter-testing")
        Notice(
            "Conversation compacted",
            icon = KitIcons.archive,
            action = "Compact again",
            detail = "Kept the last 12 messages and a summary of the rest.",
        )
    }

    @Composable private fun NoticesFailed() = Scene {
        Notice("Compaction did not finish", failed = true, action = "Try again")
        Notice("Instructions not loaded", failed = true, technical = "packages/checkout/AGENTS.md")
    }

    @Composable private fun Markers() = Scene {
        ReplyMessage()
        Marker("Switched to Sonnet 4", icon = KitIcons.model)
        Marker("Compacting the conversation", working = true)
        PromptMessage()
    }

    @Test fun kit_message_default() {
        FlutterGoldenGate.check("kit_message_default", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_message_default_1280x800", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_message_default_text2", wrap = wrap) { Default() }
        FlutterGoldenGate.check("kit_message_default_text2_1280x800", wrap = wrap) { Default() }
    }

    @Test fun kit_message_prompts() {
        FlutterGoldenGate.check("kit_message_prompt", wrap = wrap) { Prompt() }
        FlutterGoldenGate.check("kit_message_prompt_attachments", wrap = wrap) { PromptAttachments() }
        FlutterGoldenGate.check("kit_message_prompt_long", wrap = wrap) { PromptLong() }
    }

    @Test fun kit_message_replies() {
        FlutterGoldenGate.check("kit_message_reply", wrap = wrap) { Reply() }
        FlutterGoldenGate.check("kit_message_reply_code", wrap = wrap) { ReplyCode() }
        FlutterGoldenGate.check("kit_message_thought_folded", wrap = wrap) { ThoughtFolded() }
        FlutterGoldenGate.check("kit_message_thought_open", wrap = wrap) { ThoughtOpen() }
    }

    @Test fun kit_message_notices() {
        FlutterGoldenGate.check("kit_message_notice", wrap = wrap) { Notices() }
        FlutterGoldenGate.check("kit_message_notice_failed", wrap = wrap) { NoticesFailed() }
        FlutterGoldenGate.check("kit_message_marker", wrap = wrap) { Markers() }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_message", caps = mapOf("KitMessage" to "conversation"), wrap = wrap) { Default() }
    }
}
