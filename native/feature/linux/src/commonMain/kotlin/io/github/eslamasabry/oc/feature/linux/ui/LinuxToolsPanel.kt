package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.components.ComponentPresence
import io.github.eslamasabry.oc.engine.runtime.components.ComponentRemovalBlock
import io.github.eslamasabry.oc.engine.runtime.components.RemovalResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflight
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflightResult
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxToolsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxToolsPanelUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** A visible inventory and guarded removal path; scripts and error details never become copy. */
@Composable
internal fun LinuxToolsPanel(state: LinuxToolsPanelUi, actions: LinuxToolsActions, busy: Boolean) {
    val tools = state.tools
    when (val result = tools.preflight) {
        null,
        SetupPreflightResult.Ok -> Unit
        is SetupPreflightResult.UnsupportedAbi ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightUnsupportedHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightUnsupportedBody,
                        result.reportedAbi,
                    ),
            )
        is SetupPreflightResult.LowMemory ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowMemoryHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowMemoryBody,
                        SetupPreflight.MINIMUM_MEMORY_MB.toString(),
                        result.totalMemoryMb.toString(),
                    ),
            )
        is SetupPreflightResult.LowSpace ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowSpaceHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowSpaceBody,
                        size(result.bytesToFree),
                    ),
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.phoneSetupPreflightOpenStorage),
                            actions::openStorageSettings,
                        )
                    ),
            )
        is SetupPreflightResult.MayBeSlow ->
            KitNotice(
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightMayBeSlow,
                        result.totalMemoryMb.toString(),
                    )
            )
    }
    KitSectionLabel(stringResource(Res.string.linuxToolsTitle))
    val working = busy || tools.loading || tools.removing || state.measuring
    val wait = stringResource(Res.string.linuxToolsWait)
    when (val result = tools.removal) {
        null -> Unit
        RemovalResult.Removed -> KitNotice(message = stringResource(Res.string.linuxToolsRemoved))
        is RemovalResult.Blocked -> KitNotice(message = result.code.words())
    }
    KitRowGroup {
        for (entry in tools.entries) {
            KitRow(
                title = componentTitle(entry.component.id),
                supporting =
                    stringResource(
                        when (entry.presence) {
                            ComponentPresence.Installed -> Res.string.linuxToolsInstalled
                            ComponentPresence.Absent -> Res.string.linuxToolsAbsent
                            ComponentPresence.Unknown -> Res.string.linuxToolsUnknown
                        }
                    ),
            )
        }
        KitRow(
            title = stringResource(Res.string.linuxToolsRefresh),
            enabled = !working,
            disabledReason = wait,
            onClick = actions::refresh,
        )
        val python = tools.entries.firstOrNull { it.component.id == "python" }
        if (python != null && python.presence != ComponentPresence.Absent) {
            KitRow(
                title = stringResource(Res.string.linuxToolsRemovePython),
                supporting = stringResource(Res.string.linuxToolsRemoveBody),
                supportingMaxLines = 3,
                enabled = !working && python.canRemove,
                disabledReason = if (working) wait else python.block?.words(),
                onClick = actions::askRemove,
            )
        }
    }
    KitDetailsFold(
        label = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyAll),
        text = tools.details,
    )
    KitConfirm(
        spec =
            if (state.confirming && !busy && !tools.removing)
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxToolsRemoveQuestion),
                    confirmLabel = stringResource(Res.string.linuxToolsRemovePython),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.linuxToolsRemoveBody),
                    consequences =
                        state.bytes
                            ?.takeIf { it > 0 }
                            ?.let {
                                listOf(
                                    stringResource(Res.string.linuxToolsFreedBytes, size(it))
                                )
                            }
                            .orEmpty(),
                )
            else null,
        onConfirm = actions::confirmRemove,
        onCancel = actions::dismiss,
    )
}

@Composable
internal fun componentTitle(id: String): String =
    stringResource(
        when (id) {
            "linux" -> Res.string.phoneSetupLinuxTitle
            "essentials" -> Res.string.phoneSetupEssentialsTitle
            "python" -> Res.string.linuxRuntimePythonTitle
            "node" -> Res.string.linuxRuntimeNodeTitle
            "opencode" -> Res.string.linuxRuntimeOpenCodeTitle
            "start" -> Res.string.phoneSetupStartTitle
            "aiteam" -> Res.string.linuxToolsAiTeam
            else -> Res.string.linuxToolsPart
        }
    )

@Composable
private fun ComponentRemovalBlock.words(): String =
    stringResource(
        when (this) {
            ComponentRemovalBlock.DependentInstalled -> Res.string.linuxToolsDependent
            ComponentRemovalBlock.DependencyUnknown -> Res.string.linuxToolsDependencyUnknown
            ComponentRemovalBlock.RequiredComponent -> Res.string.linuxToolsRequired
            ComponentRemovalBlock.Unsupported -> Res.string.linuxToolsUnsupported
            ComponentRemovalBlock.NotInstalled -> Res.string.linuxToolsAbsent
            ComponentRemovalBlock.InventoryUnavailable -> Res.string.linuxToolsInventoryFailed
            ComponentRemovalBlock.SetupRunning -> Res.string.linuxToolsSetupRunning
            ComponentRemovalBlock.Busy -> Res.string.linuxToolsWait
            ComponentRemovalBlock.Failed -> Res.string.linuxToolsRemoveFailed
        }
    )
