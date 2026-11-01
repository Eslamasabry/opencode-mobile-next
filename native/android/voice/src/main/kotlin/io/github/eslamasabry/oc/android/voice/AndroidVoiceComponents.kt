package io.github.eslamasabry.oc.android.voice

import android.content.Context
import io.github.eslamasabry.oc.android.voice.audio.AndroidAudioCapture
import io.github.eslamasabry.oc.android.voice.audio.AndroidAudioSession
import io.github.eslamasabry.oc.android.voice.audio.AndroidPcmPlayer
import io.github.eslamasabry.oc.android.voice.device.AndroidDeviceProbe
import io.github.eslamasabry.oc.android.voice.device.AndroidSetupNetwork
import io.github.eslamasabry.oc.android.voice.files.AndroidModelFiles
import io.github.eslamasabry.oc.android.voice.files.PlatformDigests
import io.github.eslamasabry.oc.android.voice.net.HttpsModelFetcher
import io.github.eslamasabry.oc.android.voice.speech.SherpaRuntime
import io.github.eslamasabry.oc.android.voice.speech.SherpaSpeechRecognizer
import io.github.eslamasabry.oc.android.voice.speech.SherpaSpeechSynthesizer
import io.github.eslamasabry.oc.android.voice.tts.AndroidSystemTts
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.voice.dictation.MicPermission
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceController
import io.github.eslamasabry.oc.engine.voice.dictation.asSpeechPackSource
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.models.ModelPacks
import io.github.eslamasabry.oc.engine.voice.models.PackInstaller
import io.github.eslamasabry.oc.engine.voice.models.PackKind
import io.github.eslamasabry.oc.engine.voice.prefs.VoicePrefs
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadAloud
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetup
import kotlinx.coroutines.CoroutineScope
import java.io.File
import kotlin.time.Clock

/**
 * Builds the whole voice stack for the app graph (the coordinator binds these into Metro when `:androidApp` takes the
 * module; see docs/native/units/VOICE.md). One instance per process, App scope. Nothing here touches the microphone,
 * the speech engine or the network until a feature calls in (plan 10 §18 "cold start cost of media: 0 ms").
 *
 * The models live in `files/voice_models` exactly where the Flutter app put them (plan 10 §7.2), so an in-place update
 * keeps them with no re-download.
 */
public class AndroidVoiceComponents(
    context: Context,
    dispatchers: AppDispatchers,
    clock: Clock,
    scope: CoroutineScope,
    prefs: VoicePrefs,
    permission: MicPermission,
    sherpa: SherpaRuntime,
    appLanguage: () -> String?,
    voiceAvailable: Boolean = true,
    readAloudAvailable: Boolean = true,
) {
    private val app = context.applicationContext
    private val speechLane = dispatchers.io.limitedParallelism(1, "voice.speech")

    public val probe: AndroidDeviceProbe = AndroidDeviceProbe(app, dispatchers.io)
    public val network: AndroidSetupNetwork = AndroidSetupNetwork(app)
    public val installer: PackInstaller =
        PackInstaller(
            root = File(app.filesDir, PackKind.Speech.root).path,
            files = AndroidModelFiles(),
            fetcher = HttpsModelFetcher(dispatchers.io),
            dispatchers = dispatchers,
            clock = clock,
            digests = PlatformDigests,
        )
    public val packs: ModelPacks = ModelPacks(installer, probe, prefs, scope)
    public val systemTts: AndroidSystemTts = AndroidSystemTts(app, dispatchers.main)

    private lateinit var reader: ReadAloud

    public val audio: AndroidAudioSession =
        AndroidAudioSession(app, clock) {
            reader.stop()
            systemTts.releaseIfIdle()
            true
        }

    public val readAloud: ReadAloud =
        ReadAloud(
            audio = audio,
            systemTts = systemTts,
            synthesizer = SherpaSpeechSynthesizer(sherpa, speechLane),
            player = AndroidPcmPlayer(dispatchers.io),
            prefs = prefs,
            scope = scope,
            naturalPack = { packs.installed(ModelCatalog.kokoroEn.id) },
            appLanguage = appLanguage,
            cpuCount = { Runtime.getRuntime().availableProcessors() },
            available = readAloudAvailable,
        ).also { reader = it }

    public val voice: VoiceController =
        VoiceController(
            audio = audio,
            capture = AndroidAudioCapture(audio, dispatchers.io.limitedParallelism(1, "voice.mic")),
            recognizer = SherpaSpeechRecognizer(sherpa, speechLane, scope),
            packs = packs.asSpeechPackSource(),
            permission = permission,
            probe = probe,
            clock = clock,
            dispatchers = dispatchers,
            scope = scope,
            appLanguage = appLanguage,
            stopReading = { readAloud.stop() },
            voiceAvailable = voiceAvailable,
        )

    public val setup: VoiceSetup = VoiceSetup(packs, network, scope)
}
