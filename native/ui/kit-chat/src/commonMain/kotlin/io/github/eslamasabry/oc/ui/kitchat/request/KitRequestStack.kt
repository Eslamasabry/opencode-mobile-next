package io.github.eslamasabry.oc.ui.kitchat.request

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedRows

/**
 * Several request cards may take this share of the window; the rest stays for the conversation (P-103: at 0.6 three
 * cards left the conversation 88 dp).
 */
internal const val STACK_HEIGHT_SHARE: Float = 0.4f

/** P-103: how far a card scrolled past the stack's edge melts away instead of ending in a hard cut. */
private const val EDGE_FADE_DP = Space.S6

/** Which vertical edges fade: only an edge with more of the stack past it. */
internal fun requestStackFades(value: Int, max: Int): Pair<Boolean, Boolean> = (value > 0) to (value < max)

/**
 * Every request waiting in one conversation, one card each, in arrival order (E06-S01): nothing
 * hides behind "N more waiting", and each card is answered on its own.
 *
 * Each item is one TalkBack group that starts with [label] (its place and what it asks about, for
 * example "Request 2 of 4: Run npm test"), so cards are never read as one block.
 *
 * States: one card, several cards (scroll inside the stack), a card arriving, a card leaving
 * Text: [label] per item (the caller words it)
 * Cap: [STACK_HEIGHT_SHARE] of the window; more cards scroll so the composer stays on screen, and an edge with more
 *   past it fades over [EDGE_FADE_DP] (a card is never cut hard mid-line)
 * Motion: [KitAnimatedRows]: a card unfolds and fades in over STANDARD, folds out over QUICK;
 * Off = instant
 */
@Composable
public fun <T> KitRequestStack(
    items: List<T>,
    key: (T) -> Any,
    label: @Composable (index: Int, item: T) -> String,
    modifier: Modifier = Modifier,
    card: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val indexed = items.mapIndexed { index, item -> index to item }
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = stackMaxHeight())
            .edgeFade(scroll)
            .verticalScroll(scroll)
            .testTag("kit-request-stack")
    ) {
        KitAnimatedRows(items = indexed, key = { (_, item) -> key(item) }) { (index, item) ->
            val spoken = label(index, item)
            Column(
                Modifier.fillMaxWidth().padding(bottom = Space.S2.dp).semantics {
                    isTraversalGroup = true
                    contentDescription = spoken
                }
            ) {
                card(item)
            }
        }
    }
}

@Composable
private fun stackMaxHeight(): Dp {
    val heightPx = LocalWindowInfo.current.containerSize.height
    if (heightPx <= 0) return Dp.Infinity
    return with(LocalDensity.current) { (heightPx * STACK_HEIGHT_SHARE).toDp() }
}

/** Fades the top and bottom edges where more of the stack lies past them; read in draw, so it follows the scroll. */
private fun Modifier.edgeFade(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val (top, bottom) = requestStackFades(scroll.value, scroll.maxValue)
            if (!top && !bottom) return@drawWithContent
            val e = if (size.height <= 0f) 0f else (EDGE_FADE_DP.dp.toPx() / size.height).coerceIn(0f, 0.5f)
            val t = if (top) Color.Transparent else Color.Black
            val b = if (bottom) Color.Transparent else Color.Black
            drawRect(
                Brush.verticalGradient(0f to t, e to Color.Black, 1f - e to Color.Black, 1f to b),
                blendMode = BlendMode.DstIn,
            )
        }
