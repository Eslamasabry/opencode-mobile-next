package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.kitSelectTap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRiskNotNow
import io.github.eslamasabry.oc.ui.words.res.kitRiskTurnOff
import io.github.eslamasabry.oc.ui.words.res.kitRiskTurnOn
import io.github.eslamasabry.oc.ui.words.res.kitUntilConversation
import io.github.eslamasabry.oc.ui.words.res.kitUntilHour
import io.github.eslamasabry.oc.ui.words.res.kitUntilOff
import org.jetbrains.compose.resources.stringResource

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

    /**
     * The row's supporting line (Dart `_supporting`, kit_row_parts.dart:397-413): a disabled row says why; a risky
     * switch that is on leads with its scope and the duration in force (the only one offered when there is one), then
     * the row's own line, joined by " · "; otherwise the row's own line.
     */
    public fun supporting(
        supporting: String?,
        enabled: Boolean,
        disabledReason: String?,
        checked: Boolean,
        riskScope: String?,
        current: KitUntil?,
        until: List<KitUntil>,
        untilLabel: (KitUntil) -> String,
    ): String? {
        if (!enabled && disabledReason != null) return disabledReason
        if (riskScope != null && checked) {
            val inForce = current ?: until.singleOrNull()
            return listOfNotNull(riskScope, inForce?.let(untilLabel), supporting).joinToString(" · ")
        }
        return supporting
    }

    /**
     * The step is folded again when the switch was turned on elsewhere or is no longer risky (Dart `didUpdateWidget`,
     * kit_row_parts.dart:353-355).
     */
    public fun keepStep(step: Boolean, checked: Boolean, risky: Boolean): Boolean = step && !checked && risky
}

/** How long a risky switch stays on (Dart `KitUntil`, kit-v2.md §2.6), in Flutter's order. */
public enum class KitUntil {
    /** "Until I turn it off". */
    Off,

    /** "For this conversation". */
    Conversation,

    /** "For an hour". */
    Hour,
}

/** The words for [until] (Dart `KitUntil.label`). */
@Composable
public fun kitUntilLabel(until: KitUntil): String =
    when (until) {
        KitUntil.Off -> stringResource(Res.string.kitUntilOff)
        KitUntil.Conversation -> stringResource(Res.string.kitUntilConversation)
        KitUntil.Hour -> stringResource(Res.string.kitUntilHour)
    }

/**
 * A risky switch's scope (Dart `KitRisk`; KIT-30, SEC-9): [scope] is what turning it on covers, [onLabel] the status
 * line's words while on, [icon] its glyph there, [until] the durations offered (1 to 3), [current] the one in force,
 * and [onUntil] hears the chosen duration before the switch turns on.
 */
@Immutable
public data class KitRisk(
    val scope: String,
    val onLabel: String,
    val icon: ImageVector? = null,
    val until: List<KitUntil> = listOf(KitUntil.Off),
    val current: KitUntil? = null,
    val onUntil: (KitUntil) -> Unit = {},
) {
    init {
        require(until.size in 1..3) { "KitRisk offers 1 to 3 durations" } // F: lib/ui/kit/kit_row_parts.dart:239
    }
}

