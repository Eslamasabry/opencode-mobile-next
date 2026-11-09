package io.github.eslamasabry.oc.ui.kit.modal

import io.github.eslamasabry.oc.ui.kit.foundation.kitPaneInset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.toIntRect
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitFloating
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassSheet
import io.github.eslamasabry.oc.ui.kit.effects.LiquidMotion
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidMenuFace
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.kitLeaving
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Plan 06 §7.4: a menu panel is at most 320 dp wide. */
internal const val MENU_MAX_WIDTH_DP: Int = 320
private const val MENU_MIN_WIDTH_DP = 180

/** A named group in a menu; its [heading] leads the group's first item (Dart `KitMenuGroup`). */
@Immutable public data class KitMenuGroup(val id: String, val heading: String? = null)

/** When a selection releases its reviewed target. */
public enum class KitMenuDismissal {
    BeforeSelect,
    AfterSelect,
    /** The action owns closing the menu and releasing its target. */
    ByAction,
}

/**
 * One menu item (Dart `KitMenuItem`). A disabled item says why on a second muted line. [checked]
 * non-null draws the check mark slot (a chosen value). [destructive] items move last, after one
 * divider (B-KIT-022); never red.
 */
@Immutable
public data class KitMenuItem(
    val label: String,
    val onSelect: () -> Unit,
    val icon: ImageVector? = null,
    val group: KitMenuGroup? = null,
    val destructive: Boolean = false,
    val checked: Boolean? = null,
    val enabled: KitEnabled = KitEnabled.Enabled,
    val dismissal: KitMenuDismissal = KitMenuDismissal.BeforeSelect,
)

/** One line of an arranged menu. */
public sealed interface KitMenuEntry {
    public data class Heading(val group: KitMenuGroup) : KitMenuEntry

    public data class Item(val item: KitMenuItem) : KitMenuEntry

    public data object Divider : KitMenuEntry
}

/**
 * The ordering rule (Dart `kitMenuLayout`, KitMenu.md "Ordering"), pure: items keep their order
 * within their group; groups keep the order of their first item; a divider sits where consecutive
 * groups differ and a named group's heading leads it; every destructive item then moves, in stable
 * order, after one divider at the end (none when all are destructive). B-KIT-022.
 */
public object MenuLayout {
    public fun arrange(items: List<KitMenuItem>): List<KitMenuEntry> {
        val rows = withGroupDividers(items.filterNot { it.destructive })
        val destructive = items.filter { it.destructive }
        if (destructive.isNotEmpty()) {
            if (rows.isNotEmpty()) rows += KitMenuEntry.Divider
            rows += withGroupDividers(destructive)
        }
        return rows
    }

    private fun withGroupDividers(items: List<KitMenuItem>): MutableList<KitMenuEntry> {
        val out = mutableListOf<KitMenuEntry>()
        items.forEachIndexed { i, item ->
            val starts = i == 0 || item.group != items[i - 1].group
            if (i > 0 && starts) out += KitMenuEntry.Divider
            val group = item.group
            if (starts && group?.heading != null) out += KitMenuEntry.Heading(group)
            out += KitMenuEntry.Item(item)
        }
        return out
    }
}

/**
 * The one popup menu (Dart `showKitMenu`, plan 06 §7.4), as screen state: shown while [expanded],
 * anchored to [anchor] ([MenuPlacement]: from its start edge, below it or above it, never over it).
 * Auto-dismissing items close before selection by default; [KitMenuItem.dismissal] can defer it
 * until after selection or leave dismissal to the action owner. Dismissing (outside tap, Back) calls [onDismissRequest]
 * and runs nothing. An empty [items] never opens.
 *
 * States: closed, opening, open, closing Text: frame Cap: Menu (320) Motion: fades in QUICK and drops 8 dp from its
 * anchor at Full; Calm fades; fades out QUICK; Off appears and leaves at once Replaces: showKitMenu, KitMenuPanel (lib/ui/kit/kit_menu.dart)
 */
