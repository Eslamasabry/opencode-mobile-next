package io.github.eslamasabry.oc.ui.kit.input

import io.github.eslamasabry.oc.ui.kit.effects.kitWordsPlate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow

/** What a [KitAskField] is doing (Dart `KitAskFieldPhase`). */
public enum class KitAskFieldPhase {
    /** Waiting for words: the mic while empty (where there is voice), Send once there is text. */
    Ready,

    /** Dictation is recording; the round button finishes it. */
    Listening,

    /** The recording, or the request, is on its way: the field and the button hold still. */
    Working,
}

/** The tone of the one line above a [KitAskField] (Dart `KitAskFieldNoteTone`): never red, never amber. */
public enum class KitAskFieldNoteTone {
    /** It went where it was meant to (a check). */
    Done,

    /** It did not go; the words are still in the field (an info glyph). */
    NotDone,
}

/** The round button's choice (kit_ask_field.dart:368-413), pure so it is tested without a screen. */
public enum class KitAskFieldButton { Stop, Mic, Send }

/** The rules of [KitAskField] that are not drawing. */
public object KitAskFieldRules {
    /** Which round button shows: finish listening, the mic while empty and there is voice, else Send. */
    public fun button(phase: KitAskFieldPhase, hasText: Boolean, hasVoice: Boolean): KitAskFieldButton =
        when {
            phase == KitAskFieldPhase.Listening -> KitAskFieldButton.Stop
            phase != KitAskFieldPhase.Working && !hasText && hasVoice -> KitAskFieldButton.Mic
            else -> KitAskFieldButton.Send
        }

    /** Whether the round button can be pressed (kit_ask_field.dart:377-380). */
    public fun live(phase: KitAskFieldPhase, hasText: Boolean, hasVoice: Boolean, enabled: Boolean): Boolean {
        val listening = phase == KitAskFieldPhase.Listening
        val working = phase == KitAskFieldPhase.Working
        val mic = button(phase, hasText, hasVoice) == KitAskFieldButton.Mic
        return enabled && !working && (listening || mic || (hasText && !listening))
    }

    /** Send goes only from Ready with words in the field (kit_ask_field.dart:160-164). */
    public fun sends(phase: KitAskFieldPhase, text: String, enabled: Boolean): Boolean =
        enabled && phase == KitAskFieldPhase.Ready && text.isNotBlank()
}

/**
 * One glowing field that asks for something and sends it somewhere else: the in-app preview's "Ask for a change…"
 * (Dart `KitAskField`). A solid pill on surface1 with a still accent edge and a soft accent halo; inside, the field
 * and one round accent button at the end: the mic while empty ([onVoice]; without voice it is Send, off until there
 * are words), Send once there is text, finish while listening, progress while working. [note] is one quiet line above
 * the pill that fades in and out over QUICK (at once under reduced motion). It is not the chat composer. The words
 * stay in [state] until the host clears them after a send it accepted.
 *
 * States: empty, disabled, working, answered. Cap: conversation (700).
 */
