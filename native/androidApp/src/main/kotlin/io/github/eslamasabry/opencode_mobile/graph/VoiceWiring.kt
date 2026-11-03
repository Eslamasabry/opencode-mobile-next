package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.voice.AndroidVoiceComponents
import io.github.eslamasabry.oc.android.voice.speech.KokoroTtsConfig
import io.github.eslamasabry.oc.android.voice.speech.NativeRecognizer
import io.github.eslamasabry.oc.android.voice.speech.NativeTts
import io.github.eslamasabry.oc.android.voice.speech.SherpaRuntime
import io.github.eslamasabry.oc.android.voice.speech.WhisperRecognizerConfig
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.dictation.MicPermission
import io.github.eslamasabry.oc.engine.voice.dictation.MicPermissionStatus
import io.github.eslamasabry.oc.engine.voice.dictation.OnDeviceSpeech
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceStart
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.language.recognizerCode
import io.github.eslamasabry.oc.engine.voice.prefs.KeyValueVoicePrefs
import io.github.eslamasabry.oc.engine.voice.prefs.VoicePrefs
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.feature.settings.presentation.SpeechModel
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceFacts
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceSettings
import io.github.eslamasabry.oc.feature.voice.presentation.Dictation
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechEngineKind
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceHub
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceParts
import io.github.eslamasabry.oc.feature.voice.presentation.asReader
import io.github.eslamasabry.oc.feature.voice.presentation.asVoiceModels
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.voiceNativePhoneRecognizer
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString

/*
 * P12-VOICE app wiring (the one Android-only voice file). It builds `:android:voice`'s stack over the app's stores,
 * asks for the microphone through the resumed activity, and types with the phone's own speech recognizer while the
 * offline engine (sherpa-onnx, not vendored in this tree) is missing. When the sherpa adapter lands, bind it in place
 * of [NoOfflineSpeechEngine] and hand `components.voice.asDictation()` to [VoiceParts] with
 * `SpeechEngineKind.OfflineModel`; nothing in `:feature:voice` changes.
 */

/** The app's voice: built once, after the stores are warm (the adopted Flutter prefs throw on early reads). */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
class AndroidVoiceHub(
    private val application: Application,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val scope: AppCoroutineScope,
    private val stores: PreviewStores,
    private val activities: ResumedActivities,
) : VoiceHub {
    private val building = Mutex()
    private var built: Outcome<VoiceParts>? = null

    override suspend fun parts(): Outcome<VoiceParts> =
        building.withLock {
            built?.let { return@withLock it }
            when (val warm = stores.warm()) {
                is Outcome.Failed -> return@withLock warm // not cached: a later screen tries again
                is Outcome.Ok -> Unit
            }
            build().also { built = it }
        }

    private fun build(): Outcome<VoiceParts> {
        val prefs = OfflineEngineAwarePrefs(KeyValueVoicePrefs(stores.prefs, stores.lanes.app), naturalVoiceRuns = false)
        val permission = ActivityMicPermission(application, activities, dispatchers.main)
        val hasMic = application.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val components =
            AndroidVoiceComponents(
                context = application,
                dispatchers = dispatchers,
                clock = clock,
                scope = scope,
                prefs = prefs,
                permission = permission,
                sherpa = NoOfflineSpeechEngine,
                appLanguage = { Locale.getDefault().language },
                voiceAvailable = hasMic,
                readAloudAvailable = true,
            )
        val models = components.packs.asVoiceModels()
        val dictation =
            PhoneDictation(
                app = application,
                permission = permission,
                main = dispatchers.main,
                clock = clock,
                scope = scope,
                language = { models.state.value.language },
                stopReading = { components.readAloud.stop() },
            )
        return VoiceParts(
            dictation = dictation,
            models = models,
            reader = components.readAloud.asReader(),
            engine = SpeechEngineKind.PhoneRecognizer,
            // S1: only an on-device recognizer types; without one the mic is not offered (never a cloud fallback).
            micAvailable = hasMic && onDeviceRecognizer(application),
            naturalVoiceAvailable = false,
        ).ok()
    }

    override fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", application.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            application.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            // No settings app: the note keeps saying what to do.
        }
    }

    override fun ticks(period: Duration): Flow<Unit> =
        flow {
            while (true) {
                emit(Unit)
                delay(period)
            }
        }
}

