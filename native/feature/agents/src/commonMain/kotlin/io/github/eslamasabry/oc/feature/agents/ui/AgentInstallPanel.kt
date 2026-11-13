package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.feature.agents.hub.agentTile
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallFailure
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.ui.design.color.StatusTone
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
    var stopAsked by remember { mutableStateOf(false) }
    state.installingAgent?.let { agent ->
        val blocker = state.installBlocker
        val size = agent.downloadBytes?.let(::agentPayloadSize)
        val needsPhoneSetup = !state.phone.available || state.phone.owner == null
        // Known before the press: this phone cannot fit the install, so Install waits and the review says why.
        val short = state.installSpace?.takeIf { it.short }
        val installEnabled =
            if (short != null) KitEnabled.Disabled(stringResource(Res.string.linuxAgentInstallSpace))
            else
                KitEnabled.of(
                    // The live row, not the one captured when the review opened (another install may have started
                    // since); a failed or cut-short install of this agent may always be tried again (the presenter's
                    // own rule).
                    ((state.phone.agents.firstOrNull { it.id == agent.id } ?: agent).installable ||
                        state.phone.installation?.let {
                            it.id == agent.id && it.phase != PhoneAgentInstallPhase.Installing
                        } == true) && enabled.isEnabled,
                    // Wait for it, or stop it where the sheet offers to; without that, only wait.
                    stringResource(if (blocker?.canCancel == false) Res.string.agentsFailBusy else Res.string.linuxAgentInstallBusy),
                )
        KitSheet(
            visible = true,
            onDismissRequest = actions::dismissInstall,
            // Closing the review changes nothing that runs: always possible, a busy page included.
            dismissible = true,
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
                        // A blocked install waits, plainly: a disabled button with its reason, not a spinner.
                        working = state.busy && blocker == null,
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
            if (blocker != null) {
                val job = blocker.agentId?.let { id -> state.phone.agents.firstOrNull { it.id == id } }
                val progress =
                    if (job != null) installingWords(agentTile(job, state.phone.installation, quiet = state.installQuiet))
                    else stringResource(Res.string.agentsTileStepLinux)
                KitNotice(
                    if (blocker.name != null) stringResource(Res.string.agentsInstalling, blocker.name) + "\n" + progress
                    else progress,
                    tone = StatusTone.Attention,
                    actions =
                        if (blocker.canCancel)
                            listOf(KitLineAction(stringResource(Res.string.linuxAgentInstallCancel), { stopAsked = true }, enabled = enabled.isEnabled))
                        else emptyList(),
                )
            }
            KitText(stringResource(Res.string.agentsSetupBody, agent.name))
            if (agent.id == "gemini") KitNotice(stringResource(Res.string.agentsGeminiMigrationNote))
            if (size != null) KitText(stringResource(Res.string.agentsSetupSizeNote, size))
            if (needsPhoneSetup) KitNotice(stringResource(Res.string.agentsCatalogNeedsPhoneSetup))
            // What the install needs, the shared parts this phone does not have yet included, and what it has.
            val space = state.installSpace
            if (space != null && !space.short)
                KitText(
                    stringResource(
                        Res.string.agentsInstallSpaceFits,
                        agentPayloadSize(space.neededBytes),
                        agentPayloadSize(space.freeBytes),
                    )
                )
            if (short != null)
                KitNotice(
                    stringResource(
                        Res.string.agentsInstallNeedsSpace,
                        agent.name,
                        agentPayloadSize(short.neededBytes),
                        agentPayloadSize(short.freeBytes),
                    ),
                    tone = StatusTone.Attention,
                    actions = listOf(freeUpSpace(actions)),
                )
        }
    }
    // Stopping another agent's setup asks first: it ends work that may be half done.
    state.installingAgent?.let { _ ->
        KitConfirm(
            spec =
                if (stopAsked)
                    KitConfirmSpec(
                        title = stringResource(Res.string.linuxAgentInstallCancel),
                        body = stringResource(Res.string.agentsSetupInterrupted),
                        confirmLabel = stringResource(Res.string.linuxAgentInstallCancel),
                        kind = KitConfirmKind.Neutral,
                        cancelLabel = stringResource(Res.string.kitSheetDismiss),
                    )
                else null,
            onConfirm = {
                stopAsked = false
                actions.cancelPhoneInstall()
            },
            onCancel = { stopAsked = false },
        )
    }
    state.installIssue?.takeIf { state.installingAgent == null || it != PhoneAgentInstallIssue.Busy }?.let { issue ->
        KitErrorNotice(
            stringResource(
                when (issue) {
                    PhoneAgentInstallIssue.Busy -> Res.string.linuxAgentInstallBusy
                    PhoneAgentInstallIssue.Space -> Res.string.linuxAgentInstallSpace
                    PhoneAgentInstallIssue.Unavailable -> Res.string.linuxAgentInstallUnavailable
                }
            ),
            modifier,
            report = if (issue == PhoneAgentInstallIssue.Space) freeUpSpace(actions) else null,
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
            // Both are page steps: while another step runs they read as waiting for it, never a press that does nothing.
            val waiting = (enabled as? KitEnabled.Disabled)?.reason
            val retry =
                KitLineAction(
                    stringResource(Res.string.kitTryAgain),
                    { actions.installPhone(installation.id) },
                    enabled = enabled.isEnabled,
                    disabledReason = waiting,
                )
            val dismiss =
                KitLineAction(
                    stringResource(Res.string.kitSheetDismiss),
                    actions::dismissInstallResult,
                    enabled = enabled.isEnabled,
                    disabledReason = waiting,
                )
            KitErrorNotice(
                installResultWords(installation, name),
                modifier,
                details = installation.details,
                detailsLabel = stringResource(Res.string.kitDetails),
                retry = retry,
                // Short of space: the way forward is the phone's storage, beside Try again.
                report =
                    if (installation.failure == PhoneAgentInstallFailure.Storage) freeUpSpace(actions) else null,
                dismiss = dismiss,
            )
        }
    }
}

