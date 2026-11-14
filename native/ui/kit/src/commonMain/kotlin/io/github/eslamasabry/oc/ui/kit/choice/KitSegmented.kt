package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.row.kitContainer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** One segment (Dart `KitSegment`): a value, its word, an optional icon, and its own disabled reason. */
@Immutable
public data class KitSegment<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: KitEnabled = KitEnabled.Enabled,
    val count: Int? = null,
)

/**
 * The geometry of a segmented control's track and its chosen thumb (owner 2026-11-09: the thumb touched the track's
 * edge and its corners did not follow the track's). The thumb sits [INSET] inside the track on every side and its
 * corner is concentric with the track's: inner radius = outer radius - [INSET]. Both come from the Corners knob, so
 * Round is a pill inside a pill, Soft a softer box, Sharp (and Brutal) a square-ish one that is still inset.
 */
public object SegmentedGeometry {
    /** The space between the track's edge and the thumb, on all four sides. */
    public const val INSET: Float = Space.S1.toFloat()

    /** The track's radius token: half the 48 dp target, so Round is a stadium at the control's usual height. */
    public const val TRACK_TOKEN_DP: Float = FlutterKitTokens.MIN_TARGET / 2f

    /** The track's corner under [corners] (Soft takes about 60 %, Sharp the style's [sharpDp]). */
    public fun trackRadiusDp(corners: Corners, sharpDp: Float): Float = StyleResolver.corner(TRACK_TOKEN_DP, corners, sharpDp)

    /** The thumb's corner: the track's, less the inset, never below square. */
    public fun thumbRadiusDp(corners: Corners, sharpDp: Float): Float = maxOf(0f, trackRadiusDp(corners, sharpDp) - INSET)
}

/** The track's and the thumb's corner radii under the style in force (see [SegmentedGeometry]). */
@Composable
internal fun segmentedRadii(): Pair<Dp, Dp> {
    val style = LocalKitStyle.current
    val sharp = style.spec.tokens.sharpRadius
    return SegmentedGeometry.trackRadiusDp(style.knobs.corners, sharp).dp to
        SegmentedGeometry.thumbRadiusDp(style.knobs.corners, sharp).dp
}

/** Pure rules of [KitSegmented] (B-KIT-031). */
public object SegmentedRules {
    public const val MIN: Int = 2
    public const val MAX: Int = 4

    /** At 2× text the row always stacks (Dart `KitSegmented`). */
    public const val STACK_AT_SCALE: Float = 2.0f

    public fun validCount(count: Int): Boolean = count in MIN..MAX

    /** The row stacks into choice rows when one word does not fit its equal share, or at 2× text. */
    public fun stacks(labelWidthsDp: List<Float>, availableDp: Float, paddingDp: Float, fontScale: Float): Boolean {
        if (fontScale >= STACK_AT_SCALE) return true
        if (labelWidthsDp.isEmpty()) return false
        val share = availableDp / labelWidthsDp.size - paddingDp
        return labelWidthsDp.any { it > share }
    }

    /** What a tap on [tapped] does: the new value, or null when it is the selected one (no callback) or disabled. */
    public fun <T> onTap(tapped: KitSegment<T>, selected: T, groupEnabled: Boolean): T? =
        if (!groupEnabled || !tapped.enabled.isEnabled || tapped.value == selected) null else tapped.value