/**
 * Settings › Voice (plan 07 I11; B-MEDIA-012/017/022): offered where dictation can run; its Voice typing row names the
 * phone's on-device recognizer and opens Voice typing settings (language, models, Send when I stop talking). Read
 * aloud is asked for at first use from a reply, so its switch is not shown here.
 */
@SingleIn(AppScope::class)
@Inject
class AndroidVoiceSettings(application: Application, appScope: AppCoroutineScope) : VoiceSettings {
    val offered: Boolean =
        application.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) && onDeviceRecognizer(application)

    private val mutable = MutableStateFlow(VoiceFacts())
    override val facts: StateFlow<VoiceFacts> = mutable.asStateFlow()

    init {
        if (offered) {
            appScope.launch { mutable.value = VoiceFacts(speechModel = SpeechModel.Ready(getString(Res.string.voiceNativePhoneRecognizer))) }
        }
    }

    override suspend fun setReadAloud(on: Boolean): Outcome<Unit> = Unit.ok() // no switch: readAloudSupported is false
}

/** An on-device recognizer exists (API 31+): the only one dictation may use (plan 10 §17 S1). */
private fun onDeviceRecognizer(app: Application): Boolean =
    OnDeviceSpeech.usable(Build.VERSION.SDK_INT) {
        Build.VERSION.SDK_INT >= OnDeviceSpeech.MIN_SDK && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
    }

/** The natural voice needs the offline engine; until it is in the app, a stored "natural" choice reads as the phone's. */
private class OfflineEngineAwarePrefs(private val inner: VoicePrefs, private val naturalVoiceRuns: Boolean) : VoicePrefs by inner {
    override fun readAloudNatural(): Boolean = naturalVoiceRuns && inner.readAloudNatural()
}

/** sherpa-onnx is not vendored in this tree (docs/native/units/VOICE.md §5): every native call says so. */
private object NoOfflineSpeechEngine : SherpaRuntime {
    override fun newRecognizer(config: WhisperRecognizerConfig): NativeRecognizer =
        throw IllegalStateException("offline speech engine not in this build")

    override fun newTts(config: KokoroTtsConfig): NativeTts = throw IllegalStateException("offline speech engine not in this build")
}

/**
 * RECORD_AUDIO through the resumed activity's result registry. The request uses one stable key, and an activity created
 * while the request waits (rotation with the dialog open) registers that key again, so the answer the replacement
 * activity receives reaches the waiting request (B-MEDIA-023). Denied twice (no rationale left) = only Android settings
 * can allow it now.
 */
private class ActivityMicPermission(
    private val app: Application,
    private val activities: ResumedActivities,
    private val main: CoroutineDispatcher,
) : MicPermission {
    private val relay = MicRequestRelay()

    init {
        activities.onCreated { activity -> relay.hostCreated(RegistryMicHost(activity)) }
    }

    override suspend fun request(): MicPermissionStatus =
        withContext(main) {
            if (granted()) return@withContext MicPermissionStatus.Granted
            val activity = activities.current ?: return@withContext MicPermissionStatus.Denied
            val allowed = relay.ask(RegistryMicHost(activity))
            val asker = activities.current ?: activity
            when {
                allowed -> MicPermissionStatus.Granted
                ActivityCompat.shouldShowRequestPermissionRationale(asker, Manifest.permission.RECORD_AUDIO) ->
                    MicPermissionStatus.Denied
                else -> MicPermissionStatus.PermanentlyDenied
            }
        }

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

/** One activity's result registry, as [MicRequestRelay] needs it (lifecycle-free registration: results arrive at once). */
private class RegistryMicHost(private val activity: ComponentActivity) : MicHost {
    override fun register(key: String, onResult: (Boolean) -> Unit): MicLauncher {
        val launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.RequestPermission(), onResult)
        return object : MicLauncher {
            override fun launch() = launcher.launch(Manifest.permission.RECORD_AUDIO)

            override fun unregister() = launcher.unregister()
        }
    }
}

/** The resumed activity, followed from app start (`OcApplication` touches it right after the graph is made). */
@ContributesTo(AppScope::class)
interface VoiceActivityGraph {
    val resumedActivities: ResumedActivities
}

@SingleIn(AppScope::class)
@Inject
class ResumedActivities(app: Application) {
    private var resumed = WeakReference<ComponentActivity>(null)

    private val created = mutableListOf<(ComponentActivity) -> Unit>()

    val current: ComponentActivity? get() = resumed.get()

    /** Called on the main thread for every activity created from now on (after its result registry is restored). */
    fun onCreated(listener: (ComponentActivity) -> Unit) {
        created += listener
    }

