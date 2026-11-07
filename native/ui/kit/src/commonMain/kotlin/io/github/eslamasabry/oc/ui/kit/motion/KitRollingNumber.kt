package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText

/**
 * A number that changes in place, as an odometer (owner reference 2026-11-07): each digit that changes rolls in its
 * own column, upward when the number grows and downward when it falls, on the SpatialFast spring, softly blurred while
 * it moves (API 31+; plain below). Digits that did not change stay still; the ones digit keeps its column when the
 * number gains a digit. [text] is the formatted value ("1,284", "34%", "$0.42", "12 files"): only digits roll, every
 * other character cross-fades. TalkBack hears [text] once.
 *
 * States: at rest, rolling
 * Text: frame (tabular figures)
 * Cap: none
 * Motion: digits roll SpatialFast + blur at Full; Calm = cross-fade; Off = instant (class Travel)
 * Replaces: KitSwap.count for multi-digit values (token counts, durations, usage %, cost, "N files")
 */
@Composable
public fun KitRollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    role: TypeRole = TypeRole.Body,
    tone: TextTone = TextTone.Primary,
) {
    val state = KitMotion.state
    if (state.reduced) {
        KitText(text, modifier, role = role, tone = tone, maxLines = 1, tabular = true)
        return
    }
    val last = remember { arrayOf(text) }
    val up = remember(text) { (RollingRules.valueOf(text) >= RollingRules.valueOf(last[0])).also { last[0] = text } }
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            // Columns are keyed from the end, so 99 → 100 adds a column at the start and the ones digit stays put.
            key(text.length - i) { RollingChar(ch, up, role, tone) }
        }
    }
}

@Composable
private fun RollingChar(ch: Char, up: Boolean, role: TypeRole, tone: TextTone) {
    val specs = KitMotion.specs
    val travels = KitMotion.state.travels
    AnimatedContent(
        targetState = ch,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val roll = travels && initialState.isDigit() && targetState.isDigit()
            val dir = if (up) 1 else -1
            if (roll) {
                (slideInVertically(specs.spatialFast()) { h -> dir * h } + fadeIn(specs.pace(KitPace.Quick)))
                    .togetherWith(slideOutVertically(specs.spatialFast()) { h -> -dir * h } + fadeOut(specs.pace(KitPace.Quick)))
                    .using(SizeTransform(clip = true) { _, _ -> snap<IntSize>() })
            } else {
                (fadeIn(specs.pace(KitPace.Quick)) togetherWith fadeOut(specs.pace(KitPace.Quick)))
                    .using(SizeTransform(clip = false) { _, _ -> snap<IntSize>() })
            }
        },
        label = "kit.rolling.char",
    ) { shown ->
        val blur = if (travels) motionBlur() else 0.dp
        Box(Modifier.blur(blur, BlurredEdgeTreatment.Unbounded)) {
            KitText(shown.toString(), role = role, tone = tone, maxLines = 1, tabular = true)
        }
    }
}

/** Blurred while entering or leaving, sharp at rest: the motion blur of a rolling digit or a dissolving state. */
@Composable
internal fun AnimatedVisibilityScope.motionBlur(max: Dp = RollingRules.BLUR): Dp {
    val specs = KitMotion.specs
    val blur by transition.animateDp(transitionSpec = { specs.effectsFast() }, label = "kit.motion.blur") {
        if (it == EnterExitState.Visible) 0.dp else max
    }
    return blur
}

/** The rolling number's pure rules. */
public object RollingRules {
    /** How soft a moving digit gets at the start and end of its roll. */
    public val BLUR: Dp = 3.dp

    /** The number a formatted value shows (digits and one decimal point; "1,284" = 1284, "$0.42" = 0.42). */
    public fun valueOf(text: String): Double {
        val sign = if (text.trimStart().startsWith("-") || text.trimStart().startsWith("−")) -1 else 1
        val digits = text.filter { it.isDigit() || it == '.' }
        return sign * (digits.toDoubleOrNull() ?: 0.0)
    }
}
