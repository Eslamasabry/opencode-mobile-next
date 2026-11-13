package io.github.eslamasabry.oc.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.style.LocalKitSignalInSlip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/** Dart `KitButtonRole`: primary = accent fill, secondary = surface3, tertiary = accent-ink words on a faint capsule. */
public enum class KitButtonRole {
    Primary,
    Secondary,
    Tertiary,
}

/**
 * One action a block or menu can offer (Dart `KitAction`). [onClick] null means the action cannot
 * run now, and then [enabled] must carry the reason. [working] marks the tap in flight: the action
 * stays its enabled colour but ignores taps (STATE-7). [destructive] is never red: a strong
 * neutral, and it is offered apart (B-KIT-011).
 */
@Immutable
public data class KitAction(
    val label: String,
    val onClick: (() -> Unit)?,
    val enabled: KitEnabled = KitEnabled.Enabled,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val working: Boolean = false,
    val haptic: KitHaptic? = null,
    /** What a screen reader says instead of [label] (e.g. the label plus its object). */
    val semanticsLabel: String? = null,
    /**
     * The key combination that does the same ("Ctrl+Enter"): shown after the label on a fine pointer from the expanded
     * window up. Display only; the shortcut layer binds it.
     */
    val shortcut: String? = null,
) {
    /** True when a tap would run the action now. */
    val runnable: Boolean
        get() = onClick != null && enabled.isEnabled
}

/**
 * A button (Dart `KitButton`; plan 06 §7.5). Primary = accent fill with `onAccent` words; a
 * destructive primary is the strong neutral (text1 fill, ground words), never red; secondary =
 * `surface3`; tertiary = the accent's readable ink on a faint capsule of it ([kitTertiaryInk]). Disabled = `surface3` with `text3` words (tertiary: `text3`
 * words) and its reason as the state a screen reader hears. [working] shows the working mark before
 * the words and makes taps no-ops while the button keeps its colour. Labels wrap to two lines.
 * Plain presses are silent; pass [haptic] only when the action's meaning calls for one (plan 06
 * §4.9). [fullWidth] makes it the screen's full-width button (the mint one at the foot of a page), capped at 480 dp and
 * centred on wide windows.
 *
 * Press (Material 3 Expressive, on our shapes): the face rounds its corners a little and settles 2 % smaller on the
 * fast spatial spring, then springs back; reduced motion keeps the ripple only.
 *
 * States: default, pressed, disabled, working Text: frame Cap: Control Motion: fill eases between
 * enabled and disabled (QUICK); Off = instant; the working mark is still under Off Replaces:
 * KitButton, KitButton.primary/secondary/tertiary (lib/ui/kit/kit_buttons.dart)
 */
