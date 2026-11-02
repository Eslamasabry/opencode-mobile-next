package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.presentation.PackRowUi
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechEngineKind
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConfirm
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiCancelDownload
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiDeleteDetail
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiDeletePack
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiDownloadProgress
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiInstalled
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiLanguage
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNoBuiltInMic
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNotNow
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiPrivacyDownload
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiSetupFailed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiVerifying
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceDownloading
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceKeep
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceRemove
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceRemoveBody
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.readAloudVoiceNatural
import io.github.eslamasabry.oc.ui.words.res.readAloudVoiceNaturalDetail
import io.github.eslamasabry.oc.ui.words.res.readAloudVoiceNaturalDownload
import io.github.eslamasabry.oc.ui.words.res.readAloudVoicePhone
import io.github.eslamasabry.oc.ui.words.res.readAloudVoicePhoneDetail
import io.github.eslamasabry.oc.ui.words.res.readAloudVoiceTitle
import io.github.eslamasabry.oc.ui.words.res.settingsV3VoiceTyping
import io.github.eslamasabry.oc.ui.words.res.voiceInputUnavailable
import io.github.eslamasabry.oc.ui.words.res.voiceNativeNaturalNeedsEngine
import io.github.eslamasabry.oc.ui.words.res.voiceNativePartial
import io.github.eslamasabry.oc.ui.words.res.voiceNativePhoneEngine
import io.github.eslamasabry.oc.ui.words.res.voiceSetupBusyReason
import io.github.eslamasabry.oc.ui.words.res.voiceSetupDeletePack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupDownloadPack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupDownloadingPack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupKeepPack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupModelLabel
import io.github.eslamasabry.oc.ui.words.res.voiceSetupNotDownloaded
import io.github.eslamasabry.oc.ui.words.res.voiceSetupRedownloadPack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupSubtitle
import io.github.eslamasabry.oc.ui.words.res.vpttAutoSendDetail
import io.github.eslamasabry.oc.ui.words.res.vpttAutoSendTitle
import io.github.eslamasabry.oc.ui.words.res.vpttLanguageDetectDetail
import io.github.eslamasabry.oc.ui.words.res.vpttLanguageFollowAppDetail
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Voice typing (plan 07 I11, Flutter `VoiceSettingsScreen`): what types (the phone's recognizer or the
 * offline model), the speech models with download, verification, download again and delete, the transcription
 * language, "Send when I stop talking" (off by default) and the read-aloud voice.
 */
@Composable
public fun VoiceSettingsScreen(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.settingsV3VoiceTyping),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp)
                .testTag("voice-settings-list"),
            verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
        ) {
            if (!state.available) {
                KitNotice(stringResource(Res.string.voiceInputUnavailable), tone = StatusTone.Failure)
                return@Column
            }
            KitText(stringResource(Res.string.voiceSetupSubtitle), role = TypeRole.Secondary, tone = TextTone.Secondary)
            // What is happening comes first (STATE-1): the running download or why it failed.
            DownloadProgress(state, actions)
            state.problem?.let { problem ->
                KitNotice(
                    message = stringResource(Res.string.e7VoiceUiSetupFailed, problemWords(problem)),
                    tone = StatusTone.Failure,
                    dismiss = KitLineAction(stringResource(Res.string.e7VoiceUiNotNow), actions::dismissProblem),
                )
            }
            KitNotice(
                message =
                    stringResource(
                        if (state.engine == SpeechEngineKind.PhoneRecognizer) Res.string.voiceNativePhoneEngine
                        else Res.string.e7VoiceUiPrivacyDownload,
                    ),
                liveRegion = false,
            )
            if (!state.hasMicrophone) KitNotice(stringResource(Res.string.e7VoiceUiNoBuiltInMic))
            Models(state, actions)
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.e7VoiceUiLanguage),
                    supporting =
                        when (state.language) {
                            VoiceLanguage.Auto -> stringResource(Res.string.vpttLanguageFollowAppDetail)
                            VoiceLanguage.Detect -> stringResource(Res.string.vpttLanguageDetectDetail)
                            else -> null
                        },
                    supportingMaxLines = 4,
                    trailing = KitRowTrailing.Value(languageName(state.language)),
                    enabled = !state.busy,
                    disabledReason = if (state.busy) stringResource(Res.string.voiceSetupBusyReason) else null,
                    onClick = actions::openLanguages,
                    modifier = Modifier.testTag("voice-model-language"),
                )
                KitSwitchRow(
                    title = stringResource(Res.string.vpttAutoSendTitle),
                    checked = state.autoSend,
                    supporting = stringResource(Res.string.vpttAutoSendDetail),
                    onCheckedChange = actions::setAutoSend,
                    modifier = Modifier.testTag("voice-auto-send"),
                )
            }
            ReadAloudVoice(state, actions)
        }
        // Inside the frame: KitScreen places the sheet host these overlays register with.
        LanguageSheet(state, actions)
        VoiceConfirmView(state, actions)
    }
}