/**
 * A row with a switch (Dart `KitSwitchRow` + `KitRisk`, kit_row_parts.dart:279-589): the title (two lines allowed),
 * what it does in the supporting line (two lines allowed) and the switch at the end; the whole row toggles
 * (`Role.Switch`). A flip plays `ToggleOn`/`ToggleOff` through the kit seam. A disabled row says why on its supporting
 * line; nothing is dimmed. With [riskSpec] (or the older [risk] scope alone), turning on first unfolds a step under the
 * row that states the scope and asks for how long ("Until I turn it off", …, "Not now"); while on, the row puts
 * [KitRisk.onLabel] on the screen's one status line with "Turn off". [locked] with [lockedLabel] ("Always included")
 * draws the lock and the word instead of a switch.
 *
 * States: off, on, disabled with its reason, risk step unfolded, risk on, locked
 * Text: reading (title), frame (risk actions)
 * Cap: List
 * Motion: thumb slides and track cross-fades QUICK; the risk step reveals; Off = instant
 * Replaces: KitSwitchRow, KitRisk, KitUntil (lib/ui/kit/kit_row_parts.dart)
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
    leading: KitRowLeading? = null,
    lockedLabel: String? = null,
    below: (@Composable () -> Unit)? = null,
    supportingMaxLines: Int = 2, // F: lib/ui/kit/kit_row_parts.dart:287
    riskSpec: KitRisk? = null,
    switchTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalKitTokens.current
    if (locked && lockedLabel != null) {
        Column(modifier.fillMaxWidth().kitMaxWidth(CapRole.List)) {
            KitRow(
                title = title,
                modifier = Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState.On; disabled() }.testTag("kit-switch-locked"),
                supporting = supporting,
                supportingMaxLines = supportingMaxLines,
                leading = leading,
                titleMaxLines = 2, // F: lib/ui/kit/kit_row_parts.dart:425
                trailing = KitRowTrailing.Locked(lockedLabel),
            )
            if (below != null) Box(Modifier.padding(horizontal = kitRowGutter())) { below() }
        }
        return
    }
    val specs = KitMotion.specs
    val platform = LocalKitPlatform.current
    val hapticsOn = LocalKitAppearance.current.haptics
    val scope = riskSpec?.scope ?: risk
    val until = riskSpec?.until ?: listOf(KitUntil.Off)
    val on = enabled.isEnabled
    var asking by rememberSaveable { mutableStateOf(false) }
    val step = SwitchRules.keepStep(asking, checked, scope != null)
    if (asking && !step) SideEffect { asking = false }
    val untilLabels = KitUntil.entries.associateWith { kitUntilLabel(it) }
    val line = SwitchRules.supporting(supporting, on, enabled.reason, checked, scope, riskSpec?.current, until) { untilLabels.getValue(it) }
    val choose: (KitUntil) -> Unit = { picked ->
        riskSpec?.onUntil?.invoke(picked)
        if (on) onCheckedChange(true)
        asking = false
    }
    Column(modifier.fillMaxWidth().kitMaxWidth(CapRole.List)) {
        Row(
            Modifier.fillMaxWidth()
                // 56 dp with one line, 64 with two (kit_row.dart:497-507); the 48 dp switch reaches into the words' room.
                .heightIn(min = (if (line != null || below != null) tokens.rowHeightTwo else tokens.rowHeightOne).dp)
                .kitEdge(KitEdge.Bleed) // a row's highlight runs edge to edge (native/docs/kit/LAYOUT-RULES.md §2)
                .kitSelectTap(selected = checked, role = Role.Switch, enabled = enabled, haptic = null) {
                    when (SwitchRules.onTap(checked, on, false, scope != null)) {
                        SwitchTap.Flip -> {
                            asking = false
                            if (hapticsOn) platform.haptic(SwitchRules.haptic(!checked))
                            onCheckedChange(!checked)
                        }
                        SwitchTap.AskRisk -> asking = !asking
                        SwitchTap.Refuse, SwitchTap.Ignore -> Unit
                    }
                }
                // With a trailing part the row ends space1 in; the part keeps space2 itself (kit_row.dart:507-512).
                .padding(start = kitRowGutter(), end = Space.S1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                Box(Modifier.padding(vertical = Space.S2.dp)) { RowLeadingTile(it, dim = false) }
                Spacer(Modifier.width(Space.S3.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = Space.S2.dp)) {
                // Nothing is dimmed: a disabled row's reason is its supporting line (kit_row_parts.dart:270-271).
                KitText(title, Modifier.testTag("kit-row-title"), role = TypeRole.RowTitle, maxLines = kitRowTitleLines(kitTextScale(), 2)) // F: lib/ui/kit/kit_row_parts.dart:544
                line?.let {
                    Spacer(Modifier.size(FlutterKitTokens.ROW_LINE_GAP.dp))
                    KitText(
                        it,
                        Modifier.testTag("kit-row-supporting"),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        maxLines = kitRowSupportingLines(kitTextScale(), supportingMaxLines),
                    )
                }
                below?.let {
                    Spacer(Modifier.size(FlutterKitTokens.ROW_LINE_GAP.dp))
                    it()
                }
            }
            Spacer(Modifier.width(Space.S2.dp))
            Box(Modifier.padding(end = Space.S2.dp).then(switchTag?.let { Modifier.testTag(it) } ?: Modifier)) {
                KitSwitchBox(checked, on)
            }
        }
        if (scope != null) {
            val reduced = specs.state.reduced
            AnimatedVisibility(
                visible = step,
                enter = if (reduced) EnterTransition.None else expandVertically(specs.enter()) + fadeIn(specs.enter()),
                exit = if (reduced) ExitTransition.None else shrinkVertically(specs.exit()) + fadeOut(specs.exit()),
            ) {
                RiskStep(scope, until, untilLabels, choose) { asking = false }
            }
        }
    }
    if (riskSpec != null) {
        val turnOff = stringResource(Res.string.kitRiskTurnOff)
        KitStatusContribution(
            if (checked) {
                KitStatus(
                    kind = KitStatusKind.RiskySwitch,
                    message = riskSpec.onLabel,
                    id = "risk:${switchTag ?: title}",
                    icon = riskSpec.icon,
                    action = KitLineAction(turnOff, onClick = { onCheckedChange(false) }, enabled = on, disabledReason = enabled.reason),
                )
            } else null,
        )
    }
}

/** The risk step under the row (Dart `_riskStep`, kit_row_parts.dart:455-495). */
@Composable
private fun RiskStep(
    scope: String,
    until: List<KitUntil>,
    labels: Map<KitUntil, String>,
    choose: (KitUntil) -> Unit,
    fold: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("kit-switch-risk-step")
            .padding(start = kitRowGutter(), end = kitRowGutter(), bottom = Space.S3.dp),
    ) {
        KitText(scope, role = TypeRole.Secondary, tone = TextTone.Secondary)
        Spacer(Modifier.size(Space.S3.dp))
        if (until.size == 1) {
            KitButton(stringResource(Res.string.kitRiskTurnOn), { choose(until.single()) }, role = KitButtonRole.Secondary, fullWidth = true, haptic = KitHaptic.ToggleOn)
        } else {
            until.forEachIndexed { i, u ->
                if (i > 0) Spacer(Modifier.size(Space.S2.dp))
                KitButton(labels.getValue(u), { choose(u) }, role = KitButtonRole.Secondary, fullWidth = true, haptic = KitHaptic.ToggleOn)
            }
        }
        Spacer(Modifier.size(Space.S2.dp))
        KitButton(stringResource(Res.string.kitRiskNotNow), fold, role = KitButtonRole.Tertiary, fullWidth = true)
    }
}

