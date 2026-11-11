package io.github.eslamasabry.oc.feature.team.fleet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.eslamasabry.oc.engine.team.fleet.FleetPolicy
import io.github.eslamasabry.oc.engine.team.fleet.FleetSupervision
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The words for a supervision level the host reports; the same as the Start-a-run choices (Dart `teamPolicySupervisionName`). */
@Composable
public fun fleetSupervisionName(level: FleetSupervision): String =
    stringResource(
        when (level) {
            FleetSupervision.High -> Res.string.teamUiStartRunSupervisionHigh
            FleetSupervision.Balanced -> Res.string.teamUiStartRunSupervisionBalanced
            FleetSupervision.Autonomous -> Res.string.teamUiStartRunSupervisionAutonomous
        },
    )

/**
 * The host's supervision policy (Dart `TeamPolicyBlock`, TEAM-207): "Supervision · Balanced" with the rig it describes,
 * the host's boundaries each on a row in the host's words, and the line that says they are set on the host. Nothing here
 * is editable: the level and the boundaries are the host owner's. Shown only when the host gave a policy (the caller
 * omits it otherwise), so no empty block appears.
 */
@Composable
public fun FleetPolicyBlock(policy: FleetPolicy, modifier: Modifier = Modifier) {
    val level = fleetSupervisionName(policy.supervision)
    val none = stringResource(Res.string.teamUiPolicyBoundariesNone)
    val spoken = stringResource(Res.string.teamUiPolicySemantics, level, if (policy.boundaries.isEmpty()) none else policy.boundaries.joinToString(", ") { it.text })
    KitRowGroup(
        modifier.testTag("team-run-policy").semantics(mergeDescendants = true) { contentDescription = spoken },
        label = stringResource(Res.string.teamUiPolicyBoundariesLabel),
        note = stringResource(Res.string.teamUiPolicyFromHost),
    ) {
        KitRow(
            title = stringResource(Res.string.teamUiPolicySupervision, level),
            supporting = policy.rig?.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.teamUiPolicyRig, it) },
            leading = KitRowLeading.Glyph(KitIcons.policy),
            modifier = Modifier.testTag("team-run-policy-supervision"),
        )
        if (policy.boundaries.isEmpty()) {
            KitRow(title = none, modifier = Modifier.testTag("team-run-policy-none"))
        } else {
            policy.boundaries.forEach { b ->
                KitRow(
                    title = b.text,
                    titleMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.shield),
                    modifier = Modifier.testTag("team-run-policy-boundary-${b.key}"),
                )
            }
        }
    }
}
