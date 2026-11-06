package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
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

/** Flutter KitOptionCard.theme: the candidate's colours, with the name outside its swatch. */
@Composable
public fun KitThemeOptionCard(
    pack: PackId,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = LocalKitColors.current
    val roles =
        remember(pack, frame.dark) { ThemeResolver.roles(pack, null, frame.dark, false, null) }
    val colors = roles.toKitColors()
    val radius = RoundedCornerShape((FlutterKitTokens.CARD_RADIUS - Space.S1).dp)
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Content,
                KitShapeId.Card,
                ownFill = colors.ground,
                color = colors.ground,
                selected = selected,
            )
        )
    KitTappable(
        onClick = onSelect,
        modifier =
            modifier.kitMaxWidth(CapRole.Reading).semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        shape = KitShapeId.Card,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .then(look.modifier)
                    .clip(radius)
                    .border(
                        if (selected) kitFocusRingWidth() else kitHairline(),
                        if (selected) frame.accent else frame.hairline,
                        radius,
                    )
                    .padding(Space.S3.dp)
            ) {
                if (selected) OptionCheck(Modifier.align(Alignment.TopEnd))
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth(.72f) // F: lib/ui/kit/kit_option_card.dart:451
                                .height((Space.S1 + 2).dp) // F: lib/ui/kit/kit_option_card.dart:453
                                .background(colors.accent, RoundedCornerShape(Space.S1.dp))
                        )
                    }
                    Spacer(Modifier.width(Space.S2.dp))
                    Box(Modifier.size(Space.S3.dp).background(colors.partner, CircleShape))
                }
            }
            Spacer(Modifier.height(Space.S2.dp))
            KitText(
                label,
                Modifier.padding(start = Space.S1.dp),
                role = TypeRole.RowTitle,
                maxLines = 1,
            )
        }
    }
}

/**
 * Flutter KitOptionCard.surface: the candidate style draws its sample, independently of the active
 * style.
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
    val colors = LocalKitColors.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Content,
                KitShapeId.Card,
                SurfaceLevel.Surface2,
                ownFill = colors.surface1,
                selected = selected,
            )
        )
    KitTappable(
        onClick = onSelect,
        modifier =
            modifier.kitMaxWidth(CapRole.Reading).semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        enabled = enabled,
        disabledReason = disabledReason,
        shape = KitShapeId.Card,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(look.modifier)
                .border(
                    if (selected) kitFocusRingWidth() else kitHairline(),
                    if (selected) colors.accent else colors.hairline,
                    look.shape,
                )
                .padding(Space.S3.dp)
        ) {
            StyleSample(style)
            Spacer(Modifier.height(Space.S2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitText(label, Modifier.weight(1f), role = TypeRole.RowTitle, maxLines = 1)
                if (selected) OptionCheck() else Spacer(Modifier.size(Space.S5.dp))
            }
        }
    }
}

/**
 * Flutter KitOptionCard.look (`lib/ui/kit/kit_option_card.dart:68-81`, `:204-262` at GLASS_REF 1bbdc3f9c): a still
 * frame of a live look behind a tiny mock conversation, filling the card, with the look's name under it. [look] is the
 * look's id; null is "Off" (the conversation on the plain ground). The card fills the cell its grid gives it.
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
    val colors = LocalKitColors.current
    val look0 =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Content,
                KitShapeId.Card,
                SurfaceLevel.Surface2,
                ownFill = colors.surface1,
                selected = selected,
                disabled = !enabled,
            )
        )
    // A look's name may take two lines in a narrow cell at ordinary text (kit_option_card.dart:216-221).
    val lines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.5f) 2 else 1
    KitTappable(
        onClick = onSelect,
        modifier =
            modifier.kitMaxWidth(CapRole.Reading).semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        enabled = enabled,
        disabledReason = disabledReason,
        shape = KitShapeId.Card,
    ) {
        Column(
            Modifier.fillMaxSize()
                .then(look0.modifier)
                .border(
                    if (selected) kitFocusRingWidth() else kitHairline(),
                    if (selected) colors.accent else colors.hairline,
                    look0.shape,
                )
                .padding(Space.S3.dp)
                .clearAndSetSemantics {}
        ) {
            io.github.eslamasabry.oc.ui.kit.live.KitLiveLookStill(look, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(Space.S2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitText(
                    label,
                    Modifier.weight(1f),
                    role = TypeRole.RowTitle,
                    tone = if (enabled) TextTone.Primary else TextTone.Tertiary,
                    maxLines = lines,
                )
                if (selected) OptionCheck() else Spacer(Modifier.size(Space.S5.dp))
            }
        }
    }
}

/** Flutter KitOptionCard.plain: a small icon/check rail, title and three-line caption. */
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
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Content,
                KitShapeId.Card,
                SurfaceLevel.Surface2,
                ownFill = colors.surface1,
                selected = selected,
                disabled = !enabled.isEnabled,
            )
        )
    val mark by
        animateFloatAsState(
            if (selected) 1f else 0f,
            KitMotion.specs.enter(),
            label = "option-mark",
        )
    val markScale by
        animateFloatAsState(
            if (selected) 1f else MotionTokens.MARK_FROM,
            KitMotion.specs.enter(),
            label = "option-mark-scale",
        )
    KitTappable(
        onClick = onSelect,
        modifier =
            modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            },
        enabled = enabled.isEnabled,
        disabledReason = enabled.reason,
        shape = KitShapeId.Card,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(look.modifier)
                .border(
                    if (selected) kitFocusRingWidth() else kitHairline(),
                    if (selected) colors.accent else colors.hairline,
                    look.shape,
                )
                .padding(Space.S3.dp)
                .clearAndSetSemantics {}
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KitGlyph(icon, if (selected) colors.accent else colors.text2, tokens.smallIcon.dp)
                Spacer(Modifier.weight(1f))
                OptionCheck(
                    Modifier.graphicsLayer {
                        alpha = mark
                        scaleX = markScale
                        scaleY = markScale
                    }
                )
            }
            Spacer(Modifier.height(Space.S2.dp))
            KitText(
                label,
                role = TypeRole.RowTitle,
                tone = if (enabled.isEnabled) TextTone.Primary else TextTone.Tertiary,
                maxLines = 1,
            )
            KitText(
                caption,
                role = TypeRole.Secondary,
                maxLines = 3,
            ) // F: lib/ui/kit/kit_option_card.dart:263
            enabled.reason?.let {
                KitText(it, role = TypeRole.Secondary, maxLines = 3)
            } // F: lib/ui/kit/kit_option_card.dart:266
        }
    }
}

