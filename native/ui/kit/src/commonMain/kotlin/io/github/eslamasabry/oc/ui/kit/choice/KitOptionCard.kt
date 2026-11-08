package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.*
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import androidx.compose.runtime.SideEffect
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSample
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSwatchInUse
import org.jetbrains.compose.resources.stringResource

/** Sizes a [KitOptionCard] host gives it inside a carousel (Dart `KitOptionCard.themeHeight` / `lookHeight`). */
public object KitOptionCardSizes {
    /** The height a theme swatch takes in a carousel at normal text. */
    public const val THEME_HEIGHT: Float = 168f // F: lib/ui/kit/kit_option_card.dart:164

    /** The height a look card takes in a carousel at normal text. */
    public const val LOOK_HEIGHT: Float = 190f // F: lib/ui/kit/kit_option_card.dart:167
}

/** The glowing-border style a glow card draws (Dart `KitGlowStyle`): Classic's travelling head, or the soft ring. */
public enum class KitGlowSampleStyle { Classic, SoftRing }

/**
 * A border drawn at its exact width inside the box, as Flutter's `Border.all` paints it: Compose's `border` rounds a
 * stroke up to whole pixels, which turns the third-pixel hairline of the 1x goldens into a full line.
 */
private fun Modifier.exactEdge(width: Dp, color: Color, shape: Shape): Modifier =
    drawWithContent {
        drawContent()
        val w = width.toPx()
        if (w <= 0f || color.alpha == 0f) return@drawWithContent
        val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
        translate(w / 2f, w / 2f) { drawOutline(outline, color, style = Stroke(w)) } // NV: half a stroke, so it lies inside the box
    }

/**
 * Insets the content by a border of [edge], as a Flutter Container pads its child by its border, in whole pixels:
 * both sides together take two of [most] (the widest edge the part draws) rounded, the leading side one [edge]
 * rounded. A chosen card then keeps its neighbours' height at every density (the kit's spacing steps hold) and its
 * content lands within a fraction of a pixel of Flutter's.
 */
private fun Modifier.edgeInset(edge: Dp, most: Dp): Modifier =
    layout { m, c ->
        val total = (most.toPx() * 2).roundToInt() // NV: one edge on each side
        val lead = edge.toPx().roundToInt().coerceAtMost(total)
        val p = m.measure(c.offset(-total, -total))
        layout(p.width + total, p.height + total) { p.placeRelative(lead, lead) }
    }

/** The card's semantics: one of a mutually exclusive group, "In use" when chosen (Dart `l10n.kitSwatchInUse`). */
@Composable
private fun Modifier.optionSemantics(label: String, selected: Boolean): Modifier {
    val inUse = stringResource(Res.string.kitSwatchInUse)
    return semantics {
        role = Role.RadioButton
        this.selected = selected
        contentDescription = label
        if (selected) stateDescription = inUse
    }
}

/**
 * The card's own frame (Dart `KitOptionCard._buildContent`, `own`/`asked`/`frame`/`chosenRing`): a content surface in
 * the person's Style. Solid draws surface1 with a hairline, or the accent focus ring when chosen; a style that sets
 * panels bare on the page (Paper) is asked for the raised step; any other style keeps its own face and, chosen, the
 * accent ring over it.
 */
@Composable
private fun optionFrame(selected: Boolean): Modifier {
    val colors = LocalKitColors.current
    val asked = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, ownFill = colors.surface1, selected = selected))
    val raised =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, SurfaceLevel.Surface2, ownFill = colors.surface1, selected = selected)
        )
    val look = if (asked.paints) asked else raised
    val solid = LocalKitStyle.current.style == Style.Solid
    val glass = LocalKitStyle.current.style == Style.Glass
    val edge = if (selected) kitFocusRingWidth() else kitHairline()
    val ring =
        when {
            solid -> Modifier.exactEdge(edge, if (selected) colors.accent else colors.hairline, look.shape)
            // Glass's chosen face draws its own accent rim on its own outline: a second ring over it never matched.
            selected && !glass -> Modifier.exactEdge(edge, colors.accent, look.shape)
            else -> Modifier
        }
    // Flutter insets the content by the edge's width (a Container pads its child by its border). The native card
    // insets it by the chosen ring's width in both states, so a chosen card and its neighbours keep one height (a row of
    // cards is as tall as Flutter's tallest, the chosen one) and the rows keep the kit's spacing steps.
    return look.modifier.then(ring)
}

