package io.github.eslamasabry.oc.ui.kit.nav

import io.github.eslamasabry.oc.ui.kit.frame.kitScrollFade
import androidx.compose.foundation.ExperimentalFoundationApi
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextDirection
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitSwapCount
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouBadgeSuffix
import io.github.eslamasabry.oc.ui.words.res.kitNeedsYouBadgeSuffix__eq1
import io.github.eslamasabry.oc.ui.words.res.kitTabLabel
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One tab of a strip (Dart `KitTab`). [count] null while the first answer comes: no number, never "0". [mark] is a
 * small one-colour picture before the words (a provider's logo): it is drawn in the colour it is given, so the pill
 * turns it with the words.
 */
@Immutable
public data class KitTab(
    val label: String,
    val count: Int? = null,
    val needsYou: Int = 0,
    val icon: ImageVector? = null,
    val mark: (@Composable (tint: Color) -> Unit)? = null,
)

/** A keyboard arrow, physical. */
public enum class TabArrow { Left, Right }

/** How the body changes when a tab is chosen (plan 08 §5.9.2). */
public enum class TabBodyMotion { FadeThrough, CrossFade, Instant }

/** Dart `KitTabSwitcher`/`KitTabStrip` rules (B-KIT-050). */
public object TabRules {
    public const val MIN_TABS: Int = 2
    public const val MAX_TABS: Int = 8

    /** The room the pill keeps above and below inside a tab's 48 dp target, in dp. */
    public const val PILL_INSET_DP: Int = 6

    /** How much thinner the pill is, top and bottom, at its longest stretch, in dp. */
    public const val PILL_SQUASH_DP: Float = 1.5f

    public fun validStrip(size: Int): Boolean = size in MIN_TABS..MAX_TABS

    public fun bodiesMatch(tabs: Int, bodies: Int): Boolean = tabs == bodies

    /** Counts loading show no number (never "0" for "not known yet"). */
    public fun countWords(count: Int?): String? = count?.toString()

    /** Arrows move in reading order: Right is next in LTR and previous in RTL. They stop at the ends. */
    public fun arrow(selected: Int, arrow: TabArrow, size: Int, rtl: Boolean): Int {
        val forward = (arrow == TabArrow.Right) != rtl
        return (selected + if (forward) 1 else -1).coerceIn(0, size - 1)
    }

    /** A switcher without a strip (`KitTabSwitcher(index, bodies)`): its bodies fade, they do not page. */
    public fun bodyMotion(level: MotionLevel): TabBodyMotion =
        when (level) {
            MotionLevel.Full -> TabBodyMotion.FadeThrough
            MotionLevel.Calm -> TabBodyMotion.CrossFade
            MotionLevel.Off -> TabBodyMotion.Instant
        }

    /** A strip with no pages under it: its pill glides to a tapped tab only at Full; it jumps at Calm and Off. */
    public fun indicatorMoves(level: MotionLevel): Boolean = level == MotionLevel.Full

    /** The pill stretches toward the tab it is going to only at Full; at Calm and Off both edges keep the same pace. */
    public fun pillStretches(level: MotionLevel): Boolean = level == MotionLevel.Full

    /** A tap slides the pages to the tab at Full and Calm (Calm on the quick curve); Off shows the tab at once. */
    public fun tapSlides(level: MotionLevel): Boolean = level != MotionLevel.Off

    /** A tapped tab this many pages away or more has its neighbour brought next to it first: one page slides, never a row of them. */
    public const val FAR_TAB: Int = 2
}

/**
 * A strip of 2–8 labelled tabs with counts and a needs-you badge (Dart `KitTabStrip`; plan 06 §7.3, plan 08 §5.9.2).
 * Start-aligned; the chosen tab stands on the pill, its words in the pill's own ink. Scrolls sideways when the tabs do
 * not fit and keeps the pill in view. The strip is one Tab stop, entered on the chosen tab; Left and Right (mirrored
 * right to left) move focus, Home and End jump, Enter and Space choose: arrows never choose. [onSelected] is called
 * once per activation of a **different** tab. No disabled tab: a tab that cannot open is not shown.
 *
 * This form has no pages under it: the pill glides to the tab that was chosen. With pages, pass the pages'
 * [KitTabsState] instead and the pill follows the finger.
 *
 * States: default, counts loading, needs you, overflowing
 * Text: frame
 * Cap: Fill
 * Motion: the pill glides and stretches to the chosen tab (class Travel; Calm and Off jump); counts roll (KitSwapCount)
 * Replaces: KitTabStrip, KitTab, TeamBoardTabs (lib/ui/kit/motion/kit_tab_switcher.dart)
 */
@Composable
public fun KitTabStrip(
    tabs: List<KitTab>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
) {
    kitAssert(selected in tabs.indices) { "KitTabStrip: selected is not a tab" }
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val place = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        if (TabRules.indicatorMoves(motion.level)) place.animateTo(selected.toFloat(), specs.spatialFast())
        else place.snapTo(selected.toFloat())
    }
    TabStrip(
        tabs = tabs,
        selected = selected,
        onSelected = onSelected,
        position = { place.value },
        elastic = TabRules.pillStretches(motion.level),
        modifier = modifier,
        semanticsLabel = semanticsLabel,
        gutter = kitPageGutter(),
    )
}

