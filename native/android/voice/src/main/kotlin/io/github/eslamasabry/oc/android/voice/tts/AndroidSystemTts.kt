package io.github.eslamasabry.oc.android.voice.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadAloud
import io.github.eslamasabry.oc.engine.voice.readaloud.SystemTts
import io.github.eslamasabry.oc.engine.voice.readaloud.TtsVoice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/** A phone voice as the rules see it (pure, so the offline filter and the default choice are tested on the JVM). */
public data class VoiceInfo(val name: String, val localeTag: String, val needsNetwork: Boolean, val notInstalled: Boolean)

/** The pure rules of `ReadAloudBridge.kt` (plan 10 §8.4). */
public object SystemTtsRules {
    /** Offline only: no network voice and no voice whose data is not installed; sorted by name; id = label = name. */
    public fun offline(voices: List<VoiceInfo>): List<TtsVoice> =
        voices.filter { !it.needsNetwork && !it.notInstalled }.sortedBy { it.name }.map { TtsVoice(it.name, it.name, it.localeTag) }

    /** No voice id given: the engine default → the device locale → the same language → the first offline voice. */
    public fun defaultVoice(offline: List<TtsVoice>, engineDefault: String?, deviceLocaleTag: String): TtsVoice? {
        offline.firstOrNull { it.id == engineDefault }?.let { return it }
        val wanted = deviceLocaleTag.lowercase().replace('_', '-')
        offline.firstOrNull { it.localeTag.lowercase().replace('_', '-') == wanted }?.let { return it }
        val language = wanted.substringBefore('-')
        offline.firstOrNull { it.localeTag.lowercase().replace('_', '-').substringBefore('-') == language }?.let { return it }
        return offline.firstOrNull()
    }

    /** Pieces of at most [max] UTF-16 units that never split a surrogate pair, spoken one after another. */
    public fun chunks(text: String, max: Int): List<String> {
        require(max >= 2)
        val pieces = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + max, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            pieces += text.substring(start, end)
            start = end
        }
        return pieces
    }
}

/**
 * The phone's text-to-speech (port of `ReadAloudBridge.kt`, plan 10 §8.4). The engine is created lazily on the first
 * [voices]/[speak] — the ReadAloud engine calls neither before consent — with an 8 s init timeout; a second request
 * during init is `ReadAloudBusy`. Each chunk has a 300 s watchdog. Text is never logged or kept; cancelling a [speak]
 * stops the engine. All `TextToSpeech` calls run on [main].
 */
public class AndroidSystemTts(
    private val context: Context,
    private val main: CoroutineDispatcher,
    private val newEngine: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = { c, l -> TextToSpeech(c, l) },
) : SystemTts {
    private var engine: TextToSpeech? = null
    private var initializing = false
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Outcome<Unit>>>()
    private val ids = AtomicLong()

    override suspend fun voices(): Outcome<ImmutableList<TtsVoice>> =
        withContext(main) {
            val tts =
                when (val ready = engine()) {
                    is Outcome.Failed -> return@withContext ready
                    is Outcome.Ok -> ready.value
                }
            val offline = SystemTtsRules.offline(infos(tts))
            if (offline.isEmpty()) media(MediaProblem.NoOfflineVoice) else offline.toImmutableList().ok()
        }

    override suspend fun speak(text: String, voiceId: String?): Outcome<Unit> {
        if (text.isBlank() || text.length > ReadAloud.MAX_CHARS) return media(MediaProblem.ReadAloudTooLong)
        val tts =
            withContext(main) {
                when (val ready = engine()) {
                    is Outcome.Failed -> return@withContext null
                    is Outcome.Ok -> ready.value
                }
            } ?: return media(MediaProblem.SpeechEngineUnavailable)
        val chosen =
            withContext(main) {
                val all = tts.voices.orEmpty().toList()
                val offline = SystemTtsRules.offline(all.map(::info))
                if (offline.isEmpty()) return@withContext null
                val pick = offline.firstOrNull { it.id == voiceId } ?: SystemTtsRules.defaultVoice(offline, tts.defaultVoice?.name, Locale.getDefault().toLanguageTag())
                all.firstOrNull { it.name == pick?.id }?.let { tts.voice = it }
                pick
            } ?: return media(MediaProblem.NoOfflineVoice)
        check(chosen.id.isNotEmpty())
        val max = minOf(TextToSpeech.getMaxSpeechInputLength(), 3000)
        try {
            for (piece in SystemTtsRules.chunks(text, max)) {
                val id = "oc-tts-${ids.incrementAndGet()}"
                val done = CompletableDeferred<Outcome<Unit>>()
                pending[id] = done
                val queued = withContext(main) { tts.speak(piece, TextToSpeech.QUEUE_FLUSH, null, id) }
                if (queued != TextToSpeech.SUCCESS) {
                    pending.remove(id)
                    return media(MediaProblem.SpeechEngineUnavailable)
                }
                val result = withTimeoutOrNull(300.seconds) { done.await() } ?: media(MediaProblem.SpeechEngineUnavailable)
                pending.remove(id)
                if (result is Outcome.Failed) {
                    withContext(NonCancellable + main) { tts.stop() }
                    return result
                }
            }
            return Unit.ok()
        } catch (e: CancellationException) {
            pending.clear() // releases the closures that held text
            withContext(NonCancellable + main) { tts.stop() }
            throw e
        }
    }

    /** `beforeCapture()` releases the engine when nothing plays (A1); the next reading creates it again. */
    public suspend fun releaseIfIdle() {
        withContext(main) {
            if (pending.isEmpty() && !initializing) {
                engine?.shutdown()
                engine = null
            }
        }
    }

    private suspend fun engine(): Outcome<TextToSpeech> {
        engine?.let { return it.ok() }
        if (initializing) return media(MediaProblem.ReadAloudBusy)
        initializing = true
        val ready = CompletableDeferred<Boolean>()
        val created =
            try {
                newEngine(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
            } catch (_: RuntimeException) {
                initializing = false
                return media(MediaProblem.SpeechEngineUnavailable)
            }
        val ok = withTimeoutOrNull(8.seconds) { ready.await() } ?: false
        initializing = false
        if (!ok) {
            created.shutdown()
            return media(MediaProblem.SpeechEngineUnavailable)
        }
        created.setOnUtteranceProgressListener(listener)
        engine = created
        return created.ok()
    }

    private val listener =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pending[it]?.complete(Unit.ok()) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending[it]?.complete(media(MediaProblem.SpeechEngineUnavailable)) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { pending[it]?.complete(media(MediaProblem.SpeechEngineUnavailable)) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // An unknown terminal status never leaves the UI "speaking".
                utteranceId?.let { pending[it]?.complete(media(MediaProblem.SpeechEngineUnavailable)) }
            }
        }

    private fun infos(tts: TextToSpeech): List<VoiceInfo> = tts.voices.orEmpty().map(::info)

    private fun info(voice: Voice): VoiceInfo =
        VoiceInfo(
            name = voice.name,
            localeTag = voice.locale.toLanguageTag(),
            needsNetwork = voice.isNetworkConnectionRequired,
            notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in voice.features.orEmpty(),
        )

    private companion object {
        fun media(problem: MediaProblem): Outcome<Nothing> = Failure.Media(problem).failed()
    }
}
