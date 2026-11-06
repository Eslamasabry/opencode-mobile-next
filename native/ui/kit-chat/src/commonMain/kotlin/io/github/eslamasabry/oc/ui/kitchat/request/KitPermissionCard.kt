package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
        if (targets.isNotEmpty()) {
            KitRequestTargets(targets)
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
                    enabled = availability)
            }
            val reject: @Composable (Modifier) -> Unit = { modifier ->
                KitButton(denyLabel, onDeny, modifier.testTag("permission-card-reject"), role = KitButtonRole.Secondary, enabled = availability)
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
            KitButton(alwaysLabel, onAlways, Modifier.fillMaxWidth(), role = KitButtonRole.Secondary, enabled = availability)
        }
    }
}

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