/**
 * The strip over pages a finger can drag ([KitTabPages] with the same [state]). The pill is wherever the pages are,
 * frame for frame: half-way through a swipe it lies half-way between the two tabs with the width in between, and a
 * label it covers in part is two-coloured, split at the pill's edge. A tap slides the pages to that tab and the pill
 * crosses the tabs between. Everything else is as the plain strip.
 *
 * States: default, counts loading, needs you, overflowing, dragging
 * Text: frame
 * Cap: Fill
 * Motion: the pill follows the pages (class Follow, every level); at Full it stretches toward the tab it is going to
 * Hidden: swiping the pages changes the tab; every tab is also a tap away
 */
@Composable
public fun KitTabStrip(
    tabs: List<KitTab>,
    state: KitTabsState,
    modifier: Modifier = Modifier,
    semanticsLabel: String? = null,
) {
    TabStrip(
        tabs = tabs,
        selected = state.index.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        onSelected = state::choose,
        position = state.place,
        elastic = TabRules.pillStretches(KitMotion.state.level),
        modifier = modifier,
        semanticsLabel = semanticsLabel,
        gutter = kitPageGutter(),
    )
}

/** Counts how often a strip's parts were composed: a drag must not add to it (the pill moves in layout and draw). */
internal object TabStripProbe {
    var compositions: Int = 0
}

