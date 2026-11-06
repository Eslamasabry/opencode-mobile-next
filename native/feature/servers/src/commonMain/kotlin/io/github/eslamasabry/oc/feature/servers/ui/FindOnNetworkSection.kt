package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.presentation.FindOnNetworkUi
import io.github.eslamasabry.oc.feature.servers.presentation.FindPhase
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * "Find on my network": the second way to connect, after scanning the code. One row to start, then a
 * live list while the phone looks, then a way forward when nothing turns up or access was refused.
 * Nothing here sends a password; picking a row only fills the form.
 */
@Composable
internal fun FindOnNetworkSection(
    find: FindOnNetworkUi,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPick: (LanFinding) -> Unit,
    onEnterAddress: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        when (find.phase) {
            FindPhase.Idle ->
                KitRow(
                    title = stringResource(Res.string.upgradeFindTitle),
                    supporting = stringResource(Res.string.upgradeFindBody),
                    supportingMaxLines = 2,
                    leading = KitRowLeading.Glyph(KitIcons.networkCheck),
                    trailing = KitRowTrailing.Chevron,
                    enabled = enabled,
                    onClick = onStart,
                )
            FindPhase.Denied -> {
                KitNotice(stringResource(Res.string.upgradeFindDeniedBody), icon = KitIcons.info)
                KitButton(
                    stringResource(Res.string.upgradeFindAllow),
                    onStart,
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                    fullWidth = true,
                )
                KitButton(
                    stringResource(Res.string.upgradeFindEnterAddress),
                    onEnterAddress,
                    role = KitButtonRole.Tertiary,
                    fullWidth = true,
                )
            }
            FindPhase.Blocked -> {
                KitNotice(stringResource(Res.string.upgradeFindBlockedBody), icon = KitIcons.info)
                KitButton(
                    stringResource(Res.string.upgradeFindOpenSettings),
                    onOpenSettings,
                    role = KitButtonRole.Secondary,
                    fullWidth = true,
                )
                KitButton(
                    stringResource(Res.string.upgradeFindEnterAddress),
                    onEnterAddress,
                    role = KitButtonRole.Tertiary,
                    fullWidth = true,
                )
            }
            FindPhase.Scanning,
            FindPhase.Done -> {
                val scanning = find.phase == FindPhase.Scanning
                KitText(
                    stringResource(Res.string.upgradeFindTitle),
                    role = TypeRole.Headline,
                )
                if (scanning) {
                    KitProgressView(
                        KitProgress.Waiting(stringResource(Res.string.upgradeFindScanning)),
                        semanticsLabel = stringResource(Res.string.upgradeFindScanning),
                    )
                    KitButton(
                        stringResource(Res.string.upgradeFindStop),
                        onStop,
                        role = KitButtonRole.Tertiary,
                        fullWidth = true,
                    )
                }
                KitReveal(visible = find.findings.isNotEmpty()) {
                    KitRowGroup(label = stringResource(Res.string.upgradeFindResultsLabel)) {
                        for (found in find.findings) FoundRow(found, enabled, onPick)
                    }
                }
                if (!scanning && find.findings.isEmpty()) {
                    KitNotice(
                        stringResource(Res.string.upgradeFindEmptyBody),
                        title = stringResource(Res.string.upgradeFindEmptyTitle),
                        icon = KitIcons.info,
                    )
                    KitButton(
                        stringResource(Res.string.upgradeFindEnterAddress),
                        onEnterAddress,
                        role = KitButtonRole.Secondary,
                        fullWidth = true,
                    )
                }
                if (!scanning)
                    KitButton(
                        stringResource(Res.string.upgradeFindSearchAgain),
                        onStart,
                        role = KitButtonRole.Tertiary,
                        enabled = enabled,
                        fullWidth = true,
                    )
            }
        }
    }
}

@Composable
private fun FoundRow(found: LanFinding, enabled: Boolean, onPick: (LanFinding) -> Unit) {
    val version = found.version?.takeIf { it.isNotBlank() }
    val facts =
        listOfNotNull(
                found.hostPort,
                version?.let { stringResource(Res.string.upgradeFindVersion, it) },
            )
            .joinToString(" · ")
    KitRow(
        title = found.title,
        supporting = facts,
        supportingMaxLines = 2,
        leading = KitRowLeading.Glyph(KitIcons.computer),
        meta = if (found.needsPassword) stringResource(Res.string.upgradeFindNeedsPassword) else null,
        trailing = KitRowTrailing.Chevron,
        enabled = enabled,
        onClickLabel = stringResource(Res.string.upgradeFindPickLabel, found.title),
        onClick = { onPick(found) },
    )
}