/** The full sentence for a failed or cut-short install of [name]: what happened and the way forward. */
@Composable
internal fun installResultWords(installation: PhoneAgentInstallation, name: String): String {
    val message = installFailedMessage(installation, name)
    return stringResource(message.words.resource(), *message.args.toTypedArray())
}

/** "Free up space": this phone's page, where its storage is measured and cleared. */
@Composable
private fun freeUpSpace(actions: AgentsActions): KitLineAction =
    KitLineAction(
        stringResource(Res.string.linuxMgrFreeUp),
        {
            actions.dismissInstall()
            actions.openPhone()
        },
    )

private fun failedWords(failure: PhoneAgentInstallFailure?): InstallFailedWords =
    when (failure) {
        PhoneAgentInstallFailure.Download -> InstallFailedWords.Download
        PhoneAgentInstallFailure.Checksum -> InstallFailedWords.Checksum
        PhoneAgentInstallFailure.Storage -> InstallFailedWords.Storage
        PhoneAgentInstallFailure.Version -> InstallFailedWords.Version
        PhoneAgentInstallFailure.NotStarted -> InstallFailedWords.NotStarted
        else -> InstallFailedWords.Unknown
    }

/** Which sentence a failed or cut-short install shows; [resource] picks its words. */
internal enum class InstallFailedWords {
    Interrupted,
    Download,
    Checksum,
    Storage,
    /** Another version, without the versions to name (Paseo's own check, or a line that did not name them). */
    Version,
    /** Another version, naming the one that arrived and the one this app installs. */
    VersionOther,
    NotStarted,
    Withdrawn,
    Unknown,
}

internal fun InstallFailedWords.resource() =
    when (this) {
        InstallFailedWords.Interrupted -> Res.string.agentsInstallInterruptedNow
        InstallFailedWords.Download -> Res.string.agentsInstallFailedDownload
        InstallFailedWords.Checksum -> Res.string.agentsInstallFailedChecksum
        InstallFailedWords.Storage -> Res.string.agentsInstallFailedStorage
        InstallFailedWords.Version -> Res.string.agentsInstallFailedVersion
        InstallFailedWords.VersionOther -> Res.string.agentsInstallFailedVersionOther
        InstallFailedWords.NotStarted -> Res.string.agentsInstallFailedNotStarted
        InstallFailedWords.Withdrawn -> Res.string.agentsInstallFailedWithdrawn
        InstallFailedWords.Unknown -> Res.string.agentsInstallFailedUnknown
    }

/** A failed install's sentence and its arguments (the agent's name first). */
internal data class InstallFailedMessage(val words: InstallFailedWords, val args: List<String>)

/**
 * The sentence for a failed or cut-short install of [name]. Another version than the app's, or a pinned version no
 * longer offered, names the versions (left to right in any language), because trying again cannot change either;
 * without them it falls back to the kind's own sentence.
 */
internal fun installFailedMessage(installation: PhoneAgentInstallation, name: String): InstallFailedMessage {
    val versions = installation.versions
    val found = versions?.found
    return when {
        installation.phase == PhoneAgentInstallPhase.Interrupted ->
            InstallFailedMessage(InstallFailedWords.Interrupted, listOf(name))
        installation.failure == PhoneAgentInstallFailure.Version && versions != null && found != null ->
            InstallFailedMessage(
                InstallFailedWords.VersionOther,
                listOf(name, KitBidi.ltr(found), KitBidi.ltr(versions.expected)),
            )
        installation.failure == PhoneAgentInstallFailure.Withdrawn && versions != null ->
            InstallFailedMessage(InstallFailedWords.Withdrawn, listOf(name, KitBidi.ltr(versions.expected)))
        else -> InstallFailedMessage(failedWords(installation.failure), listOf(name))
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
