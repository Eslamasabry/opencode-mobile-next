package io.github.eslamasabry.oc.ui.kit.swatch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSwatchInUse
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewCode
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewPrimary
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewSecondary
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewTitle
import io.github.eslamasabry.oc.ui.words.res.kitThemePreviewWorking
import org.jetbrains.compose.resources.stringResource

/**
 * One choice in a colour picker (Flutter `KitSwatch`, `KitSwatchGrid`; B-KIT-049): a theme miniature drawn in the
 * theme's own [KitColors] ([KitSwatch.Theme]) or an accent circle ([KitSwatch.Accent]). A swatch without [onClick]
 * must say why ([disabledReason]): unavailable is said, never faded.
 */
public sealed interface KitSwatch {
    public val label: String
    public val selected: Boolean
    public val onClick: (() -> Unit)?
    public val disabledReason: String?
    public val tag: String?

    public data class Theme(
        val colors: KitColors,
        override val label: String,
        override val selected: Boolean,
        override val onClick: (() -> Unit)?,
        override val disabledReason: String? = null,
        override val tag: String? = null,
    ) : KitSwatch

    public data class Accent(
        val color: Color,
        override val label: String,
        override val selected: Boolean,
        override val onClick: (() -> Unit)?,
        override val disabledReason: String? = null,
        override val tag: String? = null,
    ) : KitSwatch

    public val enabled: Boolean get() = onClick != null
}

/** Pure rules of the grid (Flutter `KitSwatchGrid.columnsFor`, `accentColumnsFor`, the roving focus). */
public object KitSwatchRules {
    /** The least width of a theme miniature (Dart `KitTokens.swatchMinWidth`). */
    public const val MIN_TILE_DP: Float = 112f
    public const val ACCENT_SPACING_DP: Float = 8f

    /** Columns from the width it has: 2 to 6; one fewer from text scale 1.3 so the names still fit. */
    public fun columnsFor(widthDp: Float, textScale: Float): Int {
        var columns = (widthDp / MIN_TILE_DP).toInt().coerceIn(2, 6)
        if (textScale >= 1.3f) columns = (columns - 1).coerceAtLeast(1)
        return columns
    }

    /** Accent circles: as many 48 dp targets, 8 dp apart, as fit. */
    public fun accentColumnsFor(widthDp: Float, targetDp: Float, spacingDp: Float = ACCENT_SPACING_DP): Int =
        ((widthDp + spacingDp) / (targetDp + spacingDp)).toInt().coerceAtLeast(1)

    /** The next swatch from [from] in steps of [step] that can take focus; null when none that way. */
    public fun nextEnabled(enabled: List<Boolean>, from: Int, step: Int): Int? {
        var i = from + step
        while (i >= 0 && i < enabled.size) {
            if (enabled[i]) return i
            i += step
        }
        return null
    }

    /** The one Tab stop: the selected swatch when it can take focus, else the first that can. */
    public fun initialActive(swatches: List<KitSwatch>): Int {
        val selected = swatches.indexOfFirst { it.selected && it.enabled }
        if (selected >= 0) return selected
        return swatches.indexOfFirst { it.enabled }.coerceAtLeast(0)
    }

    /** The target of an arrow, Home or End key from [index], or null for any other key. */
    public fun target(key: Key, index: Int, enabled: List<Boolean>, columns: Int, rtl: Boolean): Int? = when (key) {
        Key.DirectionDown -> nextEnabled(enabled, index, columns)
        Key.DirectionUp -> nextEnabled(enabled, index, -columns)
        Key.DirectionRight -> nextEnabled(enabled, index, if (rtl) -1 else 1)
        Key.DirectionLeft -> nextEnabled(enabled, index, if (rtl) 1 else -1)
        Key.MoveHome -> nextEnabled(enabled, -1, 1)
        Key.MoveEnd -> nextEnabled(enabled, enabled.size, -1)
        else -> null
    }
}

private class GridFocus(val index: Int, val focusable: Boolean, val requester: FocusRequester, val onKey: (Key) -> Boolean)

private val LocalGridFocus = compositionLocalOf<GridFocus?> { null }

/**
 * The swatch grid: a labelled group, columns from the space it has (start-aligned), one Tab stop, arrow keys move
 * between swatches and pass over disabled ones, Home and End go to the ends. All swatches are one kind (theme
 * miniatures or accent circles).
 *
 * States: default, selected, focused, hovered, disabled with reason
 * Text: frame (names)
 * Replaces: KitSwatchGrid, KitSwatch (lib/ui/kit/kit_swatch.dart)
 */
