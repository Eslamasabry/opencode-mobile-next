package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * Where a guided flow is (owner reference 2026-11-07, phone setup): [steps] short bars side by side, the first [done]
 * filled with the accent. A bar that becomes done fills from its start edge on the Spatial spring; going back empties
 * it the same way. The fill is drawn, never laid out, so the row never moves. TalkBack hears [label] ("Step 3 of 6")
 * and the progress.
 *
 * States: empty, part done, all done
 * Text: none ([label] is heard, not drawn)
 * Cap: none
 * Motion: each bar fills on the Spatial spring; Calm = same; Off = instant (class Fold)
 */
@Composable
public fun KitStepProgress(steps: Int, done: Int, label: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val shape = KitShapeId.Pill.shape()
    Row(
        modifier
            .fillMaxWidth()
            .testTag("kit-step-progress")
            .clearAndSetSemantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(done.coerceIn(0, steps).toFloat(), 0f..steps.toFloat(), steps)
            },
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        repeat(steps) { i ->
            val target = if (i < done) 1f else 0f
            val fill = remember { Animatable(target) }
            LaunchedEffect(target) { fill.animateTo(target, specs.spatial()) }
            Box(
                Modifier.weight(1f)
                    .height(BAR_DP.dp)
                    .clip(shape)
                    .drawBehind {
                        val r = CornerRadius(size.height / 2f)
                        drawRoundRect(colors.surface3, cornerRadius = r)
                        val w = size.width * fill.value.coerceIn(0f, 1f)
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                        if (w > 0f) drawRoundRect(colors.accent, Offset(x, 0f), Size(w, size.height), r)
                    },
            )
        }
    }
}

private const val BAR_DP = 4