@Composable
public fun KitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: KitButtonRole = KitButtonRole.Primary,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    /** A quiet (tertiary) button whose words are the kit's warning ink: the destructive answer beside a filled safe one. */
    danger: Boolean = false,
    maxLines: Int = 2,
    semanticsLabel: String? = null,
    enabled: KitEnabled = KitEnabled.Enabled,
    working: Boolean = false,
    haptic: KitHaptic? = null,
    fullWidth: Boolean = false,
    hero: Boolean = false,
    expand: Boolean = true,
    shortcut: String? = null,
) {
    kitAssert(label.isNotBlank()) { "KitButton: a button needs words (B-KIT-011)" }
    kitAssert(enabled !is KitEnabled.Disabled || enabled.reason.isNotBlank()) {
        "KitButton: a disabled button must say why (B-KIT-011)"
    }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val on = enabled.isEnabled
    val heroWaits = hero && LocalKitStyle.current.style != Style.Solid
    val heroFill = colors.accent.copy(alpha = .14f).compositeOver(colors.surface1) // F: lib/ui/kit/kit_buttons.dart:26
    val heroWords = if (!heroWaits) colors.text3 else remember(colors) {
        val under = colors.ground.copy(alpha = 1f)
        val surface = colors.surface1.compositeOver(under)
        Color(RoleDerivation.readableOn(
            Argb(colors.accent.copy(alpha = .55f).compositeOver(surface).toArgb()), // F: lib/ui/kit/kit_buttons.dart:37
            listOf(Argb(heroFill.compositeOver(under).toArgb())),
            4.6, toward = Argb(colors.text1.copy(alpha = 1f).toArgb()), // F: lib/ui/kit/kit_buttons.dart:39
        ).v)
    }
    val buttonShape = if (hero) KitShapeId.Pill else KitShapeId.Button
    val signalKey = LocalKitSignalInSlip.current
    // Device pass 2026-11-13 ("Send again" read as a heading): a text action in plain text1 words looked like any
    // other bold line. It reads as an action in every style as a native text button does: its words in the accent's
    // readable ink (danger: the attention colour), with no face at rest; the press, focus and hover state layer is its
    // touch shape. A style whose links are underlined (Brutal) underlines it too.
    val tertiaryInk = kitTertiaryInk(danger)
    val fill: Color =
        when {
            role == KitButtonRole.Tertiary -> Color.Transparent
            !on -> if (heroWaits) heroFill else colors.surface3
            role == KitButtonRole.Secondary -> colors.surface3
            destructive -> colors.text1
            else -> colors.accent
        }
    val words: Color =
        when {
            // A waiting hero (Start) stays readable: its words in text2, never the near-invisible text3.
            !on -> if (heroWaits) heroWords else if (hero) colors.text2 else colors.text3
            role == KitButtonRole.Primary && destructive -> colors.ground
            role == KitButtonRole.Primary -> colors.onAccent
            role == KitButtonRole.Tertiary -> tertiaryInk
            // The solid accent fill stays the primary action's alone; a text action shows the accent only as its ink.
            else -> colors.text1 // F: lib/ui/kit/kit_buttons.dart:732
        }
    // Muted → solid when the step becomes valid (owner reference 2026-11-07): the fill washes in on the effects spring.
    val easedFill by animateColorAsState(fill, specs.effects(), label = "kit-button-fill")
    val easedWords by animateColorAsState(words, specs.effects(), label = "kit-button-words")
    val ink =
        if (role == KitButtonRole.Primary && !destructive) TextTone.OnAccent
        else TextTone.Primary
    // The style decides the face (B-KIT-043..045); Solid keeps the fill above. A tertiary button has no face.
    val look =
        if (role == KitButtonRole.Tertiary) {
            null
        } else {
            kitSurfaceLook(
                KitSurfaceInput(
                    role = SurfaceRole.Control,
                    shape = buttonShape,
                    level = SurfaceLevel.Surface3,
                    ownFill = easedFill,
                    color = if ((role == KitButtonRole.Primary && on) || (!on && heroWaits)) easedFill else null,
                    disabled = !on,
                    action = true,
                ),
            )
        }
    val wordsColor = look?.words?.takeIf { on } ?: easedWords
    // Dart: a hero is minTarget + space2 tall; an expanded button buttonHeight; a tertiary or a button in a row
    // (expand false) the touch target.
    val minHeight =
        if (hero) tokens.minTarget + Space.S2
        else if (role == KitButtonRole.Tertiary || !expand) tokens.minTarget
        else tokens.buttonHeight
    // A tertiary button keeps space2 on both sides; a filled one the app theme's 16, with or without its glyph.
    // Its press and focus layer fills that padded box, which reaches out past the words by the padding (kitBleed below).
    val pad = if (role == KitButtonRole.Tertiary) Space.S2 else FILLED_SIDE_PAD_DP // F: lib/ui/kit/kit_buttons.dart:728
    val padStart = pad
    // The app theme's filled buttons keep 12 above and below their words, so large text grows the button; a tertiary
    // button's own padding is horizontal only.
    val padV = if (role == KitButtonRole.Tertiary) 0 else FILLED_VERTICAL_PAD_DP
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val reduced = KitMotion.state.reduced
    val press by animateFloatAsState(if (pressed && on && !reduced) 1f else 0f, specs.spatial(), label = "kit-button-press")
    val flatFace = LocalKitStyle.current.style == Style.Solid
    val rest = if (hero) minHeight / 2 else (Shapes.of(KitShapeId.Button) as? Corner.Radius)?.dp ?: PRESS_ROUND_DP // NV: a stadium's radius is half its height
    Row(
        modifier =
            // A text action's words sit on the column's edge, like the fields and words around it; its padding and
            // touch target reach into the gutter on both sides, so a centred one stays centred.
            // The control cap holds whatever the caller passes (kit-v2 §8.6: a part never paints wider than its cap),
            // inside a text action's reach so its target is capped too.
            // In a stacked block the text action fills the column like the buttons above it, so it keeps their
            // edges exactly instead (E20-S09: the bleed then hit the control cap and shifted it off the gutter).
            (if (role == KitButtonRole.Tertiary && !hero && !LocalKitStackedAction.current) Modifier.kitBleed(pad.dp)
            else Modifier)
                .kitMaxWidth(CapRole.Control)
                .then(modifier)
                .then(if (hero) Modifier.kitMaxWidth(CapRole.Control).fillMaxWidth() else if (fullWidth) Modifier.widthIn(max = FILL_MAX_WIDTH_DP.dp).fillMaxWidth() else Modifier)
                .heightIn(min = minHeight.dp)
                .widthIn(min = if (role == KitButtonRole.Tertiary || !expand) tokens.minTarget.dp else 0.dp)
                .graphicsLayer {
                    if (press > 0f) {
                        val scale = 1f - PRESS_SHRINK * press
                        scaleX = scale
                        scaleY = scale
                        shape = if (hero) look?.shape ?: androidx.compose.foundation.shape.CircleShape else RoundedCornerShape((rest + (PRESS_ROUND_DP - rest) * press).dp)
                        // Solid rounds the pressed face by clipping it; a style face keeps its shape and its shadow.
                        clip = role != KitButtonRole.Tertiary && flatFace
                    }
                }
                // The face (fill, edge and the style's shadow) sits outside the press clip, so its shadow is whole.
                .then(if (look != null) look.modifier else Modifier.background(easedFill, buttonShape.shape()))
                .kitTappable(
                    onClick = { if (!working) onClick() },
                    enabled = on,
                    disabledReason = enabled.reason,
                    haptic = if (working) null else haptic,
                    shape = buttonShape,
                    ink = ink,
                    interactionSource = interactions,
                )
                .semantics {
                    if (working) disabled()
                    if (semanticsLabel != null) contentDescription = semanticsLabel
                    if (role == KitButtonRole.Primary && !hero) this[KitPrimaryButtonKey] = true
                }
                .then(if (hero) Modifier.testTag("kit-button-hero") else Modifier)
                .then(
                    if (signalKey)
                        Modifier.drawBehind {
                            val stroke = 1.dp.toPx()
                            drawRect(
                                colors.text1.copy(alpha = .25f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                            )
                            drawLine(
                                colors.text1.copy(alpha = .35f),
                                Offset(0f, size.height - stroke),
                                Offset(size.width, size.height - stroke),
                                2.dp.toPx(),
                            )
                        }
                    else Modifier
                )
                .padding(start = padStart.dp, end = pad.dp, top = padV.dp, bottom = padV.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            working -> ButtonSpinner(wordsColor, tokens.smallIcon.dp)
            icon != null -> KitGlyph(icon, wordsColor, tokens.smallIcon.dp)
        }
        KitButtonWords(label, wordsColor, maxLines, shortcut, role)
    }
}

/**
 * Marks a filled accent button in the semantics tree so a page test can hold the one-accent-button rule (audit C15):
 * `onAllNodes(SemanticsMatcher.keyIsDefined(KitPrimaryButtonKey))` is at most one per page.
 */
public val KitPrimaryButtonKey: androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean> =
    androidx.compose.ui.semantics.SemanticsPropertyKey("KitPrimaryButton")

/** True inside a stacked [io.github.eslamasabry.oc.ui.kit.action.KitActionBlock]: every action there fills one column. */
internal val LocalKitStackedAction = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Measures [x] wider on each side than offered and places the content [x] towards the start, reporting the offered size. */
private fun Modifier.kitBleed(x: Dp): Modifier =
    layout { measurable, constraints ->
        val px = x.roundToPx()
        val grow = if (constraints.hasBoundedWidth) 2 * px else 0
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = (constraints.minWidth + grow).coerceAtMost(constraints.maxWidth + grow),
                    maxWidth = constraints.maxWidth + grow,
                )
            )
        val width = (placeable.width - 2 * px).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, placeable.height) { placeable.placeRelative(-px, 0) }
    }

