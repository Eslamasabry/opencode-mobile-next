package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

/** Lazy, foreground-only system TTS. Never logs text or engine diagnostics.
 * Offline voice metadata is an engine declaration, not a network sandbox.
 * All mutable state and channel callbacks are confined to the main thread.
 */
class ReadAloudBridge(private val activity: Activity, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "oc/read-aloud")
    private val handler = Handler(Looper.getMainLooper())
    private val audio = activity.getSystemService(AudioManager::class.java)
    private var engine: TextToSpeech? = null
    private var ready = false
    private var destroyed = false
    private var foreground = false
    private var initGeneration = 0
    private var pending: MethodChannel.Result? = null
    private var pendingAction: (() -> Unit)? = null
    private var initTimeout: Runnable? = null
    private var operation: String? = null
    private var accepted = false
    private var chunks = emptyList<String>()
    private var chunkIndex = 0
    private var utterance: String? = null
    private var utteranceSequence = 0L
    private var monitors = false
    private var micGuardUntil = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var focusGeneration = 0L
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            // Conservative route-loss interruption, without adding a receiver.
            if (removedDevices.any { it.isSink }) cancel()
        }
    }
    private val recordings = if (Build.VERSION.SDK_INT >= 24) {
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                if (configs.isNotEmpty()) cancel("busy")
            }
        }
    } else null
    private val playbackTimeout = Runnable { cancel("engineUnavailable") }

    init { channel.setMethodCallHandler(::handle) }

    fun resume() { foreground = true }

    fun pause() {
        foreground = false
        cancel()
        // Also cancel lazy initialization, including a pending voices query.
        abortInit("busy")
    }

    /** Called before the existing permission result can allow recorder.start. */
    fun beforeMicrophoneCapture(): Boolean {
        micGuardUntil = SystemClock.elapsedRealtime() + 10000
        val stopped = cancel("busy")
        abortInit("busy")
        if (!stopped) releaseEngine()
        return stopped
    }

    fun dispose() {
        if (destroyed) return
        pause()
        destroyed = true
        releaseEngine()
        channel.setMethodCallHandler(null)
    }

    private fun error(result: MethodChannel.Result, code: String) {
        result.error(code, "Read-aloud is unavailable.", null)
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        if (destroyed) { error(result, "engineUnavailable"); return }
        if (call.method == "stop") {
            val id = (call.arguments as? Map<*, *>)?.get("operationID") as? String
            if (id != null && id == operation) cancel()
            result.success(null)
            return
        }
        if (call.method != "voices" && call.method != "speak") {
            result.notImplemented(); return
        }
        if (!foreground) { error(result, "busy"); return }
        if (call.method == "voices") {
            withEngine(result) {
                try {
                    val voices = offlineVoices()
                    if (voices.isEmpty()) error(result, "noOfflineVoice")
                    else result.success(voices.map {
                        mapOf("id" to it.name, "label" to it.name,
                            "locale" to it.locale.toLanguageTag())
                    })
                } catch (_: Exception) { error(result, "engineUnavailable") }
            }
            return
        }
        val args = call.arguments as? Map<*, *>
        val id = args?.get("operationID") as? String
        val text = args?.get("text") as? String
        val selected = args?.get("voiceID")
        if (id.isNullOrBlank() || id.length > 128 || (selected != null && selected !is String)) {
            error(result, "engineUnavailable"); return
        }
        if (text.isNullOrBlank() || text.length > 12000) {
            error(result, "tooLong"); return
        }
        if (captureActive()) { error(result, "busy"); return }
        cancel()
        // A voices request owns initialization until its bounded response.
        if (pending != null) { error(result, "busy"); return }
        operation = id
        withEngine(result) {
            if (operation != id || !foreground) { error(result, "busy"); return@withEngine }
            try {
                if (captureActive()) {
                    cancel("busy"); error(result, "busy"); return@withEngine
                }
                val tts = engine ?: throw IllegalStateException()
                val voices = offlineVoices()
                val voice = if (selected is String) voices.firstOrNull { it.name == selected }
                    else voices.firstOrNull { it.name == tts.defaultVoice?.name }
                        ?: voices.firstOrNull { it.locale == Locale.getDefault() }
                        ?: voices.firstOrNull { it.locale.language == Locale.getDefault().language }
                        ?: voices.firstOrNull()
                if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
                    cancel(); error(result, "noOfflineVoice"); return@withEngine
                }
                if (tts.setAudioAttributes(attributes) != TextToSpeech.SUCCESS) throw IllegalStateException()
                startMonitors()
                if (!requestFocus()) {
                    cancel("busy"); error(result, "busy"); return@withEngine
                }
                chunks = splitText(text)
                chunkIndex = 0
                enqueueChunk()
                if (operation != id) { error(result, "engineUnavailable"); return@withEngine }
                accepted = true
                result.success(mapOf("operationID" to id, "status" to "accepted"))
            } catch (_: Exception) {
                cancel("engineUnavailable"); error(result, "engineUnavailable")
            }
        }
    }

    private fun offlineVoices(): List<Voice> = engine?.voices.orEmpty()
        .filter { !it.isNetworkConnectionRequired &&
            !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        .sortedBy { it.name }

    private fun withEngine(result: MethodChannel.Result, action: () -> Unit) {
        if (ready) { action(); return }
        if (pending != null) { error(result, "busy"); return }
        pending = result
        pendingAction = action
        val generation = ++initGeneration
        val timeout = Runnable {
            if (generation == initGeneration) abortInit("engineUnavailable")
        }
        initTimeout = timeout
        handler.postDelayed(timeout, 8000)
        try {
            engine = TextToSpeech(activity.applicationContext) { status ->
                handler.post {
                    if (generation != initGeneration || destroyed) return@post
                    if (status != TextToSpeech.SUCCESS) {
                        abortInit("engineUnavailable"); return@post
                    }
                    try {
                        val listenerResult = engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                handler.post {
                                    if (utteranceId == null || utteranceId != utterance || operation == null) return@post
                                    handler.removeCallbacks(playbackTimeout)
                                    chunkIndex++
                                    if (chunkIndex == chunks.size) cancel("completed") else enqueueChunk()
                                }
                            }
                            @Deprecated("Android legacy callback")
                            override fun onError(utteranceId: String?) { failed(utteranceId) }
                            override fun onError(utteranceId: String?, errorCode: Int) { failed(utteranceId) }
                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                handler.post { if (utteranceId != null && utteranceId == utterance) cancel() }
                            }
                        })
                        if (listenerResult != TextToSpeech.SUCCESS) {
                            abortInit("engineUnavailable"); return@post
                        }
                        ready = true
                        handler.removeCallbacks(timeout)
                        initTimeout = null
                        val next = pendingAction
                        pending = null
                        pendingAction = null
                        next?.invoke()
                    } catch (_: Exception) { abortInit("engineUnavailable") }
                }
            }
        } catch (_: Exception) { abortInit("engineUnavailable") }
    }

    private fun failed(id: String?) {
        handler.post { if (id != null && id == utterance && operation != null) cancel("engineUnavailable") }
    }

    private fun splitText(text: String): List<String> {
        val limit = minOf(TextToSpeech.getMaxSpeechInputLength(), 3000)
        check(limit >= 2)
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1]) &&
                Character.isLowSurrogate(text[end])) end--
            parts.add(text.substring(start, end))
            start = end
        }
        return parts
    }

    private fun enqueueChunk() {
        if (!foreground || captureActive()) { cancel("busy"); return }
        val id = "chunk-${++utteranceSequence}"
        utterance = id
        try {
            val result = engine?.speak(chunks[chunkIndex], TextToSpeech.QUEUE_FLUSH, null, id)
            if (result != TextToSpeech.SUCCESS) { cancel("engineUnavailable"); return }
            // A missing terminal listener must not retain focus/text forever.
            handler.removeCallbacks(playbackTimeout)
            handler.postDelayed(playbackTimeout, 300000)
        } catch (_: Exception) { cancel("engineUnavailable") }
    }

    private fun captureActive(): Boolean {
        if (SystemClock.elapsedRealtime() < micGuardUntil) return true
        return try {
            Build.VERSION.SDK_INT >= 24 && audio.activeRecordingConfigurations.isNotEmpty()
        } catch (_: Exception) { true }
    }

    private fun startMonitors() {
        if (monitors) return
        monitors = true
        audio.registerAudioDeviceCallback(devices, handler)
        if (Build.VERSION.SDK_INT >= 24 && recordings != null) {
            audio.registerAudioRecordingCallback(recordings, handler)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        val generation = ++focusGeneration
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) handler.post {
                if (generation == focusGeneration) cancel()
            }
        }
        focusListener = listener
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(listener, handler)
                .setWillPauseWhenDucked(true).build()
            focusRequest = request
            audio.requestAudioFocus(request)
        } else audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun cancel(status: String = "cancelled"): Boolean {
        val id = operation
        val notify = accepted
        accepted = false
        focusGeneration++
        operation = null
        utterance = null
        chunks = emptyList()
        handler.removeCallbacks(playbackTimeout)
        val stopped = try { engine?.stop()?.let { it == TextToSpeech.SUCCESS } ?: true }
            catch (_: Exception) { false }
        if (monitors) {
            monitors = false
            try { audio.unregisterAudioDeviceCallback(devices) } catch (_: Exception) {}
            if (Build.VERSION.SDK_INT >= 24 && recordings != null) {
                try { audio.unregisterAudioRecordingCallback(recordings) } catch (_: Exception) {}
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            else focusListener?.let { audio.abandonAudioFocus(it) }
        } catch (_: Exception) {}
        focusRequest = null
        focusListener = null
        if (id != null) {
            // Pending speak closures retain text: invalidate and release them too.
            abortInit("busy")
            if (notify) channel.invokeMethod("status", mapOf("operationID" to id, "status" to status))
        }
        return stopped
    }

    private fun abortInit(code: String) {
        if (pending == null) return
        val result = pending
        pending = null
        pendingAction = null
        initGeneration++
        initTimeout?.let { handler.removeCallbacks(it) }
        initTimeout = null
        releaseEngine()
        result?.let { error(it, code) }
        operation = null
        accepted = false
    }

    private fun releaseEngine() {
        ready = false
        try { engine?.stop() } catch (_: Exception) {}
        try { engine?.shutdown() } catch (_: Exception) {}
        engine = null
    }
}
