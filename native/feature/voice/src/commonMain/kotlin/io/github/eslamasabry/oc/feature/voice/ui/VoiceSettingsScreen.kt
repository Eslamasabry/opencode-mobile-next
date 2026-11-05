package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
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
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
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
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupDownload
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupDownloadMobile
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupMaybeMetered
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupMobileData
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupTitle
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
 * Settings › Voice typing (plan 07 I11, Flutter `VoiceSettingsScreen`): what types (the phone's
 * recognizer or the offline model), the speech models with download, verification, download again
 * and delete, the transcription language, "Send when I stop talking" (off by default) and the
 * read-aloud voice.
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
        bottom =
            if (
                state.available &&
                    !state.loading &&
                    !state.busy &&
                    state.selectedRow?.installed == false
            ) {
                {
                    val row = state.selectedRow!!
                    KitActionBlock(
                        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
                            .padding(vertical = Space.S2.dp)
                            .testTag("voice-model-primary-action"),
                        primary =
                            KitAction(
                                stringResource(
                                    Res.string.voiceSetupDownloadPack,
                                    packName(row.id),
                                    sizeWords(row.bytes),
                                ),
                                actions::downloadSelected,
                                icon = KitIcons.download,
                                enabled =
                                    if (row.unsupported) KitEnabled.Disabled(unsupportedReason(row))
                                    else KitEnabled.Enabled,
                            ),
                    )
                }
            } else null,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp)
                .padding(top = Space.S2.dp, bottom = Space.S6.dp)
                .testTag("voice-settings-list")
        ) {
            if (!state.available) {
                KitNotice(
                    stringResource(Res.string.voiceInputUnavailable),
                    tone = StatusTone.Failure,
                )
                return@Column
            }
            KitText(
                stringResource(Res.string.voiceSetupSubtitle),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(Space.S4.dp))
            // What is happening comes first (STATE-1): the running download or why it failed.
            if (state.busy) {
                DownloadProgress(state, actions)
                Spacer(Modifier.height(Space.S4.dp))
            }
            state.problem?.let { problem ->
                KitNotice(
                    message =
                        stringResource(Res.string.e7VoiceUiSetupFailed, problemWords(problem)),
                    tone = StatusTone.Failure,
                    dismiss =
                        KitLineAction(
                            stringResource(Res.string.e7VoiceUiNotNow),
                            actions::dismissProblem,
                        ),
                )
                Spacer(Modifier.height(Space.S3.dp))
            }
            KitNotice(
                message =
                    stringResource(
                        if (state.engine == SpeechEngineKind.PhoneRecognizer)
                            Res.string.voiceNativePhoneEngine
                        else Res.string.e7VoiceUiPrivacyDownload
                    ),
                icon = KitIcons.locked,
                liveRegion = false,
            )
            if (!state.hasMicrophone) {
                Spacer(Modifier.height(Space.S3.dp))
                KitNotice(stringResource(Res.string.e7VoiceUiNoBuiltInMic))
            }
            Spacer(Modifier.height(Space.S4.dp))
            Models(state, actions)
            Spacer(Modifier.height(Space.S4.dp))
            KitText(stringResource(Res.string.e7VoiceUiLanguage), role = TypeRole.Label)
            Spacer(Modifier.height(Space.S2.dp))
            val sourceLanguages =
                listOf(VoiceLanguage.Detect, VoiceLanguage.English, VoiceLanguage.Arabic)
            val shownLanguages =
                if (state.language in sourceLanguages) sourceLanguages
                else sourceLanguages + state.language
            KitSegmented(
                segments =
                    shownLanguages.map { language ->
                        KitSegment(
                            language,
                            when (language) {
                                VoiceLanguage.Detect -> stringResource(Res.string.e7VoiceUiAuto)
                                VoiceLanguage.English -> stringResource(Res.string.rb51VoiceEnglish)
                                VoiceLanguage.Arabic -> stringResource(Res.string.rb51VoiceArabic)
                                else -> languageName(language)
                            },
                        )
                    },
                selected = state.language,
                semanticsLabel = stringResource(Res.string.e7VoiceUiLanguage),
                enabled =
                    if (state.busy)
                        KitEnabled.Disabled(stringResource(Res.string.voiceSetupBusyReason))
                    else KitEnabled.Enabled,
                onChange = actions::chooseLanguage,
                modifier = Modifier.testTag("voice-model-language"),
            )
            KitButton(
                stringResource(Res.string.rb51VoiceMoreLanguages),
                actions::openLanguages,
                role = KitButtonRole.Tertiary,
                enabled =
                    if (state.busy)
                        KitEnabled.Disabled(stringResource(Res.string.voiceSetupBusyReason))
                    else KitEnabled.Enabled,
            )
            Spacer(Modifier.height(Space.S4.dp))
            KitRowGroup {
                KitSwitchRow(
                    title = stringResource(Res.string.vpttAutoSendTitle),
                    checked = state.autoSend,
                    supporting = stringResource(Res.string.vpttAutoSendDetail),
                    onCheckedChange = actions::setAutoSend,
                    modifier = Modifier.testTag("voice-auto-send"),
                )
            }
            Spacer(Modifier.height(Space.S4.dp))
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
    val fraction = job.fraction
    val announcement =
        if (job.verifying) stringResource(Res.string.e7VoiceUiVerifying)
        else
            stringResource(
                Res.string.e7VoiceUiDownloadPercent,
                kotlin.math.floor((fraction ?: 0f) * 10).toInt() * 10,
            ) // F: lib/voice/voice_ui.dart:363
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        modifier = Modifier.testTag("voice-download-progress"),
    ) {
        Column(
            Modifier.clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitText(
                when {
                    job.verifying -> stringResource(Res.string.e7VoiceUiVerifying)
                    natural -> stringResource(Res.string.naturalVoiceDownloading)
                    else -> stringResource(Res.string.voiceSetupDownloadingPack, packName(job.pack))
                },
                role = TypeRole.RowTitle,
            )
            KitProgressView(
                progress =
                    if (job.verifying || fraction == null) KitProgress.Waiting()
                    else
                        KitProgress.Known(
                            fraction.coerceIn(0f, 1f),
                            caption =
                                stringResource(
                                    Res.string.e7VoiceUiDownloadProgress,
                                    sizeWords(job.received),
                                    sizeWords(job.total),
                                ),
                        )
            )
        }
        if (!job.verifying)
            KitActionBlock(
                secondary =
                    KitAction(
                        stringResource(Res.string.e7VoiceUiCancelDownload),
                        actions::cancelDownload,
                    )
            )
    }
}

