package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.widgetV2Accepted
import io.github.eslamasabry.oc.ui.words.res.widgetV2Busy
import io.github.eslamasabry.oc.ui.words.res.widgetV2Cancel
import io.github.eslamasabry.oc.ui.words.res.widgetV2Dictate
import io.github.eslamasabry.oc.ui.words.res.widgetV2Sending
import org.jetbrains.compose.resources.stringResource

/** What the box is doing right now (Dart `KitSteerBoxPhase`). */
public enum class KitSteerBoxPhase {
    /** Waiting for a message; Send is on once there is text. */
    Ready,

    /** The phone's speech window is open. */
    Dictating,

    /** The message is on its way; the field and Send hold still. */
    Sending,

    /** The message was taken. The box shows a check, then the host closes it. */
    Sent,
}

/** The pure rules of [KitSteerBox] (lib/ui/kit/kit_steer_box.dart), kept apart so they are unit-tested. */
public object KitSteerBoxRules {
    /** The longest message the field takes. */
    public const val MAX_LENGTH: Int = 8000 // F: lib/ui/kit/kit_steer_box.dart:92

    /**
     * The field asks for 2 to 5 lines (kit_steer_box.dart:144-145), but Flutter's multiline KitField starts at
     * min(cap, 3) lines and ignores the asked minimum, so what it draws is 3 to 5.
     */
    public val FIELD_LINES: IntRange = 3..5 // F: lib/ui/kit/kit_field.dart:713 lib/ui/kit/kit_steer_box.dart:145

    /** Ready and not blocked: the field, the microphone and (with words) Send take input. */
    public fun idle(phase: KitSteerBoxPhase, blockedReason: String?): Boolean =
        phase == KitSteerBoxPhase.Ready && blockedReason == null

    /** The field takes typing while idle or dictating. */
    public fun fieldEnabled(phase: KitSteerBoxPhase, blockedReason: String?): Boolean =
        idle(phase, blockedReason) || phase == KitSteerBoxPhase.Dictating

    /** Send runs only when idle and the trimmed text is not empty. */
    public fun canSend(phase: KitSteerBoxPhase, blockedReason: String?, text: String): Boolean =
        idle(phase, blockedReason) && text.trim().isNotEmpty()

    /** Close is off only while the message is on its way. */
    public fun canClose(phase: KitSteerBoxPhase): Boolean = phase != KitSteerBoxPhase.Sending
}

/**
 * The small composer a home-screen widget opens (Dart `KitSteerBox`): a title that says what sending will do ("Add to
 * this turn", "Send after this reply"), one field, a microphone and Send, and one line for how the last try went.
 *
 * The draft is kept in [state] and stays through a failure. [blockedReason] turns the field and Send off and says why
 * in words; [message] is a neutral line under the field, never the server's own text. Send is also off while there is
 * nothing to send.
 *
 * States: disabled (blocked), working (dictating, sending), answered (sent)
 * Text: reading
 * Cap: Conversation (700)
 * Motion: the note reveals and Send cross-fades to its sent look on QUICK; reduced motion = instant
 * Replaces: KitSteerBox (lib/ui/kit/kit_steer_box.dart)
 */
@Composable
public fun KitSteerBox(
    title: String,
    state: TextFieldState,
    hint: String,
    sendLabel: String,
    phase: KitSteerBoxPhase,
    onSend: (String) -> Unit,
    onDictate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    message: String? = null,
    blockedReason: String? = null,
    fieldModifier: Modifier = Modifier,
    micModifier: Modifier = Modifier,
    autofocus: Boolean = true,
) {
    val idle = KitSteerBoxRules.idle(phase, blockedReason)
    val sent = phase == KitSteerBoxPhase.Sent
    val sendingWords = stringResource(Res.string.widgetV2Sending)
    val acceptedWords = stringResource(Res.string.widgetV2Accepted)
    val busyWords = stringResource(Res.string.widgetV2Busy)
    val focus = remember { FocusRequester() }
    Column(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KitText(
                title,
                Modifier.weight(1f),
                role = TypeRole.Headline,
                tone = TextTone.Primary,
                maxLines = 2, // F: lib/ui/kit/kit_steer_box.dart:116
            )
            KitIconButton(
                KitIcons.close,
                stringResource(Res.string.widgetV2Cancel),
                onClose,
                enabled = if (KitSteerBoxRules.canClose(phase)) KitEnabled.Enabled else KitEnabled.Disabled(sendingWords),
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(caption, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        Spacer(Modifier.height(Space.S3.dp))
        KitField(
            state,
            hint,
            modifier = fieldModifier,
            kind = KitFieldKind.Multiline,
            placeholder = hint,
            maxLength = KitSteerBoxRules.MAX_LENGTH,
            multilineLines = KitSteerBoxRules.FIELD_LINES,
            focusRequester = if (autofocus) focus else null,
            enabled =
                if (KitSteerBoxRules.fieldEnabled(phase, blockedReason)) KitEnabled.Enabled
                else KitEnabled.Disabled(blockedReason ?: if (sent) acceptedWords else sendingWords),
        )
        KitReveal(visible = message != null) {
            message?.let {
                KitNotice(it, Modifier.padding(top = Space.S3.dp), tone = StatusTone.Neutral, icon = KitIcons.info)
            }
        }
        Spacer(Modifier.height(Space.S3.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KitIconButton(
                KitIcons.mic,
                stringResource(Res.string.widgetV2Dictate),
                onDictate,
                modifier = micModifier,
                enabled = if (idle) KitEnabled.Enabled else KitEnabled.Disabled(blockedReason ?: busyWords),
                working = phase == KitSteerBoxPhase.Dictating,
            )
            Spacer(Modifier.width(Space.S3.dp))
            val text = state.text.toString()
            val send = KitSteerBoxRules.canSend(phase, blockedReason, text)
            Crossfade(sent, Modifier.weight(1f), animationSpec = KitMotion.specs.quick(), label = "steer-send") { done ->
                KitButton(
                    label = if (done) acceptedWords else if (phase == KitSteerBoxPhase.Sending) sendingWords else sendLabel,
                    onClick = { if (send) onSend(text.trim()) },
                    icon = if (done) KitIcons.check else KitIcons.send,
                    enabled = if (send) KitEnabled.Enabled else KitEnabled.Disabled(blockedReason ?: if (done) acceptedWords else hint),
                    working = phase == KitSteerBoxPhase.Sending,
                    modifier = Modifier.fillMaxWidth(),
                    fullWidth = true,
                )
            }
        }
    }
    if (autofocus) {
        LaunchedEffect(focus) {
            try {
                focus.requestFocus()
            } catch (_: IllegalStateException) {
                // Not attached yet (a desktop render); focus is a convenience, the field still works.
            }
        }
    }
}