/**
 * The colours of a switch (Dart `_kitSwitchLooks`, kit_row_parts.dart:655-708, and the platform switch's theme,
 * app_theme.dart:457-478): [track] fill, [thumb] fill, and the 2 dp [outline] an off track keeps.
 */
@Immutable
public data class KitSwitchColors(val track: Color, val thumb: Color, val outline: Color)

/** [KitSwitchColors] for a switch that is [on], [enabled] or not, [quiet] (a row that opens on a tap) or not. */
public fun kitSwitchColors(colors: KitColors, on: Boolean, enabled: Boolean, quiet: Boolean = false): KitSwitchColors {
    val onFill = when {
        !enabled -> colors.text3.copy(alpha = .35f) // F: lib/ui/kit/kit_row_parts.dart:663
        quiet -> colors.accent.copy(alpha = .24f).compositeOver(colors.surface1) // F: lib/ui/kit/kit_row_parts.dart:665
        else -> colors.accent
    }
    val thumb = when {
        !enabled -> if (on) colors.text2 else colors.text3
        on -> if (quiet) colors.accent else colors.onAccent
        else -> colors.text2
    }
    val outline = when {
        !on -> colors.hairline
        quiet -> colors.accent.copy(alpha = .5f) // F: lib/ui/kit/kit_row_parts.dart:674
        else -> Color.Transparent
    }
    return KitSwitchColors(if (on) onFill else colors.surface3, thumb, outline)
}

/** The switch's box, track and thumb sizes in dp (Dart `_KitStyledSwitch`, kit_row_parts.dart:710-728). */
public object KitSwitchGeometry {
    public const val BOX_W: Int = 60 // F: lib/ui/kit/kit_row_parts.dart:726
    public const val BOX_H: Int = 48 // F: lib/ui/kit/kit_row_parts.dart:726
    public const val TRACK_W: Int = 52 // F: lib/ui/kit/kit_row_parts.dart:727
    public const val TRACK_H: Int = 32 // F: lib/ui/kit/kit_row_parts.dart:727
    public const val THUMB: Int = 24 // F: lib/ui/kit/kit_row_parts.dart:728
    public const val OUTLINE: Int = 2 // F: lib/ui/kit/kit_row_parts.dart:681

