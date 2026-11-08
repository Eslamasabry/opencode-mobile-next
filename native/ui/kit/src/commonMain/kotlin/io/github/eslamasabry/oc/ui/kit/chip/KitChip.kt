package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitChipRemove
import org.jetbrains.compose.resources.stringResource

public enum class KitChipTone { Neutral, Attention, Active }
public enum class KitChipKind { Plain, Action, Removable, Count, Summary }

/** Flutter's worded pill, with independent body/remove targets and a decorative custom glyph slot. */
@Composable
public fun KitChip(
    label: String,
    modifier: Modifier = Modifier,
    kind: KitChipKind = KitChipKind.Plain,
    tone: KitChipTone = KitChipTone.Neutral,
    icon: ImageVector? = null,
    count: Int? = null,
    selected: Boolean? = null,
    removeLabel: String? = null,
    opensMenu: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    glyph: (@Composable () -> Unit)? = null,
    spoken: String? = null,
    liveRegion: Boolean = false,
    expanded: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    adaptiveModel: Boolean = false,
) {
    kitAssert(label.isNotBlank()) { "KitChip: a chip always carries a word" }
    kitAssert(kind != KitChipKind.Removable || onRemove != null) { "KitChip: a removable chip needs onRemove" }
    kitAssert(kind != KitChipKind.Action || onClick != null) { "KitChip: an action chip needs onClick" }
    val specs = KitMotion.specs
    val c = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val track = LocalKitStyle.current.spec.tokens.track
    val nativeInk = LocalKitPlatform.current.family == KitPlatformFamily.Android && !LocalKitMotion.current.reduced
    val bodySource = remember(nativeInk) { ChipZoneInteractions(nativeInk) }
    val removeSource = remember(nativeInk) { ChipZoneInteractions(nativeInk) }
    var faceSize by remember { mutableStateOf(IntSize.Zero) }
    val bodyFocus by bodySource.face.collectIsFocusedAsState()
    val removeFocus by removeSource.face.collectIsFocusedAsState()
    val pressed by bodySource.face.collectIsPressedAsState()
    val hovered by bodySource.face.collectIsHoveredAsState()
    val removePressed by removeSource.face.collectIsPressedAsState()
    val removeHovered by removeSource.face.collectIsHoveredAsState()
    // Glass draws an active chip as it draws a chosen one: accent-tinted frost with the accent rim (owner 2026-11-08: the
    // olive fill read heavy), its words the pane's own.
    val glassActive = tone == KitChipTone.Active && LocalKitStyle.current.style == Style.Glass
    val tint = if (tone == KitChipTone.Active && !glassActive) c.accent.copy(alpha = .22f).compositeOver(c.surface3) else null // F: lib/ui/kit/kit_chip.dart:232
    val fill = tint ?: if (pressed || hovered || removePressed || removeHovered) c.surface2 else c.surface3
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Pill, level = if (fill == c.surface2) SurfaceLevel.Surface2 else SurfaceLevel.Surface3, ownFill = fill, color = tint, selected = selected == true || glassActive, pressed = pressed || removePressed))
    val semanticInk = when (tone) {
        KitChipTone.Attention -> c.attention
        KitChipTone.Active -> c.accent
        KitChipTone.Neutral -> if (kind == KitChipKind.Plain || kind == KitChipKind.Count) c.text2 else c.text1
    }
    val ink = if (tone == KitChipTone.Neutral) semanticInk else Color(RoleDerivation.readableOn(Argb(semanticInk.toArgb()), (if (tint != null) listOf(tint) else listOf(c.surface2, c.surface3)).map { Argb(it.toArgb()) }, 4.7, Argb(c.text1.toArgb())).v) // F: lib/ui/kit/kit_chip.dart:239
    val words = if (selected == true || glassActive) look.words ?: ink else ink
    // On the chat's overlaid bottom block (the chips over the message box) a Glass chip is a small drop of liquid glass
    // over the transcript, as the jump pill is; chosen or on, it leans to the accent (owner 2026-11-08). Null keeps the
    // chip's own face.
    val liquid =
        io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidChrome.current?.let { chrome ->
            io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace(
                io.github.eslamasabry.oc.ui.design.effects.LiquidRole.Pill,
                chrome,
                look.shape,
                chosen = selected == true || glassActive,
            )
        }
    val baseStyle = LocalKitTypography.current.style(TypeRole.Secondary)
    // C09: a label reads in its own direction (first strong letter), so a Latin model name in an Arabic layout
    // keeps its ellipsis at its end ("Crouton Fa…"), never before it ("…Crouton Fa").
    val firm = !adaptiveModel && (kind == KitChipKind.Plain || track)
    val style =
        baseStyle.copy(
            color = words,
            fontWeight = if (firm) FontWeight.SemiBold else baseStyle.fontWeight,
            textDirection = androidx.compose.ui.text.style.TextDirection.Content,
        )
    val formatted = count?.let { Numbers.format(it) }
    val text = if (kind == KitChipKind.Count && formatted != null) {
        AnnotatedString.Builder().apply {
            append(label)
            append(" · ")
            pushStyle(SpanStyle(color = if (tone == KitChipTone.Neutral) c.text1 else words, fontFeatureSettings = "tnum"))
            append(formatted)
            pop()
        }.toAnnotatedString()
    } else AnnotatedString(label)
    val accessible = spoken ?: if (kind == KitChipKind.Count && formatted != null) "$label, $formatted" else label
    val removeSpoken = removeLabel ?: stringResource(Res.string.kitChipRemove, label)
    val iconSize = tokens.smallIcon.dp * LocalDensity.current.fontScale.coerceAtMost(tokens.maxIconScale)
    val minHeight = tokens.chipHeight.dp + if (track && !adaptiveModel) Space.S1.dp else 0.dp
    val focusWidth = (2f / LocalDensity.current.density).dp // F: lib/ui/kit/kit_chip.dart:801
    val target = modifier.kitMaxWidth(if (adaptiveModel) CapRole.Conversation else CapRole.Chip).heightIn(min = tokens.minTarget.dp).then(
        if (onClick != null || onRemove != null) Modifier.widthIn(min = tokens.minTarget.dp) else Modifier,
    ).onPreviewKeyEvent {
        if (onRemove != null && it.type == KeyEventType.KeyDown && (it.key == Key.Delete || it.key == Key.Backspace)) { onRemove(); true } else false
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val platform = LocalKitPlatform.current
    BoxWithConstraints(target, contentAlignment = Alignment.Center) {
        val available = maxWidth - Space.S3.dp * 2 - iconSize * 2 - Space.S2.dp - Space.S1.dp // F: lib/ui/kit/chat/kit_composer_chips.dart:472-477
        fun fits(probe: String): Boolean {
            if (available <= 0.dp) return false
            return !measurer.measure(
                probe, style, maxLines = 1,
                constraints = Constraints(maxWidth = with(density) { available.roundToPx() }),
            ).hasVisualOverflow
        }
        val prefix = if (adaptiveModel) platform.graphemePrefix(label, 6) ?: label else label // F: lib/ui/kit/chat/kit_composer_chips.dart:478-485
        val narrow = adaptiveModel && constraints.hasBoundedWidth && !fits(label) && (prefix == label || !fits("$prefix…"))
        val modelPairFits = iconSize * 2 + Space.S1.dp * 2 <= maxWidth // F: lib/ui/kit/chat/kit_composer_chips.dart:504-506
        Row(
            Modifier.onSizeChanged { faceSize = it }.heightIn(min = minHeight).then(liquid ?: look.modifier)
                .drawWithContent {
                    drawContent()
                    if (bodyFocus || removeFocus) {
                        val w = focusWidth.toPx()
                        val outline = look.shape.createOutline(Size(size.width + w, size.height + w), layoutDirection, this)
                        translate(-w / 2, -w / 2) { drawOutline(outline, c.accent, style = Stroke(w)) } // NV: paint Flutter strokeAlignOutside around the face, converted to a centred Compose stroke
                    }
                }
                .padding(horizontal = if (adaptiveModel && narrow) Space.S1.dp else if (adaptiveModel) Space.S3.dp else if (track) Space.S4.dp else Space.S3.dp)
                .clearAndSetSemantics { if (onClick == null && onRemove == null) contentDescription = accessible },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasStart = selected == true || glyph != null || icon != null
            if (hasStart) {
                AnimatedContent(
                    targetState = selected == true,
                    transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) },
                    label = "kit-chip-glyph",
                ) { checked ->
                    Box(Modifier.padding(end = if (narrow) 0.dp else if (adaptiveModel) Space.S2.dp else Space.S1.dp).size(iconSize).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        if (checked) KitGlyph(KitIcons.check, c.accent, iconSize, Modifier.testTag("kit-chip-check"))
                        else if (glyph != null) glyph()
                        else if (icon != null) KitGlyph(icon, if (adaptiveModel) c.text2 else words, iconSize)
                    }
                }
            }
            if (!narrow) BasicText(
                text, Modifier.weight(1f, fill = false).testTag(if (adaptiveModel) "kit-model-chip-label" else "kit-chip-label"),
                style = style,
                maxLines = if (adaptiveModel && density.fontScale >= 1.3f) 2 else 1, // F: lib/ui/kit/chat/kit_composer_chips.dart:468,523
                softWrap = adaptiveModel,
                overflow = TextOverflow.Ellipsis,
            )
            if (kind == KitChipKind.Removable || (opensMenu && (!narrow || modelPairFits)) || expanded != null) {
                KitGlyph(if (kind == KitChipKind.Removable) KitIcons.close else KitIcons.chevronDown, if (adaptiveModel || kind == KitChipKind.Removable) c.text2 else c.text1, iconSize,
                    Modifier.padding(start = if (narrow) 0.dp else Space.S1.dp).testTag("kit-chip-chevron").then(if (expanded == true) Modifier.rotate(180f) else Modifier)) // NV: Flutter kit_chip.dart:384 half-turn converted to Compose degrees
            }
        }
        Box(Modifier.matchParentSize().drawWithContent {
            // The hit zones remain 48 dp; their native ripple paints only on the shared visible pill.
            val face = Size(faceSize.width.toFloat(), faceSize.height.toFloat())
            val path = Path().apply {
                addOutline(look.shape.createOutline(face, layoutDirection, this@drawWithContent))
                translate(Offset((size.width - face.width) / 2, (size.height - face.height) / 2)) // NV: convert Flutter centred pill alignment to hit-area coordinates
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        }) {
        if (onRemove != null) {
            if (onClick != null) Box(Modifier.matchParentSize().padding(end = tokens.minTarget.dp).kitTappable(onClick, onLongClick = onLongClick, shape = KitShapeId.Pill, interactionSource = bodySource).semantics { contentDescription = accessible })
            Box(Modifier.align(Alignment.CenterEnd).width(tokens.minTarget.dp).heightIn(min = tokens.minTarget.dp).kitTappable(onRemove, shape = KitShapeId.Pill, interactionSource = removeSource).testTag("kit-chip-remove").semantics { contentDescription = removeSpoken })
        } else if (onClick != null) {
            Box(Modifier.matchParentSize().kitTappable(onClick, onLongClick = onLongClick, shape = KitShapeId.Pill, interactionSource = bodySource).semantics {
                contentDescription = accessible
                if (selected != null) { this.selected = selected; role = Role.Checkbox }
                if (expanded == true) collapse { onClick(); true } else if (expanded == false) expand { onClick(); true }
                if (liveRegion) this.liveRegion = LiveRegionMode.Polite
            })
        }
        }
    }
}

