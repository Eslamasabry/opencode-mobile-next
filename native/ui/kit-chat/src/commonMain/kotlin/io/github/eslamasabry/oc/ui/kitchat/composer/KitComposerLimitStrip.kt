package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.text.kitDateTime
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.composerLimitAm
import io.github.eslamasabry.oc.ui.words.res.composerLimitApproaching
import io.github.eslamasabry.oc.ui.words.res.composerLimitClock12
import io.github.eslamasabry.oc.ui.words.res.composerLimitDismiss
import io.github.eslamasabry.oc.ui.words.res.composerLimitFriday
import io.github.eslamasabry.oc.ui.words.res.composerLimitMonday
import io.github.eslamasabry.oc.ui.words.res.composerLimitPm
import io.github.eslamasabry.oc.ui.words.res.composerLimitReached
import io.github.eslamasabry.oc.ui.words.res.composerLimitResetsInHours
import io.github.eslamasabry.oc.ui.words.res.composerLimitResetsInHoursMinutes
import io.github.eslamasabry.oc.ui.words.res.composerLimitResetsInMinutes
import io.github.eslamasabry.oc.ui.words.res.composerLimitResetsOnDate
import io.github.eslamasabry.oc.ui.words.res.composerLimitResetsOnDay
import io.github.eslamasabry.oc.ui.words.res.composerLimitSaturday
import io.github.eslamasabry.oc.ui.words.res.composerLimitSunday
import io.github.eslamasabry.oc.ui.words.res.composerLimitThursday
import io.github.eslamasabry.oc.ui.words.res.composerLimitTuesday
import io.github.eslamasabry.oc.ui.words.res.composerLimitViewUsage
import io.github.eslamasabry.oc.ui.words.res.composerLimitWednesday
import org.jetbrains.compose.resources.stringResource

/** What the limit strip says. [reached] switches "Approaching limit" to "Limit reached"; [reset] may be unknown. */
@Immutable
public data class KitLimitNotice(val reached: Boolean, val reset: KitLimitReset?)

/** When the plan window resets, already on the phone's clock (the engine places it; the kit only words it). */
@Immutable
public sealed interface KitLimitReset {
    /** "Resets in 2 h 10 min". */
    public data class Within(val hours: Int, val minutes: Int) : KitLimitReset

    /** "Resets Friday at 5:00 AM". [isoWeekday] 1 = Monday … 7 = Sunday; [hour] 0–23. */
    public data class OnDay(val isoWeekday: Int, val hour: Int, val minute: Int) : KitLimitReset

    /** "Resets <date and time>", for a reset more than a week away. */
    public data class OnDate(val epochMs: Long) : KitLimitReset
}

/**
 * The composer's limit strip (owner reference 2026-11-08, `docs/design/native-ui-rebuild/visual/s24-limit/`): a
 * rounded band one surface step below the composer face, placed in the box's `above` slot over the field. The title
 * ("Approaching limit" / "Limit reached") and the muted reset line take the start; an outlined "View usage" pill and a
 * ✕ sit at the end. Words wrap, so it grows taller and never wider (360 dp, large text, Arabic).
 *
 * Motion: unfolds with a fade on STANDARD (250 ms) and folds away on QUICK (150 ms); reduced motion is instant. The
 * leaving strip keeps its last words while it folds.
 *
 * Text: frame. Replaces: none (new in the native app; the reference is Claude's app).
 */
@Composable
public fun KitComposerLimitStrip(
    notice: KitLimitNotice?,
    onViewUsage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = KitMotion.state
    val specs = KitMotion.specs
    // The last shown words stay drawn while the strip folds away (read only by the leaving content).
    val held = remember { arrayOfNulls<KitLimitNotice>(1) }
    if (notice != null) held[0] = notice
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = if (motion.reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
        exit = if (motion.reduced) ExitTransition.None else shrinkVertically(specs.quick()) + fadeOut(specs.quick()),
        label = "composer-limit-strip",
    ) {
        val shown = notice ?: held[0] ?: return@AnimatedVisibility
        LimitBand(shown, onViewUsage, onDismiss)
    }
}

