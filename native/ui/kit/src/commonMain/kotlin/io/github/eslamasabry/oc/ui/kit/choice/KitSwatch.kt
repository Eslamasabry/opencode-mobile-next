package io.github.eslamasabry.oc.ui.kit.choice

import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.color.Argb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * One swatch in a [KitSwatchGrid] (Dart `KitSwatch` / `KitSwatch.accent`; B-KIT-049). With [theme] it is a theme tile:
 * a miniature of [roles] (null roles, or no action, shows the unavailable miniature) with [label] under it. Without
 * it, it is an accent circle of [color] (null is the "Match theme" circle, drawn like an unavailable one).
 */
public data class KitSwatch(
    val id: String,
    val label: String,
    val color: Color?,
    val selected: Boolean,
    /** Null = the swatch cannot be chosen now, and [disabledReason] must say why. */
    val onPressed: (() -> Unit)?,
    val disabledReason: String? = null,
    /** The theme this tile offers (Dart `KitSwatch.roles`); only read when [theme] is true. */
    val roles: Roles? = null,
    /** True for a theme tile (Dart `KitSwatch.new`), false for an accent circle (`KitSwatch.accent`). */
    val theme: Boolean = false,
)

/**
 * The colour swatch picker (Dart `KitSwatchGrid`): theme tiles in columns from the width, accent circles as 48 dp
 * targets 8 dp apart; one Tab stop (the selected swatch), arrows move focus and skip disabled swatches, Enter or Space
 * presses. [inUseLabel] is the spoken value of the chosen swatch ("In use").
 *
 * Cap: reading
 */
@Composable
public fun KitSwatchGrid(
    label: String,
    swatches: List<KitSwatch>,
    inUseLabel: String,
    modifier: Modifier = Modifier,
) {
    kitAssert(swatches.all { it.onPressed != null || !it.disabledReason.isNullOrBlank() }) {
        "KitSwatchGrid: a swatch with no action must give a reason (B-KIT-049)"
    }
    kitAssert(swatches.isEmpty() || swatches.all { it.theme == swatches.first().theme }) {
        "KitSwatchGrid: children must all be theme tiles or all accents, never a mix"
    }
    val tokens = LocalKitTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val requesters = remember(swatches.size) { List(swatches.size) { FocusRequester() } }
    fun initial(): Int {
        val chosen = swatches.indexOfFirst { it.selected && it.onPressed != null }
        if (chosen >= 0) return chosen
        return swatches.indexOfFirst { it.onPressed != null }.coerceAtLeast(0) // NV: first swatch that can take focus
    }
    var active by remember(swatches.size) { mutableIntStateOf(initial()) }
    var moveTo by remember { mutableIntStateOf(-1) } // NV: -1 = no pending focus move
    if (active !in swatches.indices || swatches.getOrNull(active)?.onPressed == null) active = initial()
    LaunchedEffect(moveTo) {
        if (moveTo in requesters.indices) {
            requesters[moveTo].requestFocus()
            moveTo = -1
        }
    }
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().semantics { contentDescription = label }) {
        val accents = swatches.isNotEmpty() && !swatches.first().theme
        val width = maxWidth.value
        val scale = kitTextScale()
        val columns =
            if (accents) swatchAccentColumns(width, tokens.minTarget.toFloat(), Space.S2.toFloat())
            else swatchColumns(width, scale)
        fun move(from: Int, step: Int): Int? {
            var i = from + step
            while (i in swatches.indices) {
                if (swatches[i].onPressed != null) return i
                i += step
            }
            return null
        }
        fun go(target: Int?): Boolean {
            if (target != null) {
                active = target
                moveTo = target
            }
            return true
        }
        val spacing = (if (accents) Space.S2 else Space.S3).dp
        val tileWidth = ((width - Space.S3 * (columns - 1)) / columns).let { kotlin.math.floor(it).coerceAtLeast(1f) }
        @Composable
        fun tile(index: Int) {
            val swatch = swatches[index]
            SwatchBody(
                swatch,
                inUseLabel,
                Modifier.focusRequester(requesters[index])
                    .focusProperties { canFocus = swatch.onPressed != null && (index == active || moveTo == index) }
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (e.key) {
                                Key.Enter, Key.NumPadEnter, Key.Spacebar -> swatch.onPressed?.let { it(); true } ?: false
                                Key.DirectionDown -> go(move(index, columns))
                                Key.DirectionUp -> go(move(index, -columns))
                                Key.DirectionRight -> go(move(index, if (rtl) -1 else 1))
                                Key.DirectionLeft -> go(move(index, if (rtl) 1 else -1))
                                Key.MoveHome -> go(move(-1, 1))
                                Key.MoveEnd -> go(move(swatches.size, -1))
                                else -> false
                            }
                        }
                    },
            )
        }
        // Theme tiles: the Wrap shrinks to its run and the host centres it; accents fill the width, start-aligned.
        Column(
            Modifier.then(if (accents) Modifier.fillMaxWidth() else Modifier.align(Alignment.TopCenter)),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            swatches.indices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { index ->
                        if (accents) tile(index) else Box(Modifier.width(tileWidth.dp)) { tile(index) }
                    }
                }
            }
        }
    }
}