@Composable
private fun Models(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    KitText(stringResource(Res.string.voiceSetupModelLabel), role = TypeRole.Label)
    Spacer(Modifier.height(Space.S1.dp))
    KitChoiceList(
        choices = state.packs.map { row -> packChoice(row, state.busy) },
        selected = state.selected,
        semanticsLabel = stringResource(Res.string.voiceSetupModelLabel),
        loading = state.loading,
        empty = {},
        onSelect = actions::choose,
    )
    val row = state.selectedRow ?: return
    if (state.busy) return
    val name = packName(row.id)
    val size = sizeWords(row.bytes)
    if (row.installed) {
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
private fun packChoice(
    row: PackRowUi,
    busy: Boolean,
): KitChoice<io.github.eslamasabry.oc.engine.voice.state.PackId> {
    val where =
        when (val status = row.status) {
            PackStatus.Installed -> stringResource(Res.string.e7VoiceUiInstalled)
            is PackStatus.Partial ->
                stringResource(
                    Res.string.voiceNativePartial,
                    sizeWords(status.bytes),
                    sizeWords(status.total),
                )
            else -> stringResource(Res.string.voiceSetupNotDownloaded, sizeWords(row.bytes))
        }
    val line = "$where · ${packDetail(row.id)}"
    return KitChoice(
        value = row.id,
        title = packName(row.id),
        supporting = line,
        enabled =
            when {
                row.unsupported -> KitEnabled.Disabled(unsupportedReason(row))
                busy -> KitEnabled.Disabled(line)
                else -> KitEnabled.Enabled
            },
    )
}

@Composable
private fun unsupportedReason(row: PackRowUi): String {
    val problem =
        (row.status as? PackStatus.Unsupported)?.problem
            ?: return stringResource(Res.string.voiceInputUnavailable)
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
                            else
                                stringResource(
                                    Res.string.readAloudVoiceNaturalDownload,
                                    sizeWords(natural.bytes),
                                ),
                        supporting = stringResource(Res.string.readAloudVoiceNaturalDetail),
                        enabled =
                            when {
                                !natural.available ->
                                    KitEnabled.Disabled(
                                        stringResource(Res.string.voiceNativeNaturalNeedsEngine)
                                    )
                                state.busy ->
                                    KitEnabled.Disabled(
                                        stringResource(Res.string.voiceSetupBusyReason)
                                    )
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
                                VoiceLanguage.Auto ->
                                    stringResource(Res.string.vpttLanguageFollowAppDetail)
                                VoiceLanguage.Detect ->
                                    stringResource(Res.string.vpttLanguageDetectDetail)
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
            is VoiceConfirm.Download -> {
                val size = sizeWords(pending.bytes)
                KitConfirmSpec(
                    title = stringResource(Res.string.voiceAutoSetupTitle),
                    body =
                        when (pending.network) {
                            SetupNetwork.Unmetered ->
                                stringResource(Res.string.e7VoiceUiPrivacyDownload)
                            SetupNetwork.Metered ->
                                stringResource(Res.string.voiceAutoSetupMobileData)
                            else -> stringResource(Res.string.voiceAutoSetupMaybeMetered)
                        },
                    confirmLabel =
                        stringResource(
                            if (pending.network == SetupNetwork.Unmetered)
                                Res.string.voiceAutoSetupDownload
                            else Res.string.voiceAutoSetupDownloadMobile,
                            size,
                        ),
                    cancelLabel = stringResource(Res.string.e7VoiceUiNotNow),
                    kind = KitConfirmKind.Neutral,
                )
            }
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
                    confirmLabel =
                        stringResource(
                            Res.string.naturalVoiceRemove,
                            sizeWords(state.natural?.bytes ?: 0),
                        ),
                    cancelLabel = stringResource(Res.string.naturalVoiceKeep),
                    kind = KitConfirmKind.Destructive,
                )
        }
    if (spec != null)
        KitConfirm(spec = spec, onConfirm = actions::confirm, onCancel = actions::dismissConfirm)
}