@Composable
public fun KitAskField(
    state: TextFieldState,
    hint: String,
    sendLabel: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    phase: KitAskFieldPhase = KitAskFieldPhase.Ready,
    onVoice: (() -> Unit)? = null,
    voiceLabel: String? = null,
    listeningHint: String? = null,
    stopListeningLabel: String? = null,
    note: String? = null,
    noteTone: KitAskFieldNoteTone = KitAskFieldNoteTone.Done,
    enabled: Boolean = true,
    prominent: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val styleState = LocalKitStyle.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val listening = phase == KitAskFieldPhase.Listening
    val working = phase == KitAskFieldPhase.Working
    val lit = focused || listening
    val glow = if (prominent || lit) 1f else 0.5f // F: lib/ui/kit/kit_ask_field.dart:181
    val shownHint = if (listening) listeningHint ?: hint else hint
    // Large text keeps the field to two lines, so what it is about stays in view above it.
    val large = LocalDensity.current.fontScale >= 1.3f // F: lib/ui/kit/kit_ask_field.dart:187
    val accent = colors.accent
    val solid = styleState.style == Style.Solid
    // A style that asks for the field halo itself (Soft) keeps it plainly lit at rest (kit_ask_field.dart:205-208).
    val styleHalo = styleState.spec.tokens.fieldHalo == true
    val wide = if (styleHalo && !solid) 0.5f else 0.32f // F: lib/ui/kit/kit_ask_field.dart:201,207
    val near = if (styleHalo && !solid) 0.36f else 0.22f // F: lib/ui/kit/kit_ask_field.dart:201,207
    val showsHalo = solid || styleState.spec.tokens.fieldHaloShown
    val radius = tokens.composerRadius
    // Every style but Solid draws the style's field as a pane of the composer's radius, whose corner grows or scales with
    // the style (Glass's pane corner, the Corners knob); its halo and focus ring follow that one shape, never a fixed
    // one of their own (owner 2026-11-13: an outline on another radius is cut at the corners or stands off the face).
    val fieldLook =
        if (solid) null
        else kitSurfaceLook(KitSurfaceInput(SurfaceRole.Field, KitShapeId.Card, ownFill = colors.surface1, selected = lit, radius = radius.dp))
    val shape = fieldLook?.shape ?: RoundedCornerShape(radius.dp)
    val halo =
        if (!showsHalo) Modifier
        else
            Modifier
                .dropShadow(
                    shape,
                    ComposeShadow(
                        radius = (if (lit) 30 else 26).dp, // F: lib/ui/kit/kit_ask_field.dart:194
                        color = accent.copy(alpha = (if (lit) 0.4f else wide) * glow), // F: lib/ui/kit/kit_ask_field.dart:193
                    ),
                )
                .dropShadow(
                    shape,
                    ComposeShadow(
                        radius = 6.dp, // F: lib/ui/kit/kit_ask_field.dart:198
                        color = accent.copy(alpha = (if (lit) 0.3f else near) * glow), // F: lib/ui/kit/kit_ask_field.dart:197
                    ),
                )
    // Solid's glowing pill is the field's own look; every other style makes it that style's field, lit while it has
    // focus or listens (kit_ask_field.dart:218-226).
    val edgeAlpha by animateColorAsState(
        accent.copy(alpha = (if (lit) 1f else 0.85f) * glow), // F: lib/ui/kit/kit_ask_field.dart:213
        KitMotion.specs.quick(),
        label = "ask-edge",
    )
    val face =
        if (fieldLook == null) Modifier.clip(shape).background(colors.surface1).border(kitFocusRingWidth(), edgeAlpha, shape)
        else
            fieldLook.modifier
                .then(if (focused) Modifier.border(kitFocusRingWidth(), accent, shape) else Modifier)
    val body = typography.style(TypeRole.Body)
    val fade = KitMotion.specs.quick<Float>()
    Column(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
        KitReveal(visible = note != null) {
            AnimatedContent(
                note.orEmpty(),
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "ask-note",
            ) { words ->
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = Space.S5.dp, end = Space.S5.dp, bottom = Space.S3.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val done = noteTone == KitAskFieldNoteTone.Done
                    KitIcon(if (done) KitIcons.check else KitIcons.info, size = KitIconSize.Small, tone = if (done) TextTone.Accent else TextTone.Secondary)
                    Spacer(Modifier.width(Space.S2.dp))
                    BasicText(
                        words,
                        Modifier.weight(1f),
                        style = typography.style(TypeRole.Caption).copy(color = colors.text2),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .then(halo)
                .then(face)
                .heightIn(min = (tokens.minTarget + Space.S3).dp)
                .padding(start = Space.S5.dp, end = Space.S1.dp, top = Space.S1.dp, bottom = Space.S1.dp),
            // The round button stays at the end of the last line as the words grow.
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier.weight(1f).heightIn(min = tokens.minTarget.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    state = state,
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = tokens.minTarget.dp)
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .semantics { contentDescription = hint },
                    enabled = enabled && !working && !listening,
                    textStyle = body.copy(color = colors.text1),
                    cursorBrush = SolidColor(accent),
                    interactionSource = interaction,
                    lineLimits = TextFieldLineLimits.MultiLine(1, if (large) 2 else 4), // F: lib/ui/kit/kit_ask_field.dart:275,276
                    onKeyboardAction = { if (KitAskFieldRules.sends(phase, state.text.toString(), enabled)) onSend() },
                    decorator = { field ->
                        // The field is a full touch target tall, its words centred (kit_ask_field.dart:264-266).
                        Box(
                            // On Glass's liquid message box the words (hint and text) lie on their plate.
                            Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(vertical = Space.S2.dp)
                                .kitWordsPlate(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.text.isEmpty()) {
                                BasicText(
                                    shownHint,
                                    Modifier.clearAndSetSemantics {},
                                    style = body.copy(color = colors.text2),
                                    maxLines = if (large) 2 else 1, // F: lib/ui/kit/kit_ask_field.dart:297
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            field()
                        }
                    },
                )
            }
            Spacer(Modifier.width(Space.S2.dp))
            AskButton(
                phase = phase,
                hasText = state.text.isNotBlank(),
                onVoice = onVoice,
                onSend = { if (KitAskFieldRules.sends(phase, state.text.toString(), enabled)) onSend() },
                label = when (KitAskFieldRules.button(phase, state.text.isNotBlank(), onVoice != null)) {
                    KitAskFieldButton.Stop -> stopListeningLabel ?: sendLabel
                    KitAskFieldButton.Mic -> voiceLabel ?: sendLabel
                    KitAskFieldButton.Send -> sendLabel
                },
                hint = hint,
                enabled = enabled,
                prominent = prominent,
            )
        }
    }
}

/** The round button at the field's end (kit_ask_field.dart:368-479). */
@Composable
private fun AskButton(
    phase: KitAskFieldPhase,
    hasText: Boolean,
    onVoice: (() -> Unit)?,
    onSend: () -> Unit,
    label: String,
    hint: String,
    enabled: Boolean,
    prominent: Boolean,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val still = KitMotion.state.reduced
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val working = phase == KitAskFieldPhase.Working
    val fade = KitMotion.specs.quick<Float>()
    val kind = KitAskFieldRules.button(phase, hasText, onVoice != null)
    val live = KitAskFieldRules.live(phase, hasText, onVoice != null, enabled)
    val solid = (live || working) && prominent
    val fill by animateColorAsState(if (solid) colors.accent else colors.surface3, KitMotion.specs.quick(), label = "ask-fill")
    val ink = if (solid) TextTone.OnAccent else if (live) TextTone.Primary else TextTone.Tertiary
    val onTap: (() -> Unit)? =
        when (kind) {
            KitAskFieldButton.Stop -> onVoice
            KitAskFieldButton.Mic -> if (enabled) onVoice else null
            KitAskFieldButton.Send -> if (live) onSend else null
        }
    KitTappable(
        onClick = { onTap?.invoke() },
        modifier = Modifier.semantics { contentDescription = label },
        enabled = onTap != null,
        disabledReason = if (onTap == null) hint else null,
        shape = KitShapeId.Circle,
    ) {
        Box(
            Modifier.size(tokens.minTarget.dp).clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                if (working) null else kind,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "ask-glyph",
            ) { shown ->
                when (shown) {
                    null ->
                        if (still) KitIcon(KitIcons.statusDot, size = KitIconSize.Small, tone = ink)
                        else KitWorkingMark(colors.tone(ink), KitIconSize.Small.dp.dp)
                    KitAskFieldButton.Stop -> KitIcon(KitIcons.check, size = KitIconSize.Medium, tone = ink)
                    KitAskFieldButton.Mic -> KitIcon(KitIcons.mic, size = KitIconSize.Medium, tone = ink)
                    // Send points the way the words go; the mic and the check do not.
                    KitAskFieldButton.Send ->
                        KitIcon(KitIcons.send, if (rtl) Modifier.scale(-1f, 1f) else Modifier, size = KitIconSize.Medium, tone = ink)
                }
            }
        }
    }
}

