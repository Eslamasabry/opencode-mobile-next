package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitPaneInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** How high on its edge the light sits. */
public enum class KitLampHeight { High, Middle, Low }

/** Where the light sits: its side in reading order and its height. Low on the reading end is where it starts. */
@Immutable
public data class KitLampPlace(val side: KitLampSide = KitLampSide.End, val height: KitLampHeight = KitLampHeight.Low)

/** The picker's words: "Where the edge light sits", "Tap a spot. The edge light moves there now.", the sides and heights. */
public class KitLampPlaceWords(
    public val title: String,
    public val body: String,
    public val left: String,
    public val right: String,
    public val high: String,
    public val middle: String,
    public val low: String,
    public val done: String,
)

/** The place's measures. */
public object KitLampPlaceDims {
    /** Low: the touch box's bottom this far above the bottom of the space (LAMP-PLAN §2: clear of chips and the status strip). */
    public const val LOW_ABOVE: Int = 196 // NV: LAMP-PLAN §2, 196 dp above the bottom safe area

    /** Middle and High: the light's centre at these shares of the space's height, from the bottom (the prototype's lampY). */
    public const val MIDDLE_SHARE: Float = 0.45f // NV: prototype lampY, Middle
    public const val HIGH_SHARE: Float = 0.64f // NV: prototype lampY, High

    /** The picker's phone figure. */
    public const val PHONE_W: Int = Space.S6 * 7 + Space.S2
    public const val PHONE_H: Int = Space.S6 * 12 + Space.S4

    /** One spot: a 12 × 36 pill in a 44 dp touch box. */
    public const val SPOT_W: Int = Space.S3
    public const val SPOT_H: Int = Space.S6 + Space.S3
    public const val SPOT_TOUCH: Int = Space.S6 + Space.S5
}

/** The place's pure rules. */
public object KitLampPlaceRules {
    /** The touch box's top in a space [areaH] tall (px) for a box [touchH] tall; [lowAbove] is LOW_ABOVE in px. */
    public fun touchTop(height: KitLampHeight, areaH: Float, touchH: Float, lowAbove: Float): Float =
        when (height) {
            KitLampHeight.Low -> areaH - lowAbove - touchH
            KitLampHeight.Middle -> areaH - areaH * KitLampPlaceDims.MIDDLE_SHARE - touchH / 2f
            KitLampHeight.High -> areaH - areaH * KitLampPlaceDims.HIGH_SHARE - touchH / 2f
        }.coerceIn(0f, (areaH - touchH).coerceAtLeast(0f))

    /** The side a spot on the physical [left] edge stands for, in reading order. */
    public fun sideOf(left: Boolean, rtl: Boolean): KitLampSide = if (left != rtl) KitLampSide.Start else KitLampSide.End
}

/**
 * Places the real light at [place] on the edge of the space it is given (LAMP-CRAFT §5 "Move…"): a height change glides
 * along the edge (`spatial`); a side change fades it out at the old edge and in at the new one (QUICK each, no flight
 * across the screen). [content] draws the light for the side it stands on now (pass that side to `KitLamp`).
 *
 * States: resting, gliding, crossing sides
 * Text: none
 * Cap: none (the light's own)
 * Motion: height `spatial`; side QUICK out + QUICK in; Off = instant
 * Replaces: none
 */
@Composable
public fun KitLampAtPlace(place: KitLampPlace, modifier: Modifier = Modifier, content: @Composable (side: KitLampSide) -> Unit) {
    val specs = KitMotion.specs
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var shownSide by remember { mutableStateOf(place.side) }
    val alpha = remember { Animatable(1f) }
    val top = remember { Animatable(Float.NaN) }
    LaunchedEffect(place.side) {
        if (place.side != shownSide) {
            alpha.animateTo(0f, specs.quick())
            shownSide = place.side
            alpha.animateTo(1f, specs.quick())
        }
    }
    val target = remember { mutableStateOf(Float.NaN) }
    LaunchedEffect(target.value) {
        val t = target.value
        if (t.isNaN()) return@LaunchedEffect
        if (top.value.isNaN() || shownSide != place.side) top.snapTo(t) else if (top.targetValue != t) top.animateTo(t, specs.spatial())
    }
    val lowAbove = KitLampPlaceDims.LOW_ABOVE.dp
    Layout(content = { Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { content(shownSide) } }, modifier = modifier.fillMaxSize()) { ms, c ->
        val p = ms.first().measure(Constraints())
        val w = if (c.hasBoundedWidth) c.maxWidth else p.width
        val h = if (c.hasBoundedHeight) c.maxHeight else p.height
        val want = KitLampPlaceRules.touchTop(place.height, h.toFloat(), p.height.toFloat(), lowAbove.toPx())
        if (target.value != want) target.value = want
        val y = if (top.value.isNaN()) want else top.value
        val left = KitLampRules.onLeft(shownSide, rtl)
        layout(w, h) { p.place(if (left) 0 else w - p.width, y.toInt()) }
    }
}

/**
 * The six-spot picker (LAMP-CRAFT §5 "Move…", picture `move-2`): a phone figure with three spots on each physical edge;
 * a tap on a spot calls [onPick] at once (the caller moves the real light, which stays lit above the picker's scrim)
 * and plays [KitHaptic.Select]; the chosen spot is filled in the accent, the others ringed. Under it "Right · High";
 * Done closes. The figure is physical: it does not mirror in Arabic, its left is the screen's left.
 *
 * States: one chosen spot of six
 * Text: frame (title Title, body Secondary, the place Secondary)
 * Cap: Dialog
 * Motion: none of its own (the light moves); Off = same
 * Replaces: none
 */
