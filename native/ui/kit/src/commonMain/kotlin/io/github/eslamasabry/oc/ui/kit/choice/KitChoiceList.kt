package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.row.LocalKitOnRail
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.row.kitChosenPlate
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitChoiceCurrent
import io.github.eslamasabry.oc.ui.words.res.kitChoiceRecommended
import io.github.eslamasabry.oc.ui.words.res.kitChoiceSelectedCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge

/** Plan 06 §7.5: skeleton rows while choices load. */
private const val LOADING_ROWS = 3

/**
 * One choice (Dart `KitChoice`): a value, a title, one supporting line, and its own disabled
 * reason.
 */
@Immutable
public data class KitChoice<T>(
    val value: T,
    val title: String,
    val supporting: String? = null,
    val recommended: Boolean = false,
    val enabled: KitEnabled = KitEnabled.Enabled,
)

/** The mark at a choice row's start (Dart `KitChoiceMark`). */
public enum class KitChoiceMark {
    Radio,
    Check,
    None,

    /**
     * A long pick list (the model picker, Dart `KitPickerRow`): no ring on every row; the chosen row takes a quiet
     * `surface3` band and a mint check at its start, the others keep the space so titles line up.
     */
    Tick,
}

/** Pure rules of the choice list (B-KIT-014). */
public object ChoiceRules {
    /** Multi: tapping toggles exactly that value. */
    public fun <T> toggle(selected: Set<T>, value: T): Set<T> =
        if (value in selected) selected - value else selected + value

    /** "Current" shows on the in-use row only while the selection differs from it. */
    public fun <T> showsCurrent(value: T, current: T?, selected: T?): Boolean =
        current != null && value == current && selected != current
}

/**
 * One answer from a short list (Dart `KitChoiceList.single`; plan 06 §7.5): radio rows, one tap =
 * one [onSelect] call (there is no Apply; the host closes its sheet or sends). Disabled choices
 * state their reason on their own line. [sends] plays `Send` through the kit seam (the tap sends
 * the person's answer). While [loading], skeleton rows; with no choices, the [empty] slot (a
 * `KitStateView`). "Current" marks the value in use while the selection differs from it. Replaces
 * dropdowns.
 *
 * States: default, selected, disabled, loading, empty Text: reading (titles), frame (tags) Cap:
 * List Motion: none of its own Replaces: KitChoiceList.single, KitChoiceRow, KitChoiceMark
 * (lib/ui/kit/kit_choice_list.dart)
 */
@Composable
public fun <T> KitChoiceList(
    choices: List<KitChoice<T>>,
    selected: T?,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    current: T? = null,
    sends: Boolean = false,
    loading: Boolean = false,
    empty: (@Composable () -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    kitAssert(loading || choices.isNotEmpty() || empty != null) {
        "KitChoiceList: an empty list needs its empty state"
    }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = semanticsLabel }.selectableGroup()
    ) {
        when {
            loading -> KitSkeletonRows(count = LOADING_ROWS)
            choices.isEmpty() -> empty?.invoke()
            else ->
                choices.forEach { c ->
                    KitChoiceRow(
                        title = c.title,
                        selected = c.value == selected,
                        supporting = c.supporting,
                        mark = KitChoiceMark.Radio,
                        tag =
                            when {
                                ChoiceRules.showsCurrent(c.value, current, selected) ->
                                    stringResource(Res.string.kitChoiceCurrent)
                                c.recommended -> stringResource(Res.string.kitChoiceRecommended)
                                else -> null
                            },
                        enabled = c.enabled,
                        haptic = if (sends) KitHaptic.Send else null,
                        onClick = { onSelect(c.value) },
                    )
                }
        }
    }
}

/**
 * Several answers (Dart `KitChoiceList.multi`): check rows, each tap toggles one value and calls
 * [onChange] once; the host's primary applies them. [showCount] puts "N selected" above the list.
 */