    init {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (activity is ComponentActivity) resumed = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed.get() === activity) resumed = WeakReference(null)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (activity is ComponentActivity) created.toList().forEach { it(activity) }
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}

/**
 * Voice typing with the phone's on-device speech recognizer (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, API
 * 31+; never the default service, which may send audio away: plan 10 §17 S1). Partial results are the live words. A tap without "Send when I stop talking" and a held mic keep listening
 * across pauses (the recognizer is restarted after each phrase); with the setting on, a tap ends on the first pause
 * (`VoiceEnd.Silence`). All recognizer calls run on the main thread, as Android requires.
 */
private class PhoneDictation(
    private val app: Application,
    private val permission: MicPermission,
    private val main: CoroutineDispatcher,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val language: () -> VoiceLanguage,
    private val stopReading: suspend () -> Unit,
) : Dictation {
    override val state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val level = MutableStateFlow(0f)

    private var recognizer: SpeechRecognizer? = null
    private var request: VoiceStart? = null
    private val committed = StringBuilder()
    private var partial = ""
    private var since: Instant = Instant.fromEpochMilliseconds(0)
    private var stopping = false
    private var host = VoiceHost.Foreground
    private var finished: CompletableDeferred<Unit>? = null

    private val words: String
        get() = listOf(committed.toString().trim(), partial.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    override suspend fun start(request: VoiceStart): Outcome<Unit> =
        withContext(main) {
            when (state.value) {
                VoiceState.Idle, VoiceState.NeedsModel, is VoiceState.Done, is VoiceState.Failed -> Unit
                else -> return@withContext Unit.ok() // a second tap while starting is a no-op
            }
            // S1: no on-device recognizer = no dictation; the default service may send the audio off the phone.
            if (!onDeviceRecognizer(app)) return@withContext fail(MediaProblem.SpeechModelMissing)
            stopReading()
            when (permission.request()) {
                MicPermissionStatus.Granted -> Unit
                MicPermissionStatus.Denied -> return@withContext fail(MediaProblem.MicDenied)
                MicPermissionStatus.PermanentlyDenied -> return@withContext fail(MediaProblem.MicDeniedForever)
            }
            if (host != VoiceHost.Foreground) return@withContext fail(MediaProblem.LeftTheApp)
            this@PhoneDictation.request = request
            committed.clear()
            partial = ""
            stopping = false
            since = clock.now()
            level.value = 0f
            state.value = VoiceState.Starting
            if (!listen()) return@withContext fail(MediaProblem.NoMicrophone)
            Unit.ok()
        }

    private fun fail(problem: MediaProblem): Outcome<Nothing> {
        state.value = VoiceState.Failed(problem, "")
        return Failure.Media(problem).failed()
    }

    /** Opens the on-device recognizer for the next phrase (never the default service: S1). */
    private fun listen(): Boolean {
        recognizer?.destroy()
        if (Build.VERSION.SDK_INT < OnDeviceSpeech.MIN_SDK) return false
        val r =
            try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            } catch (_: RuntimeException) {
                null
            } ?: return false
        recognizer = r
        r.setRecognitionListener(Listener(r))
        r.startListening(intent())
        return true
    }

    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
            val code = language().recognizerCode(Locale.getDefault().language)
            if (code.isNotEmpty()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, code)
        }

    private fun publishListening() {
        val mode = request?.mode ?: VoiceMode.Tap
        state.value = VoiceState.Listening(since, mode, 0, words)
    }

    private fun end(next: VoiceState) {
        recognizer?.destroy()
        recognizer = null
        level.value = 0f
        state.value = next
        finished?.complete(Unit)
        finished = null
    }

    private inner class Listener(private val owner: SpeechRecognizer) : RecognitionListener {
        private val current: Boolean get() = recognizer === owner

        override fun onReadyForSpeech(params: Bundle?) {
            if (current && !stopping) publishListening()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (current) level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (current && stopping) state.value = VoiceState.Writing(words)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current) return
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
            if (!stopping) publishListening() else state.value = VoiceState.Writing(words)
        }

        override fun onResults(results: Bundle?) {
            if (!current) return
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (heard.isNotBlank()) committed.append(' ').append(heard.trim())
            partial = ""
            phraseEnded()
        }

        override fun onError(error: Int) {
            if (!current) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    partial = ""
                    phraseEnded()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> end(VoiceState.Failed(MediaProblem.MicDenied, words))
                SpeechRecognizer.ERROR_AUDIO -> end(VoiceState.Failed(MediaProblem.RecordingInterrupted, words))
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> end(VoiceState.Failed(MediaProblem.MicBusy, words))
                LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE ->
                    // The on-device recognizer lacks the language. Never hand the audio to the default service (S1).
                    end(VoiceState.Failed(MediaProblem.ChunkNotTranscribed, words))
                else -> if (stopping) end(VoiceState.Done(words, VoiceEnd.Person)) else end(VoiceState.Failed(MediaProblem.ChunkNotTranscribed, words))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        /** One phrase is written down: end (Done, a pause with the setting on) or keep listening. */
        private fun phraseEnded() {
            val req = request
            when {
                stopping -> end(VoiceState.Done(words, VoiceEnd.Person))
                req != null && req.stopOnSilence -> end(VoiceState.Done(words, VoiceEnd.Silence))
                else -> if (!listen()) end(VoiceState.Done(words, VoiceEnd.Person)) else publishListening()
            }
        }
    }

    override suspend fun finish(): Outcome<String> =
        withContext(main) {
            val r = recognizer
            if (r != null && !stopping) {
                stopping = true
                state.value = VoiceState.Writing(words)
                val waiter = CompletableDeferred<Unit>()
                finished = waiter
                r.stopListening()
                if (withTimeoutOrNull(FINISH_WAIT) { waiter.await() } == null) end(VoiceState.Done(words, VoiceEnd.Person))
            }
            when (val now = state.value) {
                is VoiceState.Done -> now.words.ok()
                is VoiceState.Failed -> Failure.Media(now.problem).failed()
                else -> "".ok()
            }
        }

    override suspend fun cancel(keepWords: Boolean) {
        withContext(main) {
            val r = recognizer ?: return@withContext
            recognizer = null
            r.cancel()
            r.destroy()
            level.value = 0f
            val kept = words
            state.value = if (keepWords && kept.isNotEmpty()) VoiceState.Done(kept, VoiceEnd.Person) else VoiceState.Idle
            finished?.complete(Unit)
            finished = null
        }
    }

    override fun onHostChanged(host: VoiceHost) {
        if (host == VoiceHost.Inactive || host == VoiceHost.ScopeChanged) return
        this.host = host
        if (host == VoiceHost.Background) {
            // Leaving the app ends the recording, keeping what was written (plan 10 §6.5).
            scope.launch(main) {
                when (state.value) {
                    VoiceState.Starting -> cancel(keepWords = true)
                    is VoiceState.Listening -> {
                        val r = recognizer ?: return@launch
                        recognizer = null
                        r.cancel()
                        r.destroy()
                        level.value = 0f
                        state.value = VoiceState.Done(words, VoiceEnd.LeftApp)
                    }
                    else -> Unit
                }
            }
        }
    }

    private companion object {
        val FINISH_WAIT = 4.seconds

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31; constants inlined).
        const val LANGUAGE_NOT_SUPPORTED = 12
        const val LANGUAGE_UNAVAILABLE = 13
    }
}