@Composable
private fun OptionCheck(modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Box(
        modifier.size(Space.S5.dp).background(colors.accent, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        KitGlyph(
            KitIcons.check,
            colors.onAccent,
            (Space.S3 + Space.S1 / 2f).dp, // F: lib/ui/kit/kit_option_card.dart:381
        )
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
            val card =
                spec.resolve(
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
                )
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
                        // Accent at .7, unit * 8 across, top -unit, end unit * 2 (kit_option_card.dart:697-701).
                        drawCircle(accent.copy(alpha = .7f), radius = unit * 4f, center = androidx.compose.ui.geometry.Offset(x(size.width - unit * 6f), unit * 3f))
                        // Partner at .5, unit * 6 across, bottom -unit * 2, start unit * 3 (kit_option_card.dart:702-706).
                        drawCircle(partner.copy(alpha = .5f), radius = unit * 3f, center = androidx.compose.ui.geometry.Offset(x(unit * 6f), size.height - unit))
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
                                    .border(kitHairline(), colors.hairline, cardShape)
                            else if (glass)
                                // A light frost of its own, sigma 3 (kit_option_card.dart:669).
                                Modifier.kitSurfaceGlass(card.copy(frost = 3f)).kitGlassFace(card)
                            else Modifier.kitDecorated(card)
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
                        Box(
                            Modifier.width(
                                    (Space.S1 * 7).dp // F: lib/ui/kit/kit_option_card.dart:660
                                ) // F: lib/ui/kit/kit_option_card.dart:660
                                .height(
                                    (Space.S1 * 3.5f).dp // F: lib/ui/kit/kit_option_card.dart:653
                                ) // F: lib/ui/kit/kit_option_card.dart:653
                                .then(control.modifier)
                                .border(kitHairline(), colors.hairline, pill)
                        )
                        Spacer(Modifier.width(Space.S1.dp))
                        Box(
                            Modifier.width(
                                    (Space.S1 * 6).dp // F: lib/ui/kit/kit_option_card.dart:663
                                ) // F: lib/ui/kit/kit_option_card.dart:663
                                .height(
                                    (Space.S1 * 3.5f).dp // F: lib/ui/kit/kit_option_card.dart:653
                                ) // F: lib/ui/kit/kit_option_card.dart:653
                                .background(colors.accent, pill)
                        )
                    }
                }
              }
            }
        }
    }
}
