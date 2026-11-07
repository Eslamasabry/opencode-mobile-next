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
import androidx.compose.runtime.LaunchedEffect
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
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
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
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupMobileData
import io.github.eslamasabry.oc.ui.words.res.voiceInputUnavailable
import io.github.eslamasabry.oc.ui.words.res.voiceNativeNaturalNeedsEngine
import io.github.eslamasabry.oc.ui.words.res.voiceNativePartial
import io.github.eslamasabry.oc.ui.words.res.voiceNativePhoneEngine
import io.github.eslamasabry.oc.ui.words.res.voiceSetupBusyReason
import io.github.eslamasabry.oc.ui.words.res.voiceSetupDeletePack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupDownloadSize
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
                    // E20-S08: the bottom block already keeps the page's gutter (a second one set the button in
                    // further than the page), and the button is one line: the model's name is the selected row's.
                    KitActionBlock(
                        Modifier.testTag("voice-model-primary-action"),
                        primary =
                            KitAction(
                                stringResource(Res.string.voiceSetupDownloadSize, sizeWords(row.bytes)),
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
        // One rail: rows and groups bring their own 16 dp gutter, so the column adds none and every other part pads
        // itself by the same gutter (owner 2026-11-07: the cards sat a second gutter further in than the labels).
        val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
        val scroll = rememberScrollState()
        // A download started from the pinned button, or a problem that just arrived, shows at the top: bring it
        // into view instead of leaving the person scrolled down among locked choices.
        val headline = state.busy || state.problem != null
        LaunchedEffect(headline) { if (headline) scroll.animateScrollTo(0) }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = Space.S2.dp, bottom = Space.S6.dp)
                .testTag("voice-settings-list")
        ) {
            if (!state.available) {
                KitNotice(
                    stringResource(Res.string.voiceInputUnavailable),
                    modifier = gutter,
                    tone = StatusTone.Failure,
                )
                return@Column
            }
            KitText(
                stringResource(Res.string.voiceSetupSubtitle),
                gutter,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(Space.S4.dp))
            // What is happening comes first (STATE-1): the running download or why it stopped.
            if (state.busy) {
                DownloadProgress(state, actions, gutter)
                Spacer(Modifier.height(Space.S4.dp))
            }
            state.problem?.let { problem ->
                DownloadProblem(problem, state.retryPack != null, actions, gutter)
                Spacer(Modifier.height(Space.S3.dp))
            }
            KitNotice(
                message =
                    stringResource(
                        if (state.engine == SpeechEngineKind.PhoneRecognizer)
                            Res.string.voiceNativePhoneEngine
                        else Res.string.e7VoiceUiPrivacyDownload
                    ),
                modifier = gutter,
                icon = KitIcons.locked,
                liveRegion = false,
            )
            if (!state.hasMicrophone) {
                Spacer(Modifier.height(Space.S3.dp))
                KitNotice(stringResource(Res.string.e7VoiceUiNoBuiltInMic), modifier = gutter)
            }
            Models(state, actions)
            Language(state, actions, gutter)
            Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
            KitRowGroup {
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

/** Why the last download stopped: plain words, Try again, and the problem code under Details (never raw text). */
@Composable
private fun DownloadProblem(
    problem: io.github.eslamasabry.oc.core.common.MediaProblem,
    canRetry: Boolean,
    actions: VoiceSettingsPresenter,
    gutter: Modifier,
) {
    Column(gutter.testTag("voice-download-problem")) {
        KitNotice(
            title = stringResource(Res.string.voiceDownloadStopped).takeIf { canRetry },
            message =
                if (canRetry) problemWords(problem)
                else stringResource(Res.string.e7VoiceUiSetupFailed, problemWords(problem)),
            tone = StatusTone.Failure,
            actions =
                if (canRetry)
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.e7VoiceUiRetry),
                            actions::retry,
                            testTag = "voice-download-retry",
                        )
                    )
                else emptyList(),
            dismiss = KitLineAction(stringResource(Res.string.e7VoiceUiNotNow), actions::dismissProblem),
        )
        KitDetailsFold(
            label = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            notes = listOf(stringResource(Res.string.voiceDownloadDetailCode, problem.name)),
        )
    }
}

@Composable
private fun Language(state: VoiceSettingsUi, actions: VoiceSettingsPresenter, gutter: Modifier) {
    KitSectionLabel(stringResource(Res.string.e7VoiceUiLanguage), gapBefore = LocalKitTokens.current.sectionGap.dp)
    val sourceLanguages = listOf(VoiceLanguage.Detect, VoiceLanguage.English, VoiceLanguage.Arabic)
    val shownLanguages =
        if (state.language in sourceLanguages) sourceLanguages else sourceLanguages + state.language
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
            if (state.busy) KitEnabled.Disabled(stringResource(Res.string.voiceSetupBusyChoice))
            else KitEnabled.Enabled,
        // E20-S08: the model list's note above already says it once; TalkBack still hears it here.
        reasonShown = false,
        onChange = actions::chooseLanguage,
        modifier = gutter.testTag("voice-model-language"),
    )
    // The rest of the languages are one row away: a row that opens the list, not a loose link.
    if (!state.busy) {
        Spacer(Modifier.height(Space.S2.dp)) // panel styles: the row's card must not touch the segmented track
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.rb51VoiceMoreLanguages),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::openLanguages,
                modifier = Modifier.testTag("voice-more-languages"),
            )
        }
    }
}

