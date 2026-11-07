package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/** One answer of [KitChoiceCards] (Dart `KitChoice` with its `leading` icon): a value, the answer, its line, its icon. */
@Immutable
public data class KitChoiceCardItem<T>(
    val value: T,
    val title: String,
    val supporting: String? = null,
    val icon: ImageVector? = null,
    val enabled: KitEnabled = KitEnabled.Enabled,
)

/** Pure rules of the choice cards and tiles (lib/ui/kit/kit_choice_cards.dart, kit_choice_tiles.dart). */
public object ChoiceCardRules {
    /** The line under the answer: a disabled choice's reason first, then its own line, joined by " · ". */
    public fun line(supporting: String?, enabled: KitEnabled): String =
        listOfNotNull(enabled.reason, supporting?.takeIf { it.isNotEmpty() }).joinToString(" · ")

    /** A choice takes a tap only when it is enabled and no choice is busy (kit_choice_cards.dart, the cards' onTap). */
    public fun tappable(enabled: KitEnabled, waiting: Boolean): Boolean = enabled.isEnabled && !waiting

    /** The card shows progress only when the busy value is its own. */
    public fun <T> showsBusy(value: T, busy: T?): Boolean = busy != null && value == busy

    /** The read-aloud name: the answer, then its line. */
    public fun label(title: String, line: String): String = if (line.isEmpty()) title else "$title. $line"
}

/**
 * One large answer to one question (Dart `_KitChoiceCard`, lib/ui/kit/kit_choice_cards.dart): an icon drawn large,
 * the answer in title size (headline at large text) and one calm line of what it costs. The chosen card has its icon
 * in the accent, a filled accent check and the style's chosen surface (Solid: a ring twice the focus ring and a
 * breath of the accent); the others a muted icon and an empty ring. A tap chooses; it does not go anywhere.
 *
 * States: default, selected, disabled (the reason first on the line), working ([busy]: a small progress mark where
 * the check goes; [waiting]: every card waits).
 * Text: reading. Cap: Reading.
 * Motion: icon colour and the ring QUICK, the check lands over STANDARD from [MotionTokens.MARK_FROM]; Off = at once.
 */
