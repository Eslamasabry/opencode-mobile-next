package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.prefs.VoicePrefs
import io.github.eslamasabry.oc.engine.voice.state.ModelPacksState
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJob
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The model manager (plan 10 §4.6 `ModelPacks`, Dart `VoiceModelManager` + `NaturalVoiceController`). One job at a
 * time; a download keeps running in [scope] when the page or sheet that started it closes. Work that a later delete,
 * selection, download, cancel or [close] supersedes never publishes (B-MEDIA-030).
 */
public class ModelPacks(
    private val installer: PackInstaller,
    private val probe: DeviceProbe,
    private val prefs: VoicePrefs,
    private val scope: CoroutineScope,
    private val catalog: List<ModelPackSpec> = ModelCatalog.all,
) {
    private val mutableState =
        MutableStateFlow(
            ModelPacksState(
                selected = ModelCatalog.speechOrDefault(prefs.selectedPack()).id,
                language = VoiceLanguage.fromStoredId(prefs.language()),
                autoSend = prefs.autoSend(),
                naturalVoice = prefs.readAloudNatural(),
            ),
        )
    public val state: StateFlow<ModelPacksState> = mutableState.asStateFlow()

    private val lane = Mutex()
    private var generation = 0L
    private var closed = false
    private var device: DeviceProbeResult? = null
    private var running: Running? = null

    private class Running(val pack: PackId, val deferred: Deferred<Outcome<InstalledPack>>)

    public val speechPacks: List<ModelPackSpec> get() = catalog.filter { it.kind == PackKind.Speech }

    /** Bytes already in the pack's kept `.part` files. */
    public suspend fun partialBytes(pack: PackId): Long = specOf(pack)?.let { installer.partialBytes(it) } ?: 0L

    public val selectedSpeech: ModelPackSpec
        get() {
            val speech = catalog.filter { it.kind == PackKind.Speech }
            return speech.firstOrNull { it.id == mutableState.value.selected } ?: speech.first()
        }

    /** The device facts, probed once per manager (re-probed by [refresh]). Stale probes never publish. */
    public suspend fun device(): DeviceProbeResult {
        device?.let { return it }
        val started = lane.withLock { generation }
        val probed = probe.probe()
        lane.withLock { if (generation == started && !closed) device = probed }
        return probed
    }

    /** Re-probes and recomputes every row; a newer command started meanwhile wins (B-MEDIA-030). */
    public suspend fun refresh() {
        val started = lane.withLock { generation.also { device = null } }
        val probed = probe.probe()
        val rows = catalog.associate { it.id to statusOf(it, probed) }
        lane.withLock {
            if (generation != started || closed) return
            device = probed
            mutableState.update { it.copy(packs = it.packs.toMutableMap().apply { putAll(rows) }.toImmutableMap()) }
        }
    }

    private suspend fun statusOf(pack: ModelPackSpec, device: DeviceProbeResult): PackStatus {
        if (installer.installed(pack) != null) return PackStatus.Installed
        val partial = installer.partialBytes(pack)
        val support = PackSupport.supportFor(pack, device, installed = false, partialBytes = partial)
        if (support is Support.Blocked && support.block != PackBlock.Storage) return PackStatus.Unsupported(support.block.problem)
        if (partial > 0) return PackStatus.Partial(partial, pack.totalBytes)
        if (support is Support.Blocked) return PackStatus.Unsupported(support.block.problem)
        return PackStatus.Missing
    }

    /** The selected speech pack when it is installed and this phone's RAM allows it; null → setup is needed. */
    public suspend fun readySpeechPack(): InstalledPack? {
        val pack = selectedSpeech
        val support = PackSupport.supportFor(pack, device(), installed = true)
        if (support is Support.Blocked) return null
        return installer.installed(pack)
    }

    public suspend fun installed(pack: PackId): InstalledPack? = specOf(pack)?.let { installer.installed(it) }

    /** Full SHA-256 check after a recognizer load failure; a mismatch makes the pack Missing. */
    public suspend fun verifyFully(pack: PackId): Boolean {
        val spec = specOf(pack) ?: return false
        val ok = installer.verifyFully(spec)
        if (!ok) setStatus(pack, PackStatus.Missing)
        return ok
    }

    /** Refused while a download, verification or load runs (Dart `selectPack` returns early). */
    public suspend fun select(pack: PackId): Outcome<Unit> =
        lane.withLock {
            if (running != null) return@withLock busy()
            val spec = specOf(pack)?.takeIf { it.kind == PackKind.Speech } ?: return@withLock busy()
            generation++
            mutableState.update { it.copy(selected = spec.id) }
            prefs.setSelectedPack(spec.id.raw)
        }

    public suspend fun setLanguage(choice: VoiceLanguage): Outcome<Unit> {
        mutableState.update { it.copy(language = choice) }
        return prefs.setLanguage(choice.storedId)
    }

    /** "Send when I stop talking": off by default (B-MEDIA-017). */
    public suspend fun setAutoSend(on: Boolean): Outcome<Unit> {
        mutableState.update { it.copy(autoSend = on) }
        return prefs.setAutoSend(on)
    }

    /**
     * Read-aloud voice (plan 10 §7.8 item 9). Phone's voice (false) cancels a running natural-voice download (the
     * `.part` files stay). Natural (true) is stored only once the files are installed: when missing, it downloads
     * first, with no metered-data question (the row names the size).
     */
    public suspend fun setNaturalVoice(on: Boolean): Outcome<Unit> {
        val natural = ModelCatalog.kokoroEn
        if (!on) {
            if (running?.pack == natural.id) cancelDownload()
            mutableState.update { it.copy(naturalVoice = false) }
            return prefs.setReadAloudNatural(false)
        }
        if (installer.installed(natural) == null) {
            return when (val result = download(natural.id)) {
                is Outcome.Failed -> result
                is Outcome.Ok -> Unit.ok() // download() turns the choice on once installed
            }
        }
        mutableState.update { it.copy(naturalVoice = true) }
        return prefs.setReadAloudNatural(true)
    }

    /**
     * Downloads and verifies [pack] (idempotent when installed). The caller may stop waiting; the download goes on until
     * [cancelDownload]. A second download while one runs is `DownloadBusy`.
     */
    public suspend fun download(pack: PackId, replaceExisting: Boolean = false): Outcome<InstalledPack> {
        val spec = specOf(pack) ?: return busy()
        val probed = device()
        val job =
            lane.withLock {
                if (closed) return@withLock null
                if (running != null) return busy()
                val partial = installer.partialBytes(spec)
                val installedNow = installer.installed(spec) != null
                val support = PackSupport.supportFor(spec, probed, installedNow, partial, replaceExisting)
                if (support is Support.Blocked) {
                    val problem = support.block.problem
                    mutableState.update { it.copy(lastProblem = problem) }
                    return if (problem == null) Failure.Unavailable(FeatureId("voice")).failed() else Failure.Media(problem).failed()
                }
                generation++
                mutableState.update {
                    it.copy(job = PackJob(spec.id, PackJobPhase.Downloading(partial, spec.totalBytes)), lastProblem = null)
                }
                val deferred = scope.async { runInstall(spec, replaceExisting) }
                Running(spec.id, deferred).also { running = it }
            } ?: return Failure.Unavailable(FeatureId("voice")).failed()
        return job.deferred.await()
    }

    private suspend fun runInstall(spec: ModelPackSpec, replaceExisting: Boolean): Outcome<InstalledPack> {
        val result =
            try {
                installer.install(spec, replaceExisting) { progress ->
                    val phase =
                        when (progress) {
                            is InstallProgress.Downloading -> PackJobPhase.Downloading(progress.received, progress.total)
                            InstallProgress.Verifying -> PackJobPhase.Verifying
                        }
                    mutableState.update { it.copy(job = PackJob(spec.id, phase)) }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val partial = installer.partialBytes(spec)
                    lane.withLock {
                    running = null
                    mutableState.update {
                            it.copy(job = null, packs = it.packs.with(spec.id, if (partial > 0) PackStatus.Partial(partial, spec.totalBytes) else PackStatus.Missing))
                        }
                    }
                }
                throw e
            }
        lane.withLock {
            running = null
            when (result) {
                is Outcome.Ok -> {
                    mutableState.update { it.copy(job = null, packs = it.packs.with(spec.id, PackStatus.Installed)) }
                    if (spec.kind == PackKind.NaturalVoice) {
                        mutableState.update { it.copy(naturalVoice = true) }
                        prefs.setReadAloudNatural(true)
                    }
                }
                is Outcome.Failed -> {
                    val problem = (result.failure as? Failure.Media)?.problem
                    val partial = installer.partialBytes(spec)
                    mutableState.update {
                        it.copy(
                            job = null,
                            lastProblem = problem,
                            packs = it.packs.with(spec.id, if (partial > 0) PackStatus.Partial(partial, spec.totalBytes) else PackStatus.Missing),
                        )
                    }
                }
            }
        }
        return result
    }

    /** Stops the running download; its `.part` files stay for the next attempt (M3). */
    public suspend fun cancelDownload() {
        val job = lane.withLock { running?.also { generation++ } } ?: return
        job.deferred.cancel()
        job.deferred.join()
    }

    /** Deletes every file of the pack, partials included; refused while that pack downloads (B-MEDIA-013). */
    public suspend fun remove(pack: PackId): Outcome<Unit> {
        val spec = specOf(pack) ?: return Unit.ok()
        lane.withLock {
            if (running?.pack == pack) return busy()
            generation++
        }
        val result = installer.remove(spec)
        if (result is Outcome.Ok) {
            setStatus(pack, PackStatus.Missing)
            if (spec.kind == PackKind.NaturalVoice) {
                mutableState.update { it.copy(naturalVoice = false) }
                prefs.setReadAloudNatural(false)
            }
        }
        return result
    }

    /** Deletes only the kept `.part` files ("Discard 120 MB"); refused while that pack downloads. */
    public suspend fun discardPartial(pack: PackId): Outcome<Unit> {
        val spec = specOf(pack) ?: return Unit.ok()
        lane.withLock {
            if (running?.pack == pack) return busy()
            generation++
        }
        val result = installer.discardPartial(spec)
        if (result is Outcome.Ok && mutableState.value.packs[pack] is PackStatus.Partial) setStatus(pack, PackStatus.Missing)
        return result
    }

    public val downloading: PackId? get() = running?.pack

    /** Dispose: in-flight readiness and device work never publish afterwards. Running downloads are cancelled. */
    public suspend fun close() {
        val job =
            lane.withLock {
                closed = true
                generation++
                running
            }
        job?.deferred?.cancel()
    }

    private suspend fun setStatus(pack: PackId, status: PackStatus) {
        lane.withLock { mutableState.update { it.copy(packs = it.packs.with(pack, status)) } }
    }

    private fun specOf(pack: PackId) = catalog.firstOrNull { it.id == pack }

    private fun busy(): Outcome<Nothing> = Failure.Media(MediaProblem.DownloadBusy).failed()

    private fun Map<PackId, PackStatus>.with(pack: PackId, status: PackStatus) =
        toMutableMap().apply { put(pack, status) }.toImmutableMap()
}
