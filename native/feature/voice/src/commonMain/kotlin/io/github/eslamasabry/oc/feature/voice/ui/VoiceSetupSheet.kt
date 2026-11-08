package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupStage
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiCancelDownload
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiDownloadProgress
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNotNow
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiPermissionBlocked
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiRetry
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiStartListening
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiVerifying
import io.github.eslamasabry.oc.ui.words.res.voiceAllowMicInSettings
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupBusy
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupChecking
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupChooseModel
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupDownload
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupDownloadMobile
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupMaybeMetered
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupMobileData
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupNoCapture
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupOffer
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupOffline
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupOtherModel
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupPicked
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupReady
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupShowDownload
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupStartsAfter
import io.github.eslamasabry.oc.ui.words.res.voiceComponentTitle
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupUnknownMemory
import org.jetbrains.compose.resources.stringResource

/**
 * The first-mic "Voice typing" sheet (Flutter `_VoiceAutomaticSetupBody`): checking, then the offer of the model
 * picked for this phone with its data cost, the download with Cancel, "The speech model is on this phone" with Start
 * listening, or what stopped it with the one way forward. Listening starting closes it; the composer's own voice
 * surface takes the recording.
 */
@Composable
internal fun VoiceSetupSheet(stage: VoiceSetupStage?, presenter: ComposerVoicePresenter) {
    val notNow = KitAction(stringResource(Res.string.e7VoiceUiNotNow), presenter::closeSetup)
    val retry = KitAction(stringResource(Res.string.e7VoiceUiRetry), presenter::setupRetry, icon = KitIcons.retry)
    var primary: KitAction? = null
    var secondary: KitAction? = notNow
    val tertiary = mutableListOf<KitAction>()
    val body: @Composable () -> Unit
    when (stage) {
        null,
        VoiceSetupStage.Idle,
        VoiceSetupStage.Checking,
        VoiceSetupStage.AskingNotifications,
        VoiceSetupStage.Listening -> {
            body = { KitProgressView(KitProgress.Waiting(stringResource(Res.string.voiceAutoSetupChecking))) }
        }
        is VoiceSetupStage.NeedsConsent -> {
            val unmetered = stage.network == SetupNetwork.Unmetered
            val size = sizeWords(stage.bytesLeft)
            body = {
                KitText(stringResource(Res.string.voiceAutoSetupOffer), role = TypeRole.Body)
                Spacer(Modifier.height(Space.S2.dp))
                KitText(
                    stringResource(Res.string.voiceAutoSetupPicked, packName(stage.pack)),
                    Modifier.testTag("voice-auto-setup-pack"),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
                if (!unmetered) {
                    Spacer(Modifier.height(Space.S3.dp))
                    KitNotice(
                        stringResource(
                            if (stage.network == SetupNetwork.Metered) Res.string.voiceAutoSetupMobileData
                            else Res.string.voiceAutoSetupMaybeMetered
                        ),
                        icon = KitIcons.warning,
                    )
                }
            }
            primary =
                KitAction(
                    stringResource(
                        if (unmetered) Res.string.voiceAutoSetupDownload else Res.string.voiceAutoSetupDownloadMobile,
                        size,
                    ),
                    presenter::setupDownload,
                    icon = KitIcons.download,
                )
            tertiary +=
                KitAction(
                    stringResource(Res.string.voiceAutoSetupOtherModel),
                    presenter::setupOpenSettings,
                    icon = KitIcons.settingsAdvanced,
                )
        }
        is VoiceSetupStage.Downloading -> {
            val fraction = if (stage.total > 0) stage.received.toFloat() / stage.total else null
            body = {
                KitProgressView(
                    if (fraction == null) KitProgress.Waiting(stringResource(Res.string.e7VoiceUiVerifying))
                    else
                        KitProgress.Known(
                            fraction.coerceIn(0f, 1f),
                            caption =
                                stringResource(
                                    Res.string.e7VoiceUiDownloadProgress,
                                    sizeWords(stage.received),
                                    sizeWords(stage.total),
                                ),
                        )
                )
                Spacer(Modifier.height(Space.S3.dp))
                KitText(
                    stringResource(Res.string.voiceAutoSetupStartsAfter),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
            secondary = KitAction(stringResource(Res.string.e7VoiceUiCancelDownload), presenter::closeSetup)
        }
        VoiceSetupStage.Verifying -> {
            body = {
                KitProgressView(KitProgress.Waiting(stringResource(Res.string.e7VoiceUiVerifying)))
                Spacer(Modifier.height(Space.S3.dp))
                KitText(
                    stringResource(Res.string.voiceAutoSetupStartsAfter),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
            secondary = null
        }
        is VoiceSetupStage.Ready -> {
            body = { KitNotice(stringResource(Res.string.voiceAutoSetupReady), tone = StatusTone.Ok) }
            primary =
                KitAction(stringResource(Res.string.e7VoiceUiStartListening), presenter::setupListen, icon = KitIcons.mic)
        }
        is VoiceSetupStage.Problem -> {
            val (message, action) = problem(stage, presenter, retry)
            body = {
                KitNotice(
                    message,
                    Modifier.testTag("voice-auto-setup-problem"),
                    tone = if (stage.problem.blocked) StatusTone.Neutral else StatusTone.Failure,
                )
            }
            primary = action
        }
    }
    KitSheet(
        visible = stage != null,
        onDismissRequest = presenter::closeSetup,
        title = stringResource(Res.string.voiceComponentTitle),
        icon = KitIcons.mic,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
    ) {
        body()
    }
}

/** A setup that cannot go on here, as opposed to one that failed and can be retried (Flutter `blocked`). */
private val MediaProblem.blocked: Boolean
    get() =
        this == MediaProblem.MemoryUnknown ||
            this == MediaProblem.NoMicrophone ||
            this == MediaProblem.PackMemoryTooSmall ||
            this == MediaProblem.PackAbiUnsupported

/** What stopped setup, in plain words, and the one way forward (Flutter `_problem`). */
@Composable
private fun problem(
    stage: VoiceSetupStage.Problem,
    presenter: ComposerVoicePresenter,
    retry: KitAction,
): Pair<String, KitAction?> =
    when (stage.problem) {
        MediaProblem.MemoryUnknown ->
            stringResource(Res.string.voiceAutoSetupUnknownMemory) to
                KitAction(
                    stringResource(Res.string.voiceAutoSetupChooseModel),
                    presenter::setupOpenSettings,
                    icon = KitIcons.settingsAdvanced,
                )
        MediaProblem.NoMicrophone -> stringResource(Res.string.voiceAutoSetupNoCapture) to null
        MediaProblem.PackMemoryTooSmall,
        MediaProblem.PackAbiUnsupported -> problemWords(stage.problem) to null
        MediaProblem.DownloadOffline -> stringResource(Res.string.voiceAutoSetupOffline) to retry
        MediaProblem.DownloadBusy ->
            stringResource(Res.string.voiceAutoSetupBusy) to
                KitAction(stringResource(Res.string.voiceAutoSetupShowDownload), presenter::setupOpenSettings)
        MediaProblem.MicDeniedForever ->
            stringResource(Res.string.e7VoiceUiPermissionBlocked) to
                KitAction(
                    stringResource(Res.string.voiceAllowMicInSettings),
                    presenter::setupAllowMic,
                    icon = KitIcons.settings,
                )
        else -> problemWords(stage.problem) to retry
    }