/** A place that can ask for the microphone (an activity's result registry); see [MicRequestRelay]. */
internal interface MicHost {
    fun register(key: String, onResult: (Boolean) -> Unit): MicLauncher
}

internal interface MicLauncher {
    fun launch()

    fun unregister()
}

/**
 * The one microphone request in flight, kept across activity recreation (B-MEDIA-023): the dialog's answer goes to the
 * activity that exists when it is closed, so a host created while the request waits registers the same key and the
 * registry hands the answer over. Main thread only.
 */
internal class MicRequestRelay {
    private var waiting: CompletableDeferred<Boolean>? = null
    private var launcher: MicLauncher? = null

    suspend fun ask(host: MicHost): Boolean {
        waiting?.let { return it.await() } // a second ask while the dialog is open waits for the same answer
        val answer = CompletableDeferred<Boolean>()
        waiting = answer
        try {
            listen(host).launch()
            return answer.await()
        } finally {
            if (waiting === answer) {
                waiting = null
                launcher?.unregister()
                launcher = null
            }
        }
    }

    /** A new activity (rotation, theme change) takes over the request that is still waiting. */
    fun hostCreated(host: MicHost) {
        if (waiting == null) return
        listen(host)
    }

    private fun listen(host: MicHost): MicLauncher {
        launcher?.unregister()
        val answer = waiting
        return host.register(KEY) { ok -> answer?.complete(ok) }.also { launcher = it }
    }

    companion object {
        const val KEY = "oc.voice.mic"
    }
}
