package io.github.eslamasabry.oc.engine.voice.readaloud

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.audio.AudioInterruption
import io.github.eslamasabry.oc.engine.voice.audio.AudioSession
import io.github.eslamasabry.oc.engine.voice.audio.PcmPlayer
import io.github.eslamasabry.oc.engine.voice.models.InstalledPack
import io.github.eslamasabry.oc.engine.voice.models.conservativeThreadCount
import io.github.eslamasabry.oc.engine.voice.prefs.VoicePrefs
import io.github.eslamasabry.oc.engine.voice.state.ReadAloudState
import io.github.eslamasabry.oc.engine.voice.state.ReadEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One reading. [utteranceId] (`<sessionId>/voice-reply/<id>` for automatic readings) is engine bookkeeping only: it
 * never reaches the speech engine. [text] is already prose (`MarkdownProse.forSpeech`, `:core:markdown`).
 */
public data class ReadRequest(val utteranceId: String, val text: String, val voiceId: String? = null, val automatic: Boolean = false)

/**
 * Read aloud (plan 10 §8, MEDIA-VOICE-19…24; Dart `ReadAloudController` + `NaturalVoiceController`).
 *
 * - Consent once per phone (`oc.readAloudConsent`); no engine is touched before it (B-MEDIA-009, §17 S3).
 * - One reader app-wide: a new reading cancels the previous one; a stale stop cannot stop a newer reading.
 * - Foreground only: refused with `ReadAloudBusy` unless the host is resumed; leaving stops it (A6).
 * - The natural voice (Kokoro) reads when chosen, installed and the reply's script routes to a language it speaks;
 *   otherwise the phone's voice reads, and [ReadAloudState.Speaking.fallbackLanguage] carries the note once per
 *   language per app run.
 */