/** The chosen card's check (Dart `_CheckBadge`): a 20 dp accent disc that fades in from slightly smaller. */
@Composable
private fun CheckBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val shown by animateFloatAsState(if (selected) 1f else 0f, KitMotion.specs.enter(), label = "option-check")
    val scale by animateFloatAsState(if (selected) 1f else MotionTokens.MARK_FROM, KitMotion.specs.enter(), label = "option-check-scale")
    Box(
        modifier
            .size(Space.S5.dp)
            .graphicsLayer {
                alpha = shown
                scaleX = scale
                scaleY = scale
            }
            .background(colors.accent, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        KitGlyph(KitIcons.check, colors.onAccent, (Space.S3 + Space.S1 / 2f).dp) // F: lib/ui/kit/kit_option_card.dart:381
    }
}

/**
 * Flutter `KitOptionCard.theme`: a compact swatch in the candidate's own colours (its ground, a stripe of its main
 * colour and a dot of its partner colour at its foot), the name under it. The ring and the check follow the current
 * theme. Disabled (a theme this phone cannot offer) says [disabledReason] on the swatch, never faded. [dark] picks the
 * candidate's dark or light palette; null follows the current one.
 *
 * States: selected, disabled.
 * Cap: reading
 */
@Composable
public fun KitThemeOptionCard(
    pack: PackId,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    dark: Boolean? = null,
) {
    val frame = LocalKitColors.current
    val isDark = dark ?: frame.dark
    val roles = remember(pack, isDark) { ThemeResolver.roles(pack, null, isDark, false, null) }
    val swatch = roles.toKitColors()
    val chosen = selected && enabled
    // Inside the card's edge; never below square (Corners › Sharp).
    val radius = RoundedCornerShape((FlutterKitTokens.CARD_RADIUS - Space.S1).coerceAtLeast(0).dp)
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, ownFill = swatch.ground, color = swatch.ground, selected = chosen)
        )
    val ringWidth by animateDpAsState(if (chosen) kitFocusRingWidth() else kitHairline(), KitMotion.specs.enter(), label = "theme-ring")
    val ringColor by animateColorAsState(if (chosen) frame.accent else frame.hairline, KitMotion.specs.enter(), label = "theme-ring-colour")
    KitTappable(
        onClick = onSelect,
        modifier = modifier.kitMaxWidth(CapRole.Reading).optionSemantics(label, selected),
        enabled = enabled,
        disabledReason = disabledReason,
        shape = KitShapeId.Card,
    ) {
        Column(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            Box(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .then(look.modifier)
                    .clip(radius)
                    .background(swatch.ground)
                    .exactEdge(ringWidth, ringColor, radius)
            ) {
                if (!enabled && disabledReason != null) {
                    // Unavailable is said on the swatch, not faded (STATE-8).
                    Box(
                        Modifier.fillMaxSize()
                            .background(frame.surface1.copy(alpha = .86f)) // F: lib/ui/kit/kit_option_card.dart:524
                            .padding(Space.S2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KitText(
                            disabledReason,
                            role = TypeRole.Caption,
                            tone = TextTone.Secondary,
                            textAlign = TextAlign.Center,
                            maxLines = 4, // F: lib/ui/kit/kit_option_card.dart:533
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(Space.S3.dp)) {
                        CheckBadge(selected, Modifier.align(Alignment.End))
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                Box(
                                    Modifier.fillMaxWidth(.72f) // F: lib/ui/kit/kit_option_card.dart:451
                                        .height((Space.S1 + 2).dp) // F: lib/ui/kit/kit_option_card.dart:453
                                        .background(swatch.accent, RoundedCornerShape(Space.S1.dp))
                                )
                            }
                            Spacer(Modifier.width(Space.S2.dp))
                            Box(Modifier.size(Space.S3.dp).background(swatch.partner, CircleShape))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.S2.dp))
            KitText(
                label,
                Modifier.padding(start = Space.S1.dp),
                role = TypeRole.RowTitle,
                tone = if (enabled) TextTone.Primary else TextTone.Tertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The body every non-theme card shares (Dart `KitOptionCard._buildContent`): the media (or the plain card's icon
 * line), the name with the check, a caption and a reason, inside the card's frame.
 */
@Composable
private fun OptionCardBody(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    disabledReason: String?,
    caption: String? = null,
    icon: ImageVector? = null,
    fills: Boolean = false,
    sayReason: Boolean = true,
    media: (@Composable () -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val frame = optionFrame(selected)
    val ring = kitFocusRingWidth()
    // A look's name may take two lines in a narrow cell at ordinary text (large text keeps the sample its room).
    val lines = if (fills && LocalDensity.current.fontScale < 1.5f) 2 else 1 // F: lib/ui/kit/kit_option_card.dart:224,225
    KitTappable(
        onClick = onSelect,
        // The face (fill, edge and the style's shadow) sits outside the press clip, so its shadow is whole.
        modifier = modifier.kitMaxWidth(CapRole.Reading).optionSemantics(label, selected).then(frame),
        enabled = enabled,
        disabledReason = disabledReason,
        shape = KitShapeId.Card,
    ) {
        Column(
            Modifier.then(if (fills) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .edgeInset(if (selected) ring else kitHairline(), ring)
                .padding(Space.S3.dp)
                .clearAndSetSemantics {}
        ) {
            when {
                icon != null -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        KitGlyph(icon, if (selected) colors.accent else colors.text2, LocalKitTokens.current.smallIcon.dp)
                        Spacer(Modifier.weight(1f))
                        CheckBadge(selected)
                    }
                    Spacer(Modifier.height(Space.S2.dp))
                }
                fills -> {
                    Box(Modifier.fillMaxWidth().weight(1f)) { media?.invoke() }
                    Spacer(Modifier.height(Space.S2.dp))
                }
                media != null -> {
                    Box(Modifier.alpha(if (enabled) 1f else .45f)) { media() } // F: lib/ui/kit/kit_option_card.dart:257
                    Spacer(Modifier.height(Space.S2.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitText(
                    label,
                    Modifier.weight(1f),
                    role = TypeRole.RowTitle,
                    tone = if (enabled) TextTone.Primary else TextTone.Tertiary,
                    maxLines = lines,
                )
                if (icon == null) CheckBadge(selected)
            }
            caption?.let { KitText(it, role = TypeRole.Secondary, maxLines = 3) } // F: lib/ui/kit/kit_option_card.dart:263
            if (sayReason) disabledReason?.let { KitText(it, role = TypeRole.Secondary, maxLines = 3) } // F: lib/ui/kit/kit_option_card.dart:266
        }
    }
}

/**
 * Flutter `KitOptionCard.surface`: a small sample made the way [style] makes surfaces, whatever style is in force.
 * Disabled, its reason is the host's to say once under the row; it is still announced.
 */
@Composable
public fun KitStyleOptionCard(
    style: Style,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    OptionCardBody(label, selected, onSelect, modifier, enabled, disabledReason, sayReason = false) { StyleSample(style) }
}

/**
 * Flutter `KitOptionCard.look`: a still frame of a live look behind a tiny mock conversation, filling the card, with
 * the look's name under it. [look] is the look's id; null is "Off" (the conversation on the plain ground). The card
 * fills the cell its grid gives it.
 *
 * States: selected, disabled.
 * Cap: reading
 */
@Composable
public fun KitLookOptionCard(
    look: String?,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    OptionCardBody(label, selected, onSelect, modifier, enabled, disabledReason, fills = true) {
        io.github.eslamasabry.oc.ui.kit.live.KitLiveLookStill(look, Modifier.fillMaxSize())
    }
}

/** Flutter `KitOptionCard.plain`: an icon and the check, the name, and one line of words ([caption]). */
@Composable
public fun KitPlainOptionCard(
    label: String,
    caption: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    OptionCardBody(label, selected, onSelect, modifier.fillMaxWidth(), enabled.isEnabled, enabled.reason, caption = caption, icon = icon)
}

/**
 * Flutter `KitOptionCard.glow`: the message box with its border drawn the way [style] draws it, so the two read apart
 * at a glance: Classic is a thin dim line with one bright head on it; Soft ring is a full bright line inside a wide soft
 * wash. A null [style] is "Off": the box with its plain edge. [caption] says it in words.
 *
 * States: selected.
 * Cap: reading
 */
@Composable
public fun KitGlowOptionCard(
    style: KitGlowSampleStyle?,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    OptionCardBody(label, selected, onSelect, modifier, enabled = true, disabledReason = null, caption = caption) { GlowSample(style) }
}

/** Dart `_GlowSample`: the message box with one glow style's border. */
@Composable
private fun GlowSample(style: KitGlowSampleStyle?) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val soft = style == KitGlowSampleStyle.SoftRing
    val off = style == null
    val shape = RoundedCornerShape(tokens.composerRadius.dp)
    val ring = kitFocusRingWidth()
    val edge = when {
        off -> colors.hairline
        soft -> colors.accent
        else -> colors.accent.copy(alpha = .35f) // F: lib/ui/kit/kit_option_card.dart:801
    }
    val edgeWidth = if (soft) ring * 1.5f else kitHairline() // F: lib/ui/kit/kit_option_card.dart:803
    // Flutter's test harness draws a BoxShadow without its blur (debugDisableShadows), so the goldens show the wash as
    // a hard band of its spread; a still-frame render (the golden gate) draws it the same way.
    val hard = LocalKitMotion.current.stillFrame
    val wash =
        if (soft && hard) {
            val band = colors.accent.copy(alpha = .55f) // F: lib/ui/kit/kit_option_card.dart:809
            val spread = (Space.S1 / 2f).dp // F: lib/ui/kit/kit_option_card.dart:811
            Modifier.drawBehind {
                val s = spread.toPx()
                val outline = shape.createOutline(Size(size.width + s * 2, size.height + s * 2), layoutDirection, this) // NV: the spread on both sides
                translate(-s, -s) { drawOutline(outline, band) }
            }
        } else if (soft) {
            Modifier.dropShadow(
                shape,
                Shadow(
                    radius = Space.S4.dp,
                    color = colors.accent.copy(alpha = .55f), // F: lib/ui/kit/kit_option_card.dart:809
                    spread = (Space.S1 / 2f).dp, // F: lib/ui/kit/kit_option_card.dart:811
                ),
            )
        } else Modifier
    // Room for the wash so the card does not clip it.
    Box(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .height((Space.S6 * 1.5f).dp) // F: lib/ui/kit/kit_option_card.dart:788
                .then(wash)
                .background(colors.ground, shape)
                .exactEdge(edgeWidth, edge, shape)
                .padding(start = Space.S3.dp, end = Space.S1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Space.S6.dp, Space.S1.dp).background(colors.text3, RoundedCornerShape(Space.S1.dp)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(Space.S5.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(Space.S2.dp).background(colors.onAccent))
            }
        }
        if (!soft && !off) {
            // Classic's bright head, on the top edge.
            Box(
                Modifier.offset(x = (Space.S6 * 1.4f).dp, y = -ring) // F: lib/ui/kit/kit_option_card.dart:856
                    .size(Space.S6.dp, ring * 2) // F: lib/ui/kit/kit_option_card.dart:860
                    .then(if (hard) Modifier else Modifier.dropShadow(
                        RoundedCornerShape(Space.S1.dp),
                        Shadow(radius = Space.S2.dp, color = colors.accent.copy(alpha = .8f)), // F: lib/ui/kit/kit_option_card.dart:866
                    ))
                    .background(colors.accent, RoundedCornerShape(Space.S1.dp))
            )
        }
    }
}

/** One swatch of a [KitAccentRow]: its stored [id], its [color] as it will be used here and its name for a screen reader. */
public data class KitAccentSwatch(val id: String, val color: Color, val label: String)

/**
 * The Accent choice of Settings › Appearance (Dart `KitAccentRow`): a "Match theme" tile first (the theme's own main
 * colour, [themeAccent]) and then round swatches, one line that scrolls sideways when it does not fit. The chosen one
 * has a ring in its own colour and a check, so choosing is looking, never colour alone. Every swatch is its own 48 dp
 * target and one of a mutually exclusive group.
 *
 * [grid] (Dart `KitAccentRow.grid`): every swatch in sight, four to a row, each with its name under it and the chosen
 * one ringed in the accent; nothing scrolls sideways. It sits on the host's rails (no gutter of its own).
 *
 * [selected] is a swatch's id, or null for "Match theme".
 *
 * States: none — every swatch can always be chosen.
 * Cap: reading
 */
@Composable
public fun KitAccentRow(
    semanticsLabel: String,
    matchLabel: String,
    themeAccent: Color,
    swatches: List<KitAccentSwatch>,
    selected: String?,
    onChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    grid: Boolean = false,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val matchOn = selected == null
    val group = modifier.kitMaxWidth(CapRole.Reading).semantics { contentDescription = semanticsLabel }
    if (grid) {
        val cells = buildList<@Composable () -> Unit> {
            add {
                AccentCircle(KitAccentSwatch("", themeAccent, matchLabel), matchOn, onTap = { if (!matchOn) onChanged(null) }, caption = matchLabel, ring = colors.accent)
            }
            swatches.forEach { s ->
                add { AccentCircle(s, s.id == selected, onTap = { if (s.id != selected) onChanged(s.id) }, caption = s.label, ring = colors.accent) }
            }
        }
        val perRow = 4 // F: lib/ui/kit/kit_option_card.dart:983
        Column(group.fillMaxWidth()) {
            cells.chunked(perRow).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(Space.S2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    for (i in 0 until perRow) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { row.getOrNull(i)?.invoke() }
                    }
                }
            }
        }
        return
    }
    val matchLook = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Button, ownFill = colors.surface1, selected = matchOn))
    val solid = LocalKitStyle.current.style == Style.Solid
    val matchWidth = if (matchOn) kitFocusRingWidth() else kitHairline()
    val matchEdge =
        when {
            solid -> Modifier.exactEdge(matchWidth, if (matchOn) colors.accent else colors.hairline, matchLook.shape)
            matchOn -> Modifier.exactEdge(matchWidth, colors.accent, matchLook.shape)
            else -> Modifier
        }
    Row(
        group.horizontalScroll(rememberScrollState()).padding(horizontal = tokens.gutter.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tile is as tall as a swatch's target; its words may take a second line at larger text.
        KitTappable(
            onClick = { if (!matchOn) onChanged(null) },
            modifier = Modifier.optionSemantics(matchLabel, matchOn).then(matchLook.modifier).then(matchEdge),
            shape = KitShapeId.Button,
        ) {
            Column(
                Modifier.defaultMinSize((tokens.minTarget + Space.S3).dp, (tokens.minTarget + Space.S3).dp)
                    .edgeInset(if (solid) matchWidth else 0.dp, if (solid) kitHairline() else 0.dp)
                    .padding(Space.S2.dp)
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(Space.S4.dp).background(themeAccent, CircleShape))
                Spacer(Modifier.height(Space.S1.dp))
                KitText(
                    matchLabel,
                    Modifier.widthIn(max = (tokens.minTarget * 1.2f).dp), // F: lib/ui/kit/kit_option_card.dart:1071
                    role = TypeRole.Caption,
                    tone = TextTone.Secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2, // F: lib/ui/kit/kit_option_card.dart:1077
                )
            }
        }
        Spacer(Modifier.width(Space.S2.dp))
        swatches.forEach { s -> AccentCircle(s, s.id == selected, onTap = { if (s.id != selected) onChanged(s.id) }) }
    }
}

/** Dart `_AccentCircle`: one round swatch in its 48 dp target; chosen, a ring stands off it and a check sits on it. */
@Composable
private fun AccentCircle(swatch: KitAccentSwatch, selected: Boolean, onTap: () -> Unit, caption: String? = null, ring: Color? = null) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val focus = kitFocusRingWidth()
    val circle = (tokens.minTarget - Space.S3).dp
    val inner = if (selected) circle - (Space.S1 * 1.5f).dp - focus * 2 else circle // F: lib/ui/kit/kit_option_card.dart:1211,1212
    val gap = if (selected) (Space.S1 * .75f).dp else 0.dp // F: lib/ui/kit/kit_option_card.dart:1197
    val edgeWidth = if (selected) focus else kitHairline()
    val edge = if (selected) ring ?: swatch.color else colors.hairline
    val shown by animateFloatAsState(if (selected) 1f else 0f, KitMotion.specs.quick(), label = "accent-check")
    val scale by animateFloatAsState(if (selected) 1f else MotionTokens.MARK_FROM, KitMotion.specs.quick(), label = "accent-check-scale")
    val mark = Color(RoleDerivation.onColor(Argb(swatch.color.toArgb())).v)
    // The named form keeps its caption whole: a circle press shape would clip the words under the swatch.
    val press = if (caption == null) KitShapeId.Circle else KitShapeId.Button
    KitTappable(onClick = onTap, modifier = Modifier.optionSemantics(swatch.label, selected), shape = press) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(tokens.minTarget.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.exactEdge(edgeWidth, edge, CircleShape).padding(edgeWidth + gap),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(inner).clip(CircleShape).background(swatch.color), contentAlignment = Alignment.Center) {
                        KitGlyph(
                            KitIcons.check,
                            mark,
                            tokens.smallIcon.dp,
                            Modifier.graphicsLayer {
                                alpha = shown
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                    }
                }
            }
            if (caption != null) {
                KitText(
                    caption,
                    role = TypeRole.Caption,
                    tone = if (selected) TextTone.Primary else TextTone.Secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2, // F: lib/ui/kit/kit_option_card.dart:1166
                )
            }
        }
    }
}



