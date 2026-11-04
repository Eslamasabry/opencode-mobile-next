package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.components.ComponentIds
import io.github.eslamasabry.oc.engine.runtime.components.ComponentPresence
import io.github.eslamasabry.oc.engine.runtime.components.ComponentRemovalBlock
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun componentName(id: String): String =
    stringResource(
        when (id) {
            ComponentIds.LINUX -> Res.string.phoneSetupLinuxTitle
            ComponentIds.ESSENTIALS -> Res.string.phoneSetupEssentialsTitle
            ComponentIds.PYTHON -> Res.string.linuxRuntimePythonTitle
            ComponentIds.NODE -> Res.string.linuxRuntimeNodeTitle
            ComponentIds.OPENCODE -> Res.string.linuxRuntimeOpenCodeTitle
            ComponentIds.START -> Res.string.phoneSetupStartTitle
            ComponentIds.AITEAM -> Res.string.linuxComponentsTeam
            ComponentIds.VOICE -> Res.string.voiceComponentTitle
            else -> Res.string.linuxComponentsUnknown
        }
    )

@Composable
private fun componentBlockText(block: ComponentRemovalBlock, dependents: String = ""): String =
    stringResource(
        when (block) {
            ComponentRemovalBlock.DependentInstalled -> Res.string.linuxComponentsDependents
            ComponentRemovalBlock.DependencyUnknown -> Res.string.linuxComponentsDependencyUnknown
            ComponentRemovalBlock.RequiredComponent -> Res.string.linuxComponentsRequired
            ComponentRemovalBlock.Unsupported -> Res.string.linuxComponentsUnsupported
            ComponentRemovalBlock.NotInstalled -> Res.string.linuxComponentsAbsent
            ComponentRemovalBlock.InventoryUnavailable -> Res.string.linuxComponentsUnknownPresence
            ComponentRemovalBlock.SetupRunning,
            ComponentRemovalBlock.Busy -> Res.string.linuxComponentsBusy
            ComponentRemovalBlock.Failed -> Res.string.linuxComponentsFailed
        },
        dependents,
    )

/** Presence and dependency truth are refreshed again by the engine after confirmation. */
@Composable
internal fun LinuxComponentsPanel(state: LinuxHomeUi, actions: LinuxHomeActions) {
    val busy = state.working || state.job.running || state.setup.state == SetupState.Running
    state.componentBlock?.let { block ->
        val dependents =
            state.components
                .filter { it.block == block }
                .flatMap { it.blockingDependents }
                .distinct()
                .map { componentName(it) }
                .joinToString(", ")
        KitNotice(message = componentBlockText(block, dependents))
    }
    KitRowGroup(label = stringResource(Res.string.linuxComponentsTitle)) {
        KitRow(
            title = stringResource(Res.string.linuxComponentsRefresh),
            enabled = !busy && !state.componentsLoading,
            onClick = actions::refreshComponents,
        )
        state.components.forEach { entry ->
            val name = componentName(entry.component.id)
            val dependents = entry.blockingDependents.map { componentName(it) }.joinToString(", ")
            val detail =
                entry.block?.let { componentBlockText(it, dependents) }
                    ?: stringResource(Res.string.linuxComponentsInstalled)
            KitRow(
                title = name,
                supporting = detail,
                supportingMaxLines = 3,
            )
            if (entry.canRemove && entry.presence == ComponentPresence.Installed) {
                KitRow(
                    title = stringResource(Res.string.linuxComponentsRemove, name),
                    enabled = !busy && !state.componentsLoading,
                    onClick = { actions.askRemoveComponent(entry.component.id) },
                )
            }
        }
    }
}
