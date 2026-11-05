package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
import io.github.eslamasabry.oc.ui.words.res.kitTabLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
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
 * A strip of 2–8 labelled tabs with counts and a needs-you badge (Dart `KitTabStrip`; plan 06 §7.3, plan 08 §5.9.2).
 * Start-aligned; the chosen tab sits on a `surface3` pill. Scrolls sideways when the tabs do not fit and keeps the
 * chosen tab in view. The strip is one Tab stop, entered on the chosen tab; Left and Right (mirrored right to left) move
 * focus, Home and End jump, Enter and Space choose: arrows never choose. [onSelected] is called once per activation
 * of a **different** tab. No disabled tab: a tab that cannot open is not shown.
 *
 * States: default, counts loading, needs you, overflowing
 * Text: frame
 * Cap: Fill
 * Motion: counts roll (KitSwapCount); the strip scrolls to the chosen tab (class Travel)
 * Replaces: KitTabStrip, KitTab, TeamBoardTabs (lib/ui/kit/motion/kit_tab_switcher.dart)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scroll = rememberScrollState()
    val reveal = remember(tabs.size) { List(tabs.size) { BringIntoViewRequester() } }
    val focus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val quiet = remember(tabs.size) { List(tabs.size) { NoFocusFill() } }
    // Only one tab can take focus at a time: the chosen one, until an arrow moves it (the Dart strip excludes the others
    // from focus traversal).
    var roving by remember(selected, tabs.size) { mutableIntStateOf(selected) }
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(selected, tabs.size) {
        androidx.compose.runtime.withFrameNanos { } // NV: the post-frame callback of _reveal, once layout has run
        reveal.getOrNull(selected)?.bringIntoView()
    }
    LaunchedEffect(roving, moving) {
        if (moving) {
            focus.getOrNull(roving)?.requestFocus()
            moving = false
        }
    }
    val pill = KitShapeId.Pill
    val pillShape = pill.shape()
    Box(modifier.clipToBounds().testTag("kit-tab-strip")) {
        Row(
            Modifier.horizontalScroll(scroll)
                .padding(horizontal = tokens.gutter.dp)
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
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: lib/ui/kit/motion/kit_tab_switcher.dart:228
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val chosen = i == selected
                var ring by remember { mutableStateOf(false) }
                val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
                val count = TabRules.countWords(tab.count)
                val spoken = if (count != null) stringResource(Res.string.kitTabLabel, tab.label, count) else tab.label
                val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = pill, level = SurfaceLevel.Surface3, ownFill = colors.surface3, selected = true))
                val ringColor = colors.accent
                val ringWidth = kitFocusRingWidth()
                Row(
                    Modifier
                        .bringIntoViewRequester(reveal[i])
                        .heightIn(min = tokens.minTarget.dp)
                        .then(if (chosen) look.modifier else Modifier)
                        .focusProperties { canFocus = i == roving }
                        .focusRequester(focus[i])
                        .onFocusChanged { ring = it.isFocused }
                        .kitTappable(onClick = { if (!chosen) onSelected(i) }, role = KitTappableRole.Tab, shape = pill, interactionSource = quiet[i])
                        .drawWithContent {
                            drawContent()
                            if (ring && keyboard) {
                                val w = ringWidth.toPx()
                                // the ring is deflated by half its width to stay inside
                                val inner = Size(size.width - w, size.height - w) // F: lib/ui/kit/kit_tappable.dart:277
                                val outline = pillShape.createOutline(inner, layoutDirection, this)
                                translate(w / 2, w / 2) { // F: lib/ui/kit/kit_tappable.dart:277
                                    drawOutline(outline, ringColor, style = Stroke(w))
                                }
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            this.selected = chosen
                            contentDescription = spoken
                        }
                        .padding(horizontal = Space.S3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tone = if (chosen) TextTone.Primary else TextTone.Secondary
                    // The badge sits at the words' top-end corner and takes no room of its own, so the words leave it
                    // BADGE_MIN_WIDTH after them (F: lib/ui/kit/motion/kit_tab_switcher.dart:362).
                    Box {
                        Box(Modifier.padding(end = if (tab.needsYou > 0) FlutterKitTokens.BADGE_MIN_WIDTH.dp else 0.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
                            ) {
                                tab.icon?.let { KitGlyph(it, colors.tone(tone), tokens.smallIcon.dp) }
                                TabText(tab.label, TypeRole.Label, colors.tone(tone), null, tabular = false) // the strip's words follow the platform's text size in full: F: lib/ui/kit/motion/kit_tab_switcher.dart:684
                                if (tab.count != null) {
                                    KitSwapCount(tab.count) { n ->
                                        TabText(n.toString(), TypeRole.Label, colors.tone(TextTone.Primary), null, tabular = true)
                                    }
                                }
                            }
                        }
                        if (tab.needsYou > 0) NeedsYouBadge(tab.needsYou, rtl, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
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
