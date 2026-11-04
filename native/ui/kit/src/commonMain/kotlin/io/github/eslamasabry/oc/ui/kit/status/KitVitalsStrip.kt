package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** One short observation; [semanticsLabel] reads the value with its meaning. */
public data class KitVital(val value: String, val word: String, val semanticsLabel: String)

/**
 * Live readings without a live region. At most four cells; narrow windows and large text wrap to pairs.
 * States: loading, values, optional actionable summary. Text: frame cells, reading summary. Cap: Reading.
 * Motion: value cross-fade, reduced-motion aware. Replaces Flutter KitVitalsStrip.
 */
@Composable
public fun KitVitalsStrip(
    vitals: List<KitVital>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onSummaryTap: (() -> Unit)? = null,
    summaryLabel: String? = null,
    loading: Boolean = false,
) {
    require(vitals.size <= 4)
    val tokens = LocalKitTokens.current
    val scale = kitTextScale()
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (vitals.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (vitals.size > 2 && (maxWidth < 340.dp || scale >= 1.6f)) 2 else vitals.size
                Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
                    vitals.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                            row.forEach { vital ->
                                Column(
                                    Modifier.weight(1f).clearAndSetSemantics {
                                        contentDescription = if (loading) vital.word else vital.semanticsLabel
                                    },
                                ) {
                                    KitSwap(if (loading) "—" else vital.value, alignment = Alignment.CenterStart) { value ->
                                        FitVitalValue(value)
                                    }
                                    KitText(vital.word, role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
                                }
                            }
                            repeat(columns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (summary != null) {
            val tap = onSummaryTap
            val action = if (tap != null) Modifier.kitTappable(onClick = tap, onClickLabel = summaryLabel, ink = TextTone.Accent) else Modifier
            KitSwap(summary, action, alignment = Alignment.CenterStart) { text ->
                KitText(text, role = TypeRole.Secondary, tone = if (tap == null) TextTone.Secondary else TextTone.Accent, maxLines = 3)
            }
        }
    }
}

/** A value may shrink to fit, but its digits are never cut off or replaced by an ellipsis. */
@Composable
private fun FitVitalValue(value: String) {
    val appDensity = LocalKitAppDensity.current ?: LocalDensity.current
    val base = LocalKitTypography.current.style(TypeRole.Headline)
    val fontDp = TextScalePolicy.frameDp(base.fontSize.value, with(appDensity) { base.fontSize.toDp().value })
    val color = LocalKitColors.current.tone(TextTone.Primary)
    CompositionLocalProvider(LocalDensity provides Density(appDensity.density, 1f)) {
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val k = fontDp / base.fontSize.value
            val target = base.copy(
                fontSize = fontDp.sp,
                lineHeight = (base.lineHeight.value * k).sp,
                letterSpacing = (base.letterSpacing.value * k).sp,
                fontFeatureSettings = "tnum",
                color = color,
            )
            val measured = measurer.measure(value, style = target, softWrap = false, maxLines = 1)
            val available = with(LocalDensity.current) { maxWidth.toPx() }
            val ratio = if (measured.size.width > 0) (available / measured.size.width).coerceAtMost(1f) else 1f
            BasicText(value, style = target.copy(fontSize = (fontDp * ratio).sp), softWrap = false, maxLines = 1)
        }
    }
}