@Composable
public fun KitLampPlacePicker(
    place: KitLampPlace,
    words: KitLampPlaceWords,
    onPick: (KitLampPlace) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Floating, shape = KitShapeId.Dialog, level = SurfaceLevel.Surface1, ownFill = colors.surface1))
    val onLeftNow = KitLampRules.onLeft(place.side, rtl)
    fun heightWord(h: KitLampHeight) = when (h) {
        KitLampHeight.High -> words.high
        KitLampHeight.Middle -> words.middle
        KitLampHeight.Low -> words.low
    }
    Column(
        modifier
            .kitMaxWidth(CapRole.Dialog)
            .testTag(LAMP_PLACE_PICKER_TAG)
            .kitSurface(look)
            .padding(Space.S5.dp + kitPaneInset()),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        KitText(words.title, role = TypeRole.Title, kind = KitTextKind.Frame, pageTitle = true)
        KitText(words.body, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        Box(Modifier.fillMaxWidth().padding(vertical = Space.S3.dp), contentAlignment = Alignment.Center) {
            PhoneFigure(place, rtl, { left, h -> "${if (left) words.left else words.right}, ${heightWord(h)}" }) { left, h ->
                onPick(KitLampPlace(KitLampPlaceRules.sideOf(left, rtl), h))
            }
        }
        KitText(
            "${if (onLeftNow) words.left else words.right} · ${heightWord(place.height)}",
            Modifier.fillMaxWidth(),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            KitButton(words.done, onDone, role = KitButtonRole.Primary, expand = false)
        }
    }
}

/** The phone figure and its six spots, laid out physically (left is the screen's left in every language). */
@Composable
private fun PhoneFigure(place: KitLampPlace, rtl: Boolean, name: (Boolean, KitLampHeight) -> String, onPick: (Boolean, KitLampHeight) -> Unit) {
    val colors = LocalKitColors.current
    val chosenLeft = KitLampRules.onLeft(place.side, rtl)
    val heights = listOf(KitLampHeight.High, KitLampHeight.Middle, KitLampHeight.Low)
    val spots = listOf(true, false).flatMap { left -> heights.map { left to it } }
    val ring = kitHairline()
    Layout(
        content = {
            Canvas(Modifier.size(KitLampPlaceDims.PHONE_W.dp, KitLampPlaceDims.PHONE_H.dp)) {
                val line = Stroke(ring.toPx() * FIGURE_LINE)
                val r = Space.S6.dp.toPx()
                drawRoundRect(colors.text3, size = size, cornerRadius = CornerRadius(r), style = line)
                val inset = Space.S4.dp.toPx()
                val bar = Space.S5.dp.toPx()
                drawRoundRect(colors.text3, Offset(inset, inset), Size(size.width - 2 * inset, bar), CornerRadius(bar / 2f), style = line)
                val box = Space.S6.dp.toPx() * 2
                drawRoundRect(colors.text3, Offset(inset * 2, size.height - inset - box), Size(size.width - 4 * inset, box), CornerRadius(box / 2f), style = line)
            }
            for ((left, h) in spots) {
                val on = left == chosenLeft && h == place.height
                Box(
                    Modifier
                        .size(KitLampPlaceDims.SPOT_TOUCH.dp)
                        .testTag(LAMP_PLACE_SPOT_TAG)
                        .semantics {
                            contentDescription = name(left, h)
                            selected = on
                        }
                        .kitTappable(onClick = { onPick(left, h) }, haptic = KitHaptic.Select, shape = KitShapeId.Pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(KitLampPlaceDims.SPOT_W.dp, KitLampPlaceDims.SPOT_H.dp)) {
                        val cr = CornerRadius(size.width / 2f)
                        if (on) drawRoundRect(colors.accent, cornerRadius = cr)
                        else drawRoundRect(colors.text2, cornerRadius = cr, style = Stroke(ring.toPx() * FIGURE_LINE))
                    }
                }
            }
        },
    ) { ms, _ ->
        val phone = ms[0].measure(Constraints())
        val w = phone.width
        val h = phone.height
        val touch = KitLampPlaceDims.SPOT_TOUCH.dp.roundToPx()
        val inset = Space.S1.dp.roundToPx()
        val placed = spots.indices.map { ms[it + 1].measure(Constraints.fixed(touch, touch)) }
        layout(w, h) {
            phone.place(0, 0)
            spots.forEachIndexed { i, (left, ht) ->
                val p = placed[i]
                val share = when (ht) {
                    KitLampHeight.High -> SPOT_HIGH
                    KitLampHeight.Middle -> SPOT_MIDDLE
                    KitLampHeight.Low -> SPOT_LOW
                }
                val x = if (left) inset else w - inset - touch
                // Physical: place, not placeRelative.
                p.place(x, (h * share).toInt() - touch / 2)
            }
        }
    }
}

private const val FIGURE_LINE: Float = 2f // NV: the figure's lines at two hairlines
private const val SPOT_HIGH: Float = 0.27f // NV: the High spot's centre down the figure (picture move-2)
private const val SPOT_MIDDLE: Float = 0.48f // NV: the Middle spot's centre down the figure
private const val SPOT_LOW: Float = 0.69f // NV: the Low spot's centre down the figure

/** The picker, for tests. */
public const val LAMP_PLACE_PICKER_TAG: String = "kit-lamp-place-picker"

/** One of the six spots. */
public const val LAMP_PLACE_SPOT_TAG: String = "kit-lamp-place-spot"
