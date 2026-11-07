package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kitchat.tool.KitMonoLtr

/**
 * The words and the callback of the inline "Tell it what to do instead" reply that Deny opens.
 * [onSend] gets the typed note (never blank); [justDeny] words the choice that denies with no note.
 */
public class KitDenyReply(
    public val fieldLabel: String,
    public val sendLabel: String,
    public val justDenyLabel: String,
    public val backLabel: String,
    public val emptyReason: String,
    public val onSend: (String) -> Unit,
)

/** Plan 09 R13 / plan 06 §8: the card height share grows at very large text. */
private const val HEIGHT_SHARE = 0.45f
private const val HEIGHT_SHARE_LARGE_TEXT = 0.60f
private const val LARGE_TEXT = 2.0f

/**
 * The permission card's shell (plan 09 §5.1–§5.2, plan 06 §7.8 `KitRequestCard` decide form): the
 * engine words the ask and decides what happens; this card only draws it. The needs-you look
 * (attention surface and line), the [title], an optional [summary], the [subject] (the command or
 * the paths) in mono reading left to right and redacted, then the answers as visible buttons (R9):
 * Allow once is the primary answer (secondary among list rows), Deny is never drawn in the primary colour, and Always allow
 * shows only when [alwaysLabel] and [onAlways] are given.
 *
 * The card is at most 700 dp wide and 45 % of the window tall (60 % at 2× text and above); longer
 * words scroll inside it while the answers stay in sight. While it cannot answer ([enabled] false,
 * e.g. an answer is being sent) the buttons are disabled and say why ([disabledReason], required
 * then).
 *
 * Text: title and summary reading (full size); buttons frame (≤ 1.3×); subject mono, left to right.
 */
@Composable
public fun KitPermissionCard(
    title: String,
    summary: String?,
    subject: String?,
    allowOnceLabel: String,
    alwaysLabel: String?,
    denyLabel: String,
    onAllowOnce: () -> Unit,
    onAlways: (() -> Unit)?,
    onDeny: () -> Unit,
    enabled: Boolean = true,
    disabledReason: String? = null,
    modifier: Modifier = Modifier,
    targets: List<KitRequestTarget> = emptyList(),
    secondary: Boolean = false,
    toolLabel: String? = null,
    copyLabel: String? = null,
    copiedLabel: String? = null,
    denyReply: KitDenyReply? = null,
) {
    require(enabled || disabledReason != null) {
        "KitPermissionCard: a card that cannot answer must say why"
    }
    val colors = LocalKitColors.current
    val reason = if (enabled) null else disabledReason
    KitRequestFrame(
        modifier = modifier,
        answers = {
            Answers(
                allowOnceLabel,
                alwaysLabel?.takeIf { onAlways != null },
                denyLabel,
                onAllowOnce,
                onAlways,
                onDeny,
                enabled,
                reason,
                secondary,
                denyReply,
                onDenyJustDeny = onDeny,
            )
        },
    ) {
        KitText(
            title,
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            role = TypeRole.Headline,
            tone = TextTone.Primary,
            kind = KitTextKind.Reading,
        )
        if (!summary.isNullOrEmpty()) {
            KitText(
                summary,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Reading,
            )
        }
        if (!toolLabel.isNullOrEmpty()) {
            KitText(toolLabel, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Reading)
        }
        if (targets.isNotEmpty()) {
            KitRequestTargets(targets, copyLabel = copyLabel, copiedLabel = copiedLabel)
        } else if (!subject.isNullOrEmpty()) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(KitShapeId.Code.shape())
                    .background(colors.ground)
                    .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp)
            ) {
                KitMonoLtr(subject, Modifier.fillMaxWidth(), TextTone.Primary)
            }
        }
    }
}

/**
 * Deny and Allow once side by side (Allow at the end, under the thumb); Always allow as a quiet
 * answer under them.
 */