    /**
     * Whether the track draws a hairline after segment [index]: never (cut audit 2, finding 21). Seams only between
     * resting segments left one lone line on one side of the chosen block that moved with the choice (Mode: Dark |
     * Saver with Light chosen); the segments are parted by their own room and the chosen block, symmetrically.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun seamAfter(index: Int, count: Int, selectedIndex: Int, tall: Boolean): Boolean = false
}

/**
 * Two to four mutually exclusive values on one line (Dart `KitSegmented`; plan 06 §7.5). The chosen one is a raised
 * step (surface 2 in light, 3 in dark) with the accent check and primary words in every style, so it is told apart by
 * shape and the check, not colour alone (owner 2026-11-07: a bright accent block read as a pressed button, and stayed
 * bright when the control was disabled). A tap on another enabled
 * segment calls [onChange] once and plays `Select` through the kit seam; a tap on the chosen one does nothing. When a
 * word does not fit its share, or at 2× text, the control stacks into choice rows. A disabled control shows its reason
 * under it, on the control's edge; a disabled segment says its own. [reasonShown] = false when the page already says
 * the same reason once nearby (it then goes to the screen reader only, so it is never shown twice).
 *
 * States: default, selected, disabled, stacked
 * Text: frame
 * Cap: Reading
 * Motion: fill cross-fades QUICK; Off = instant
 * Replaces: KitSegmented, KitSegment (lib/ui/kit/kit_segmented.dart)
 */
@Composable
public fun <T> KitSegmented(
    segments: List<KitSegment<T>>,
    selected: T,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    enabled: KitEnabled = KitEnabled.Enabled,
    tall: Boolean = false,
    reasonShown: Boolean = true,
    onChange: (T) -> Unit,
) {
    kitAssert(SegmentedRules.validCount(segments.size)) { "KitSegmented needs 2 to 4 segments (B-KIT-031)" }
    kitAssert(segments.any { it.value == selected }) { "KitSegmented.selected must be one of its segments" }
    kitAssert(segments.all { it.enabled !is KitEnabled.Disabled || it.enabled.reason.isNotBlank() }) {
        "KitSegmented: a disabled segment must say why (B-KIT-031)"
    }
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val measurer = rememberTextMeasurer()
    val appDensity = LocalKitAppDensity.current ?: LocalDensity.current
    val fontScale = appDensity.fontScale
    val reasons =
        if (!enabled.isEnabled) listOfNotNull(enabled.reason)
        else segments.mapNotNull { it.enabled.reason }.distinct()
    Column(
        // Finding 17 (cut audit 2): the control reports its span to the stack it is in, so a card or row group set
        // right under it keeps the kit's container gap instead of touching it (the control itself never moves).
        modifier.kitContainer(spaced = false).fillMaxWidth().semantics {
            contentDescription = semanticsLabel
            if (!reasonShown && reasons.isNotEmpty()) stateDescription = reasons.joinToString(". ")
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val unscaled = Density(appDensity.density, 1f)
            fun width(text: String, role: TypeRole): Float =
                measurer.measure(text, typography.style(role), density = unscaled).size.width / appDensity.density *
                    minOf(fontScale, TextScalePolicy.FRAME_CAP)
            val smallIcon = tokens.smallIcon.toFloat()
            val widths =
                segments.map { s ->
                    if (tall) {
                        2f * Space.S1 + maxOf(width(s.label, TypeRole.Caption), smallIcon) // F: lib/ui/kit/kit_segmented.dart:488
                    } else {
                        2f * Space.S2 + smallIcon + Space.S1 + width(s.label, TypeRole.Label) + // F: lib/ui/kit/kit_segmented.dart:496
                            (if (s.icon != null) smallIcon + Space.S1 else 0f) +
                            (s.count?.let { Space.S1 + width(Numbers.format(it), TypeRole.Caption) } ?: 0f)
                    }
                }
            val room = maxWidth.value - 2f * kitHairline().value // F: lib/ui/kit/kit_segmented.dart:452-453
            val stacked = fontScale >= SegmentedRules.STACK_AT_SCALE || widths.any { it > room / segments.size }
            if (stacked) {
                StackedSegments(segments, selected, enabled, onChange)
            } else {
                RowSegments(segments, selected, enabled, tall, onChange)
            }
        }
        // In the stack a disabled segment's reason is on its row; only the whole control's reason stays under it.
        // (The stack decision lives inside the constraints, so the segment reasons are shown here for both forms.)
        // E20-S08: on the control's edge, not 8 dp in (it read as a second, indented copy of the page's caption).
        if (reasons.isNotEmpty() && reasonShown) {
            Column(Modifier.padding(top = Space.S1.dp).testTag("kit-segmented-reason")) {
                reasons.forEach { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame) }
            }
        }
    }
}

