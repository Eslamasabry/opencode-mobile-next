package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollFade
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitSwapCount
import io.github.eslamasabry.oc.ui.kit.motion.spatialFast
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTabLabel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** One tab of a strip (Dart `KitTab`). [count] null while the first answer comes: no number, never "0". */
@Immutable
public data class KitTab(
    val label: String,
    val count: Int? = null,
    val needsYou: Int = 0,
    val icon: ImageVector? = null,
)

/** A keyboard arrow, physical. */
public enum class TabArrow { Left, Right }

/** How the body changes when a tab is chosen (plan 08 §5.9.2). */
public enum class TabBodyMotion { FadeThrough, CrossFade, Instant }

/** Dart `KitTabSwitcher`/`KitTabStrip` rules (B-KIT-050). */
public object TabRules {
    public const val MIN_TABS: Int = 2
    public const val MAX_TABS: Int = 8

    public fun validStrip(size: Int): Boolean = size in MIN_TABS..MAX_TABS

    public fun bodiesMatch(tabs: Int, bodies: Int): Boolean = tabs == bodies

    /** Counts loading show no number (never "0" for "not known yet"). */
    public fun countWords(count: Int?): String? = count?.toString()

    /** Arrows move in reading order: Right is next in LTR and previous in RTL. They stop at the ends. */
    public fun arrow(selected: Int, arrow: TabArrow, size: Int, rtl: Boolean): Int {
        val forward = (arrow == TabArrow.Right) != rtl
        return (selected + if (forward) 1 else -1).coerceIn(0, size - 1)
    }

    public fun bodyMotion(level: MotionLevel): TabBodyMotion =
        when (level) {
            MotionLevel.Full -> TabBodyMotion.FadeThrough
            MotionLevel.Calm -> TabBodyMotion.CrossFade
            MotionLevel.Off -> TabBodyMotion.Instant
        }

    /** The selection pill glides only at Full; it jumps at Calm and Off. */
    public fun indicatorMoves(level: MotionLevel): Boolean = level == MotionLevel.Full
}

/**
 * A strip of 2–8 labelled tabs with counts and a needs-you marker (Dart `KitTabStrip`; plan 06 §7.3, plan 08 §5.9.2).
 * Start-aligned; the chosen tab sits on a `surface3` pill that moves to it. Scrolls sideways when the tabs do not fit,
 * with faded edges, and keeps the chosen tab in view. Arrow keys move the choice in reading order. [onSelected] is
 * called once per activation of a **different** tab. No disabled tab: a tab that cannot open is not shown.
 *
 * States: default, counts loading, needs you, overflowing
 * Text: frame
 * Cap: Fill
 * Motion: indicator on SpatialFast at Full; Calm/Off = jumps; counts roll (KitSwapCount) (class Travel)
 * Replaces: KitTabStrip, KitTab, TeamBoardTabs (lib/ui/kit/motion/kit_tab_switcher.dart)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun KitTabStrip(
    tabs: List<KitTab>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
) {
    kitAssert(TabRules.validStrip(tabs.size)) { "KitTabStrip: a strip holds ${TabRules.MIN_TABS}–${TabRules.MAX_TABS} tabs" }
    kitAssert(selected in tabs.indices) { "KitTabStrip: selected is not a tab" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scroll = rememberScrollState()
    val requesters = remember(tabs.size) { List(tabs.size) { BringIntoViewRequester() } }
    LaunchedEffect(selected) { requesters.getOrNull(selected)?.bringIntoView() }
    val pill = KitShapeId.Pill
    Box(modifier.clipToBounds().kitScrollFade(scroll).testTag("kit-tab-strip")) {
        Row(
            Modifier.horizontalScroll(scroll)
                .padding(horizontal = tokens.gutter.dp)
                .semantics {
                    selectableGroup()
                    if (semanticsLabel != null) paneTitle = semanticsLabel
                }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val arrow =
                        when (e.key) {
                            Key.DirectionLeft -> TabArrow.Left
                            Key.DirectionRight -> TabArrow.Right
                            else -> return@onPreviewKeyEvent false
                        }
                    val next = TabRules.arrow(selected, arrow, tabs.size, rtl)
                    if (next != selected) onSelected(next)
                    true
                },
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: lib/ui/kit/motion/kit_tab_switcher.dart:197
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val chosen = i == selected
                val count = TabRules.countWords(tab.count)
                val spoken = if (count != null) stringResource(Res.string.kitTabLabel, tab.label, count) else tab.label
                val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = pill, level = SurfaceLevel.Surface3, ownFill = colors.surface3, selected = true))
                Row(
                    Modifier
                        .bringIntoViewRequester(requesters[i])
                        .heightIn(min = tokens.minTarget.dp)
                        .then(if (chosen) look.modifier else Modifier)
                        .kitTappable(onClick = { if (!chosen) onSelected(i) }, role = KitTappableRole.Tab, shape = pill)
                        .semantics(mergeDescendants = true) {
                            this.selected = chosen
                            contentDescription = spoken
                        }
                        .padding(horizontal = Space.S3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
                ) {
                    val tone = if (chosen) TextTone.Primary else TextTone.Secondary
                    tab.icon?.let { KitGlyph(it, colors.tone(tone), tokens.smallIcon.dp) }
                    KitText(tab.label, role = TypeRole.Label, tone = tone, kind = KitTextKind.Frame, maxLines = 1)
                    if (tab.count != null) {
                        KitSwapCount(tab.count) { n ->
                            KitText(n.toString(), role = TypeRole.Label, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
                        }
                    }
                    if (tab.needsYou > 0) NeedsYouMark(tab.needsYou)
                }
            }
        }
    }
}

/**
 * The needs-you marker on a tab: the amber dot with its count, the only attention colour in the strip (LOOK-24). A
 * stand-in until part B's `KitNeedsYou.badge(count)` exists (report §4 D6).
 */
@Composable
private fun NeedsYouMark(count: Int) {
    val colors = LocalKitColors.current
    Row(
        Modifier.background(colors.attentionSurface, KitShapeId.Pill.shape()).padding(horizontal = Space.S1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        Box(Modifier.size(DOT_DP.dp).background(colors.attention, KitShapeId.Circle.shape()))
        KitSwapCount(count) { n ->
            KitText(n.toString(), role = TypeRole.Caption, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 1)
        }
    }
}

private const val INDICATOR_INSET = 4
private const val DOT_DP = 6