/** Theme tiles per row: [FlutterKitTokens.SWATCH_MIN_WIDTH] wide at least, 2 to 6, one fewer from 1.3x text. */
internal fun swatchColumns(widthDp: Float, textScale: Float): Int {
    var columns = (widthDp / FlutterKitTokens.SWATCH_MIN_WIDTH).toInt().coerceIn(2, 6) // F: lib/ui/kit/kit_swatch.dart:770
    if (textScale >= 1.3f) columns = maxOf(columns - 1, 1) // F: lib/ui/kit/kit_swatch.dart:771
    return columns
}

/** Accent circles per row: as many 48 dp targets 8 dp apart as fit, never fewer than one. */
internal fun swatchAccentColumns(widthDp: Float, target: Float, spacing: Float): Int =
    maxOf(((widthDp + spacing) / (target + spacing)).toInt(), 1)

@Composable
private fun SwatchBody(swatch: KitSwatch, inUseLabel: String, modifier: Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val enabled = swatch.onPressed != null
    Box(
        modifier
            .focusable(enabled, interactions)
            .selectable(
                selected = swatch.selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactions,
                indication = null,
                onClick = { swatch.onPressed?.invoke() },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = swatch.label
                if (swatch.selected) stateDescription = inUseLabel
                else swatch.disabledReason?.let { stateDescription = it }
                if (!enabled) disabled()
            },
    ) {
        if (swatch.theme) ThemeTile(swatch, hovered && enabled, focused) else AccentTile(swatch, focused)
    }
}