public class ReadAloud(
    private val audio: AudioSession,
    private val systemTts: SystemTts,
    private val synthesizer: SpeechSynthesizer,
    private val player: PcmPlayer,
    private val prefs: VoicePrefs,
    private val scope: CoroutineScope,
    /** The installed natural pack (`kokoro-en`), or null. */
    private val naturalPack: suspend () -> InstalledPack?,
    private val appLanguage: () -> String?,
    private val cpuCount: () -> Int = { 4 },
    /** `PlatformCapabilities.readAloud`. */
    private val available: Boolean = true,
    private val firstSoundWithin: Duration = 90.seconds,
    private val warmKeep: Duration = 20.seconds,
) {
    private val mutableState = MutableStateFlow<ReadAloudState>(ReadAloudState.Idle)
    public val state: StateFlow<ReadAloudState> = mutableState.asStateFlow()

    private val lane = Mutex()
    private var reading: Deferred<Outcome<Unit>>? = null
    private var foreground = true
    private var closed = false
    private val notedFallback = mutableSetOf<String>()
    private var warm: SynthesisSession? = null
    private var warmCloser: Job? = null

    public fun consentGiven(): Boolean = prefs.readAloudConsent()

    public suspend fun giveConsent(): Outcome<Unit> = prefs.setReadAloudConsent(true)

    /** The phone's offline voices; asked only after consent, never on a platform without read aloud. */
    public suspend fun voices(): Outcome<ImmutableList<TtsVoice>> {
        if (!available) return unavailable()
        if (!consentGiven()) return Failure.Refused(RefusalReason.ConsentMissing).failed()
        return systemTts.voices()
    }

    /** Reads [request] and suspends until it is spoken, stopped or failed. Cancelling the caller stops it. */
    public suspend fun read(request: ReadRequest): Outcome<Unit> {
        if (!available) return unavailable()
        if (!consentGiven()) return Failure.Refused(RefusalReason.ConsentMissing).failed()
        if (request.text.isBlank() || request.text.length > MAX_CHARS) return media(MediaProblem.ReadAloudTooLong)
        val job =
            lane.withLock {
                if (closed || !foreground) return media(MediaProblem.ReadAloudBusy)
                reading?.cancelAndJoin()
                scope.async { perform(request) }.also { reading = it }
            }
        return try {
            job.await()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) {
                job.cancel() // the caller went away: the reading stops with it
                throw e
            }
            Unit.ok() // stopped (Stop, a newer reading, leaving the app): not a failure
        }
    }

    /** Stops the current reading at once (Kokoro included); errors are swallowed. */
    public suspend fun stop() {
        val job = lane.withLock { reading.also { reading = null } } ?: return
        job.cancelAndJoin()
    }

    /** Resumed (true) or paused (false): leaving stops the reading and blocks new ones (A6). */
    public fun onHostChanged(resumed: Boolean) {
        foreground = resumed
        if (!resumed) scope.launch { stop() }
    }

    /** Dispose: a pending reading is stopped and a late start stays cancelled. */
    public suspend fun close() {
        lane.withLock { closed = true }
        stop()
        warmCloser?.cancel()
        warm?.let { withContext(NonCancellable) { it.close() } }
        warm = null
    }

    private suspend fun perform(request: ReadRequest): Outcome<Unit> {
        val natural = chooseNatural(request.text)
        val lease =
            when (val leased = audio.acquirePlayback()) {
                is Outcome.Failed -> return finish(leased)
                is Outcome.Ok -> leased.value
            }
        mutableState.value = ReadAloudState.Speaking(request.utteranceId, if (natural.pack != null) ReadEngine.Natural else ReadEngine.System, natural.fallback)
        var interruption: AudioInterruption? = null
        return try {
            val result =
                coroutineScope {
                    val speaking = async { if (natural.pack != null) speakNatural(natural.pack, request.text) else systemTts.speak(request.text, request.voiceId) }
                    val watcher =
                        launch {
                            interruption = audio.interruptions.first()
                            speaking.cancel()
                        }
                    try {
                        speaking.await()
                    } catch (e: CancellationException) {
                        if (interruption == null) throw e
                        Unit.ok()
                    } finally {
                        watcher.cancel()
                    }
                }
            when (interruption) {
                AudioInterruption.OtherRecordingStarted -> finish(media(MediaProblem.ReadAloudBusy))
                else -> finish(result)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { if (mutableState.value is ReadAloudState.Speaking) mutableState.value = ReadAloudState.Idle }
            throw e
        } finally {
            withContext(NonCancellable) { lease.release() }
        }
    }

    private fun finish(result: Outcome<Unit>): Outcome<Unit> {
        mutableState.value =
            when (result) {
                is Outcome.Ok -> ReadAloudState.Idle
                is Outcome.Failed -> ReadAloudState.Failed((result.failure as? Failure.Media)?.problem ?: MediaProblem.SpeechEngineUnavailable)
            }
        return result
    }

    private class NaturalChoice(val pack: InstalledPack?, val fallback: String?)

    private suspend fun chooseNatural(text: String): NaturalChoice {
        if (!prefs.readAloudNatural()) return NaturalChoice(null, null)
        val pack = naturalPack() ?: return NaturalChoice(null, null)
        val language = ReadLanguage.detect(text, appLanguage())
        val speaks = pack.spec.natural?.languages ?: emptySet()
        if (language in speaks) return NaturalChoice(pack, null)
        val note = if (notedFallback.add(language)) language else null
        return NaturalChoice(null, note)
    }

    private suspend fun speakNatural(pack: InstalledPack, text: String): Outcome<Unit> {
        val natural = pack.spec.natural ?: return media(MediaProblem.SpeechEngineUnavailable)
        warmCloser?.cancel()
        val session =
            warm ?: when (val opened = synthesizer.open(pack, conservativeThreadCount(cpuCount()))) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value.also { warm = it }
            }
        if (session.sampleRate != natural.sampleRate) {
            warm = null
            withContext(NonCancellable) { session.close() }
            return media(MediaProblem.SpeechEngineUnavailable)
        }
        var timedOut = false
        return try {
            coroutineScope {
                val chunks = Channel<FloatArray>(capacity = 64)
                val heard = CompletableDeferred<Unit>()
                val playing = async { player.play(session.sampleRate, chunks) }
                launch {
                    // Watchdog: the first sound within 90 s (the model load can take seconds).
                    if (withTimeoutOrNull(firstSoundWithin) { heard.await() } == null) {
                        timedOut = true
                        this@coroutineScope.cancel()
                    }
                }
                var made: Outcome<Unit> = Unit.ok()
                for (piece in SentenceSplitter.split(text)) {
                    made =
                        session.synthesize(piece, natural.speakerId, 1f) { samples ->
                            heard.complete(Unit)
                            chunks.send(samples)
                        }
                    if (made is Outcome.Failed) break
                }
                chunks.close()
                val result = if (made is Outcome.Failed) made.also { playing.cancel() } else playing.await()
                coroutineContext.cancelChildren() // the watchdog
                result
            }
        } catch (e: CancellationException) {
            if (!timedOut) throw e
            media(MediaProblem.SpeechEngineUnavailable)
        } finally {
            warmCloser =
                scope.launch {
                    delay(warmKeep)
                    warm?.close()
                    warm = null
                }
        }
    }

    private fun unavailable(): Outcome<Nothing> = Failure.Unavailable(FeatureId("readAloud")).failed()

    private fun media(problem: MediaProblem): Outcome<Nothing> = Failure.Media(problem).failed()

    public companion object {
        /** Blank or longer than 12,000 UTF-16 units → `ReadAloudTooLong` (plan 10 §8.4). */
        public const val MAX_CHARS: Int = 12_000
    }
}