@Composable
public fun KitMenu(
    expanded: Boolean,
    items: List<KitMenuItem>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    anchor: @Composable () -> Unit,
) {
    // P-017: where the anchor sits in the window, so the panel's height can be held to the side it opens on and
    // never cover the control that opened it.
    var own by remember { mutableStateOf(IntRect.Zero) }
    // P-108: a menu with no anchor of its own (a screen-level menu a row's ⋯ opens through its state) opens from the
    // control just pressed, not from the empty box in the screen's corner (which put it over that ⋯).
    val origin = LocalKitPressOrigin.current
    val open = expanded && items.isNotEmpty()
    val pressed = remember(open) { if (open) origin.take() else null }
    // M-004: the transition lives above the Popup, so a closing menu stays composed until its fade has run (the Popup
    // used to leave on the frame the menu closed). What it shows while it fades is what it showed when it closed.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = open
    val last = remember { MenuShown() }
    if (open) {
        last.items = items
        last.title = title
    }
    Box(modifier.onGloballyPositioned { own = it.boundsInWindow().toIntRect() }) {
        anchor()
        if (MenuRules.composed(shown.currentState, shown.targetState) && last.items.isNotEmpty()) {
            if (open) last.at = MenuPlacement.anchorFor(own, pressed)
            MenuPopup(shown, last, onDismissRequest, own)
        }
    }
}

/**
 * What a menu showed when it was last open, kept while it fades out. Items and title are state: a menu that swaps its
 * items while open (the chat header's Go to / More pages) must redraw, and [MenuPopup] takes this same instance each
 * time, so it is not recomposed by a changed argument (B-178).
 */
private class MenuShown {
    var items: List<KitMenuItem> by mutableStateOf(emptyList())
    var title: String? by mutableStateOf(null)
    var at: IntRect = IntRect.Zero
}

/** The menu's motion rules, pure (M-004). */
public object MenuRules {
    /** The menu's Popup is composed while it is open, opening or still fading out; it leaves once the fade is done. */
    public fun composed(current: Boolean, target: Boolean): Boolean = current || target
}

/**
 * The open menu: [last]'s items, placed by [MenuPlacement] from its anchor box (window px), which replaces the box the
 * popup is given ([own]) when the menu opens from a pressed control instead of its own.
 */
@Composable
private fun MenuPopup(
    shown: MutableTransitionState<Boolean>,
    last: MenuShown,
    onDismissRequest: () -> Unit,
    own: IntRect,
) {
    val anchor = last.at
    val override = anchor.takeIf { it != own }
    val items = last.items
    val title = last.title
    val anchorTop = anchor.top
    val anchorBottom = anchor.bottom
    // The window margin is the page's gutter in pixels (a dp count used as pixels left the menu ~10 px from
    // the screen's edge under a full-width row).
    val density = LocalDensity.current
    val gutter = with(density) { LocalKitTokens.current.gutter.dp.roundToPx() }
    // E20-S08: the menu fits between the system bars, a step from each; a longer one scrolls (its last
    // item was cut under the navigation bar).
    val step = with(density) { Space.S2.dp.roundToPx() }
    val top = WindowInsets.systemBars.getTop(density) + step
    val bottom = WindowInsets.systemBars.getBottom(density) + step
    val provider = remember(gutter, top, bottom, override) { MenuPosition(gutter, top, bottom, override) }
    Popup(
        popupPositionProvider = provider,
        // A menu already fading out does not ask to close again.
        onDismissRequest = { if (shown.targetState) onDismissRequest() },
        properties = PopupProperties(focusable = true),
    ) {
        val motion = KitMotion.state
        val specs = KitMotion.specs
        val density = LocalDensity.current
        // The panel drops 8 dp from the anchor when it opens below and rises when it opens above (Full only; Calm
        // fades); it fades out on the way out (M-004). Off: both are instant, so the first frame is final.
        val opensAbove = remember { mutableStateOf(false) }
        val drop =
            slideInVertically(specs.quick<IntOffset>()) {
                with(density) { KitModalMotionRules.menuDropDp(motion, opensAbove.value).dp.roundToPx() }
            }
        AnimatedVisibility(
            shown,
            enter = if (motion.travels) fadeIn(specs.quick()) + drop else fadeIn(specs.quick()),
            exit = fadeOut(specs.exit()),
            label = "kit.menu",
        ) {
            // The panel grows from the control that opened it (motion lead 2026-11-07): scale 0.9 → 1 on SpatialFast
            // around the anchor's centre on the side it opens from, read in the draw phase. Full only.
            val panelBox = remember { mutableStateOf(IntRect.Zero) }
            val scale by transition.animateFloat(
                transitionSpec = {
                    if (targetState == EnterExitState.Visible) specs.spatialFast() else specs.exit()
                },
                label = "kit.menu.scale",
            ) { if (it == EnterExitState.Visible || !motion.travels) 1f else MENU_FROM_SCALE }
            KitMenuPanel(
                modifier =
                    Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)
                        .onGloballyPositioned { panelBox.value = it.boundsInWindow().toIntRect() }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = MenuPlacement.origin(anchor, panelBox.value, opensAbove.value)
                        }
                        .layout { measurable, constraints ->
                        if (!constraints.hasBoundedHeight) {
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        val window = MenuWindow(constraints.maxWidth, constraints.maxHeight, gutter, top, bottom)
                        val room = MenuPlacement.room(anchorTop, anchorBottom, window)
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = room))
                        opensAbove.value = MenuPlacement.opensAbove(anchorTop, anchorBottom, placeable.height, window)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                items = items,
                title = title,
                onChoose = { item ->
                    if (item.dismissal == KitMenuDismissal.BeforeSelect) onDismissRequest()
                    item.onSelect()
                    if (item.dismissal == KitMenuDismissal.AfterSelect) onDismissRequest()
                },
                grow = { LiquidMotion.growFraction(scale, MENU_FROM_SCALE) },
            )
        }
    }
}

