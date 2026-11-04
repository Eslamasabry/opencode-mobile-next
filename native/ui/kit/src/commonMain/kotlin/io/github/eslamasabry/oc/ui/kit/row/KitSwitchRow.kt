package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import io.github.eslamasabry.oc.ui.design.tokens.Alphas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.effects.Corners
import io.github.eslamasabry.oc.ui.design.color.Argb
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.kitSelectTap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRiskNotNow
import io.github.eslamasabry.oc.ui.words.res.kitRiskTurnOn
import org.jetbrains.compose.resources.stringResource

private const val TRACK_W = 52
private const val TRACK_H = 32
private const val THUMB = 24

/** What a tap on a switch row does (pure; B-KIT-027). */
public enum class SwitchTap { Flip, AskRisk, Refuse, Ignore }

/** Pure rules of [KitSwitchRow] (Dart `KitSwitchRow`, `KitRisk`). */
public object SwitchRules {
    /**
     * A locked row ("Always included") refuses false; a risky step asks first when turning on; a disabled row ignores
     * taps; everything else flips.
     */
    public fun onTap(checked: Boolean, enabled: Boolean, locked: Boolean, risky: Boolean): SwitchTap =
        when {
            !enabled -> SwitchTap.Ignore
            locked && checked -> SwitchTap.Refuse
            risky && !checked -> SwitchTap.AskRisk
            else -> SwitchTap.Flip
        }

    /** The haptic a flip plays (plan 06 §4.9: `ToggleOn` / `ToggleOff`). */
    public fun haptic(newValue: Boolean): KitHaptic = if (newValue) KitHaptic.ToggleOn else KitHaptic.ToggleOff
}

/**
 * A row with a switch (Dart `KitSwitchRow` + `KitRisk`; plan 06 §7.5): the whole row is the toggle (`Role.Switch`), the
 * kit-drawn track sits at the end. A flip plays `ToggleOn`/`ToggleOff` through the kit seam. With [risk], turning on
 * first unfolds the risk line inline with "Turn on" / "Not now" (turning off never asks). [locked] ("Always included")
 * keeps the switch on and refuses false. A disabled row says why on its supporting line.
 *
 * States: off, on, disabled, locked, asking (risk)
 * Text: reading (title), frame (risk actions)
 * Cap: List
 * Motion: thumb slides and track cross-fades QUICK; the risk fold reveals; Off = instant
 * Replaces: KitSwitchRow, KitRisk (lib/ui/kit/kit_row_parts.dart)
 */
@Composable
public fun KitSwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    risk: String? = null,
    locked: Boolean = false,
    enabled: KitEnabled = KitEnabled.Enabled,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val platform = LocalKitPlatform.current
    val hapticsOn = LocalKitAppearance.current.haptics
    var asking by rememberSaveable { mutableStateOf(false) }
    val on = enabled.isEnabled
    val canConfirm = on && !locked && !checked && risk != null && asking
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = tokens.rowHeightOne.dp)
                .kitSelectTap(
                    selected = checked,
                    role = Role.Switch,
                    enabled = enabled,
                    haptic = null,
                ) {
                    when (SwitchRules.onTap(checked, on, locked, risk != null)) {
                        SwitchTap.Flip -> {
                            asking = false
                            if (hapticsOn) platform.haptic(SwitchRules.haptic(!checked))
                            onCheckedChange(!checked)
                        }
                        SwitchTap.AskRisk -> asking = !asking
                        SwitchTap.Refuse, SwitchTap.Ignore -> Unit
                    }
                }
                .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Column(Modifier.weight(1f)) {
                KitText(title, role = TypeRole.RowTitle, tone = if (on) TextTone.Primary else TextTone.Tertiary)
                (enabled.reason ?: supporting)?.let {
                    KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 3)
                }
            }
            KitSwitchTrack(checked, on && !locked)
        }
        AnimatedVisibility(
            visible = asking && risk != null,
            enter = expandVertically(specs.enter()) + fadeIn(specs.enter()),
            exit = shrinkVertically(specs.exit()) + fadeOut(specs.exit()),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitText(risk.orEmpty(), role = TypeRole.Secondary, tone = TextTone.Primary)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitButton(
                        label = stringResource(Res.string.kitRiskTurnOn),
                        onClick = {
                            if (canConfirm) {
                                asking = false
                                onCheckedChange(true)
                            }
                        },
                        role = KitButtonRole.Secondary,
                        enabled = if (canConfirm) KitEnabled.Enabled else KitEnabled.Disabled(enabled.reason ?: title),
                        haptic = KitHaptic.ToggleOn,
                    )
                    KitButton(label = stringResource(Res.string.kitRiskNotNow), onClick = { asking = false }, role = KitButtonRole.Tertiary)
                }
            }
        }
    }
}

/** The kit-drawn switch (styles will hook it, S4). Decorative: the row carries the semantics. */
@Composable
internal fun KitSwitchTrack(checked: Boolean, active: Boolean) {
    val colors = LocalKitColors.current
    val specs = KitMotion.specs
    val track by animateColorAsState(
        when {
            checked && active -> colors.accent
            checked -> colors.accent.copy(alpha = Alphas.DISABLED).compositeOver(colors.surface3) // on, but fixed
            // B-KIT-004: an off switch that cannot be used reads unavailable too, not just an on one.
            !active -> colors.surface3.copy(alpha = Alphas.DISABLED).compositeOver(colors.ground)
            else -> colors.surface3
        },
        specs.quick(),
        label = "switch-track",
    )
    val thumbColor = if (checked) colors.onAccent else if (active) colors.text2 else colors.text3
    val x by animateDpAsState(if (checked) (TRACK_W - THUMB - 4).dp else 4.dp, specs.quick(), label = "switch-thumb")
    val style = LocalKitStyle.current
    val square = style.corners == Corners.Sharp && (style.style == Style.Paper || style.style == Style.Signal)
    val trackShape = (if (square) KitShapeId.Button else KitShapeId.Pill).toShape()
    val thumbShape = (if (square) KitShapeId.Button else KitShapeId.Circle).toShape()
    val look = kitSurfaceLook(SurfaceRequest(SurfaceRole.Toggle, fill = if (checked) Argb(track.toArgb()) else null, selected = checked, disabled = !active), track)
    Box(Modifier.size(TRACK_W.dp, TRACK_H.dp).kitSurface(look, trackShape)) {
        val thumb = kitSurfaceLook(SurfaceRequest(SurfaceRole.Control, fill = Argb(thumbColor.toArgb()), circle = !square), thumbColor)
        Box(Modifier.offset(x = x, y = ((TRACK_H - THUMB) / 2).dp).size(THUMB.dp).kitSurface(thumb, thumbShape))
    }
}