@Composable
public fun <T> KitChoiceListMulti(
    choices: List<KitChoice<T>>,
    selected: Set<T>,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    showCount: Boolean = true,
    loading: Boolean = false,
    empty: (@Composable () -> Unit)? = null,
    onChange: (Set<T>) -> Unit,
) {
    kitAssert(loading || choices.isNotEmpty() || empty != null) {
        "KitChoiceList: an empty list needs its empty state"
    }
    Column(modifier.fillMaxWidth().semantics { contentDescription = semanticsLabel }) {
        if (showCount && !loading && choices.isNotEmpty()) {
            KitText(
                pluralStringResource(
                    Res.plurals.kitChoiceSelectedCount,
                    selected.size,
                    Numbers.format(selected.size),
                ),
                Modifier.padding(bottom = Space.S1.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
            )
        }
        when {
            loading -> KitSkeletonRows(count = LOADING_ROWS)
            choices.isEmpty() -> empty?.invoke()
            else ->
                choices.forEach { c ->
                    KitChoiceRow(
                        title = c.title,
                        selected = c.value in selected,
                        supporting = c.supporting,
                        mark = KitChoiceMark.Check,
                        tag =
                            if (c.recommended) stringResource(Res.string.kitChoiceRecommended)
                            else null,
                        enabled = c.enabled,
                        onClick = { onChange(ChoiceRules.toggle(selected, c.value)) },
                    )
                }
        }
    }
}

/**
 * One choice row (Dart `KitChoiceRow`): the mark at the start, the title (and one supporting line
 * or the disabled reason), an optional tag word at the end. Radio rows are `RadioButton`s, check
 * rows `Checkbox`es, to a screen reader.
 */
@Composable
public fun KitChoiceRow(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    mark: KitChoiceMark = KitChoiceMark.Radio,
    tag: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    haptic: KitHaptic? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val on = enabled.isEnabled
    // C04 (audit 2026-11-09): a single choice is marked by the check at the trailing edge, never by a ring at the start.
    val mark = if (mark == KitChoiceMark.Radio) KitChoiceMark.Tick else mark
    // The chosen row fills with the accent (owner reference 2026-11-07), on the effects spring: it washes in and out
    // instead of switching. Off: at once.
    val accent = LocalKitColors.current.accent
    val fill by animateColorAsState(
        if (selected && on) accent.copy(alpha = SELECTED_FILL_ALPHA) else accent.copy(alpha = 0f),
        KitMotion.specs.effects(),
        label = "kit.choice.fill",
    )
    // P-008: on a sheet's rail the row's box reaches past the rail to the sheet's edges (its band and tap area run edge
    // to edge, as a page row's do on the window), and its words start back on the rail, the sheet title's edge.
    val bleed = ChoiceRowGeometry.bleed(LocalKitOnRail.current).dp
    // The plate wraps the words with room on both sides: it reaches back past the rail by space3 (never past the sheet).
    val glassChosen = io.github.eslamasabry.oc.ui.kit.effects.kitGlassChosenRow((bleed - Space.S3.dp).coerceAtLeast(0.dp))
    Row(
        modifier
            .fillMaxWidth()
            .bleedSideways(bleed)
            // C04: the chosen wash is the row group's own plate on a flat list (as KitRow's), a band elsewhere.
            // Never a square band (owner 2026-11-09, "ugly tint": a flat slab that ignored the card's corners): Glass
            // draws the chosen row's own glass face, concentric with its group; every other style a rounded plate.
            .then(
                glassChosen?.takeIf { selected && on }
                    ?: Modifier.kitChosenPlate(fill, bleed),
            )
            // Flutter floors a choice row at the two-line row height (64, 56 compact): F: lib/ui/kit/kit_choice_list.dart:635-637.
            .heightIn(min = tokens.rowHeightTwo.dp)
            .kitEdge(KitEdge.Bleed) // a row's highlight runs edge to edge (native/docs/kit/LAYOUT-RULES.md §2)
            .kitSelectTap(
                selected = selected,
                role = if (mark == KitChoiceMark.Check) Role.Checkbox else Role.RadioButton,
                enabled = enabled,
                haptic = haptic,
                onClick = onClick,
            )
            .padding(horizontal = bleed + io.github.eslamasabry.oc.ui.kit.row.kitRowGutter(), vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        leading?.invoke()
        // A tick marks the chosen row at its end and reserves no room on the others, so titles start on one edge
        // (owner's model sheet, 2026-11-06: an empty tick slot left a big gap before every name).
        if (mark != KitChoiceMark.None && mark != KitChoiceMark.Tick) ChoiceMarkGlyph(mark, selected, on)
        Column(Modifier.weight(1f)) {
            KitText(
                title,
                role = TypeRole.RowTitle,
                tone = if (on) TextTone.Primary else TextTone.Tertiary,
            )
            // A blank reason means the group states it once (KitSegmented's stack, a list locked by one cause):
            // the row keeps its own line instead of repeating the reason.
            val line = enabled.reason?.takeIf { it.isNotBlank() } ?: supporting
            line?.let {
                KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 3)
            }
        }
        tag?.let {
            KitText(
                it,
                role = TypeRole.Caption,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
                maxLines = 1,
            )
        }
        // The tick takes no room on unchosen rows; on the chosen one it opens in (KitPresence) rather than popping.
        if (mark == KitChoiceMark.Tick) KitPresence(selected) { ChoiceMarkGlyph(mark, true, on) }
        // A trailing control (the model sheet's star) is a 48 dp target: it takes the row's 8 dp steps above and below
        // instead of stretching a one-line row past 56 dp, and on a rail its glyph lines up with the sheet's Close.
        trailing?.let {
            val overhang = ChoiceRowGeometry.trailingOverhang(bleed.value > 0f, tokens.minTarget).dp
            Box(Modifier.trailingOverhang(overhang, Space.S2.dp)) { it() }
        }
    }
}

/** The choice row's edges (P-008), in dp, pure so they are tested without Compose. */
internal object ChoiceRowGeometry {
    /** How far the row's box (its band, its tap area) reaches past its parent on each side: to a sheet's edges. */
    fun bleed(onRail: Boolean): Int = if (onRail) FlutterKitTokens.RAIL else 0

    /**
     * How far a trailing 48 dp control reaches into the row's end padding. On a rail it ends where the sheet's Close
     * does (`KitSheet`: rail − (target − 24) / 2 − the glyph's ink inset from the sheet edge), so the two glyphs
     * share a centre; on a page row it stays inside the gutter.
     */
    fun trailingOverhang(onRail: Boolean, minTarget: Int): Float =
        if (!onRail) 0f else (minTarget - CLOSE_GLYPH) / 2f + CLOSE_GLYPH * CLOSE_INK_INSET

    private const val CLOSE_GLYPH = 24 // F: lib/ui/app_iconography.dart:20

    private const val CLOSE_INK_INSET = 0.1875f // F: lib/ui/kit/kit_optical.dart:29,34
}

/** Widens the box by [bleed] on both sides without taking more room: it reaches over its parent's margin. */
private fun Modifier.bleedSideways(bleed: Dp): Modifier =
    if (bleed.value <= 0f) this
    else
        layout { measurable, constraints ->
            val b = bleed.roundToPx()
            val wide =
                if (!constraints.hasBoundedWidth) constraints
                else constraints.copy(minWidth = constraints.minWidth + 2 * b, maxWidth = constraints.maxWidth + 2 * b)
            val p = measurable.measure(wide)
            layout((p.width - 2 * b).coerceAtLeast(0), p.height) { p.place(-b, 0) }
        }

/**
 * Lets a trailing target reach [end] into the row's end padding and [vertical] into its top and bottom padding, without
 * growing the row: the box reports the smaller size and places the target over the difference (inside the row's box,
 * so its tap clip never cuts it).
 */
private fun Modifier.trailingOverhang(end: Dp, vertical: Dp): Modifier =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val e = end.roundToPx()
        val v = vertical.roundToPx()
        val w = (p.width - e).coerceAtLeast(0)
        val h = (p.height - 2 * v).coerceAtLeast(0)
        layout(w, h) { p.placeRelative(0, (h - p.height) / 2) }
    }

