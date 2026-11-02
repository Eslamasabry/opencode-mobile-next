package io.github.eslamasabry.oc.feature.voice

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceStart
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.models.ModelPackSpec
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadRequest
import io.github.eslamasabry.oc.engine.voice.state.ModelPacksState
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJob
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.engine.voice.state.ReadAloudState
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.feature.voice.presentation.Dictation
import io.github.eslamasabry.oc.feature.voice.presentation.Reader
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechEngineKind
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceFieldPort
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceHub
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceModels
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceParts
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope

/** A clock on the test scheduler's virtual time. */
class SchedulerClock(private val scope: TestScope) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000 + scope.testScheduler.currentTime)
}

class FakeDictation : Dictation {
    override val state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val level = MutableStateFlow(0f)
    val starts = mutableListOf<VoiceStart>()
    val cancels = mutableListOf<Boolean>()
    var finishes = 0
    var startResult: Outcome<Unit> = Unit.ok()
    var hosts = mutableListOf<VoiceHost>()

    override suspend fun start(request: VoiceStart): Outcome<Unit> {
        starts += request
        if (startResult is Outcome.Ok) state.value = VoiceState.Starting
        return startResult
    }

    override suspend fun finish(): Outcome<String> {
        finishes++
        return "".ok()
    }

    override suspend fun cancel(keepWords: Boolean) {
        cancels += keepWords
        state.value = VoiceState.Idle
    }

    override fun onHostChanged(host: VoiceHost) {
        hosts += host
    }
}

class FakeModels(autoSend: Boolean = false) : VoiceModels {
    override val state = MutableStateFlow(ModelPacksState(autoSend = autoSend))
    override val speechPacks: List<ModelPackSpec> = listOf(ModelCatalog.base, ModelCatalog.small, ModelCatalog.tiny)
    override val naturalPack: ModelPackSpec = ModelCatalog.kokoroEn
    val calls = mutableListOf<String>()
    var downloadGate: CompletableDeferred<Outcome<Unit>>? = null
    var nextFailure: Failure? = null

    override suspend fun refresh() {
        calls += "refresh"
    }

    override suspend fun device(): DeviceProbeResult =
        DeviceProbeResult.Known(
            totalRamMb = 8000,
            availableStorageBytes = 10_000_000_000,
            supportedAbis = persistentListOf("arm64-v8a"),
            hasMicrophone = true,
            lowRamDevice = false,
            memoryClassMb = 256,
            cpuCount = 8,
        )

    private fun failOrOk(): Outcome<Unit> = nextFailure?.also { nextFailure = null }?.failed() ?: Unit.ok()

    override suspend fun select(pack: PackId): Outcome<Unit> {
        calls += "select:${pack.raw}"
        state.update { it.copy(selected = pack) }
        return Unit.ok()
    }

    override suspend fun download(pack: PackId, replaceExisting: Boolean): Outcome<Unit> {
        calls += "download:${pack.raw}:$replaceExisting"
        nextFailure?.let { f ->
            nextFailure = null
            state.update { it.copy(lastProblem = (f as? Failure.Media)?.problem) }
            return f.failed()
        }
        state.update { it.copy(job = PackJob(pack, PackJobPhase.Downloading(0, 100))) }
        val gate = downloadGate
        val result = gate?.await() ?: Unit.ok()
        if (result is Outcome.Ok) {
            state.update { it.copy(job = null, packs = (it.packs + (pack to PackStatus.Installed)).toImmutableMap()) }
        } else {
            state.update { it.copy(job = null) }
        }
        return result
    }

    fun progress(pack: PackId, phase: PackJobPhase) {
        state.update { it.copy(job = PackJob(pack, phase)) }
    }

    override suspend fun cancelDownload() {
        calls += "cancel"
    }

    override suspend fun remove(pack: PackId): Outcome<Unit> {
        calls += "remove:${pack.raw}"
        state.update { it.copy(packs = (it.packs + (pack to PackStatus.Missing)).toImmutableMap()) }
        return failOrOk()
    }

    override suspend fun discardPartial(pack: PackId): Outcome<Unit> {
        calls += "discard:${pack.raw}"
        return Unit.ok()
    }

    override suspend fun setLanguage(choice: VoiceLanguage): Outcome<Unit> {
        calls += "language:${choice.storedId}"
        state.update { it.copy(language = choice) }
        return Unit.ok()
    }

    override suspend fun setAutoSend(on: Boolean): Outcome<Unit> {
        calls += "autoSend:$on"
        state.update { it.copy(autoSend = on) }
        return Unit.ok()
    }

    override suspend fun setNaturalVoice(on: Boolean): Outcome<Unit> {
        calls += "natural:$on"
        state.update { it.copy(naturalVoice = on) }
        return Unit.ok()
    }
}

class FakeReader(var consent: Boolean = false) : Reader {
    override val state = MutableStateFlow<ReadAloudState>(ReadAloudState.Idle)
    val reads = mutableListOf<ReadRequest>()
    var stops = 0
    var readResult: Outcome<Unit> = Unit.ok()

    override fun consentGiven(): Boolean = consent

    override suspend fun giveConsent(): Outcome<Unit> {
        consent = true
        return Unit.ok()
    }

    override suspend fun read(request: ReadRequest): Outcome<Unit> {
        reads += request
        if (readResult is Outcome.Ok) state.value = ReadAloudState.Speaking(request.utteranceId, io.github.eslamasabry.oc.engine.voice.state.ReadEngine.System)
        return readResult
    }

    override suspend fun stop() {
        stops++
        state.value = ReadAloudState.Idle
    }

    override fun onHostChanged(resumed: Boolean) = Unit
}

class FakeHub(
    val dictation: FakeDictation = FakeDictation(),
    val models: FakeModels = FakeModels(),
    val reader: FakeReader = FakeReader(),
    private val unavailable: Boolean = false,
    private val naturalVoiceAvailable: Boolean = false,
) : VoiceHub {
    var settingsOpened = 0

    override suspend fun parts(): Outcome<VoiceParts> =
        if (unavailable) Failure.Unavailable(FeatureId("voice")).failed()
        else
            VoiceParts(
                dictation = dictation,
                models = models,
                reader = reader,
                engine = SpeechEngineKind.PhoneRecognizer,
                micAvailable = true,
                naturalVoiceAvailable = naturalVoiceAvailable,
            ).ok()

    override fun openAppSettings() {
        settingsOpened++
    }

    override fun ticks(period: Duration): Flow<Unit> =
        flow {
            while (true) {
                emit(Unit)
                delay(period)
            }
        }
}

class FakeField(var value: String = "", var sel: IntRange? = null, var sendable: Boolean = true) : VoiceFieldPort {
    val sent = mutableListOf<String>()

    override fun text(): String = value

    override fun selection(): IntRange? = sel

    override fun replace(text: String) {
        value = text
        sel = null
    }

    override fun send() {
        sent += value
        value = ""
    }

    override fun canSend(): Boolean = sendable
}

/** Mic problems as the engine reports them. */
fun mediaFailure(problem: MediaProblem): Outcome<Unit> = Failure.Media(problem).failed()