@Composable
public fun KitChoiceCard(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    tag: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    busy: Boolean = false,
    waiting: Boolean = busy,
) {
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val solid = LocalKitStyle.current.style == Style.Solid
    val on = enabled.isEnabled
    val large = LocalDensity.current.fontScale > 1.3f // F: lib/ui/kit/kit_choice_cards.dart:115
    val line = ChoiceCardRules.line(listOfNotNull(tag, supporting).joinToString(" · ").ifEmpty { null }, enabled)
    val markSize = (FlutterKitTokens.ICON_TILE_SIZE - Space.S1).dp
    val focusRing = kitFocusRingWidth()
    val chosenRing = focusRing * 2 // F: lib/ui/kit/kit_choice_cards.dart:256
    val pad = Space.S5.dp + chosenRing
    val density = LocalDensity.current
    val both = pad * 2 // NV: the padding of both sides together
    val padBottom = with(density) { both.roundToPx().toDp() - pad.roundToPx().toDp() }
    // What whole pixels added to the card's height; the edge is drawn that much shorter, where Flutter draws it.
    val trim = with(density) { both.roundToPx() - both.toPx() }
    // The cards above added the same each, so in Flutter this card starts that much higher.
    val lift = trim * LocalChoiceCardIndex.current
    val fill =
        if (selected) colors.accent.copy(alpha = .07f).compositeOver(colors.surface1) // F: lib/ui/kit/kit_choice_cards.dart:258
        else colors.surface1
    val edge =
        if (selected) colors.accent
        else lerp(colors.hairline, colors.text3, .4f) // F: lib/ui/kit/kit_choice_cards.dart:270
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, SurfaceLevel.Surface2, ownFill = fill, selected = selected),
        )
    val iconColor by animateColorAsState(
        when {
            !on -> colors.text3
            selected -> colors.accent
            else -> colors.text2
        },
        specs.quick(),
        label = "kit-choice-card-icon",
    )
    val ringAlpha by animateFloatAsState(if (selected || busy) 0f else 1f, specs.quick(), label = "kit-choice-card-ring")
    val checkAlpha by animateFloatAsState(if (selected) 1f else 0f, specs.quick(), label = "kit-choice-card-check")
    val checkScale by animateFloatAsState(if (selected) 1f else MotionTokens.MARK_FROM, specs.enter(), label = "kit-choice-card-mark")
    val tap = ChoiceCardRules.tappable(enabled, waiting)
    // Hook H3: on the chosen card the words take the colour the style says reads on its chosen face.
    val words = if (selected) look.words else null
    val inkColors = if (words != null) colors.copy(text1 = words, text2 = words, text3 = words) else colors
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            // Solid: the face and its edge are drawn outside the tap's clip, where Flutter's fractional layout puts
            // them; every other style paints its own surface below.
            .then(if (solid) Modifier.choiceEdge(if (selected) chosenRing else kitHairline(), edge, look.shape, trim, lift, fill) else Modifier)
            .kitSelectTap(
                selected = selected,
                role = Role.RadioButton,
                enabled = if (tap) KitEnabled.Enabled else KitEnabled.Disabled(enabled.reason.orEmpty()),
                haptic = KitHaptic.Select,
                shape = KitShapeId.Card,
                onClick = onSelect,
            )
            .semantics(mergeDescendants = true) { contentDescription = ChoiceCardRules.label(title, line) }
            .heightIn(min = (FlutterKitTokens.MIN_TARGET * 3).dp) // F: lib/ui/kit/kit_choice_cards.dart:294
            .then(if (solid) Modifier else look.modifier)
            // The padding keeps room for the thickest ring, so choosing never moves the words. Flutter lays the
            // card out in fractions of a pixel; the bottom takes what whole pixels lose, so the card is as tall.
            .padding(start = pad, top = pad, end = pad, bottom = padBottom)
            .testTag("kit-choice-card"),
    ) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.Top) {
            if (icon != null) {
                val box = (FlutterKitTokens.MARK_SIZE + Space.S2).dp
                // Flutter fits the glyph into the box (FittedBox), so it is drawn at the box's size.
                val k = box / KitIconSize.Large.dp.dp
                Box(Modifier.size(box), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalKitColors provides colors.copy(text1 = iconColor)) {
                        KitIcon(
                            icon,
                            Modifier.requiredSize(KitIconSize.Large.dp.dp).graphicsLayer {
                                scaleX = k
                                scaleY = k
                            },
                            size = KitIconSize.Large,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(markSize).alpha(ringAlpha).choiceEdge(focusRing, colors.text3, CircleShape))
                if (busy) {
                    KitChoiceBusyMark(markSize, Space.S1.dp, Modifier.testTag("kit-choice-card-busy"))
                } else if (checkAlpha > 0f) {
                    Box(
                        Modifier.size(markSize)
                            .graphicsLayer {
                                scaleX = checkScale
                                scaleY = checkScale
                                alpha = checkAlpha
                            }
                            .background(colors.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(LocalKitColors provides colors.copy(text1 = colors.onAccent)) {
                            KitIcon(KitIcons.check, size = KitIconSize.Small)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.S5.dp))
        CompositionLocalProvider(LocalKitColors provides inkColors) {
            Column(Modifier.clearAndSetSemantics {}) {
                KitText(
                    title,
                    // Title size; at large text the headline, so the answer wraps at its words.
                    role = if (large) TypeRole.Headline else TypeRole.Title,
                    tone = if (on) TextTone.Primary else TextTone.Secondary,
                )
                if (line.isNotEmpty()) {
                    Spacer(Modifier.height(Space.S1.dp))
                    KitText(line, role = TypeRole.Body, tone = if (on) TextTone.Secondary else TextTone.Tertiary)
                }
            }
        }
    }
}

/**
 * Two or three large answers to one question, a card each, stacked [Space.S4] apart and heard as one group (Dart
 * `KitChoiceCards`). A tap chooses; [busy] shows progress on its card and every card waits.
 */
@Composable
public fun <T> KitChoiceCards(
    choices: List<KitChoiceCardItem<T>>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    busy: T? = null,
    semanticsLabel: String? = null,
) {
    KitChoiceCards(modifier.semantics { semanticsLabel?.let { contentDescription = it } }) {
        choices.forEachIndexed { i, c ->
            CompositionLocalProvider(LocalChoiceCardIndex provides i) {
                KitChoiceCard(
                    title = c.title,
                    selected = c.value == selected,
                    onSelect = { onSelected(c.value) },
                    supporting = c.supporting,
                    icon = c.icon,
                    enabled = c.enabled,
                    busy = ChoiceCardRules.showsBusy(c.value, busy),
                    waiting = busy != null,
                )
            }
        }
    }
}

/** Choice cards stacked [Space.S4] apart and heard as one group (Dart `KitChoiceCards`). */
@Composable
public fun KitChoiceCards(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
    ) { content() }
}

/**
 * The kit's small progress mark where a check goes (Dart `CircularProgressIndicator` with the spinner stroke): a
 * turning arc, its stroke centred on the box's edge as Flutter's indicator draws it; still at three quarters under
 * reduced motion (MOT-7). Decorative.
 */
@Composable
internal fun KitChoiceBusyMark(size: Dp, inset: Dp, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val motion = KitMotion.state
    val turn =
        if (motion.reduced) {
            null
        } else {
            rememberInfiniteTransition(label = "kit-choice-busy").animateFloat(
                0f,
                FULL_TURN,
                infiniteRepeatable(tween(MotionTokens.SPINNER_LAP, easing = LinearEasing), RepeatMode.Restart),
                label = "turn",
            )
        }
    Canvas(modifier.size(size).padding(inset).clearAndSetSemantics {}) {
        val w = FlutterKitTokens.SPINNER_STROKE.dp.toPx()
        val share = .75f // F: lib/ui/kit/kit_choice_cards.dart:167
        rotate(turn?.value ?: 0f) {
            drawArc(
                colors.accent,
                -FULL_TURN / 4f, // NV: Flutter's indicator starts at the top, a quarter turn back
                FULL_TURN * share,
                false,
                Offset.Zero,
                this.size,
                style = Stroke(w),
            )
        }
    }
}

/**
 * A border of exactly [width] inside the box, as Flutter paints `Border.all` (the stroke centred half its width in);
 * Compose's `border` rounds a width up to whole pixels, which turns a third-pixel hairline into a full one.
 * [trimBottomPx] lifts the bottom edge by what rounding a fractional layout to whole pixels added below it, and
 * [liftPx] the whole edge by what the boxes above it added; [face], when given, fills the same box under the content.
 */
internal fun Modifier.choiceEdge(
    width: Dp,
    color: Color,
    shape: Shape,
    trimBottomPx: Float = 0f,
    liftPx: Float = 0f,
    face: Color? = null,
): Modifier =
    drawWithContent {
        if (face != null) {
            translate(0f, -liftPx) {
                drawOutline(shape.createOutline(Size(size.width, size.height - trimBottomPx), layoutDirection, this), face)
            }
        }
        drawContent()
        val w = width.toPx()
        if (w <= 0f) return@drawWithContent
        val half = w / 2 // NV: the stroke is centred on its path, half its width in from the edge
        val box = Size(size.width, size.height - trimBottomPx)
        if (shape is RoundedCornerShape) {
            // Flutter paints Border.all on the rounded box deflated by half the width, its radius too.
            val r = (shape.topStart.toPx(box, this) - half).coerceAtLeast(0f)
            drawRoundRect(
                color,
                Offset(half, half - liftPx),
                Size(box.width - w, box.height - w),
                CornerRadius(r, r),
                style = Stroke(w),
            )
        } else {
            translate(half, half - liftPx) {
                drawOutline(shape.createOutline(Size(box.width - w, box.height - w), layoutDirection, this), color, style = Stroke(w))
            }
        }
    }

/** The card's place in its [KitChoiceCards] list (its edge follows Flutter's fractional layout). */
private val LocalChoiceCardIndex = compositionLocalOf { 0 }

private const val FULL_TURN = 360f // NV: one turn in degrees