/**
 * The words of a text action (a tertiary button, a line's inline action). Device pass 2026-11-13 ("Send again" read as a
 * heading): plain text1 words looked like any other bold line. They are the accent's readable ink (danger: the attention
 * colour), at 4.5:1 or better on the ground and the page's surface, so the action reads as one in every style without
 * a face of its own at rest (the press, focus and hover layer is its touch shape) and never as the primary's fill.
 */
@Composable
internal fun kitTertiaryInk(danger: Boolean = false): Color {
    val colors = LocalKitColors.current
    return remember(colors, danger) {
        if (danger) colors.attention
        else {
            val under = colors.ground.copy(alpha = 1f)
            Color(
                RoleDerivation.readableOn(
                    Argb(colors.accent.copy(alpha = 1f).toArgb()),
                    listOf(
                        Argb(under.toArgb()),
                        Argb(colors.surface1.compositeOver(under).toArgb()),
                    ),
                    TERTIARY_CONTRAST,
                    toward = Argb(colors.text1.copy(alpha = 1f).toArgb()),
                ).v
            )
        }
    }
}

/** True where the style in force underlines its links (Brutal): a text action is underlined there too. */
@Composable
internal fun kitTertiaryUnderlined(): Boolean = LocalKitStyle.current.spec.tokens.linkUnderline != null