@Composable
public fun KitSwatchGrid(label: String, swatches: List<KitSwatch>, modifier: Modifier = Modifier) {
    kitAssert(swatches.all { it.enabled || !it.disabledReason.isNullOrBlank() }) {
        "KitSwatch: a swatch that cannot be chosen must say why (disabledReason)"
    }
    val accents = swatches.isNotEmpty() && swatches.all { it is KitSwatch.Accent }
    val tokens = LocalKitTokens.current
    val scale = kitTextScale()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val requesters = remember(swatches.size) { List(swatches.size) { FocusRequester() } }
    var active by remember(swatches.size) { mutableIntStateOf(KitSwatchRules.initialActive(swatches)) }
    var pending by remember { mutableStateOf<Int?>(null) }
    if (active >= swatches.size || swatches.getOrNull(active)?.enabled == false) active = KitSwatchRules.initialActive(swatches)
    LaunchedEffect(pending) {
        pending?.let { requesters[it].requestFocus() }
        pending = null
    }
    val enabled = swatches.map { it.enabled }
    BoxWithConstraints(modifier.fillMaxWidth().semantics { paneTitle = label }.testTag("kit-swatch-grid")) {
        val width = maxWidth.value
        val columns =
            if (accents) KitSwatchRules.accentColumnsFor(width, tokens.minTarget.toFloat())
            else KitSwatchRules.columnsFor(width, scale)
        val spacing = if (accents) KitSwatchRules.ACCENT_SPACING_DP else Space.S2.toFloat()
        val tileWidth = if (accents) tokens.minTarget.toFloat() else ((width - spacing * (columns - 1)) / columns)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
            swatches.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.dp)) {
                    for (i in row) {
                        val focus = GridFocus(i, i == active && swatches[i].enabled, requesters[i]) { key ->
                            val target = KitSwatchRules.target(key, i, enabled, columns, rtl)
                            if (target != null) {
                                active = target
                                pending = target
                            }
                            target != null || key in setOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight)
                        }
                        CompositionLocalProvider(LocalGridFocus provides focus) {
                            Box(Modifier.width(tileWidth.dp)) {
                                when (val s = swatches[i]) {
                                    is KitSwatch.Theme -> ThemeSwatch(s)
                                    is KitSwatch.Accent -> AccentSwatch(s)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Focus, keys and press of one swatch; [content] draws it from the hovered / focused state. */
@Composable
private fun Modifier.swatchInput(swatch: KitSwatch, interactions: MutableInteractionSource): Modifier {
    val focus = LocalGridFocus.current
    val inUse = stringResource(Res.string.kitSwatchInUse)
    val run = swatch.onClick
    var m = this
    if (swatch.tag != null) m = m.testTag(swatch.tag!!)
    if (focus != null) {
        m = m.focusRequester(focus.requester)
            .focusProperties { canFocus = focus.focusable }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else if ((e.key == Key.Spacebar || e.key == Key.Enter || e.key == Key.NumPadEnter) && run != null) {
                    run()
                    true
                } else focus.onKey(e.key)
            }
    }
    m = m.hoverable(interactions, enabled = swatch.enabled)
    if (run != null) m = m.clickable(interactions, indication = null, role = Role.RadioButton, onClick = run)
    // One node: its name exactly once (the drawn words below keep out of it), its state, and why when it can't.
    m = m.semantics(mergeDescendants = true) {
        contentDescription = swatch.label
        role = Role.RadioButton
        selected = swatch.selected
        if (swatch.selected) stateDescription = inUse
        if (!swatch.enabled) {
            disabled()
            swatch.disabledReason?.let { stateDescription = it }
        }
    }
    return m
}

@Composable
private fun ThemeSwatch(swatch: KitSwatch.Theme) {
    val colors = LocalKitColors.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val r = swatch.colors
    val shape = RoundedCornerShape(12.dp)
    val ringWidth = 2.dp
    Box(Modifier.swatchInput(swatch, interactions)) {
    Column(
        Modifier
            .clip(shape)
            .background(if (hovered) colors.surface2 else colors.surface1, shape)
            .padding(Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        val edge = if (swatch.selected) r.accent else r.hairline
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(8.dp))
                .background(r.ground).border(1.dp, edge, RoundedCornerShape(8.dp)).testTag("kit-swatch-ground"),
        ) {
            if (!swatch.enabled) {
                Box(Modifier.matchParentSizeCompat().background(r.surface3).testTag("kit-swatch-unavailable"))
            } else {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(r.surface1).testTag("kit-swatch-panel")) {
                        Box(Modifier.padding(4.dp).width(28.dp).height(4.dp).background(r.text1).testTag("kit-swatch-text-line"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(r.accent).testTag("kit-swatch-accent-dot"))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(r.success).testTag("kit-swatch-success-dot"))
                    }
                }
            }
            if (swatch.selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(r.accent).testTag("kit-swatch-check")) {
                    KitGlyph(KitPartGlyphs.Check, r.onAccent, 14.dp, Modifier.align(Alignment.Center))
                }
            }
        }
        KitText(swatch.label, Modifier.testTag("kit-swatch-label").clearAndSetSemantics {}, TypeRole.Secondary, kind = KitTextKind.Frame, maxLines = 2)
        if (!swatch.enabled) {
            // The reason wraps and is never cut off; a screen reader hears it once, as the swatch's state.
            KitText(
                swatch.disabledReason.orEmpty(),
                Modifier.testTag("kit-swatch-reason").clearAndSetSemantics {},
                TypeRole.Secondary,
                TextTone.Secondary,
                KitTextKind.Frame,
            )
        }
    }
        if (focused) Box(Modifier.matchParentSize().border(ringWidth, colors.accent, shape).testTag("kit-swatch-focus-ring"))
    }
}