@Composable
private fun LimitBand(notice: KitLimitNotice, onViewUsage: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    // Concentric with the composer face: its corner less the face's inner padding.
    val corner = (tokens.composerRadius.toFloat() - Space.S1).coerceAtLeast(Space.S2.toFloat()).dp
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = Space.S1.dp)
            .clip(RoundedCornerShape(corner))
            .background(colors.surface1)
            .padding(start = Space.S4.dp, top = Space.S2.dp, bottom = Space.S2.dp)
            .testTag(LIMIT_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).padding(vertical = Space.S1.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KitText(
                stringResource(if (notice.reached) Res.string.composerLimitReached else Res.string.composerLimitApproaching),
                Modifier.semantics { heading() },
                role = TypeRole.RowTitle,
                tone = TextTone.Primary,
                kind = KitTextKind.Frame,
            )
            notice.reset?.let { reset ->
                resetWords(reset)?.let {
                    KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                }
            }
        }
        Box(Modifier.padding(start = Space.S2.dp)) { LimitPill(stringResource(Res.string.composerLimitViewUsage), onViewUsage) }
        KitIconButton(
            icon = KitIcons.close,
            label = stringResource(Res.string.composerLimitDismiss),
            onClick = onDismiss,
            tone = TextTone.Secondary,
            size = LIMIT_CLOSE_GLYPH,
        )
    }
}

/** The outlined pill: a hairline-ringed capsule with button words, in a 48 dp target. */
@Composable
private fun LimitPill(label: String, onClick: () -> Unit) {
    val colors = LocalKitColors.current
    KitTappable(onClick = onClick, shape = KitShapeId.Pill, modifier = Modifier.testTag(LIMIT_VIEW_TAG)) {
        Box(
            Modifier.heightIn(min = LIMIT_PILL_HEIGHT.dp)
                .widthIn(max = LIMIT_PILL_MAX_WIDTH.dp)
                .border(kitHairline() * 2, colors.text3, CircleShape)
                .padding(horizontal = Space.S3.dp, vertical = Space.S1.dp),
            contentAlignment = Alignment.Center,
        ) {
            KitText(label, role = TypeRole.Button, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 2)
        }
    }
}

/** The reset line, or null when there is nothing true to say (a date the platform cannot word). */
@Composable
private fun resetWords(reset: KitLimitReset): String? =
    when (reset) {
        is KitLimitReset.Within ->
            when {
                reset.hours == 0 -> stringResource(Res.string.composerLimitResetsInMinutes, reset.minutes.coerceAtLeast(1))
                reset.minutes == 0 -> stringResource(Res.string.composerLimitResetsInHours, reset.hours)
                else -> stringResource(Res.string.composerLimitResetsInHoursMinutes, reset.hours, reset.minutes)
            }
        is KitLimitReset.OnDay -> stringResource(Res.string.composerLimitResetsOnDay, weekday(reset.isoWeekday), clock(reset.hour, reset.minute))
        is KitLimitReset.OnDate -> kitDateTime(reset.epochMs)?.let { stringResource(Res.string.composerLimitResetsOnDate, it) }
    }

@Composable
private fun weekday(iso: Int): String =
    stringResource(
        when (iso) {
            1 -> Res.string.composerLimitMonday
            2 -> Res.string.composerLimitTuesday
            3 -> Res.string.composerLimitWednesday
            4 -> Res.string.composerLimitThursday
            5 -> Res.string.composerLimitFriday
            6 -> Res.string.composerLimitSaturday
            else -> Res.string.composerLimitSunday
        }
    )

/** The phone's own clock style: "05:00" on a 24-hour phone, "5:00 AM" on a 12-hour one. */
@Composable
private fun clock(hour: Int, minute: Int): String {
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val mm = minute.toString().padStart(2, '0')
    if (signals.is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return stringResource(
        Res.string.composerLimitClock12,
        "$h12:$mm",
        stringResource(if (hour < 12) Res.string.composerLimitAm else Res.string.composerLimitPm),
    )
}

/** Test handles. */
public const val LIMIT_STRIP_TAG: String = "kit-composer-limit-strip"
public const val LIMIT_VIEW_TAG: String = "kit-composer-limit-view"

private const val LIMIT_PILL_HEIGHT = 40
private const val LIMIT_PILL_MAX_WIDTH = 160
private const val LIMIT_CLOSE_GLYPH = 20
