package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.models.ModelPacks
import io.github.eslamasabry.oc.engine.voice.state.PackId

/** Plan 10 §4.5. Android: `SherpaSpeechRecognizer` (sherpa-onnx Whisper, one decode at a time app-wide). */
public interface SpeechRecognizer {
    /** [languageCode] is Whisper's code; "" = detect from speech. Failure: `Media(SpeechModelBroken)`. */
    public suspend fun open(pack: InstalledPack, languageCode: String, threads: Int): Outcome<RecognizerSession>
}

public interface RecognizerSession {
    /** One ≤ 30 s chunk of 16 kHz float samples. Cancellation stops waiting; a decode inside JNI finishes, discarded. */
    public suspend fun transcribe(samples: FloatArray): Outcome<String>

    public suspend fun close()
}

/** The three-state answer (plan 10 §6.8), owned by `:android:platform`'s `PermissionGate` on Android. */
public enum class MicPermissionStatus { Granted, Denied, PermanentlyDenied }

public fun interface MicPermission {
    /** Asks when needed (with POST_NOTIFICATIONS in the same dialog on Android 13+). Off Android: PermanentlyDenied. */
    public suspend fun request(): MicPermissionStatus

    public companion object {
        /** B-MEDIA-032: a platform without capture never claims a grant. */
        public val Refused: MicPermission = MicPermission { MicPermissionStatus.PermanentlyDenied }
    }
}

/** A capture flow failing with a known cause (`SecurityException` → MicDenied, device busy → MicBusy). */
public class CaptureFailed(public val problem: MediaProblem) : RuntimeException(problem.name)

/** What the controller needs from the model manager (a seam so the controller tests need no files). */
public interface SpeechPackSource {
    /** The selected pack when installed and allowed by this phone's RAM. */
    public suspend fun readySpeechPack(): InstalledPack?

    public suspend fun verifyFully(pack: PackId): Boolean

    public suspend fun device(): DeviceProbeResult

    public val language: VoiceLanguage
}

public fun ModelPacks.asSpeechPackSource(): SpeechPackSource =
    object : SpeechPackSource {
        override suspend fun readySpeechPack(): InstalledPack? = this@asSpeechPackSource.readySpeechPack()

        override suspend fun verifyFully(pack: PackId): Boolean = this@asSpeechPackSource.verifyFully(pack)

        override suspend fun device(): DeviceProbeResult = this@asSpeechPackSource.device()

        override val language: VoiceLanguage get() = state.value.language
    }