@Composable
private fun <T> RowSegments(segments: List<KitSegment<T>>, selected: T, enabled: KitEnabled, tall: Boolean, onChange: (T) -> Unit) {
    val haptics = rememberKitHaptics()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val solid = LocalKitStyle.current.style == Style.Solid
    val hairline = kitHairline()
    val inset = SegmentedGeometry.INSET.dp // owner 2026-11-09: the thumb sits inset in the track
    val density = LocalDensity.current
    val captionLine = with(density) { LocalKitTypography.current.style(TypeRole.Caption).fontSize.toDp() } * 1.3f * density.fontScale // F: lib/ui/kit/kit_segmented.dart:781
    val tallHeight = maxOf(tokens.minTarget.dp, (tokens.smallIcon * 1.2f).dp + Space.S1.dp + captionLine + (Space.S2 * 2).dp) // F: lib/ui/kit/kit_segmented.dart:783-786
    val selectedIndex = segments.indexOfFirst { it.value == selected }
    val step = if (colors.dark) colors.surface3 else colors.surface2
    val (trackRadius, thumbRadius) = segmentedRadii()
    val track =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Track, KitShapeId.Button, SurfaceLevel.Surface1, ownFill = colors.surface1, exactRadius = trackRadius),
        )
    Row(
        Modifier.fillMaxWidth()
            .then(track.modifier)
            .then(if (solid) Modifier.kitHairlineBorder(hairline, colors.hairline, track.shape) else Modifier)
            .clip(track.shape) // a segment's press never leaves the track's rounded edge
            .testTag("kit-segmented-track")
            .selectableGroup(),
    ) {
        segments.forEachIndexed { i, s ->
            val chosen = s.value == selected
            val segEnabled = if (!enabled.isEnabled) enabled else s.enabled
            val on = segEnabled.isEnabled
            val fill by animateColorAsState(if (chosen) step else Color.Transparent, specs.quick(), label = "segment")
            val words = when {
                !on -> TextTone.Tertiary
                chosen -> TextTone.Primary
                else -> TextTone.Secondary
            }
            val thumb =
                kitSurfaceLook(
                    KitSurfaceInput(
                        role = SurfaceRole.Control,
                        shape = KitShapeId.Tile,
                        exactRadius = thumbRadius,
                        level = SurfaceLevel.Surface3,
                        ownFill = fill,
                        color = if (chosen) step else null,
                        selected = chosen,
                        disabled = !on,
                    ),
                )
            // A style that fills its chosen thumb with ink (Paper, Signal) gives the words to read on it: the chosen
            // word then takes those, never the page's text1 on an ink fill (owner 2026-11-10: "Round" was black on
            // black in Paper).
            val inkColors = if (chosen && on) thumb.words?.let { colors.copy(text1 = it) } ?: colors else colors
            val ink = inkColors.tone(words)
            // The chosen glyph and check take the accent, the control's one accent mark.
            val glyph = if (chosen && on) colors.accent else ink
            Box(
                Modifier.weight(1f)
                    .let { if (tall) it.height(tallHeight) else it.heightIn(min = tokens.minTarget.dp) }
                    // The 48 dp target is the whole segment (the track is minTarget plus its hairlines); the chosen
                    // block is drawn inset by half a space1 inside it (F: lib/ui/kit/kit_segmented.dart:629, :678-686).
                    // A target inside the inset made every track 4 dp taller than Flutter's.
                    .kitSelectTap(chosen, Role.RadioButton, segEnabled, haptic = null, KitShapeId.Tile) {
                        SegmentedRules.onTap(s, selected, enabled.isEnabled)?.let {
                            haptics(KitHaptic.Select)
                            onChange(it)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The chosen thumb: its own layer, inset from the track's edge on every side (never the label's box).
                if (chosen) {
                    Box(Modifier.matchParentSize().padding(inset)) {
                        Box(Modifier.fillMaxSize().testTag("kit-segmented-thumb").then(thumb.modifier))
                    }
                }
                // The words keep their space2 from the segment's edge, as before the thumb moved to its own layer.
                Box(Modifier.padding(horizontal = Space.S2.dp, vertical = inset), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalKitColors provides inkColors) {
                    val count = s.count?.let { Numbers.format(it) }
                    if (tall) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            s.icon?.let { KitGlyph(it, glyph, (tokens.smallIcon * 1.2f).dp) } // F: lib/ui/kit/kit_segmented.dart:851
                            Spacer(Modifier.height(Space.S1.dp))
                            KitText(s.label, role = TypeRole.Caption, tone = words, kind = KitTextKind.Frame, maxLines = 1)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.padding(end = Space.S1.dp).alpha(if (chosen) 1f else 0f)) {
                                KitGlyph(KitIcons.check, if (!on) colors.text3 else colors.accent, tokens.smallIcon.dp)
                            }
                            s.icon?.let {
                                KitGlyph(it, glyph, tokens.smallIcon.dp)
                                Spacer(Modifier.width(Space.S1.dp))
                            }
                            KitText(s.label, role = TypeRole.Label, tone = words, kind = KitTextKind.Frame, maxLines = 1)
                            if (count != null) {
                                Spacer(Modifier.width(Space.S1.dp))
                                KitText(count, role = TypeRole.Caption, tone = words, kind = KitTextKind.Frame, maxLines = 1)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
internal fun <T> StackedSegments(
    segments: List<KitSegment<T>>,
    selected: T,
    enabled: KitEnabled,
    onChange: (T) -> Unit,
) {
    val haptics = rememberKitHaptics()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val panel = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Panel, SurfaceLevel.Surface1, ownFill = colors.surface1))
    Column(Modifier.fillMaxWidth().clip(panel.shape).then(panel.modifier).selectableGroup()) {
        segments.forEachIndexed { i, s ->
            // The whole control's reason shows once under the stack, never on every row.
            val segEnabled = if (!enabled.isEnabled) KitEnabled.Disabled("") else s.enabled
            if (i > 0) {
                Box(
                    Modifier.padding(start = (tokens.gutter + tokens.smallIcon + Space.S3).dp)
                        .fillMaxWidth().height(kitHairline()).background(colors.hairline),
                )
            }
            KitChoiceRow(
                title = s.count?.let { "${s.label} · ${Numbers.format(it)}" } ?: s.label,
                selected = s.value == selected,
                mark = KitChoiceMark.Radio,
                enabled = segEnabled,
                leading = s.icon?.let { icon -> { KitGlyph(icon, if (segEnabled.isEnabled) colors.text2 else colors.text3, tokens.smallIcon.dp) } },
                onClick = {
                    SegmentedRules.onTap(s, selected, enabled.isEnabled)?.let {
                        haptics(KitHaptic.Select)
                        onChange(it)
                    }
                },
            )
        }
    }
}