@Composable
private fun ChoiceMarkGlyph(mark: KitChoiceMark, selected: Boolean, on: Boolean) {
    val colors = LocalKitColors.current
    val size = 22.dp
    val edge = if (!on) colors.text3 else if (selected) colors.accent else colors.text2
    // The mark springs in from nothing's centre (SpatialFast, no overshoot) and shrinks away when unchosen.
    val pop by animateFloatAsState(if (selected) 1f else 0f, KitMotion.specs.spatialFast(), label = "kit.choice.mark")
    val popped = Modifier.graphicsLayer {
        scaleX = pop
        scaleY = pop
        alpha = pop.coerceIn(0f, 1f)
    }
    if (mark == KitChoiceMark.Tick) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (pop > 0f) {
                // C04: the bare check in the accent, not a disc behind it.
                Box(popped.size(size), contentAlignment = Alignment.Center) {
                    KitGlyph(KitPartGlyphs.Check, colors.accent, 20.dp, Modifier.testTag("kit-choice-tick"))
                }
            }
        }
        return
    }
    val shape = if (mark == KitChoiceMark.Radio) KitShapeId.Circle.shape() else KitShapeId.Square.shape()
    val boxShape = if (mark == KitChoiceMark.Check) KitShapeId.Code.shape() else shape
    Box(
        Modifier.size(size).clip(boxShape).border(2.dp, edge, boxShape),
        contentAlignment = Alignment.Center,
    ) {
        if (pop > 0f) {
            if (mark == KitChoiceMark.Check)
                Box(popped.size(size).clip(boxShape).background(edge), contentAlignment = Alignment.Center) {
                    KitGlyph(KitPartGlyphs.Check, colors.onAccent, 16.dp)
                }
            else Box(popped.size(10.dp).clip(KitShapeId.Circle.shape()).background(edge))
        }
    }
}

/** How strongly a chosen row's fill shows the accent: a breath, never a slab (audit 2026-11-09, C04). */
internal const val SELECTED_FILL_ALPHA: Float = 0.06f