/**
 * The strip itself. [position] is read only while laying out the pill's face and while drawing, never while composing:
 * a frame of a drag re-runs one small layout and the strip's drawing, and composes nothing.
 *
 * Three layers share one layout: the pill, the tabs with their words in the resting ink, and the same words again in
 * the pill's ink. The resting words are drawn outside the pill's outline only and the pill's words inside it only, so a
 * label the pill half covers is split exactly at its edge. No blend mode is involved, so it holds on glass too.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TabStrip(
    tabs: List<KitTab>,
    selected: Int,
    onSelected: (Int) -> Unit,
    position: () -> Float,
    elastic: Boolean,
    modifier: Modifier,
    semanticsLabel: String?,
    gutter: Dp,
    /** The most tabs this strip takes. A kit part whose tabs are data (one per provider) lifts the strip's usual limit. */
    maxTabs: Int = TabRules.MAX_TABS,
) {
    kitAssert(tabs.size in TabRules.MIN_TABS..maxTabs) { "KitTabStrip: a strip holds ${TabRules.MIN_TABS}–${TabRules.MAX_TABS} tabs" }
    SideEffect { TabStripProbe.compositions++ }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val style = LocalKitStyle.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = rememberKitHaptics()
    val scroll = rememberScrollState()
    val model = remember { TabPillModel() }
    val focus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val quiet = remember(tabs.size) { List(tabs.size) { NoFocusFill() } }
    // Only one tab can take focus at a time: the chosen one, until an arrow moves it (the Dart strip excludes the others
    // from focus traversal).
    var roving by remember(selected, tabs.size) { mutableIntStateOf(selected) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(roving, moving) {
        if (moving) {
            focus.getOrNull(roving)?.requestFocus()
            moving = false
        }
    }
    // The strip follows the pill: its scroll is the pill's own place (TabPill.stripScroll), set as the pages move. A
    // strip the person has scrolled by hand eases back under the pill as the pages travel, never in one jump.
    LaunchedEffect(model, scroll, rtl, elastic) {
        var last = Float.NaN
        snapshotFlow {
            val s = model.slots
            if (s.lefts.isEmpty() || scroll.viewportSize == 0) Float.NaN else position()
        }.collect { p ->
            if (p.isNaN()) return@collect
            val s = model.slots
            val view = scroll.viewportSize.toFloat()
            fun target(at: Float): Float =
                TabPill.stripScroll(
                    TabPill.left(s.lefts, s.widths, at, elastic), TabPill.right(s.lefts, s.widths, at, elastic), view, s.content, rtl,
                )
            if (last.isNaN()) {
                scroll.scrollTo(target(p).roundToInt())
            } else if (p != last) {
                val off = scroll.value - target(last)
                val keep = (1f - STRIP_RETURN * abs(p - last)).coerceIn(0f, 1f)
                scroll.dispatchRawDelta(target(p) + off * keep - scroll.value)
            }
            last = p
        }
    }
    val solid = style.style == Style.Solid
    // Solid's pill is ink with the ground's colour for its words, the strongest pair the theme has; every other style
    // paints its own chosen control (glass, a pressed-in well, a hard block, an ink box) and says which ink reads on it.
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Control, shape = KitShapeId.Pill, level = SurfaceLevel.Surface3,
                ownFill = if (solid) colors.text1 else colors.surface3, selected = true,
            ),
        )
    val ink = colors.text1
    val onPill = if (solid) colors.ground else look.words ?: colors.text1
    val resting = colors.text2
    val inset = TabRules.PILL_INSET_DP.dp
    val squash = TabRules.PILL_SQUASH_DP.dp
    val corner: Dp = if (style.knobs.corners == Corners.Sharp) style.spec.tokens.sharpRadius.dp else (-1).dp
    val pressShape = remember(inset, corner) { TabPillShape(inset, corner) }
    val ringColor = colors.accent
    val ringWidth = kitFocusRingWidth()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val gap = Space.S2.dp // F: lib/ui/kit/motion/kit_tab_switcher.dart:228
    // C08: a strip wider than the window fades out at the edge with more past it (the board's "Don" stood cut there).
    Box(modifier.clipToBounds().kitScrollFade(scroll).testTag("kit-tab-strip")) {
        Layout(
            modifier =
                Modifier.horizontalScroll(scroll)
                    .semantics {
                        selectableGroup()
                        if (semanticsLabel != null) paneTitle = semanticsLabel
                    }
                    .onFocusChanged { if (!it.hasFocus && !moving) roving = selected }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val last = tabs.size - 1
                        val target =
                            when (e.key) {
                                Key.DirectionLeft -> TabRules.arrow(roving, TabArrow.Left, tabs.size, rtl)
                                Key.DirectionRight -> TabRules.arrow(roving, TabArrow.Right, tabs.size, rtl)
                                Key.MoveHome -> 0
                                Key.MoveEnd -> last
                                else -> return@onPreviewKeyEvent false
                            }
                        if (target != roving) {
                            roving = target
                            moving = true
                        }
                        true
                    },
            content = {
                // 1. The pill.
                if (solid) {
                    Box(
                        Modifier.testTag("kit-tab-pill").drawBehind {
                            if (model.slots.lefts.isEmpty()) return@drawBehind
                            model.measure(position(), elastic, inset.toPx(), squash.toPx(), corner.toPx(), snap = false)
                            drawPath(model.outline(0f, 0f), ink)
                        },
                    )
                } else {
                    Box(
                        Modifier
                            .layout { measurable, constraints ->
                                val has = model.slots.lefts.isNotEmpty()
                                if (has) model.measure(position(), elastic, inset.toPx(), squash.toPx(), corner.toPx(), snap = true)
                                val w = if (has) (model.right - model.left).roundToInt().coerceAtLeast(0) else 0
                                val h = if (has) (model.bottom - model.top).roundToInt().coerceAtLeast(0) else 0
                                val x = model.left.roundToInt()
                                val y = model.top.roundToInt()
                                val face = measurable.measure(Constraints.fixed(w, h))
                                layout(constraints.maxWidth, constraints.maxHeight) { face.place(x, y) }
                            }
                            .testTag("kit-tab-pill")
                            .then(look.modifier),
                    )
                }
                // 2. The tabs: what is pressed, focused and read aloud, their words in the resting ink.
                tabs.forEachIndexed { i, tab ->
                    val chosen = i == selected
                    var ring by remember { mutableStateOf(false) }
                    val count = TabRules.countWords(tab.count)
                    val spoken = if (count != null) stringResource(Res.string.kitTabLabel, tab.label, count) else tab.label
                    // The badge is picture only; its count is spoken with the tab, as KitNeedsYouBadge does
                    // (F: lib/ui/kit/kit_needs_you.dart kitNeedsYouBadgeSuffix).
                    val needsYouWords =
                        when {
                            tab.needsYou <= 0 -> null
                            tab.needsYou == 1 -> stringResource(Res.string.kitNeedsYouBadgeSuffix__eq1)
                            else -> pluralStringResource(Res.plurals.kitNeedsYouBadgeSuffix, tab.needsYou, tab.needsYou)
                        }
                    Box(
                        Modifier
                            .heightIn(min = tokens.minTarget.dp)
                            .focusProperties { canFocus = i == roving }
                            .focusRequester(focus[i])
                            .onFocusChanged { ring = it.isFocused }
                            .kitTappable(
                                onClick = {
                                    if (!chosen) {
                                        haptics(KitHaptic.Select)
                                        onSelected(i)
                                    }
                                },
                                role = KitTappableRole.Tab, shape = KitShapeId.Pill, interactionSource = quiet[i], pressClip = pressShape,
                            )
                            .drawWithContent {
                                drawContent()
                                if (ring && keyboard) {
                                    val w = ringWidth.toPx()
                                    val top = inset.toPx()
                                    // the ring is deflated by half its width to stay inside (F: lib/ui/kit/kit_tappable.dart:277)
                                    pillPath(model.path, w / 2, top + w / 2, size.width - w / 2, size.height - top - w / 2, pressShape.radiusPx(size.height - 2 * top, this))
                                    drawPath(model.path, ringColor, style = Stroke(w))
                                }
                            }
                            .semantics(mergeDescendants = true) {
                                this.selected = chosen
                                contentDescription = spoken
                                if (needsYouWords != null) stateDescription = needsYouWords
                            }
                            .tabSplit(model, i, onPill = false, position, elastic, inset, squash, corner, snap = !solid)
                            .padding(horizontal = Space.S3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TabFace(tab, resting, resting, rtl)
                    }
                }
                // 3. The same words in the pill's ink: picture only, shown inside the pill's outline.
                tabs.forEachIndexed { i, tab ->
                    Box(
                        Modifier
                            .clearAndSetSemantics {}
                            .tabSplit(model, i, onPill = true, position, elastic, inset, squash, corner, snap = !solid)
                            .padding(horizontal = Space.S3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TabFace(tab, onPill, onPill, rtl)
                    }
                }
            },
        ) { measurables, constraints ->
            val n = tabs.size
            val pad = gutter.roundToPx()
            val between = gap.roundToPx()
            val loose = Constraints(maxHeight = constraints.maxHeight)
            val base = ArrayList<Placeable>(n)
            var height = 0
            var wide = 2 * pad + between * (n - 1)
            for (i in 0 until n) {
                val p = measurables[1 + i].measure(loose)
                base += p
                height = maxOf(height, p.height)
                wide += p.width
            }
            val width = maxOf(wide, constraints.minWidth)
            val lefts = FloatArray(n)
            val widths = FloatArray(n)
            var x = if (layoutDirection == LayoutDirection.Rtl) width - pad else pad
            for (i in 0 until n) {
                val w = base[i].width
                if (layoutDirection == LayoutDirection.Rtl) x -= w
                lefts[i] = x.toFloat()
                widths[i] = w.toFloat()
                x += if (layoutDirection == LayoutDirection.Rtl) -between else w + between
            }
            if (!model.slots.same(lefts, widths, height.toFloat(), width.toFloat())) {
                model.slots = TabSlots(lefts, widths, height.toFloat(), width.toFloat())
            }
            val over = ArrayList<Placeable>(n)
            for (i in 0 until n) over += measurables[1 + n + i].measure(Constraints.fixed(base[i].width, base[i].height))
            val pill = measurables[0].measure(Constraints.fixed(width, height))
            layout(width, height) {
                pill.place(0, 0)
                for (i in 0 until n) {
                    val top = (height - base[i].height) / 2
                    base[i].place(lefts[i].toInt(), top)
                    over[i].place(lefts[i].toInt(), top)
                }
            }
        }
    }
}

