package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** How much `text1` the leading mark and title bar carry, and the supporting bar (Dart `KitSkeletonRows`). */
internal const val SKELETON_SHAPE_ALPHA: Float = 0.12f
internal const val SKELETON_LINE_ALPHA: Float = 0.09f
private val TITLE_WIDTHS = listOf(0.72f, 0.56f, 0.64f, 0.48f, 0.6f)
private const val LINE_WIDTH = 0.28f

/**
 * Placeholder rows while a list loads (Dart `KitSkeletonRows`; design standard §4): the shape of the rows to come, no
 * words and no motion, hidden from screen readers (the loading bar already says the screen is loading). `text1` laid
 * thinly over whatever is under it, so it reads on every surface in light and dark. Matches `KitRow`'s geometry.
 *
 * States: one
 * Text: none
 * Cap: List
 * Motion: none (never shimmers)
 * Replaces: KitSkeletonRows (lib/ui/kit/kit_progress.dart)
 */
@Composable
public fun KitSkeletonRows(modifier: Modifier = Modifier, count: Int = 4) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = colors.text1.copy(alpha = SKELETON_SHAPE_ALPHA)
    val line = colors.text1.copy(alpha = SKELETON_LINE_ALPHA)
    Column(modifier.fillMaxWidth().testTag("kit-skeleton-rows").clearAndSetSemantics {}) {
        repeat(count) { i ->
            Row(
                Modifier.fillMaxWidth().height(tokens.skeletonRow.dp).padding(horizontal = tokens.gutter.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).clip(KitShapeId.Circle.shape()).background(shape))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    Box(Modifier.fillMaxWidth(TITLE_WIDTHS[i % TITLE_WIDTHS.size]).height(12.dp).clip(KitShapeId.Pill.shape()).background(shape))
                    Box(Modifier.fillMaxWidth(LINE_WIDTH).height(9.dp).clip(KitShapeId.Pill.shape()).background(line))
                }
            }
        }
    }
}
