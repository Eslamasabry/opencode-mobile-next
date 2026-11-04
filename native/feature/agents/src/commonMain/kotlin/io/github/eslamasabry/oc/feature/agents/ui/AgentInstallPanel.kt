package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun agentInstallLabel(name: String): String =
    stringResource(Res.string.linuxAgentInstall, name)

/** A durable install is never shown as runtime qualification or sign-in. */
@Composable
internal fun AgentInstallPanel(
    state: AgentsUi,
    actions: AgentsActions,
    modifier: Modifier,
    enabled: KitEnabled,
) {
    state.installIssue?.let { issue ->
        KitErrorNotice(
            stringResource(
                when (issue) {
                    PhoneAgentInstallIssue.Busy -> Res.string.linuxAgentInstallBusy
                    PhoneAgentInstallIssue.Space -> Res.string.linuxAgentInstallSpace
                    PhoneAgentInstallIssue.Unavailable -> Res.string.linuxAgentInstallUnavailable
                }
            ),
            modifier,
        )
    }
    val installation = state.phone.installation ?: return
    val name = state.phone.agents.firstOrNull { it.id == installation.id }?.name ?: installation.id
    when (installation.phase) {
        PhoneAgentInstallPhase.Installing -> {
            KitLoadingBar(true, stringResource(Res.string.linuxAgentInstalling, name), modifier)
            KitNotice(stringResource(Res.string.linuxAgentInstallBackground), modifier)
            KitButton(
                stringResource(Res.string.linuxAgentInstallCancel),
                actions::cancelPhoneInstall,
                modifier,
                role = KitButtonRole.Secondary,
                enabled = enabled,
            )
        }
        PhoneAgentInstallPhase.Done ->
            KitNotice(stringResource(Res.string.linuxAgentInstallDone, name), modifier)
        PhoneAgentInstallPhase.Failed ->
            KitErrorNotice(stringResource(Res.string.linuxAgentInstallFailed, name), modifier)
        PhoneAgentInstallPhase.Interrupted ->
            KitNotice(stringResource(Res.string.linuxAgentInstallInterrupted, name), modifier)
    }
}