@Composable
private fun DownloadProgress(state: VoiceSettingsUi, actions: VoiceSettingsPresenter, gutter: Modifier) {
    val job = state.job ?: return
    val natural = state.natural?.downloading == true
    val fraction = job.fraction
    // Nothing has arrived yet: a moving bar and "Starting the download", never a still grey track at 0 MB.
    val started = !job.verifying && fraction != null && job.received > 0
    val percent = kotlin.math.floor((fraction ?: 0f) * 100).toInt()
    val announcement =
        if (job.verifying) stringResource(Res.string.e7VoiceUiVerifying)
        else if (!started) stringResource(Res.string.voiceDownloadStarting)
        else
            stringResource(
                Res.string.e7VoiceUiDownloadPercent,
                kotlin.math.floor((fraction ?: 0f) * 10).toInt() * 10,
            ) // F: lib/voice/voice_ui.dart:363
    Column(
        gutter.testTag("voice-download-progress"),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
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
                    when {
                        job.verifying -> KitProgress.Waiting()
                        !started -> KitProgress.Waiting(stringResource(Res.string.voiceDownloadStarting))
                        else ->
                            KitProgress.Known(
                                fraction!!.coerceIn(0f, 1f),
                                caption =
                                    stringResource(
                                        Res.string.voiceDownloadProgressPercent,
                                        sizeWords(job.received),
                                        sizeWords(job.total),
                                        percent,
                                    ),
                            )
                    }
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
    // While a download runs the models are locked for one reason, said once under the list.
    KitRowGroup(
        label = stringResource(Res.string.voiceSetupModelLabel),
        note = if (state.busy) stringResource(Res.string.voiceSetupBusyChoice) else null,
        section = true,
    ) {
        KitChoiceList(
            choices = state.packs.map { row -> packChoice(row, state.busy) },
            selected = state.selected,
            semanticsLabel = stringResource(Res.string.voiceSetupModelLabel),
            loading = state.loading,
            empty = {},
            onSelect = actions::choose,
        )
    }
    val row = state.selectedRow ?: return
    if (state.busy) return
    val name = packName(row.id)
    val size = sizeWords(row.bytes)
    if (row.installed) {
        Column(
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
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
                busy -> KitEnabled.Disabled("") // the group's note says why
                else -> KitEnabled.Enabled
            },
    )
}

@Composable
private fun unsupportedReason(row: PackRowUi): String {
    val problem =
        (row.status as? PackStatus.Unsupported)?.problem
            ?: return stringResource(Res.string.voiceInputUnavailable)
    // Too little memory is a fact about this phone, not a failed download: say what it needs and what it has.
    val phone = row.phoneRamMb
    if (problem == io.github.eslamasabry.oc.core.common.MediaProblem.PackMemoryTooSmall && phone != null)
        return stringResource(Res.string.voiceAutoSetupDetailMemoryValue, row.needsRamMb.toInt(), phone.toInt())
    return problemWords(problem)
}

@Composable
private fun ReadAloudVoice(state: VoiceSettingsUi, actions: VoiceSettingsPresenter) {
    val natural = state.natural ?: return
    KitRowGroup(
        label = stringResource(Res.string.readAloudVoiceTitle),
        // E20-S08: the busy caption is said once, under the model list; this list was its second copy.
        section = true,
    ) {
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
                                // Blank: the model list's note says why, once for the page.
                                state.busy -> KitEnabled.Disabled("")
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
            modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
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
                    title =
                        if (pending.pack == NATURAL)
                            stringResource(Res.string.naturalVoiceDownloadTitle)
                        else stringResource(Res.string.voiceDownloadPackTitle, packName(pending.pack)),
                    // Only a network Android calls metered is "mobile data"; one it can't vouch for says so.
                    body =
                        when (pending.network) {
                            SetupNetwork.Metered ->
                                stringResource(Res.string.voiceAutoSetupMobileData)
                            SetupNetwork.Unmetered ->
                                stringResource(Res.string.e7VoiceUiPrivacyDownload)
                            else -> stringResource(Res.string.voiceDownloadNetworkUnknown)
                        },
                    confirmLabel =
                        stringResource(
                            if (pending.network == SetupNetwork.Metered)
                                Res.string.voiceAutoSetupDownloadMobile
                            else Res.string.voiceAutoSetupDownload,
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

/** The natural voice's pack id (the confirm names it differently from a speech model). */
private val NATURAL = io.github.eslamasabry.oc.engine.voice.models.ModelCatalog.kokoroEn.id
