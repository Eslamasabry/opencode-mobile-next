package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiAccurate
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiAccurateDetail
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiBalanced
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiBalancedDetail
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiChecksumFailed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiCompact
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiCompactDetail
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiDownloadFailed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiDownloadTimeout
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiHttpFailed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiHttpsRequired
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiIncomplete
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiInputFailed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiInterrupted
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiInvalidRedirect
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiLengthFailed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiMicrophoneError
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiModelRequired
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNoAudio
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNoResponse
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiPermissionBlocked
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiPermissionRequired
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiTransportClosed
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiUnsafeRedirect
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiUnsupportedAbi
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiVerificationFailed
import io.github.eslamasabry.oc.ui.words.res.readAloudBusy
import io.github.eslamasabry.oc.ui.words.res.readAloudNoVoice
import io.github.eslamasabry.oc.ui.words.res.readAloudTooLong
import io.github.eslamasabry.oc.ui.words.res.readAloudUnavailable
import io.github.eslamasabry.oc.ui.words.res.readAloudUnsupported
import io.github.eslamasabry.oc.ui.words.res.voiceAutoSetupOffline
import io.github.eslamasabry.oc.ui.words.res.voiceNativeDetect
import io.github.eslamasabry.oc.ui.words.res.voiceNativeNoRecognizer
import io.github.eslamasabry.oc.ui.words.res.voiceNativeSizeMb
import io.github.eslamasabry.oc.ui.words.res.vpttLanguageFollowApp
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceFailed
import org.jetbrains.compose.resources.stringResource

/** One plain sentence per media problem (never exception text; B-L10N-024's mapping, Flutter `voiceErrorText`). */
@Composable
internal fun problemWords(problem: MediaProblem): String =
    stringResource(problemResource(problem))

private fun problemResource(problem: MediaProblem) =
    when (problem) {
        MediaProblem.MicDenied -> Res.string.e7VoiceUiPermissionRequired
        MediaProblem.MicDeniedForever -> Res.string.e7VoiceUiPermissionBlocked
        MediaProblem.MicBusy, MediaProblem.MicSilenced -> Res.string.e7VoiceUiMicrophoneError
        MediaProblem.NoMicrophone -> Res.string.voiceNativeNoRecognizer
        MediaProblem.NoAudioCaptured -> Res.string.e7VoiceUiNoAudio
        MediaProblem.RecordingInterrupted, MediaProblem.LeftTheApp -> Res.string.e7VoiceUiInterrupted
        MediaProblem.SpeechModelMissing -> Res.string.e7VoiceUiModelRequired
        MediaProblem.SpeechModelBroken -> Res.string.e7VoiceUiVerificationFailed
        MediaProblem.ChunkNotTranscribed -> Res.string.e7VoiceUiInputFailed
        MediaProblem.PackAbiUnsupported -> Res.string.e7VoiceUiUnsupportedAbi
        MediaProblem.DownloadOffline -> Res.string.voiceAutoSetupOffline
        MediaProblem.DownloadHttpsRequired -> Res.string.e7VoiceUiHttpsRequired
        MediaProblem.DownloadClosed -> Res.string.e7VoiceUiTransportClosed
        MediaProblem.DownloadRedirect -> Res.string.e7VoiceUiInvalidRedirect
        MediaProblem.DownloadUnsafeRedirect -> Res.string.e7VoiceUiUnsafeRedirect
        MediaProblem.DownloadNoResponse -> Res.string.e7VoiceUiNoResponse
        MediaProblem.DownloadTimedOut -> Res.string.e7VoiceUiDownloadTimeout
        MediaProblem.DownloadChecksum -> Res.string.e7VoiceUiChecksumFailed
        MediaProblem.DownloadVerification -> Res.string.e7VoiceUiVerificationFailed
        MediaProblem.DownloadHttp -> Res.string.e7VoiceUiHttpFailed
        MediaProblem.DownloadLength -> Res.string.e7VoiceUiLengthFailed
        MediaProblem.DownloadIncomplete -> Res.string.e7VoiceUiIncomplete
        MediaProblem.ReadAloudUnsupported -> Res.string.readAloudUnsupported
        MediaProblem.NoOfflineVoice -> Res.string.readAloudNoVoice
        MediaProblem.SpeechEngineUnavailable -> Res.string.readAloudUnavailable
        MediaProblem.ReadAloudTooLong -> Res.string.readAloudTooLong
        MediaProblem.ReadAloudBusy -> Res.string.readAloudBusy
        MediaProblem.DownloadNoSpace, MediaProblem.PackStorageTooSmall, MediaProblem.PackMemoryTooSmall,
        MediaProblem.MemoryUnknown, MediaProblem.DownloadBusy,
        -> Res.string.e7VoiceUiDownloadFailed
        else -> Res.string.naturalVoiceFailed
    }

/** Balanced / High accuracy / Compact (Flutter `voicePackLabel`). */
@Composable
internal fun packName(pack: PackId): String =
    stringResource(
        when (pack.raw) {
            "small" -> Res.string.e7VoiceUiAccurate
            "tiny" -> Res.string.e7VoiceUiCompact
            else -> Res.string.e7VoiceUiBalanced
        },
    )

@Composable
internal fun packDetail(pack: PackId): String =
    stringResource(
        when (pack.raw) {
            "small" -> Res.string.e7VoiceUiAccurateDetail
            "tiny" -> Res.string.e7VoiceUiCompactDetail
            else -> Res.string.e7VoiceUiBalancedDetail
        },
    )

/** Decimal megabytes, rounded ("142 MB"; plan 10 §7.8 sizes). */
@Composable
internal fun sizeWords(bytes: Long): String = stringResource(Res.string.voiceNativeSizeMb, ((bytes + 500_000) / 1_000_000).toInt())

/** The language's own name; the two choices that are not a language are words. */
@Composable
internal fun languageName(language: VoiceLanguage): String =
    when (language) {
        VoiceLanguage.Auto -> stringResource(Res.string.vpttLanguageFollowApp)
        VoiceLanguage.Detect -> stringResource(Res.string.voiceNativeDetect)
        else -> language.label
    }

/** "0:07" (m:ss), digits as the locale writes them through the kit's pattern. */
internal fun elapsedParts(seconds: Int): Pair<String, String> = (seconds / 60).toString() to (seconds % 60).toString().padStart(2, '0')