@Composable
private fun DownloadProgress(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    val job = state.job ?: return
    val natural = state.natural?.downloading == true
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp), modifier = Modifier.testTag("voice-download-progress")) {
        KitText(
            when {
                job.verifying -> stringResource(Res.string.e7VoiceUiVerifying)
                natural -> stringResource(Res.string.naturalVoiceDownloading)
                else -> stringResource(Res.string.voiceSetupDownloadingPack, packName(job.pack))
            },
            role = TypeRole.RowTitle,
        )
        val fraction = job.fraction
        KitProgressView(
            progress =
                if (fraction == null) KitProgress.Waiting()
                else
                    KitProgress.Known(
                        fraction,
                        caption = stringResource(Res.string.e7VoiceUiDownloadProgress, sizeWords(job.received), sizeWords(job.total)),
                    ),
        )
        if (!job.verifying) {
            KitButton(
                label = stringResource(Res.string.e7VoiceUiCancelDownload),
                onClick = actions::cancelDownload,
                role = KitButtonRole.Secondary,
                modifier = Modifier.testTag("voice-model-cancel-download"),
            )
        }
    }
}

@Composable
private fun Models(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    KitRowGroup(label = stringResource(Res.string.voiceSetupModelLabel)) {
        KitChoiceList(
            choices = state.packs.map { row -> packChoice(row, state.busy) },
            selected = state.selected,
            semanticsLabel = stringResource(Res.string.voiceSetupModelLabel),
            onSelect = actions::choose,
        )
    }
    val row = state.selectedRow ?: return
    if (state.busy) return
    val name = packName(row.id)
    val size = sizeWords(row.bytes)
    if (!row.installed) {
        KitButton(
            label = stringResource(Res.string.voiceSetupDownloadPack, name, size),
            onClick = actions::downloadSelected,
            enabled =
                if (row.unsupported) KitEnabled.Disabled(unsupportedReason(row))
                else KitEnabled.Enabled,
            modifier = Modifier.fillMaxWidth().testTag("voice-model-primary-action"),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            KitButton(
                label = stringResource(Res.string.voiceSetupRedownloadPack, name),
                onClick = actions::downloadAgain,
                role = KitButtonRole.Tertiary,
                modifier = Modifier.testTag("voice-redownload-${row.id.raw}"),
            )
            KitButton(
                label = stringResource(Res.string.voiceSetupDeletePack, name, size),
                onClick = actions::askDelete,
                role = KitButtonRole.Tertiary,
                destructive = true,
                modifier = Modifier.testTag("voice-delete-${row.id.raw}"),
            )
        }
    }
}

@Composable
private fun packChoice(row: PackRowUi, busy: Boolean): KitChoice<io.github.eslamasabry.oc.engine.voice.state.PackId> {
    val where =
        when (val status = row.status) {
            PackStatus.Installed -> stringResource(Res.string.e7VoiceUiInstalled)
            is PackStatus.Partial ->
                stringResource(Res.string.voiceNativePartial, sizeWords(status.bytes), sizeWords(status.total))
            else -> stringResource(Res.string.voiceSetupNotDownloaded, sizeWords(row.bytes))
        }
    val line = "$where · ${packDetail(row.id)}"
    return KitChoice(
        value = row.id,
        title = packName(row.id),
        supporting = line,
        recommended = row.id.raw == "base",
        enabled =
            when {
                row.unsupported -> KitEnabled.Disabled(unsupportedReason(row))
                busy -> KitEnabled.Disabled(stringResource(Res.string.voiceSetupBusyReason))
                else -> KitEnabled.Enabled
            },
    )
}