/** How fast a strip scrolled away by hand comes back under the pill: all of the way in half a page of travel. */
private const val STRIP_RETURN: Float = 2f

/** A tab's words, mark, count and badge in one ink ([label]) with the count in [number]. Drawn twice per tab. */
@Composable
private fun TabFace(tab: KitTab, label: Color, number: Color, rtl: Boolean) {
    SideEffect { TabStripProbe.compositions++ }
    val tokens = LocalKitTokens.current
    // The badge sits at the words' top-end corner and takes no room of its own, so the words leave it
    // BADGE_MIN_WIDTH after them (F: lib/ui/kit/motion/kit_tab_switcher.dart:362).
    Box {
        Box(Modifier.padding(end = if (tab.needsYou > 0) FlutterKitTokens.BADGE_MIN_WIDTH.dp else 0.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                tab.mark?.invoke(label)
                tab.icon?.let { KitGlyph(it, label, tokens.smallIcon.dp) }
                TabText(tab.label, TypeRole.Label, label, TextScalePolicy.FRAME_CAP, tabular = false)
                if (tab.count != null) {
                    KitSwapCount(tab.count) { n ->
                        TabText(n.toString(), TypeRole.Label, number, TextScalePolicy.FRAME_CAP, tabular = true)
                    }
                }
            }
        }
        if (tab.needsYou > 0) NeedsYouBadge(tab.needsYou, rtl, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Draws a tab's words on one side of the pill's outline: inside it ([onPill]) or outside it. A tab the pill does not
 * touch draws as it is, or not at all; only the one or two it crosses are clipped.
 */
private fun Modifier.tabSplit(
    model: TabPillModel,
    index: Int,
    onPill: Boolean,
    position: () -> Float,
    elastic: Boolean,
    inset: Dp,
    squash: Dp,
    corner: Dp,
    snap: Boolean,
): Modifier =
    drawWithContent {
        val slots = model.slots
        if (index >= slots.lefts.size) {
            if (!onPill) drawContent()
            return@drawWithContent
        }
        model.measure(position(), elastic, inset.toPx(), squash.toPx(), corner.toPx(), snap)
        val x = slots.lefts[index]
        val y = (slots.height - size.height) / 2f
        if (model.right <= x || model.left >= x + size.width) {
            if (!onPill) drawContent()
            return@drawWithContent
        }
        clipPath(model.outline(-x, -y), if (onPill) ClipOp.Intersect else ClipOp.Difference) { this@drawWithContent.drawContent() }
    }

/** The pill's outline inside a tab's own box: what a press fills and the focus ring follows. */
private class TabPillShape(private val inset: Dp, private val corner: Dp) : Shape {
    fun radiusPx(height: Float, density: Density): Float {
        val round = height / 2f
        val c = with(density) { corner.toPx() }
        return if (c < 0f) round else minOf(c, round)
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = with(density) { inset.toPx() }.coerceAtMost(size.height / 2f)
        val r = radiusPx(size.height - 2 * top, density)
        return Outline.Rounded(RoundRect(0f, top, size.width, size.height - top, CornerRadius(r, r)))
    }
}

/**
 * One line of a tab's words or numbers: at the platform's size, or never above [cap] × the spec size when [cap] is
 * given (the badge's clamp, laid out as [KitText] lays out frame text), with tabular figures when [tabular] (Dart
 * `tabular: true`). The box takes the line's width to the nearest pixel, not the next one up, so a row of tabs adds up
 * as it does where text is laid out in fractions of a pixel.
 */
@Composable
private fun TabText(text: String, role: TypeRole, color: Color, cap: Float?, tabular: Boolean) {
    val base = LocalKitTypography.current.style(role)
    val app = LocalKitAppDensity.current ?: LocalDensity.current
    val spec = base.fontSize.value
    val target = if (cap == null) spec else minOf(with(app) { spec.sp.toDp().value }, spec * cap)
    val k = target / spec
    val style: TextStyle =
        base.copy(
            color = color,
            fontSize = if (cap == null) base.fontSize else target.sp,
            lineHeight = if (cap == null) base.lineHeight else (base.lineHeight.value * k).sp,
            letterSpacing = if (cap == null) base.letterSpacing else (base.letterSpacing.value * k).sp,
            fontFeatureSettings = if (tabular) "tnum" else null,
            textDirection = TextDirection.Content,
        )
    var width by remember(text, style) { mutableFloatStateOf(-1f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val line =
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val w = if (width < 0f) placeable.width else width.roundToInt()
            layout(w, placeable.height) { placeable.place(if (rtl) w - placeable.width else 0, 0) }
        }
    val shown: @Composable () -> Unit = {
        BasicText(
            text,
            line,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { width = it.multiParagraph.getLineRight(0) - it.multiParagraph.getLineLeft(0) },
        )
    }
    if (cap == null) shown() else CompositionLocalProvider(LocalDensity provides Density(app.density, 1f), shown)
}

/**
 * The needs-you badge on a tab (Dart `KitNeedsYou.badge`): the amber pill with its count, past the top-end corner of its
 * child by [FlutterKitTokens.BADGE_OFFSET] on both axes, at least 18 × 18, its number at most 1.3× (A11Y-8). The
 * words say the count already, so the pill is picture only.
 */
@Composable
private fun NeedsYouBadge(count: Int, rtl: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val off = with(LocalDensity.current) { FlutterKitTokens.BADGE_OFFSET.dp.roundToPx() }
    Box(
        modifier
            .offset { IntOffset(if (rtl) -off else off, -off) }
            .sizeIn(minWidth = FlutterKitTokens.BADGE_MIN_WIDTH.dp, minHeight = FlutterKitTokens.BADGE_HEIGHT.dp)
            .background(colors.attentionFill, KitShapeId.Pill.shape())
            .padding(horizontal = Space.S1.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val shown = if (count > 99) 100 else count // F: lib/ui/kit/kit_needs_you.dart:301,317
        KitSwapCount(shown) { n ->
            TabText(if (n > 99) "99+" else n.toString(), TypeRole.Caption, colors.onAttentionFill, FlutterKitTokens.BADGE_TEXT_SCALE_MAX, tabular = true) // F: lib/ui/kit/kit_needs_you.dart:301,317
        }
    }
}

/** An interaction source whose indication never sees focus: a focused tab shows the ring only, no fill (Dart paints the ring alone). */
private class NoFocusFill(private val inner: MutableInteractionSource = MutableInteractionSource()) : MutableInteractionSource {
    override val interactions: Flow<Interaction> = inner.interactions.filter { it !is FocusInteraction }
    override suspend fun emit(interaction: Interaction) = inner.emit(interaction)
    override fun tryEmit(interaction: Interaction): Boolean = inner.tryEmit(interaction)
}
