package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.foundation.shape.CircleShape
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidFace
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.Shadow
import io.github.eslamasabry.oc.ui.design.style.SignalPalette
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitBottomInset
import io.github.eslamasabry.oc.ui.kit.foundation.kitDecorated
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The floating action's round face, in dp (Dart `KitFloatingAction.size`). */
public const val KIT_FAB_SIZE: Int = 56 // F: lib/ui/kit/kit_floating_action.dart:54

/** Room a list leaves under its last row so the floating action never hides it (Dart `KitFloatingAction.clearance`). */
public const val KIT_FAB_CLEARANCE: Int = 80 // F: lib/ui/kit/kit_floating_action.dart:51

/**
 * The screen's one floating action (Dart `KitFloatingAction`): a round 56 dp button in the accent with one glyph (a
 * plus for "New conversation"). Its [label] is not drawn: a screen reader says it. The face comes from the style
 * resolver as a floating action piece: Solid keeps its plain round disc in the accent (no shadow), another style adds
 * its own edge and depth and, held, sinks into its shadow. Under Signal (a style with a track) it is a dark ink key
 * that draws its words. Its press, hover and focus looks are the tappable's own. When the action cannot run yet the
 * host passes [visible] false and it fades out, and back in (STANDARD on the Enter curve both ways, instant under
 * reduced motion). The host places it on the gutter at the bottom end ([KitFloatingAction] does that), and the list
 * under it ends with [KIT_FAB_CLEARANCE] more room so its last row scrolls clear.
 *
 * States: default, pressed, hidden
 * Text: none (the label is heard); Signal draws it as chrome, one line
 * Cap: 56 dp (Signal: the window less two gutters)
 * Motion: fade in/out; Off = instant
 * Replaces: KitFloatingAction (lib/ui/kit/kit_floating_action.dart)
 */
@Composable
public fun KitFab(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    /**
     * What scrolls behind the button (the list it floats over, recorded by
     * [io.github.eslamasabry.oc.ui.kit.effects.kitGlassSource]). Given, Glass draws the button as a drop of accent
     * liquid glass over it (Android 13+, where not held); every other style ignores it.
     */
    backdrop: KitFrostState? = null,
) {
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    // Only a change of whether it can run fades, in or out, over STANDARD on Enter (kit_floating_action.dart:189-192).
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn(specs.enter()),
        exit = if (reduced) ExitTransition.None else fadeOut(specs.enter()),
    ) {
        if (LocalKitStyle.current.spec.tokens.track) SignalKey(label, icon, onClick) else RoundFace(label, icon, onClick, backdrop)
    }
}

/** The round face (kit_floating_action.dart:136-176): a stadium in the accent, from the style resolver. */
@Composable
private fun RoundFace(label: String, icon: ImageVector, onClick: () -> Unit, backdrop: KitFrostState? = null) {
    val colors = LocalKitColors.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Floating,
                shape = KitShapeId.Circle,
                ownFill = colors.accent,
                color = colors.accent,
                pressed = pressed,
                action = true,
            )
        )
    val words = look.words ?: colors.onAccent // F: lib/ui/kit/kit_floating_action.dart:168-173
    // Glass: accent liquid glass over what the button floats over; null keeps the style's own face.
    val liquid = kitLiquidFace(LiquidRole.Action, backdrop, CircleShape)
    Box(
        Modifier
            .size(KIT_FAB_SIZE.dp)
            .kitTappable(onClick = onClick, shape = KitShapeId.Circle, ink = TextTone.OnAccent, interactionSource = interactions)
            .then(liquid ?: look.modifier)
            .semantics { contentDescription = label }
            .testTag("kit-fab"),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalKitColors provides colors.copy(onAccent = words)) {
            KitIcon(icon, size = KitIconSize.Fab, tone = TextTone.OnAccent, growsWithText = true)
        }
    }
}

/**
 * Signal (concept CD, kit_floating_action.dart:56-125): a dark ink key with its words, "+ New conversation", raised on
 * its lip, at Signal's sharp radius; at most the window less two gutters wide.
 */
@Composable
private fun SignalKey(label: String, icon: ImageVector, onClick: () -> Unit) {
    val tokens = LocalKitTokens.current
    val palette = SignalPalette.forRoles(LocalKitRoles.current)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val radius = LocalKitStyle.current.spec.tokens.sharpRadius
    val edge = palette.inkKeyEdge
    // kitSignalKey: the fill, a 1 dp edge, the lip 3 dp below (1 dp while held), no blur (lib/ui/kit/style/kit_signal.dart:94-102).
    val key =
        SurfaceDecoration(
            radii = Radii.all(radius),
            fill = palette.inkKey,
            edgeStart = edge,
            edgeEnd = edge,
            edgeWidthDp = 1f,
            shadows = listOf(Shadow(0f, if (pressed) 1f else 3f, 0f, edge)), // F: lib/ui/kit/style/kit_signal.dart:101
        )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val wordsColor = Color(palette.inkKeyWords.v)
    BoxWithConstraints {
        val cap: Dp = maxWidth - (tokens.gutter * 2).dp // F: lib/ui/kit/kit_floating_action.dart:75
        Row(
            Modifier
                .widthIn(max = cap)
                .heightIn(min = KIT_FAB_SIZE.dp)
                .kitTappable(onClick = onClick, shape = KitShapeId.Button, interactionSource = interactions)
                .kitDecorated(key, rtl)
                .semantics { contentDescription = label }
                .testTag("kit-fab")
                .padding(start = Space.S4.dp, end = Space.S5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalKitColors provides LocalKitColors.current.copy(text1 = wordsColor)) {
                KitIcon(icon, size = KitIconSize.Medium, tone = TextTone.Primary, growsWithText = true)
                Spacer(Modifier.width(Space.S2.dp))
                Box(Modifier.weight(1f, fill = false).clearAndSetSemantics {}) {
                    KitText(label, role = TypeRole.Headline, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A screen's one floating primary laid over the bottom end of [content] (Dart `KitFloatingAction`): the [KitFab] on
 * the gutter, space 3 above whatever is published below ([kitBottomInset]: the dock, the keyboard, a pinned block).
 * With [onClick] null the button fades out while [content] stays the same, so the list under it never moves.
 *
 * States: default, disabled (not floated)
 * Text: none (the label is heard)
 * Cap: 56 dp button over a wrapper
 * Motion: fade in/out; Off = instant
 * Replaces: KitFloatingAction (lib/ui/kit/kit_floating_action.dart)
 */
@Composable
public fun KitFloatingAction(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = KitIcons.add,
    content: @Composable () -> Unit,
) {
    val tokens = LocalKitTokens.current
    val bottom = Space.S3.dp + kitBottomInset().bottom // F: lib/ui/kit/kit_floating_action.dart:185
    Box(modifier) {
        content()
        KitFab(
            label = label,
            icon = icon,
            onClick = onClick ?: {},
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = tokens.gutter.dp, bottom = bottom),
            visible = onClick != null,
        )
    }
}
