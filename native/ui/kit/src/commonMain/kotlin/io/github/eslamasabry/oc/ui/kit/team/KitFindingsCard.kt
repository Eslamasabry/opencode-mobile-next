package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Alphas
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The rules of [KitFindingsCard] that are not drawing. */
public object KitFindingsRules {
    /** Critical and major findings are worded as needing attention; minor ones stay neutral. */
    public fun chipTone(severity: KitFindingSeverity): KitChipTone =
        when (severity) {
            KitFindingSeverity.Critical, KitFindingSeverity.Major -> KitChipTone.Attention
            KitFindingSeverity.Minor -> KitChipTone.Neutral
        }

    /** A finding can be picked only while the data is current and the host listens. */
    public fun canToggle(state: KitTeamState, finding: KitTeamFinding): Boolean =
        state != KitTeamState.Stale && state != KitTeamState.Loading && finding.onChanged != null
}

/**
 * A controlled selection of findings ranked by explicit, worded severity (Dart `KitFindingsCard`). Each finding is a
 * check box, its severity chip, its title, and an optional location and detail.
 *
 * States: loading, empty, error, disabled (stale), working, answered, needs you, stalled
 * Text: reading
 * Cap: Reading (720)
 * Motion: press feedback only (KitTappable)
 * Replaces: KitFindingsCard (lib/ui/kit/team/kit_findings_card.dart)
 */
@Composable
public fun KitFindingsCard(
    title: String,
    status: String,
    findings: List<KitTeamFinding>,
    modifier: Modifier = Modifier,
    state: KitTeamState = KitTeamState.Done,
    summary: String? = null,
    primary: KitAction? = null,
    actions: List<KitAction> = emptyList(),
) {
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        TeamCard(title, status, state, summary, primary = primary, actions = actions) {
            findings.forEach { finding ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp), verticalAlignment = Alignment.Top) {
                    FindingCheck(finding, KitFindingsRules.canToggle(state, finding))
                    Column(Modifier.weight(1f)) {
                        KitChip(finding.severityLabel, tone = KitFindingsRules.chipTone(finding.severity))
                        KitText(finding.title, role = TypeRole.RowTitle)
                        finding.location?.let { KitMonoText(it) }
                        finding.detail?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
                    }
                }
            }
        }
    }
}

/**
 * The finding's check box in its 48 dp target, drawn as the Flutter Material check box the Dart part uses: an 18 dp
 * square with a 2 dp edge, filled `text1` with a `surface1` tick when picked; disabled at the disabled alpha.
 */
@Composable
private fun FindingCheck(finding: KitTeamFinding, enabled: Boolean) {
    val colors = LocalKitColors.current
    val target = LocalKitTokens.current.minTarget.dp
    val spoken = finding.severityLabel + ": " + finding.title
    val ink = if (enabled) colors.text1 else colors.text1.copy(alpha = Alphas.DISABLED)
    val fill = ink.compositeOver(colors.surface1)
    val tick = colors.surface1
    val box =
        Modifier.size(target).semantics {
            contentDescription = spoken
            role = Role.Checkbox
            toggleableState = ToggleableState(finding.selected)
        }
    val face: @Composable () -> Unit = {
        Canvas(Modifier.size(target)) {
            val edge = CHECK_EDGE.dp.toPx()
            val stroke = CHECK_STROKE.dp.toPx()
            val radius = CornerRadius(CHECK_RADIUS.dp.toPx())
            val origin = Offset((size.width - edge) / 2f, (size.height - edge) / 2f) // NV: halves, to centre the box in its target
            if (finding.selected) {
                drawRoundRect(fill, origin, Size(edge, edge), radius)
                val path =
                    Path().apply {
                        moveTo(origin.x + edge * TICK_START_X, origin.y + edge * TICK_START_Y)
                        lineTo(origin.x + edge * TICK_MID_X, origin.y + edge * TICK_MID_Y)
                        lineTo(origin.x + edge * TICK_END_X, origin.y + edge * TICK_END_Y)
                    }
                drawPath(path, tick, style = Stroke(stroke))
            } else {
                val half = stroke / 2f // NV: half the stroke, so the edge is drawn inside the box
                drawRoundRect(
                    ink,
                    Offset(origin.x + half, origin.y + half),
                    Size(edge - stroke, edge - stroke),
                    CornerRadius(radius.x - half),
                    style = Stroke(stroke),
                )
            }
        }
    }
    val change = finding.onChanged
    if (enabled && change != null) {
        KitTappable({ change(!finding.selected) }, box, shape = KitShapeId.Circle) { face() }
    } else {
        Box(box, contentAlignment = Alignment.Center) { face() }
    }
}

// Flutter's Material check box painter (flutter/lib/src/material/checkbox.dart, framework, not the kit): 18 dp edge,
// 2 dp stroke and corner, the tick through three fractions of the edge. Not kit tokens, so they cannot cite a kit line.
private const val CHECK_EDGE = 18f // NV: Material Checkbox _kEdgeSize (framework painter, no kit line)
private const val CHECK_STROKE = 2f // NV: Material Checkbox _kStrokeWidth (framework painter, no kit line)
private const val CHECK_RADIUS = 2f // NV: Material 3 Checkbox corner (framework painter, no kit line)
private const val TICK_START_X = .15f // NV: Material Checkbox _drawCheck start x fraction
private const val TICK_START_Y = .45f // NV: Material Checkbox _drawCheck start y fraction
private const val TICK_MID_X = .4f // NV: Material Checkbox _drawCheck mid x fraction
private const val TICK_MID_Y = .7f // NV: Material Checkbox _drawCheck mid y fraction
private const val TICK_END_X = .85f // NV: Material Checkbox _drawCheck end x fraction
private const val TICK_END_Y = .25f // NV: Material Checkbox _drawCheck end y fraction