@Composable
private fun Answers(
    allowOnceLabel: String,
    alwaysLabel: String?,
    denyLabel: String,
    onAllowOnce: () -> Unit,
    onAlways: (() -> Unit)?,
    onDeny: () -> Unit,
    enabled: Boolean,
    reason: String?,
    secondary: Boolean,
    denyReply: KitDenyReply?,
    onDenyJustDeny: () -> Unit,
) {
    var replying by rememberSaveable { mutableStateOf(false) }
    val specs = KitMotion.specs
    val reduced = specs.state.reduced
    Column {
        // Deny with a reply: the buttons give way to the small field; 150-250 ms, instant when motion is reduced.
        AnimatedVisibility(
            visible = replying && denyReply != null,
            enter = if (reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
            exit = if (reduced) ExitTransition.None else shrinkVertically(specs.quick()) + fadeOut(specs.quick()),
        ) {
            if (denyReply != null) DenyReplyPanel(denyReply, enabled, reason, onDenyJustDeny) { replying = false }
        }
        AnimatedVisibility(
            visible = !(replying && denyReply != null),
            enter = if (reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
            exit = if (reduced) ExitTransition.None else shrinkVertically(specs.quick()) + fadeOut(specs.quick()),
        ) {
            AnswerButtons(
                allowOnceLabel, alwaysLabel, denyLabel, onAllowOnce, onAlways,
                if (denyReply != null) ({ replying = true }) else onDeny,
                enabled, reason, secondary,
                // M-005: Deny is an answer only when it does not open the reply field first.
                denyAnswers = denyReply == null,
            )
        }
    }
}

/** The inline reply: one field, "Deny and send" (needs words), "Just deny" and Back. */
@Composable
private fun DenyReplyPanel(
    reply: KitDenyReply,
    enabled: Boolean,
    reason: String?,
    onJustDeny: () -> Unit,
    onBack: () -> Unit,
) {
    val field = rememberTextFieldState()
    val note = field.text.toString().trim()
    val availability = KitEnabled.of(enabled, reason)
    val canSend = enabled && note.isNotEmpty()
    Column(Modifier.fillMaxWidth().testTag("permission-card-reply"), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitField(field, label = reply.fieldLabel, onSubmit = { if (canSend) reply.onSend(note) })
        KitButton(
            reply.sendLabel,
            { reply.onSend(note) },
            Modifier.fillMaxWidth().testTag("permission-card-reply-send"),
            role = KitButtonRole.Primary,
            haptic = ANSWER_HAPTIC,
            enabled = if (canSend) KitEnabled.Enabled else KitEnabled.Disabled(reason ?: reply.emptyReason),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
            KitButton(reply.justDenyLabel, onJustDeny, Modifier.weight(1f).testTag("permission-card-reply-just-deny"), role = KitButtonRole.Secondary, enabled = availability, haptic = ANSWER_HAPTIC)
            KitButton(reply.backLabel, onBack, Modifier.weight(1f), role = KitButtonRole.Tertiary)
        }
    }
}

@Composable
private fun AnswerButtons(
    allowOnceLabel: String,
    alwaysLabel: String?,
    denyLabel: String,
    onAllowOnce: () -> Unit,
    onAlways: (() -> Unit)?,
    onDeny: () -> Unit,
    enabled: Boolean,
    reason: String?,
    secondary: Boolean,
    denyAnswers: Boolean = true,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalKitTypography.current.style(TypeRole.Button)
    val availability = KitEnabled.of(enabled, reason)
    val signal = LocalKitAppearance.current.style == KitSurfaceStyle.Signal
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val half = (maxWidth - Space.S2.dp) / 2 // F: lib/ui/kit/kit_request_card.dart:965
            fun fits(label: String): Boolean = with(density) {
                measurer.measure(label, style, softWrap = false, maxLines = 1).size.width.toDp() + Space.S6.dp * 2 <= half // F: lib/ui/kit/kit_request_card.dart:1092-1105
            }
            val allow: @Composable (Modifier) -> Unit = { modifier ->
                KitButton(allowOnceLabel, onAllowOnce, modifier.testTag("permission-card-allow"),
                    role = if (secondary && !signal) KitButtonRole.Secondary else KitButtonRole.Primary,
                    enabled = availability, haptic = ANSWER_HAPTIC)
            }
            val reject: @Composable (Modifier) -> Unit = { modifier ->
                KitButton(denyLabel, onDeny, modifier.testTag("permission-card-reject"), role = KitButtonRole.Secondary, enabled = availability,
                    haptic = if (denyAnswers) ANSWER_HAPTIC else null)
            }
            if (fits(allowOnceLabel) && fits(denyLabel)) Row(
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                reject(Modifier.weight(1f))
                allow(Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                if (secondary) { allow(Modifier.fillMaxWidth()); reject(Modifier.fillMaxWidth()) }
                else { reject(Modifier.fillMaxWidth()); allow(Modifier.fillMaxWidth()) }
            }
        }
        if (!enabled && reason != null) KitText(reason, role = TypeRole.Secondary, tone = TextTone.Secondary)
        if (alwaysLabel != null && onAlways != null) {
            KitButton(alwaysLabel, onAlways, Modifier.fillMaxWidth().testTag("permission-card-always"), role = KitButtonRole.Secondary, enabled = availability, haptic = ANSWER_HAPTIC)
        }
    }
}

/**
 * M-005 (Flutter `kit_request_card.dart`): every answer (Allow, Deny, Always, and the deny reply's two answers) plays the
 * light `Send` haptic, the person's words leaving. Opening the reply field is not an answer and stays silent (MOT-11).
 */
internal val ANSWER_HAPTIC: KitHaptic = KitHaptic.Send

/**
 * 45 % of the window's height (60 % at 2× text and above); no cap while the window has no size yet.
 */
@Composable
internal fun maxCardHeight(): Dp {
    val density = LocalDensity.current
    val heightPx = LocalWindowInfo.current.containerSize.height
    if (heightPx <= 0) return Dp.Infinity
    val fontScale = (LocalKitAppDensity.current ?: density).fontScale
    val share = if (fontScale >= LARGE_TEXT) HEIGHT_SHARE_LARGE_TEXT else HEIGHT_SHARE
    return with(density) { (heightPx * share).toDp() }
}
