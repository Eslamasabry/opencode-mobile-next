package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.roundToLong
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
    state.installingAgent?.let { agent ->
        val size = agent.downloadBytes?.let(::agentPayloadSize)
        val needsPhoneSetup = !state.phone.available || state.phone.owner == null
        val installEnabled =
            KitEnabled.of(
                agent.installable && enabled.isEnabled,
                stringResource(Res.string.linuxAgentInstallBusy),
            )
        KitSheet(
            visible = true,
            onDismissRequest = actions::dismissInstall,
            dismissible = !state.busy,
            title =
                if (size == null) stringResource(Res.string.agentsSetupTitleNoSize, agent.name)
                else stringResource(Res.string.agentsSetupTitle, agent.name, size),
            primary =
                if (needsPhoneSetup)
                    KitAction(
                        stringResource(Res.string.phoneSetupStartSetUp),
                        {
                            actions.dismissInstall()
                            actions.openPhone()
                        },
                        enabled = enabled,
                    )
                else
                    KitAction(
                        stringResource(Res.string.agentsInstallAction, agent.name),
                        { actions.installPhone(agent.id) },
                        enabled = installEnabled,
                        working = state.busy,
                    ),
        ) {
            KitText(stringResource(Res.string.agentsSetupBody, agent.name))
            if (size != null) KitText(stringResource(Res.string.agentsSetupSizeNote, size))
            if (needsPhoneSetup) KitNotice(stringResource(Res.string.agentsCatalogNeedsPhoneSetup))
        }
    }
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

/** Pinned payload only. Unknown sizes stay absent; bytes are never borrowed from another CPU. */
internal fun agentPayloadSize(bytes: Long): String =
    KitBidi.ltr(
        when {
            bytes >= 1_000_000_000L -> {
                val tenths = (bytes / 100_000_000.0).roundToLong()
                "${tenths / 10}.${tenths % 10} GB"
            }
            bytes >= 1_000_000L -> "${(bytes / 1_000_000.0).roundToLong()} MB"
            bytes >= 1_000L -> "${(bytes / 1_000.0).roundToLong()} kB"
            else -> "$bytes B"
        }
    )