@Composable
private fun StyleSample(style: Style) {
    val appearance = LocalKitAppearance.current
    val dark = LocalKitColors.current.dark
    val base = ThemeResolver.roles(appearance.pack, null, dark, false, null)
    val roles = StyleResolver.adjust(base, style)
    val spec = StyleResolver.spec(style)
    val knobs = spec.tokens.defaults.copy(density = Density.Compact)
    val state = KitStyleState(style, spec, knobs)
    val colors = roles.toKitColors()
    val stripHeight = (Space.S6 * 2.6f).dp // F: lib/ui/kit/kit_option_card.dart:689
    CompositionLocalProvider(
        LocalKitRoles provides roles,
        LocalKitColors provides colors,
        LocalKitStyle provides state,
    ) {
        if (style == Style.Signal) {
            KitSignalSample(Modifier.height(stripHeight))
        } else {
            fun radius(value: Float) =
                RoundedCornerShape(
                    StyleResolver.corner(value, knobs.corners, spec.tokens.sharpRadius).dp
                )
            val stripShape = radius((FlutterKitTokens.CARD_RADIUS - Space.S1).toFloat())
            val cardShape =
                radius(FlutterKitTokens.CARD_RADIUS / 2f) // F: lib/ui/kit/kit_option_card.dart:668
            val cardRequest =
                    SurfaceRequest(
                        roles,
                        SurfaceRole.Content,
                        knobs,
                        SurfaceLevel.Surface2,
                        radii =
                            Radii.all(
                                StyleResolver.corner(
                                    FlutterKitTokens.CARD_RADIUS /
                                        2f, // F: lib/ui/kit/kit_option_card.dart:668
                                    knobs.corners,
                                    spec.tokens.sharpRadius,
                                )
                            ),
                    )
            val card = spec.resolve(cardRequest)
            // Glass: two spots of the theme's colours on the strip, partly under the card, and the card frosting them
            // (GLASS_REF kit_option_card.dart:660-707), so a frosted pane reads apart from an opaque one. The other
            // samples keep their plain strip.
            val glass = style == Style.Glass && card != null
            val backdrop = io.github.eslamasabry.oc.ui.kit.foundation.rememberKitGlassBackdrop()
            val accent = colors.accent
            val partner = colors.partner
            val ground = colors.ground
            val strip =
                if (!glass) {
                    Modifier.background(ground)
                } else {
                    Modifier.kitGlassBackdropSource(backdrop) {
                        val unit = Space.S1.dp.toPx()
                        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                        fun x(fromStart: Float) = if (rtl) size.width - fromStart else fromStart
                        drawRect(ground)
                        // A little colour for the pane to frost (owner 2026-11-08: the two blobs read as smudges): a soft
                        // wash from the accent at the top end to the partner at the bottom start.
                        drawRect(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                0f to accent.copy(alpha = .55f),
                                .55f to partner.copy(alpha = .30f),
                                1f to partner.copy(alpha = .12f),
                                start = androidx.compose.ui.geometry.Offset(x(size.width), 0f),
                                end = androidx.compose.ui.geometry.Offset(x(unit * 4f), size.height),
                            )
                        )
                    }
                }
            if (glass) {
                SideEffect { backdrop.painted = true }
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(stripHeight)
                    .clip(stripShape)
                    .then(strip)
                    .clearAndSetSemantics {}
                    .padding(
                        start = Space.S2.dp,
                        top = Space.S2.dp,
                        end = (Space.S2 + Space.S1).dp,
                        bottom = (Space.S2 + Space.S1).dp,
                    )
            ) {
              CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop) {
                Column(
                    Modifier.fillMaxSize()
                        .then(
                            if (card == null)
                                Modifier.background(colors.surface1, cardShape)
                                    .exactEdge(kitHairline(), colors.hairline, cardShape)
                            else if (glass)
                                // A clear pane, as Glass's in-flow panes are now: its light frost, its sheen and its
                                // white rim light over the wash (owner 2026-11-08).
                                glassSampleCard(cardRequest).let { face -> Modifier.kitSurfaceGlass(face.copy(frost = 3f)).kitGlassFace(face) }
                            else Modifier.kitDecorated(card, androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)
                        )
                        .padding(Space.S2.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        Modifier.fillMaxWidth(.7f) // F: lib/ui/kit/kit_option_card.dart:678
                            .height(Space.S1.dp)
                            .background(
                                colors.text1.copy(
                                    alpha = .8f // F: lib/ui/kit/kit_option_card.dart:645
                                ), // F: lib/ui/kit/kit_option_card.dart:645
                                radius(Space.S1 / 2f), // F: lib/ui/kit/kit_option_card.dart:646
                            ) // F: lib/ui/kit/kit_option_card.dart:645-646
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val pill = radius(999f) // F: lib/ui/kit/kit_option_card.dart:641
                        val control =
                            kitSurfaceLook(
                                KitSurfaceInput(
                                    SurfaceRole.Control,
                                    KitShapeId.Pill,
                                    SurfaceLevel.Surface2,
                                    ownFill = colors.surface1,
                                )
                            )
                        // The main action's button: the style's own control in the accent (kit_option_card.dart:661-665).
                        val action =
                            kitSurfaceLook(
                                KitSurfaceInput(
                                    SurfaceRole.Control,
                                    KitShapeId.Pill,
                                    SurfaceLevel.Surface2,
                                    ownFill = colors.accent,
                                    color = colors.accent,
                                )
                            )
                        Box(
                            Modifier.width(
                                    (Space.S1 * 7).dp // F: lib/ui/kit/kit_option_card.dart:660
                                ) // F: lib/ui/kit/kit_option_card.dart:660
                                .height(
                                    (Space.S1 * 3.5f).dp // F: lib/ui/kit/kit_option_card.dart:653
                                ) // F: lib/ui/kit/kit_option_card.dart:653
                                .then(control.modifier)
                                .then(if (style == Style.Solid) Modifier.exactEdge(kitHairline(), colors.hairline, pill) else Modifier)
                        )
                        Spacer(Modifier.width(Space.S1.dp))
                        Box(
                            Modifier.width(
                                    (Space.S1 * 6).dp // F: lib/ui/kit/kit_option_card.dart:663
                                ) // F: lib/ui/kit/kit_option_card.dart:663
                                .height(
                                    (Space.S1 * 3.5f).dp // F: lib/ui/kit/kit_option_card.dart:653
                                ) // F: lib/ui/kit/kit_option_card.dart:653
                                .then(action.modifier)
                        )
                    }
                }
              }
            }
        }
    }
}

/** The Glass sample's pane: a clear in-flow pane ([GlassSpec.resolveClear]) with its rims lit as the kit lights them. */
private fun glassSampleCard(r: SurfaceRequest): io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration =
    io.github.eslamasabry.oc.ui.kit.effects.glassRims(
        io.github.eslamasabry.oc.ui.design.style.GlassSpec.resolveClear(r)!!,
        r.roles,
        clear = true,
    )