/**
 * The menu's panel on its own (Dart `KitMenuPanel`): `surface2`, a hairline, at most 320 dp, rows
 * of at least 48 dp. Used by [KitMenu] and by captures; screens use [KitMenu].
 */
@Composable
public fun KitMenuPanel(
    items: List<KitMenuItem>,
    modifier: Modifier = Modifier,
    title: String? = null,
    onChoose: (KitMenuItem) -> Unit,
    /** How far the panel's grow-in has still to run (0 at rest, 1 at its start): liquid glass is frost meanwhile. */
    grow: () -> Float = { 0f },
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Panel.shape()
    // Glass: liquid glass over the page (Android 13+, not held), else a frosted pane with its soft rims; every other
    // style keeps surface2 and its hairline.
    val menuFace =
        kitLiquidMenuFace(shape, grow)
            ?: kitGlassSheet(KitShapeId.Panel, colors.surface2)
            ?: Modifier.background(colors.surface2).border(1.dp, colors.hairline, shape)
    Column(
        modifier
            .widthIn(min = MENU_MIN_WIDTH_DP.dp, max = MENU_MAX_WIDTH_DP.dp)
            .clip(shape)
            .then(menuFace)
            .verticalScroll(rememberScrollState())
            // Glass keeps its pane inset inside the menu (owner 2026-11-09): rows clear of the rim, sheen and lens.
            .padding(horizontal = kitPaneInset(), vertical = Space.S1.dp + kitPaneInset())
            .semantics { if (title != null) paneTitle = title }
            .kitFloating("menu")
            .testTag("kit-menu")
    ) {
        // C17 (audit 2026-11-09): glyphs are all or nothing. Every item has one, aligned in one column, or none shows
        // one; a half-iconed menu makes the text column jump.
        val glyphSlot = items.isNotEmpty() && items.all { it.icon != null }
        MenuLayout.arrange(items).forEach { entry ->
            when (entry) {
                KitMenuEntry.Divider ->
                    Box(
                        Modifier.padding(vertical = Space.S1.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.hairline)
                    )
                is KitMenuEntry.Heading ->
                    KitText(
                        entry.group.heading.orEmpty(),
                        Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S1.dp)
                            .semantics { heading() },
                        role = TypeRole.Caption,
                        tone = TextTone.Secondary,
                        kind = KitTextKind.Frame,
                    )
                is KitMenuEntry.Item -> MenuRow(entry.item, glyphSlot, onChoose)
            }
        }
    }
}

@Composable
private fun MenuRow(item: KitMenuItem, glyphSlot: Boolean, onChoose: (KitMenuItem) -> Unit) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val on = item.enabled.isEnabled
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = tokens.minTarget.dp)
            .kitTappable(
                onClick = { onChoose(item) },
                enabled = on,
                disabledReason = item.enabled.reason,
                role = KitTappableRole.Row,
                shape = KitShapeId.Square,
            )
            .semantics { if (item.checked != null) selected = item.checked }
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        val ink = if (on) colors.text1 else colors.text3
        val glyph = item.icon
        if (glyphSlot && glyph != null) KitGlyph(glyph, ink, tokens.smallIcon.dp, Modifier.testTag("kit-menu-glyph"))
        Column(Modifier.weight(1f)) {
            KitText(
                item.label,
                role = TypeRole.RowTitle,
                tone = if (on) TextTone.Primary else TextTone.Tertiary,
                kind = KitTextKind.Frame,
                maxLines = 2,
            )
            item.enabled.reason?.let {
                KitText(
                    it,
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    kind = KitTextKind.Frame,
                    maxLines = 2,
                )
            }
        }
        when (item.checked) {
            true -> KitGlyph(KitPartGlyphs.Check, colors.accent, tokens.smallIcon.dp)
            false -> Spacer(Modifier.size(tokens.smallIcon.dp))
            null -> Spacer(Modifier.width(0.dp))
        }
    }
}