@Composable
private fun unsupportedReason(row: PackRowUi): String {
    val problem = (row.status as? PackStatus.Unsupported)?.problem ?: return stringResource(Res.string.voiceInputUnavailable)
    return problemWords(problem)
}

@Composable
private fun ReadAloudVoice(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    val natural = state.natural ?: return
    KitRowGroup(label = stringResource(Res.string.readAloudVoiceTitle)) {
        KitChoiceList(
            choices =
                listOf(
                    KitChoice(
                        value = false,
                        title = stringResource(Res.string.readAloudVoicePhone),
                        supporting = stringResource(Res.string.readAloudVoicePhoneDetail),
                    ),
                    KitChoice(
                        value = true,
                        title =
                            if (natural.installed) stringResource(Res.string.readAloudVoiceNatural)
                            else stringResource(Res.string.readAloudVoiceNaturalDownload, sizeWords(natural.bytes)),
                        supporting = stringResource(Res.string.readAloudVoiceNaturalDetail),
                        enabled =
                            when {
                                !natural.available -> KitEnabled.Disabled(stringResource(Res.string.voiceNativeNaturalNeedsEngine))
                                state.busy -> KitEnabled.Disabled(stringResource(Res.string.voiceSetupBusyReason))
                                else -> KitEnabled.Enabled
                            },
                    ),
                ),
            selected = natural.chosen,
            semanticsLabel = stringResource(Res.string.readAloudVoiceTitle),
            onSelect = actions::setNaturalVoice,
        )
    }
    if (natural.installed && !state.busy) {
        KitButton(
            label = stringResource(Res.string.naturalVoiceRemove, sizeWords(natural.bytes)),
            onClick = actions::askRemoveNatural,
            role = KitButtonRole.Tertiary,
            destructive = true,
        )
    }
}

@Composable
private fun LanguageSheet(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    KitSheet(
        visible = state.languagePicking,
        onDismissRequest = actions::closeLanguages,
        title = stringResource(Res.string.e7VoiceUiLanguage),
        closeLabel = stringResource(Res.string.e7VoiceUiNotNow),
    ) {
        KitChoiceList(
            choices =
                VoiceLanguage.entries.map { language ->
                    KitChoice(
                        value = language,
                        title = languageName(language),
                        supporting =
                            when (language) {
                                VoiceLanguage.Auto -> stringResource(Res.string.vpttLanguageFollowAppDetail)
                                VoiceLanguage.Detect -> stringResource(Res.string.vpttLanguageDetectDetail)
                                else -> null
                            },
                    )
                },
            selected = state.language,
            semanticsLabel = stringResource(Res.string.e7VoiceUiLanguage),
            onSelect = actions::chooseLanguage,
            modifier = Modifier.testTag("voice-language-list"),
        )
    }
}

@Composable
private fun VoiceConfirmView(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    val spec =
        when (val pending = state.confirm) {
            null -> null
            is VoiceConfirm.DeletePack -> {
                val name = packName(pending.pack)
                val size = sizeWords(pending.bytes)
                KitConfirmSpec(
                    title = stringResource(Res.string.e7VoiceUiDeletePack, name),
                    body = stringResource(Res.string.e7VoiceUiDeleteDetail, size),
                    confirmLabel = stringResource(Res.string.voiceSetupDeletePack, name, size),
                    cancelLabel = stringResource(Res.string.voiceSetupKeepPack, name),
                    kind = KitConfirmKind.Destructive,
                )
            }
            VoiceConfirm.RemoveNatural ->
                KitConfirmSpec(
                    title = stringResource(Res.string.naturalVoiceRemoveTitle),
                    body = stringResource(Res.string.naturalVoiceRemoveBody),
                    confirmLabel = stringResource(Res.string.naturalVoiceRemove, sizeWords(state.natural?.bytes ?: 0)),
                    cancelLabel = stringResource(Res.string.naturalVoiceKeep),
                    kind = KitConfirmKind.Destructive,
                )
        }
    if (spec != null) KitConfirm(spec = spec, onConfirm = actions::confirm, onCancel = actions::dismissConfirm)
}
