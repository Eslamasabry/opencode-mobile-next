package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitComposerModelChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStopRing
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTextBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitLevelMeter
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlin.test.Test

/**
 * test/goldens/kit/kit_composer_golden_test.dart: every state of KitComposer over `_Backdrop` (a transcript under the
 * pill), 412x915 and the default at 1280x800, light and dark, and busy_text at 2.0 text. The glow_* shots are not in
 * the order-14 manifest and are not reproduced here.
 *
 * Frame: `_Backdrop` is a `SizedBox(height: window - 32)` inside kitGalleryPart (16 dp scroll padding, centred, max
 * 720), so the backdrop is drawn here in [FlutterGoldenGate.Frame.Screen] with that padding and cap (GOLDEN-STANDARD
 * §14.3). Native has no KitComposer: the scene is assembled from [KitComposerTextBox] / [KitComposerBox] and the
 * composer's own controls, following Flutter `_textContent` / `_VoiceContent` / `_FailureRow`.
 *
 * Mismatch: no native KitComposer; the note line, the failure row, the editor button and the voice content are
 * arranged here from kit parts, as Flutter's `kit_composer.dart` arranges them.
 * Mismatch: suggestions use a KitRowGroup of KitRows (no native KitComposerChips.suggestions panel); command labels
 * are not mono and the panel surface is KitRowGroup's.
 * Mismatch: the attachment chip is a removable KitChip (no native attachment chip): no "2.1 MB" detail line.
 * Mismatch: voice extras (Read replies aloud, Allow microphone) sit in KitComposerBox's `above` slot (the box has no
 * slot under its row; Flutter draws them under the row); the phase words sit in a Row, not a Wrap.
 * Mismatch: the speaking phase's Stop circle is [KitComposerStopRing] (ring around the disc); Flutter's `_Circle.stop`
 * has no ring.
 * Mismatch: the failure row's Retry and Details targets are KitTappable (48 dp minimum), Flutter's are 40 / 32 x 40.
 */
class KitComposerGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit =
        { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { FlutterCapture(c) } }

    private val draft = "Also run the unit tests for the signing config" // F: test/goldens/kit/kit_composer_golden_test.dart:162

    /** One attachment: label, glyph, removable. */
    private class Attachment(val label: String, val detail: String?)

    /** Flutter `_composer` inputs (kit_composer_golden_test.dart:128-160). */
    private class Inputs(
        val text: String = "",
        val busy: Boolean = false,
        val sending: Boolean = false,
        val canSendWhileBusy: Boolean = false,
        val addToTurn: Boolean = false,
        val offline: Boolean = false,
        val readOnlyReason: String? = null,
        val attachments: Boolean = false,
        val suggestions: Boolean = false,
        val failure: Boolean = false,
    )

    // ------------------------------------------------------------------------------------------- backdrop

    /** Flutter `_Backdrop` (kit_composer_golden_test.dart:31-100) inside kitGalleryPart's padding and cap. */
    @Composable
    private fun Backdrop(composer: @Composable () -> Unit) {
        val tokens = LocalKitTokens.current
        Box(Modifier.fillMaxSize().padding(vertical = 16.dp), contentAlignment = Alignment.TopCenter) { // F: test/goldens/kit/kit_gallery.dart:766
            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight()) { // F: test/goldens/kit/kit_gallery.dart:769
                Column(
                    Modifier.fillMaxWidth().padding(tokens.gutter.dp), // F: test/goldens/kit/kit_composer_golden_test.dart:53
                    verticalArrangement = Arrangement.spacedBy(Space.S3.dp), // F: test/goldens/kit/kit_composer_golden_test.dart:56
                ) {
                    KitText("Can you check why the release build fails on the signing step?", role = TypeRole.Body)
                    KitText(
                        "The release config reads android/key.properties, which is missing here, so validateSigningRelease " +
                            "stops the build before packaging. The Kotlin and Dart sides compile.",
                        role = TypeRole.Body, tone = TextTone.Secondary,
                    )
                    KitText(
                        "I will look at the Gradle task graph next and confirm the debug variant is not affected.",
                        role = TypeRole.Body, tone = TextTone.Secondary,
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, bottom = Space.S2.dp) // F: test/goldens/kit/kit_composer_golden_test.dart:83-85
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.widthIn(max = Layout.PANE_DETAIL_MAX_WIDTH.dp)) { composer() } // F: test/goldens/kit/kit_composer_golden_test.dart:89-91
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------- text content

    @Composable
    private fun Note(words: String) {
        Box(Modifier.padding(start = Space.S3.dp, end = Space.S3.dp, top = Space.S2.dp)) { // F: lib/ui/kit/chat/kit_composer.dart:663-667
            KitText(words, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }

    /** Flutter `_FailureRow` (kit_composer_caption.dart:7-72): "Didn't send", "· Try again", Details. */
    @Composable
    private fun FailureRow() {
        val tokens = LocalKitTokens.current
        Row(
            Modifier.fillMaxWidth().padding(start = Space.S3.dp, end = Space.S2.dp, top = Space.S1.dp), // F: lib/ui/kit/chat/kit_composer_caption.dart:22-26
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIcon(KitIcons.error, size = KitIconSize.Small, tone = TextTone.Primary)
            Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/chat/kit_composer_caption.dart:35
            KitText("Didn't send", Modifier.weight(1f, fill = false), role = TypeRole.Caption, tone = TextTone.Primary, maxLines = 1)
            KitTappable(onClick = {}, modifier = Modifier.semantics { contentDescription = "Try again" }) {
                Box(Modifier.height((tokens.minTarget - 8).dp).padding(horizontal = Space.S2.dp), contentAlignment = Alignment.Center) { // F: lib/ui/kit/chat/kit_composer_caption.dart:50-53
                    KitText("· Try again", role = TypeRole.Caption, tone = TextTone.Primary, maxLines = 1)
                }
            }
            KitTappable(onClick = {}, modifier = Modifier.semantics { contentDescription = "Details" }) {
                Box(Modifier.size(width = (tokens.minTarget - 16).dp, height = (tokens.minTarget - 8).dp), contentAlignment = Alignment.Center) { // F: lib/ui/kit/chat/kit_composer_caption.dart:66-67
                    KitIcon(KitIcons.info, size = KitIconSize.Small)
                }
            }
        }
    }

    /** Flutter `_attachments()` (kit_composer_golden_test.dart:115-126): build-log.txt, 2.1 MB, removable. */
    @Composable
    private fun Attachments() {
        val items = listOf(Attachment("build-log.txt", "2.1 MB"))
        Box(Modifier.padding(start = Space.S2.dp, end = Space.S2.dp, top = Space.S2.dp)) { // F: lib/ui/kit/chat/kit_composer.dart:691-695
            KitChipWrap {
                items.forEach {
                    KitChip(it.label, kind = KitChipKind.Removable, icon = KitIcons.file, removeLabel = "Remove ${it.label}", onClick = {}, onRemove = {})
                }
            }
        }
    }

    /** Flutter's `suggestions` state (kit_composer_golden_test.dart:186-207): /compact and /commit. */
    @Composable
    private fun Suggestions() {
        Box(Modifier.padding(start = Space.S2.dp, end = Space.S2.dp, top = Space.S2.dp)) { // F: lib/ui/kit/chat/kit_composer.dart:682-686
            KitRowGroup {
                KitRow("/compact", supporting = "Summarise the conversation so far", onClick = {})
                KitRow("/commit", supporting = "Commit the staged changes", onClick = {})
            }
        }
    }

    /** The field, with the full-screen editor's button when the words run past one line (kit_composer.dart:611-652). */
    @Composable
    private fun FieldWithEditor(state: TextFieldState, canEdit: Boolean, readOnlyReason: String?) {
        val tokens = LocalKitTokens.current
        val style = LocalKitTypography.current.style(TypeRole.Body)
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val text = state.text.toString()
            val editor = canEdit && (text.contains('\n') ||
                measurer.measure(text, style, maxLines = 1, constraints = Constraints(maxWidth = constraints.maxWidth)).hasVisualOverflow)
            KitField(
                state = state,
                label = "Message",
                modifier = Modifier.fillMaxWidth().padding(end = if (editor) tokens.minTarget.dp else 0.dp),
                kind = KitFieldKind.Multiline,
                placeholder = "Ask OpenCode…",
                enabled = if (readOnlyReason == null) KitEnabled.Enabled else KitEnabled.Disabled(readOnlyReason),
                labelShown = false,
                framed = false,
                composer = true,
                multilineLines = 1..8, // F: lib/ui/kit/chat/kit_composer.dart:590
            )
            if (editor) KitIconButton(KitIcons.expand, "Open full-screen editor", {}, Modifier.align(Alignment.TopEnd))
        }
    }

    /** Flutter `_trailingControl` (kit_composer.dart:419-433, 819-880). */
    @Composable
    private fun RowScope.Trailing(i: Inputs) {
        val hasContent = i.text.isNotEmpty()
        when {
            i.readOnlyReason != null -> Unit
            i.sending -> KitComposerSend("Sending", {}, working = true)
            i.busy && !hasContent -> KitComposerMic("Talk instead of typing", {})
            i.busy -> if (i.canSendWhileBusy) KitComposerSend("Add to this turn", {}) else Unit // the scenes busy-send only with addToThisTurn
            hasContent -> KitComposerSend(if (i.offline) "Send when back online" else "Send", {})
            else -> KitComposerMic("Talk instead of typing", {})
        }
    }

    /** Flutter `_noteLine` (kit_composer.dart:471-492). */
    private fun noteOf(i: Inputs): String? {
        if (i.readOnlyReason != null) return i.readOnlyReason
        val has = i.text.isNotEmpty()
        val parts = buildList {
            if (i.offline) add("Sends when OpenCode answers")
            if (i.busy && has && !i.canSendWhileBusy) add("You can send when this reply finishes")
            if (i.busy && has && i.canSendWhileBusy && i.addToTurn) add("Adds to this reply")
        }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    @Composable
    private fun Composer(i: Inputs, wide: Boolean = false) {
        val state = remember(i.text) { TextFieldState(i.text) }
        val readOnly = i.readOnlyReason != null
        val note = noteOf(i)
        KitComposerTextBox(
            text = { i.text },
            running = i.busy,
            hasLeading = !readOnly,
            wide = wide,
            allowStacking = !readOnly,
            above = {
                Column(Modifier.fillMaxWidth()) {
                    if (i.failure) FailureRow()
                    if (note != null) Note(note)
                    if (!readOnly && i.suggestions) Suggestions()
                    if (!readOnly && i.attachments) Attachments()
                }
            },
            field = { FieldWithEditor(state, canEdit = !readOnly && i.text.isNotEmpty(), readOnlyReason = i.readOnlyReason) },
            leading = { if (!readOnly) KitComposerAdd("Attach and more", {}) },
            trailing = { Trailing(i) },
        )
    }

    // ------------------------------------------------------------------------------------------- voice

    private enum class Phase { Listening, Speaking, MicDenied }

    /** Flutter `_VoiceContent` (kit_composer_layer.dart:76-222) in the composer's face. */
    @Composable
    private fun Voice(phase: Phase) {
        val words = when (phase) {
            Phase.Listening -> "Listening…"
            Phase.Speaking -> "Reading the reply aloud"
            Phase.MicDenied -> "The microphone is off for this app"
        }
        val reason = if (phase == Phase.MicDenied) "Android keeps the microphone off for this app." else null
        KitComposerBox(
            running = false,
            stacked = false,
            hasLeading = true,
            above = {
                Box(Modifier.padding(start = Space.S2.dp, end = Space.S2.dp, bottom = Space.S1.dp)) { // F: lib/ui/kit/chat/kit_composer_layer.dart:210-214
                    KitChipWrap {
                        if (phase == Phase.MicDenied) KitButton("Allow microphone", {}, role = KitButtonRole.Tertiary, expand = false)
                        else KitChip("Read replies aloud", kind = KitChipKind.Action, selected = true, onClick = {})
                    }
                }
            },
            leading = {
                KitIconButton(KitIcons.close, "Leave voice mode", {})
                Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/chat/kit_composer_layer.dart:203
            },
            field = {
                Box(Modifier.fillMaxWidth().heightIn(min = LocalKitTokens.current.minTarget.dp), contentAlignment = Alignment.CenterStart) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) { // F: lib/ui/kit/chat/kit_composer_layer.dart:150-151
                            if (phase == Phase.Listening) KitLevelMeter({ 0.55f }) // F: test/goldens/kit/kit_composer_golden_test.dart:214
                            KitText(words, role = TypeRole.Body, tone = TextTone.Primary)
                        }
                        if (reason != null) KitText(reason, role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
            },
            trailing = {
                when (phase) {
                    Phase.Listening -> KitComposerSend("Send", {})
                    Phase.Speaking -> KitComposerStopRing("Stop reading", {})
                    Phase.MicDenied -> Unit
                }
            },
        )
    }

    // ------------------------------------------------------------------------------------------- shots

    private fun shot(name: String, content: @Composable () -> Unit) =
        FlutterGoldenGate.check(name, FlutterGoldenGate.Frame.Screen, wrap = wrap) { Backdrop(content) }

    @Test fun idle_and_busy() {
        shot("kit_composer_idle_empty") { Composer(Inputs()) }
        shot("kit_composer_idle_text") { Composer(Inputs(text = draft, attachments = true)) }
        shot("kit_composer_sending") { Composer(Inputs(text = draft, sending = true)) }
        shot("kit_composer_busy_empty") { Composer(Inputs(busy = true)) }
        shot("kit_composer_busy_text") { Composer(Inputs(text = draft, busy = true, canSendWhileBusy = true, addToTurn = true)) }
        shot("kit_composer_busy_text_text2") { Composer(Inputs(text = draft, busy = true, canSendWhileBusy = true, addToTurn = true)) }
        shot("kit_composer_busy_cannot_send") { Composer(Inputs(text = draft, busy = true)) }
    }

    @Test fun conditions() {
        shot("kit_composer_offline") { Composer(Inputs(text = draft, offline = true)) }
        shot("kit_composer_read_only") { Composer(Inputs(readOnlyReason = "This conversation is archived. Restore it to write.")) }
        shot("kit_composer_suggestions") { Composer(Inputs(text = "/co", suggestions = true)) }
        shot("kit_composer_failed_send") { Composer(Inputs(text = draft, failure = true)) }
        shot("kit_composer_default_1280x800") { Composer(Inputs(text = draft), wide = true) }
    }

    @Test fun model_above() {
        shot("kit_composer_model_above") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { // F: test/goldens/kit/kit_composer_golden_test.dart:267
                KitComposerStatusStrip(hasChips = true, model = { KitComposerModelChip("build · Sonnet 4.5 · High", {}) }) {
                    KitChip("Auto-approving", icon = KitIcons.sparkle)
                }
                Composer(Inputs())
            }
        }
    }

    @Test fun voice() {
        shot("kit_composer_voice_listening") { Voice(Phase.Listening) }
        shot("kit_composer_voice_speaking") { Voice(Phase.Speaking) }
        shot("kit_composer_voice_mic_denied") { Voice(Phase.MicDenied) }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_composer",
            caps = mapOf("KitComposer" to "conversation"),
            wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } },
        ) { Composer(Inputs(text = draft)) }
    }
}
