package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Plan 06 §7.4: a menu panel is at most 320 dp wide. */
internal const val MENU_MAX_WIDTH_DP: Int = 320
private const val MENU_MIN_WIDTH_DP = 180

/** A named group in a menu; its [heading] leads the group's first item (Dart `KitMenuGroup`). */
@Immutable
public data class KitMenuGroup(val id: String, val heading: String? = null)

/**
 * One menu item (Dart `KitMenuItem`). A disabled item says why on a second muted line. [checked] non-null draws the
 * check mark slot (a chosen value). [destructive] items move last, after one divider (B-KIT-022); never red.
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
)

/** One line of an arranged menu. */
public sealed interface KitMenuEntry {
    public data class Heading(val group: KitMenuGroup) : KitMenuEntry

    public data class Item(val item: KitMenuItem) : KitMenuEntry

    public data object Divider : KitMenuEntry
}

/**
 * The ordering rule (Dart `kitMenuLayout`, KitMenu.md "Ordering"), pure: items keep their order within their group;
 * groups keep the order of their first item; a divider sits where consecutive groups differ and a named group's heading
 * leads it; every destructive item then moves, in stable order, after one divider at the end (none when all are
 * destructive). B-KIT-022.
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
 * The one popup menu (Dart `showKitMenu`, plan 06 §7.4), as screen state: shown while [expanded], anchored to [anchor]
 * (below its end edge; above when there is no room). Choosing an item closes the menu, then runs the item. Dismissing
 * (outside tap, Back) calls [onDismissRequest] and runs nothing. An empty [items] never opens.
 *
 * States: closed, open
 * Text: frame
 * Cap: Menu (320)
 * Motion: drops a few dp as it fades in at Full; Calm fades; Off appears at once
 * Replaces: showKitMenu, KitMenuPanel (lib/ui/kit/kit_menu.dart)
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
    Box(modifier) {
        anchor()
        if (expanded && items.isNotEmpty()) {
            val gutter = LocalKitTokens.current.labelGap
            val provider = remember(gutter) { MenuPosition(gutter) }
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                val motion = KitMotion.state
                val specs = KitMotion.specs
                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    shown,
                    enter = fadeIn(specs.quick()) + if (motion.travels) slideInVertically(specs.quick()) { -it / 16 } else fadeIn(specs.quick()),
                    exit = fadeOut(specs.exit()),
                ) {
                    KitMenuPanel(
                        items = items,
                        title = title,
                        onChoose = { item ->
                            onDismissRequest()
                            item.onSelect()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The menu's panel on its own (Dart `KitMenuPanel`): `surface2`, a hairline, at most 320 dp, rows of at least 48 dp.
 * Used by [KitMenu] and by captures; screens use [KitMenu].
 */
@Composable
public fun KitMenuPanel(
    items: List<KitMenuItem>,
    modifier: Modifier = Modifier,
    title: String? = null,
    onChoose: (KitMenuItem) -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val shape = KitShapeId.Panel.toShape()
    Column(
        modifier
            .widthIn(min = MENU_MIN_WIDTH_DP.dp, max = MENU_MAX_WIDTH_DP.dp)
            .kitSurface(kitSurfaceLook(SurfaceRequest(SurfaceRole.Sheet, level = io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Surface2), colors.surface2, colors.hairline, 1f), shape)
            .verticalScroll(rememberScrollState())
            .padding(vertical = Space.S1.dp)
            .semantics { if (title != null) paneTitle = title }
            .testTag("kit-menu"),
    ) {
        MenuLayout.arrange(items).forEach { entry ->
            when (entry) {
                KitMenuEntry.Divider ->
                    Box(Modifier.padding(vertical = Space.S1.dp).fillMaxWidth().height(1.dp).background(colors.hairline))
                is KitMenuEntry.Heading ->
                    KitText(
                        entry.group.heading.orEmpty(),
                        Modifier.padding(horizontal = tokens.gutter.dp, vertical = Space.S1.dp).semantics { heading() },
                        role = TypeRole.Caption,
                        tone = TextTone.Secondary,
                        kind = KitTextKind.Frame,
                    )
                is KitMenuEntry.Item -> MenuRow(entry.item, onChoose)
            }
        }
    }
}

@Composable
private fun MenuRow(item: KitMenuItem, onChoose: (KitMenuItem) -> Unit) {
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
        item.icon?.let { KitGlyph(it, ink, tokens.smallIcon.dp) }
        Column(Modifier.weight(1f)) {
            KitText(item.label, role = TypeRole.RowTitle, tone = if (on) TextTone.Primary else TextTone.Tertiary, kind = KitTextKind.Frame, maxLines = 2)
            item.enabled.reason?.let {
                KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 2)
            }
        }
        when (item.checked) {
            true -> KitGlyph(KitPartGlyphs.Check, colors.accent, tokens.smallIcon.dp)
            false -> Spacer(Modifier.size(tokens.smallIcon.dp))
            null -> Spacer(Modifier.width(0.dp))
        }
    }
}

/** Below the anchor, aligned to its end edge; above when there is no room below; kept inside the window by [margin]. */
private class MenuPosition(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin)),
            y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height)),
        )
    }
}