/**
 * The window a menu opens in, in px: its size, the [margin] kept from each side, and the room kept free at the [top]
 * and [bottom] (the system bars and a step).
 */
public data class MenuWindow(
    val width: Int,
    val height: Int,
    val margin: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
)

/**
 * Where a menu opens (P-017), pure so it is tested without a window. Across: the panel starts on the anchor's start
 * edge when it fits there, else ends on its end edge, else is held inside the window margin. Down: below the anchor
 * when the panel fits, else above when it fits there, else on the roomier side with its height held to that side
 * ([room]; it scrolls). It never covers the control that opened it.
 */
public object MenuPlacement {
    /**
     * Where the panel grows from: the anchor's centre across, clamped into the panel, and the edge nearest the anchor
     * (the top when it opens below, the bottom when above).
     */
    public fun origin(anchor: IntRect, panel: IntRect, opensAbove: Boolean): TransformOrigin {
        val x = if (panel.width > 0) ((anchor.center.x - panel.left).toFloat() / panel.width).coerceIn(0f, 1f) else 0.5f
        return TransformOrigin(x, if (opensAbove) 1f else 0f)
    }

    /**
     * The box a menu opens from (P-108): its [own] anchor, or the control just [pressed] when its own is empty (a
     * zero-size box: the screen-level menu of a row's ⋯), so the menu never opens over the control that opened it.
     */
    public fun anchorFor(own: IntRect, pressed: IntRect?): IntRect =
        if (pressed != null && own.width == 0 && own.height == 0) pressed else own

    /** The height a menu may take: the roomier side of the anchor, inside the window's top and bottom room. */
    public fun room(anchorTop: Int, anchorBottom: Int, window: MenuWindow): Int =
        maxOf(window.height - window.bottom - anchorBottom, anchorTop - window.top).coerceAtLeast(0)

    /** True when a panel [height] tall opens above its anchor. */
    public fun opensAbove(anchorTop: Int, anchorBottom: Int, height: Int, window: MenuWindow): Boolean {
        val below = window.height - window.bottom - anchorBottom
        val above = anchorTop - window.top
        return when {
            height <= below -> false
            height <= above -> true
            else -> above > below
        }
    }

    /** The panel's top-left corner in the window. */
    public fun offset(anchor: IntRect, content: IntSize, rtl: Boolean, window: MenuWindow): IntOffset {
        val minX = window.margin
        val maxX = maxOf(window.margin, window.width - content.width - window.margin)
        // The reading start edge of the anchor, then its end edge: the panel grows away from the edge it starts on.
        val fromStart = if (rtl) anchor.right - content.width else anchor.left
        val fromEnd = if (rtl) anchor.left else anchor.right - content.width
        val x =
            when {
                fromStart in minX..maxX -> fromStart
                fromEnd in minX..maxX -> fromEnd
                else -> fromEnd.coerceIn(minX, maxX)
            }
        val y =
            if (opensAbove(anchor.top, anchor.bottom, content.height, window)) {
                (anchor.top - content.height).coerceAtLeast(window.top)
            } else {
                anchor.bottom.coerceAtMost(maxOf(anchor.bottom, window.height - window.bottom - content.height))
            }
        return IntOffset(x, y)
    }
}

private class MenuPosition(
    private val margin: Int,
    private val top: Int = 0,
    private val bottom: Int = 0,
    private val override: IntRect? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val window = MenuWindow(windowSize.width, windowSize.height, margin, top, bottom)
        return MenuPlacement.offset(override ?: anchorBounds, popupContentSize, layoutDirection == LayoutDirection.Rtl, window)
    }
}

/** A menu grows from 90 % of its size at Full (scale and fade, never from nothing). */
internal const val MENU_FROM_SCALE: Float = 0.9f
