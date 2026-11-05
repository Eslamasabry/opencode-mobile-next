package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion

/**
 * The voice pill above the composer field while voice runs (plan 06 §7.8 `KitComposerVoice`): what is happening in
 * plain words ([title], a polite live region), the time since listening began ([elapsed]), the words heard so far
 * ([words], reading text, capped to the newest lines), the level meter read in the draw phase ([level]; nothing
 * recomposes per frame), and at most two visible actions ([primary], [secondary]). [attention] turns the frame amber
 * (slid far enough to cancel, a countdown running).
 *
 * Motion: size changes animate on the kit's quick spec; none under reduced motion.
 */
@Composable
public fun KitVoicePill(
    title: String,
    modifier: Modifier = Modifier,
    words: String? = null,
    elapsed: String? = null,
    level: (() -> Float)? = null,
    attention: Boolean = false,
    primary: KitLineAction? = null,
    secondary: KitLineAction? = null,
) {
    val colors = LocalKitColors.current
    val reduced = LocalKitMotion.current.reduced
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .testTag(VOICE_PILL_TAG)
            .then(if (reduced) Modifier else Modifier.animateContentSize())
            .background(if (attention) colors.attentionSurface else colors.surface2, shape)
            .border(1.dp, if (attention) colors.attentionLine else colors.hairline, shape)
            .padding(horizontal = Space.S4.dp, vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            if (level != null) KitLevelMeter(level)
            KitText(
                title,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                role = TypeRole.RowTitle,
                tone = if (attention) TextTone.Attention else TextTone.Primary,
                maxLines = 2,
            )
            if (elapsed != null) KitText(elapsed, role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 1)
        }
        if (!words.isNullOrBlank()) {
            KitText(words, modifier = Modifier.testTag(VOICE_WORDS_TAG), role = TypeRole.Body, maxLines = 4)
        }
        if (primary != null || secondary != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.End)) {
                if (secondary != null) {
                    KitButton(
                        label = secondary.label,
                        onClick = secondary.onClick,
                        role = KitButtonRole.Tertiary,
                        modifier = secondary.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                    )
                }
                if (primary != null) {
                    KitButton(
                        label = primary.label,
                        onClick = primary.onClick,
                        role = KitButtonRole.Secondary,
                        modifier = primary.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                    )
                }
            }
        }
    }
}

/** Nine bars that light from the start edge as the voice gets louder; drawn, never recomposed (MOT rule). */
@Composable
public fun KitLevelMeter(level: () -> Float, modifier: Modifier = Modifier, active: Boolean = true) {
    val colors = LocalKitColors.current
    val on = colors.accent
    val off = colors.surface3
    val bars = KitLevelMeterRules.BARS
    val width = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.METER_BAR_WIDTH
    val tallest = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.METER_BAR_MAX
    val shortest = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.METER_BAR_MIN
    Box(
        modifier
            .kitMaxWidth(io.github.eslamasabry.oc.ui.design.layout.CapRole.Reading)
            .width((bars * width + (bars - 1) * Space.S1).dp)
            .height(tallest.dp)
            .clearAndSetSemantics {}
            .drawBehind {
                val barWidth = width.dp.toPx().roundToInt().toFloat()
                val gap = Space.S1.dp.toPx().roundToInt().toFloat()
                val center = (bars - 1) / 2f // F: lib/ui/kit/kit_level_meter.dart:145
                val now = level()
                for (i in 0 until bars) {
                    val h = (shortest + (tallest - shortest) * kotlin.math.abs(i - center) / center).dp.toPx().roundToInt().toFloat()
                    val slot = if (layoutDirection == LayoutDirection.Rtl) bars - 1 - i else i
                    drawRoundRect(
                        color = if (active && KitLevelMeterRules.isLit(i, now)) on else off,
                        topLeft = Offset((slot * (barWidth + gap)).roundToInt().toFloat(), ((size.height - h) / 2).roundToInt().toFloat()), // F: lib/ui/kit/kit_level_meter.dart:159
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2), // F: lib/ui/kit/kit_level_meter.dart:164
                    )
                }
            },
    )
}

/** Test handles. */
public const val VOICE_PILL_TAG: String = "kit-voice-pill"
public const val VOICE_WORDS_TAG: String = "kit-voice-words"
