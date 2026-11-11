package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallFailure
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

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
                // The live row, not the one captured when the review opened (another install may have started since);
                // a failed or cut-short install of this agent may always be tried again (the presenter's own rule).
                ((state.phone.agents.firstOrNull { it.id == agent.id } ?: agent).installable ||
                    state.phone.installation?.let {
                        it.id == agent.id && it.phase != PhoneAgentInstallPhase.Installing
                    } == true) && enabled.isEnabled,
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
            secondary =
                if (state.phone.installation?.let {
                        it.id == agent.id && it.phase == PhoneAgentInstallPhase.Installing
                    } == true
                )
                    KitAction(
                        stringResource(Res.string.agentsCancelSetup),
                        actions::cancelPhoneInstall,
                        enabled = enabled,
                    )
                else null,
        ) {
            KitText(stringResource(Res.string.agentsSetupBody, agent.name))
            if (agent.id == "gemini") KitNotice(stringResource(Res.string.agentsGeminiMigrationNote))
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
        // Installing and installed read on the agent's tile and in its sheet (with Cancel), not as notices here.
        PhoneAgentInstallPhase.Installing,
        PhoneAgentInstallPhase.Done -> Unit
        // The last result stays until Try again or Dismiss; the technical side is under Details.
        PhoneAgentInstallPhase.Failed,
        PhoneAgentInstallPhase.Interrupted -> {
            val retry = KitLineAction(stringResource(Res.string.kitTryAgain), { actions.installPhone(installation.id) })
            val dismiss = KitLineAction(stringResource(Res.string.kitSheetDismiss), actions::dismissInstallResult)
            val words =
                stringResource(
                    if (installation.phase == PhoneAgentInstallPhase.Interrupted)
                        Res.string.agentsInstallInterruptedNow
                    else failedWords(installation.failure),
                    name,
                )
            KitErrorNotice(
                words,
                modifier,
                details = installation.details,
                detailsLabel = stringResource(Res.string.kitDetails),
                retry = retry,
                dismiss = dismiss,
            )
        }
    }
}

private fun failedWords(failure: PhoneAgentInstallFailure?) =
    when (failure) {
        PhoneAgentInstallFailure.Download -> Res.string.agentsInstallFailedDownload
        PhoneAgentInstallFailure.Checksum -> Res.string.agentsInstallFailedChecksum
        PhoneAgentInstallFailure.Storage -> Res.string.agentsInstallFailedStorage
        PhoneAgentInstallFailure.Version -> Res.string.agentsInstallFailedVersion
        else -> Res.string.agentsInstallFailedUnknown
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