/** The text action's words against the ground and the page's surface. */
private const val TERTIARY_CONTRAST = 4.6

/** The app theme's filled button side padding (Dart `filledButtonTheme`). */
private const val FILLED_SIDE_PAD_DP = 16 // F: lib/ui/app_theme.dart:370
private const val FILLED_VERTICAL_PAD_DP = 12 // F: lib/ui/app_theme.dart:370

/** The full-width button's cap (direction §5.6). */
private const val FILL_MAX_WIDTH_DP = 480

/** How round a pressed face gets, and how much it settles. */
private const val PRESS_ROUND_DP = 22
private const val PRESS_SHRINK = 0.02f

/**
 * The slice-0 bridge: `enabled` + `disabledReason` as two values. New code passes [KitEnabled];
 * this overload keeps the P1/P7 call sites (`:feature:chats`, `:ui:shell`, the P7 scenes) compiling
 * unchanged.
 */
@Composable
public fun KitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    disabledReason: String? = null,
    haptic: KitHaptic? = null,
) {
    KitButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = KitEnabled.of(enabled, disabledReason),
        haptic = haptic,
    )
}

/** Runs a [KitAction] as a button of [role]. */
@Composable
public fun KitButton(
    action: KitAction,
    modifier: Modifier = Modifier,
    role: KitButtonRole = KitButtonRole.Primary,
    fullWidth: Boolean = false,
    hero: Boolean = false,
    expand: Boolean = true,
) {
    val run = action.onClick
    kitAssert(run != null || !action.enabled.isEnabled) {
        "KitAction: an action without a callback must say why it cannot run (B-KIT-011)"
    }
    KitButton(
        label = action.label,
        onClick = { run?.invoke() },
        modifier = modifier,
        role = role,
        icon = action.icon,
        destructive = action.destructive,
        semanticsLabel = action.semanticsLabel,
        enabled =
            if (run == null && action.enabled.isEnabled) KitEnabled.Disabled(action.label)
            else action.enabled,
        working = action.working,
        haptic = action.haptic,
        fullWidth = fullWidth,
        hero = hero,
        expand = expand,
        shortcut = action.shortcut,
    )
}