@Composable
private fun AccentSwatch(swatch: KitSwatch.Accent) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val on = kitSwatchCheckColor(swatch.color)
    Box(Modifier.size(tokens.minTarget.dp).swatchInput(swatch, interactions)) {
    if (focused) Box(Modifier.matchParentSize().border(2.dp, colors.accent, CircleShape).testTag("kit-swatch-focus-ring"))
    Box(
        Modifier.matchParentSize()
            .clip(CircleShape)
            .background(if (hovered) colors.surface2 else Color.Transparent, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (swatch.enabled) swatch.color else colors.surface3)
            .then(if (swatch.selected) Modifier.border(2.dp, colors.text1, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (swatch.selected) {
            Box(Modifier.testTag("kit-swatch-check")) { KitGlyph(KitPartGlyphs.Check, on, tokens.smallIcon.dp) }
        }
    }
    }
}

/** The colour as 0xAARRGGBB. */
internal fun Color.toArgbInt(): Int {
    fun c(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (c(alpha) shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
}

/** The ink a check on [fill] is painted in (Dart `onColor`). */
public fun kitSwatchCheckColor(fill: Color): Color = Color(RoleDerivation.onColor(Argb(fill.toArgbInt())).v)

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().aspectRatio(1.6f)


/**
 * A small conversation drawn in a theme's own colours (Flutter `KitThemePreview`): a title, a working line, a code
 * line that stays left to right, a needs-you mark and the two buttons. One image to a screen reader, named [label].
 */
@Composable
public fun KitThemePreview(colors: KitColors, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    CompositionLocalProvider(LocalKitColors provides colors) {
        Column(
            modifier.fillMaxWidth().clip(shape).background(colors.ground, shape).padding(Space.S3.dp)
                .testTag("kit-theme-preview").clearAndSetSemantics { contentDescription = label; role = Role.Image },
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface1).padding(Space.S2.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
            ) {
                KitText(stringResource(Res.string.kitThemePreviewTitle), role = TypeRole.RowTitle, kind = KitTextKind.Frame, maxLines = 1)
                KitText(stringResource(Res.string.kitThemePreviewWorking), role = TypeRole.Secondary, tone = TextTone.Accent, kind = KitTextKind.Frame)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(colors.surface2).padding(Space.S2.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    KitText(stringResource(Res.string.kitThemePreviewCode), Modifier.testTag("kit-theme-preview-code"), TypeRole.Secondary, kind = KitTextKind.Frame)
                }
            }
            KitText(stringResource(Res.string.kitThemePreviewNeedsYou), role = TypeRole.Secondary, tone = TextTone.Attention, kind = KitTextKind.Frame)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(colors.accent).padding(horizontal = Space.S3.dp, vertical = Space.S1.dp).testTag("kit-theme-preview-primary")) {
                    KitText(stringResource(Res.string.kitThemePreviewPrimary), role = TypeRole.Secondary, tone = TextTone.OnAccent, kind = KitTextKind.Frame)
                }
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface3).padding(horizontal = Space.S3.dp, vertical = Space.S1.dp)) {
                    KitText(stringResource(Res.string.kitThemePreviewSecondary), role = TypeRole.Secondary, kind = KitTextKind.Frame)
                }
            }
        }
    }
}