    /**
     * The thumb's diameter. The style-drawn switch keeps 24 dp; Solid keeps the platform switch, whose thumb is half
     * the track's height off and 24 dp on.
     */
    public fun thumb(on: Boolean, own: Boolean): Int = if (own && !on) TRACK_H / 2 else THUMB // F: lib/ui/kit/kit_row_parts.dart:756

    /** The thumb centre's distance from the track's start: half the track's height in from either end. */
    public fun thumbCentre(on: Boolean): Int = if (on) TRACK_W - TRACK_H / 2 else TRACK_H / 2 // F: lib/ui/kit/kit_row_parts.dart:756
}

/** The switch at the end of a row, in its 60 × 48 box. Decorative: the row carries the semantics. */
@Composable
internal fun KitSwitchBox(checked: Boolean, active: Boolean, quiet: Boolean = false) {
    Box(Modifier.size(KitSwitchGeometry.BOX_W.dp, KitSwitchGeometry.BOX_H.dp), contentAlignment = Alignment.Center) {
        KitSwitchTrack(checked, active, quiet)
    }
}

/**
 * The switch's track and thumb (Dart `_KitSwitch`). Where the style answers with the part's own look (Solid) it is the
 * platform switch, as Flutter keeps `Switch.adaptive`: a 2 dp hairline round an off track and a thumb that grows from
 * 16 to 24 dp as it slides. Any other style draws the track as a `Toggle` surface and a 24 dp `Control` thumb.
 * Decorative: the row carries the semantics.
 */
@Composable
internal fun KitSwitchTrack(checked: Boolean, active: Boolean, quiet: Boolean = false) {
    val roles = LocalKitColors.current
    val specs = KitMotion.specs
    val own = LocalKitStyle.current.style == Style.Solid // Solid answers the switch with its own look (kit_row_parts.dart:591-594)
    val c = kitSwitchColors(roles, checked, active, quiet)
    val track by animateColorAsState(c.track, specs.quick(), label = "switch-track")
    val thumbColor by animateColorAsState(c.thumb, specs.quick(), label = "switch-thumb-fill")
    val outline by animateColorAsState(c.outline, specs.quick(), label = "switch-outline")
    val centre by animateDpAsState(KitSwitchGeometry.thumbCentre(checked).dp, specs.quick(), label = "switch-thumb")
    val diameter by animateDpAsState(KitSwitchGeometry.thumb(checked, own).dp, specs.quick(), label = "switch-thumb-size")
    val trackSize = Modifier.size(KitSwitchGeometry.TRACK_W.dp, KitSwitchGeometry.TRACK_H.dp).clearAndSetSemantics {}
    if (own) {
        Box(
            trackSize.drawBehind {
                val r = CornerRadius(size.height / 2) // F: lib/ui/kit/kit_row_parts.dart:756
                drawRoundRect(track, cornerRadius = r)
                val w = KitSwitchGeometry.OUTLINE.dp.toPx()
                if (outline.alpha > 0f) {
                    drawRoundRect(
                        outline,
                        topLeft = Offset(w / 2, w / 2), // F: lib/ui/kit/kit_row_parts.dart:756
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius((size.height - w) / 2), // F: lib/ui/kit/kit_row_parts.dart:756
                        style = Stroke(w),
                    )
                }
                val cx = centre.toPx().let { if (layoutDirection == LayoutDirection.Rtl) size.width - it else it }
                drawCircle(thumbColor, radius = diameter.toPx() / 2, center = Offset(cx, size.height / 2)) // F: lib/ui/kit/kit_row_parts.dart:756
            },
        )
        return
    }
    val trackLook = kitSurfaceLook(
        KitSurfaceInput(SurfaceRole.Toggle, KitShapeId.Pill, SurfaceLevel.Surface3, ownFill = track, color = if (checked) track else null, selected = checked),
    )
    val thumbLook = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Circle, SurfaceLevel.Surface2, ownFill = thumbColor, color = thumbColor))
    val inset: Dp = ((KitSwitchGeometry.TRACK_H - KitSwitchGeometry.THUMB) / 2).dp // F: lib/ui/kit/kit_row_parts.dart:756
    Box(trackSize.then(trackLook.modifier)) {
        Box(
            Modifier.offset(x = centre - diameter / 2, y = inset) // F: lib/ui/kit/kit_row_parts.dart:756
                .size(diameter)
                .then(thumbLook.modifier),
        )
    }
}