/** The focus ring: two physical pixels in the accent, outside the box. Dart `KitTokens.focusRingWidth`. */
@Composable
private fun Modifier.focusRing(on: Boolean, shape: Shape): Modifier {
    if (!on) return this
    val ring = kitFocusRingWidth()
    val color = LocalKitColors.current.accent
    return this.drawBehind {
        val w = ring.toPx()
        drawCircle(color, radius = size.minDimension / 2 + w / 2, // NV: half the box plus half a stroke
            style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    }
}

@Composable
private fun hairline(): Dp = kitHairline()

@Composable
private fun ThemeTile(swatch: KitSwatch, hovered: Boolean, focused: Boolean) {
    val colors = LocalKitColors.current
    val enabled = swatch.onPressed != null
    val shape = KitShapeId.Panel.shape()
    val fill = if (hovered) (if (colors.dark) colors.surface2 else colors.surface3) else colors.surface1 // F: lib/ui/kit/kit_swatch.dart:304,305
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Control, KitShapeId.Panel,
                if (hovered) SurfaceLevel.Surface2 else SurfaceLevel.Surface1,
                ownFill = fill, selected = enabled && swatch.selected,
            ),
        )
    val border = if (enabled && swatch.selected) colors.accent else colors.hairline // F: lib/ui/kit/kit_swatch.dart:344
    Column(
        Modifier
            .fillMaxWidth()
            .then(look.modifier)
            .border(hairline(), border, shape)
            .heightIn(min = FlutterKitTokens.MIN_TARGET.dp)
            .padding(Space.S2.dp + hairline()), // F: lib/ui/kit/kit_swatch.dart:378
    ) {
        Box(Modifier.fillMaxWidth().height(FlutterKitTokens.SWATCH_PREVIEW_HEIGHT.dp).clip(KitShapeId.Tile.shape())) {
            val offered = swatch.roles
            if (offered != null && enabled) Miniature(offered) else UnavailableMiniature()
        }
        Spacer(Modifier.height(Space.S2.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                KitText(
                    KitBidi.auto(swatch.label), role = TypeRole.Label, kind = KitTextKind.Frame, maxLines = 2, // F: lib/ui/kit/kit_swatch.dart:513
                    tone = if (enabled) TextTone.Secondary else TextTone.Tertiary,
                )
            }
            if (swatch.selected) {
                Spacer(Modifier.width(Space.S1.dp))
                KitGlyph(KitIcons.check, colors.accent, FlutterKitTokens.SMALL_ICON_SIZE.dp)
            }
        }
        val reason = swatch.disabledReason
        if (!enabled && reason != null) {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(reason, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        }
    }
}

@Composable
private fun Miniature(roles: Roles) {
    val ground = Color(roles.ground.v)
    val panel = Color(roles.surface1.v)
    val line = Color(roles.text1.v)
    Row(Modifier.fillMaxSize().background(ground).padding(Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).background(panel, KitShapeId.Tile.shape()).padding(horizontal = Space.S2.dp, vertical = Space.S3.dp)) {
            Box(
                Modifier.fillMaxWidth().height(FlutterKitTokens.PROGRESS_BAR_HEIGHT.dp)
                    .background(line, RoundedCornerShape(FlutterKitTokens.PROGRESS_BAR_RADIUS.dp)),
            )
        }
        Spacer(Modifier.width(Space.S2.dp))
        Box(Modifier.size(FlutterKitTokens.SWATCH_DOT.dp).background(Color(roles.accent.v), CircleShape))
        Spacer(Modifier.width(Space.S1.dp))
        Box(Modifier.size(FlutterKitTokens.SWATCH_DOT.dp).background(Color(roles.success.v), CircleShape))
    }
}

@Composable
private fun UnavailableMiniature() {
    val colors = LocalKitColors.current
    Box(Modifier.fillMaxSize().background(colors.surface3), contentAlignment = Alignment.Center) {
        KitGlyph(KitIcons.sparkle, colors.text3, FlutterKitTokens.SMALL_ICON_SIZE.dp)
    }
}

@Composable
private fun AccentTile(swatch: KitSwatch, focused: Boolean) {
    val colors = LocalKitColors.current
    val enabled = swatch.onPressed != null
    val dot = swatch.color
    val fill = if (enabled && dot != null) dot else colors.surface3 // F: lib/ui/kit/kit_swatch.dart:453
    Box(
        Modifier.size(FlutterKitTokens.MIN_TARGET.dp).focusRing(focused, CircleShape), // F: lib/ui/kit/kit_swatch.dart:481
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(if (swatch.selected) Modifier.border(hairline(), colors.text1, CircleShape).padding(hairline()) else Modifier) // F: lib/ui/kit/kit_swatch.dart:492
                .size(FlutterKitTokens.MARK_SIZE.dp) // F: lib/ui/kit/kit_swatch.dart:460
                .background(fill, CircleShape)
                .border(hairline(), colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (swatch.selected) {
                val on = if (dot != null) Color(RoleDerivation.onColor(Argb(dot.toArgb())).v) else colors.text1
                KitGlyph(KitIcons.check, on, FlutterKitTokens.SMALL_ICON_SIZE.dp)
            }
        }
    }
}
