package io.github.eslamasabry.oc.engine.voice.dictation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.AudioCapture
import io.github.eslamasabry.oc.engine.voice.audio.AudioInterruption
import io.github.eslamasabry.oc.engine.voice.audio.AudioSession
import io.github.eslamasabry.oc.engine.voice.audio.CaptureEvent
import io.github.eslamasabry.oc.engine.voice.audio.PlaybackLease
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbe
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.models.MemoryNow
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.state.PackId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow

internal class FakeAudioSession : AudioSession {
    var allowCapture = true
    var beforeCalls = 0
    var endCalls = 0
    var playbackBusy = false
    var leases = 0
    var released = 0
    val events = MutableSharedFlow<AudioInterruption>(extraBufferCapacity = 8)

    override suspend fun beforeCapture(): Boolean {
        beforeCalls++
        return allowCapture
    }

    override suspend fun endCapture() {
        endCalls++
    }

    override suspend fun acquirePlayback(): Outcome<PlaybackLease> {
        if (playbackBusy) return Failure.Media(MediaProblem.ReadAloudBusy).failed()
        leases++
        return PlaybackLease { released++ }.ok()
    }

    override val interruptions: SharedFlow<AudioInterruption> = events
}

internal class FakeCapture : AudioCapture {
    var starts = 0
    var stops = 0
    private var channel: Channel<CaptureEvent>? = null

    override fun frames(): Flow<CaptureEvent> =
        flow {
            starts++
            val events = Channel<CaptureEvent>(Channel.UNLIMITED)
            channel = events
            try {
                for (event in events) emit(event)
            } finally {
                stops++
            }
        }

    fun send(event: CaptureEvent) {
        check(channel!!.trySend(event).isSuccess)
    }

    /** [seconds] of audio at [level] (RMS), one event per second. */
    fun speak(seconds: Int, level: Float = 0.2f) = repeat(seconds) { send(CaptureEvent.Frame(pcm(32_000, level))) }

    fun end() = channel!!.close()

    fun fail(error: Throwable) = channel!!.close(error)
}

/** Constant-amplitude PCM16 whose RMS is [level]. */
internal fun pcm(bytes: Int, level: Float): ByteArray {
    val value = (level * 32767).toInt()
    return ByteArray(bytes) { i -> if (i % 2 == 0) (value and 0xff).toByte() else ((value shr 8) and 0xff).toByte() }
}

internal class FakeRecognizer : SpeechRecognizer {
    var opens = 0
    var closes = 0
    val languages = mutableListOf<String>()
    val threads = mutableListOf<Int>()
    var openFails = false
    var openGate: CompletableDeferred<Unit>? = null
    var calls = 0
    val sampleCounts = mutableListOf<Int>()
    var result: suspend (index: Int) -> Outcome<String> = { "w${it + 1}".ok() }

    override suspend fun open(pack: InstalledPack, languageCode: String, threads: Int): Outcome<RecognizerSession> {
        openGate?.await()
        if (openFails) return Failure.Media(MediaProblem.SpeechModelBroken).failed()
        opens++
        languages += languageCode
        this.threads += threads
        return object : RecognizerSession {
            override suspend fun transcribe(samples: FloatArray): Outcome<String> {
                val index = calls++
                sampleCounts += samples.size
                return result(index)
            }

            override suspend fun close() {
                closes++
            }
        }.ok()
    }
}

internal class FakePackSource(
    var ready: InstalledPack? = InstalledPack(ModelCatalog.base, "/data/voice_models/base"),
    var ramMb: Long? = 8_000,
    var cpus: Int = 8,
    override var language: VoiceLanguage = VoiceLanguage.Auto,
) : SpeechPackSource {
    var verifies = 0

    override suspend fun readySpeechPack(): InstalledPack? = ready

    override suspend fun verifyFully(pack: PackId): Boolean {
        verifies++
        return false
    }

    override suspend fun device(): DeviceProbeResult =
        DeviceProbeResult.Known(ramMb, null, persistentListOf("arm64-v8a"), true, false, 256, cpus)
}

internal class FakeMemoryProbe(var memory: MemoryNow? = MemoryNow(availMb = 4_000, thresholdMb = 200, low = false)) : DeviceProbe {
    override suspend fun probe(): DeviceProbeResult = DeviceProbeResult.Unknown

    override fun memoryNow(): MemoryNow? = memory
}