/** Wrapping chips retain a clear gap between their full touch areas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitChipWrap(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tokens = LocalKitTokens.current
    FlowRow(modifier.kitMaxWidth(CapRole.Reading), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalArrangement = Arrangement.spacedBy((tokens.minTarget - tokens.chipHeight).dp)) { content() }
}

@Composable
public fun KitStepChip(label: String, checked: Boolean, selected: Boolean, onClick: () -> Unit) {
    KitChip(label = label, kind = KitChipKind.Action, icon = if (checked) KitIcons.check else null, selected = selected, onClick = onClick)
}

/** Each zone sends all interactions to its pill, and only native press ink to the hit-zone indication. */
private class ChipZoneInteractions(private val nativeInk: Boolean) : MutableInteractionSource {
    val face = MutableInteractionSource()
    private val ink = MutableInteractionSource()
    override val interactions: Flow<Interaction> get() = ink.interactions
    override suspend fun emit(interaction: Interaction) {
        face.emit(interaction)
        if (nativeInk && interaction is PressInteraction) ink.emit(interaction)
    }
    override fun tryEmit(interaction: Interaction): Boolean {
        val accepted = face.tryEmit(interaction)
        return if (nativeInk && interaction is PressInteraction) ink.tryEmit(interaction) && accepted else accepted
    }
}