@Composable
private fun KitButtonWords(label: String, color: Color, maxLines: Int, shortcut: String?, role: KitButtonRole) {
    // Tone is resolved by the caller's eased colour; KitText takes a tone, so the words draw
    // through a tone-free path.
    val window = LocalKitWindow.current
    // Visual language §5: the shortcut shows on a fine pointer, from the expanded window up.
    if (shortcut == null || !window.finePointer || !window.cls.isWide) {
        KitTextColored(label, color, maxLines, underline = role == KitButtonRole.Tertiary && kitTertiaryUnderlined())
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f, fill = false)) { KitTextColored(label, color, maxLines, underline = role == KitButtonRole.Tertiary && kitTertiaryUnderlined()) }
        Spacer(Modifier.width(Space.S1.dp))
        // Mono, text3 on a tertiary button and the button's own words otherwise; always left to right.
        val mono = LocalKitTypography.current.style(TypeRole.Mono)
        val hint = if (role == KitButtonRole.Tertiary) LocalKitColors.current.text3 else color
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ButtonLine(shortcut, mono.copy(color = hint, textDirection = TextDirection.Ltr), 1)
        }
    }
}

@Composable
private fun KitTextColored(text: String, color: Color, maxLines: Int, underline: Boolean = false) {
    // Dart `_KitButtonLabel`: a plain Text in the theme's button style, so the label follows the person's text size in
    // full (2.0x text draws 2.0x words and a taller button), not the 1.3x chrome cap. A style with type of its own
    // keeps firm 600 words, and so does Arabic (the system Arabic face at 500 reads too thin on a filled button).
    val arabic = LocalKitLocaleTag.current.startsWith("ar")
    val firm = LocalKitStyle.current.spec.tokens.type.firmWeights || arabic
    val base = LocalKitTypography.current.style(TypeRole.Button)
    ButtonLine(
        text,
        base.copy(
            color = color,
            fontWeight = if (firm) FontWeight.W600 else base.fontWeight, // F: lib/ui/kit/kit_buttons.dart:805
            textAlign = TextAlign.Center,
            textDirection = TextDirection.Content,
            textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        ),
        maxLines,
    )
}

/**
 * One line of a button's words. Flutter lays words out at their fractional width; Compose rounds a paragraph up to the
 * next pixel, so a row of buttons drifted a pixel per item. A one-line text reports its width rounded to the nearest
 * pixel instead.
 */
@Composable
private fun ButtonLine(text: String, style: androidx.compose.ui.text.TextStyle, maxLines: Int) {
    val exact = remember { FloatArray(1) }
    BasicText(
        text,
        Modifier
            .kitMaxWidth(CapRole.Reading, io.github.eslamasabry.oc.ui.design.layout.CapAlign.Center)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val nearest = kotlin.math.round(exact[0]).toInt()
                val width = if (nearest in 1 until placeable.width) nearest.coerceAtLeast(constraints.minWidth) else placeable.width
                layout(width, placeable.height) { placeable.placeRelative((width - placeable.width) / 2, 0) } // NV: half the sub-pixel trim
            },
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            exact[0] = if (it.lineCount == 1 && !it.hasVisualOverflow) it.multiParagraph.getLineRight(0) - it.multiParagraph.getLineLeft(0) else 0f
        },
    )
}

/**
 * The small spinner a working button shows, in the button's own words colour (Dart `_Spinner`): the kit's spinner
 * stroke with no track; under reduced motion a still three-quarter arc from the top (MOT-7).
 */
@Composable
private fun ButtonSpinner(color: Color, size: Dp) {
    val stroke = io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens.SPINNER_STROKE.dp
    if (!KitMotion.state.reduced) {
        KitWorkingMark(color, size = size, strokeWidth = stroke)
        return
    }
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        drawArc(
            color = color,
            startAngle = -90f, // NV: Material's CircularProgressIndicator starts its arc at 12 o'clock
            sweepAngle = 360f * .75f, // NV: one turn times the still value .75 (kit_buttons.dart:856)
            useCenter = false,
            topLeft = Offset(w / 2, w / 2), // NV: the stroke sits inside the box, as Material insets it
            size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
        )
    }
}
