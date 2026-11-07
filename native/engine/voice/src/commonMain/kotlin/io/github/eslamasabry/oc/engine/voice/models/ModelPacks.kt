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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.toImmutableMap
import io.github.eslamasabry.oc.core.common.Redacted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The model manager (plan 10 §4.6 `ModelPacks`, Dart `VoiceModelManager` +
 * `NaturalVoiceController`). One job at a time; a download keeps running in [scope] when the page
 * or sheet that started it closes. Work that a later delete, selection, download, cancel or [close]
 * supersedes never publishes (B-MEDIA-030).
 */
public class ModelPacks(
    private val installer: PackInstaller,
    private val probe: DeviceProbe,
    private val prefs: VoicePrefs,
    private val scope: CoroutineScope,
    private val catalog: List<ModelPackSpec> = ModelCatalog.all,
    /** No answer from the network for this long while downloading = stalled (see [watchForStall]). */
    private val stallTimeout: Duration = 60.seconds,
) {
    private val mutableState =
        MutableStateFlow(
            ModelPacksState(
                selected = ModelCatalog.speechOrDefault(prefs.selectedPack()).id,
                language = VoiceLanguage.fromStoredId(prefs.language()),
                autoSend = prefs.autoSend(),
                naturalVoice = prefs.readAloudNatural(),
            )
        )
    public val state: StateFlow<ModelPacksState> = mutableState.asStateFlow()

    private val lane = Mutex()
    private var generation = 0L
    private var closed = false
    private var device: DeviceProbeResult? = null
    private var running: Running? = null

    /** One admitted download. [result] is what the caller gets: the install's own outcome, or the interruption's. */
    private class Running(val pack: PackId, val owner: String?) {
        lateinit var deferred: Deferred<Outcome<InstalledPack>>
        val result = CompletableDeferred<Outcome<InstalledPack>>()

        /** Bumped on every answer from the network; the stall watch compares it between two looks. */
        var activity = 0L
        var interruptedBy: MediaProblem? = null
    }

    public val speechPacks: List<ModelPackSpec>
        get() = catalog.filter { it.kind == PackKind.Speech }

    /** Bytes already in the pack's kept `.part` files. */
    public suspend fun partialBytes(pack: PackId): Long =
        specOf(pack)?.let { installer.partialBytes(it) } ?: 0L

    public val selectedSpeech: ModelPackSpec
        get() {
            val speech = catalog.filter { it.kind == PackKind.Speech }
            return speech.firstOrNull { it.id == mutableState.value.selected } ?: speech.first()
        }

    /**
     * The device facts, probed once per manager (re-probed by [refresh]). Stale probes never
     * publish.
     */
    public suspend fun device(): DeviceProbeResult {
        device?.let {
            return it
        }
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
            mutableState.update {
                it.copy(packs = it.packs.toMutableMap().apply { putAll(rows) }.toImmutableMap())
            }
        }
    }

    private suspend fun statusOf(pack: ModelPackSpec, device: DeviceProbeResult): PackStatus {
        if (installer.installed(pack) != null) return PackStatus.Installed
        val partial = installer.partialBytes(pack)
        val support =
            PackSupport.supportFor(pack, device, installed = false, partialBytes = partial)
        if (support is Support.Blocked && support.block != PackBlock.Storage)
            return PackStatus.Unsupported(support.block.problem)
        if (partial > 0) return PackStatus.Partial(partial, pack.totalBytes)
        if (support is Support.Blocked) return PackStatus.Unsupported(support.block.problem)
        return PackStatus.Missing
    }

    /**
     * The selected speech pack when it is installed and this phone's RAM allows it; null → setup is
     * needed.
     */
    public suspend fun readySpeechPack(): InstalledPack? {
        val pack = selectedSpeech
        val support = PackSupport.supportFor(pack, device(), installed = true)
        if (support is Support.Blocked) return null
        return installer.installed(pack)
    }

    public suspend fun installed(pack: PackId): InstalledPack? =
        specOf(pack)?.let { installer.installed(it) }

    /** Full SHA-256 check after a recognizer load failure; a mismatch makes the pack Missing. */
    public suspend fun verifyFully(pack: PackId): Boolean {
        val spec = specOf(pack) ?: return false
        val ok = installer.verifyFully(spec)
        if (!ok) setStatus(pack, PackStatus.Missing)
        return ok
    }

    /** Refused while a download, verification or load runs (Dart `selectPack` returns early). */
    public suspend fun select(pack: PackId): Outcome<Unit> = lane.withLock {
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
     * Read-aloud voice (plan 10 §7.8 item 9). Phone's voice (false) cancels a running natural-voice
     * download (the `.part` files stay). Natural (true) is stored only once the files are
     * installed: when missing, it downloads first, with no metered-data question (the row names the
     * size).
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
     * Downloads and verifies [pack] (idempotent when installed). The caller may stop waiting; the
     * download goes on until [cancelDownload]. A second download while one runs is `DownloadBusy`.
     */
    public suspend fun download(
        pack: PackId,
        replaceExisting: Boolean = false,
    ): Outcome<InstalledPack> = downloadFor(pack, replaceExisting, null)

    /** A caller may cancel only the download it admitted with this opaque owner id. */
    public suspend fun downloadOwned(pack: PackId, owner: String): Outcome<InstalledPack> {
        if (owner.isBlank()) return busy()
        return downloadFor(pack, false, owner)
    }

    private suspend fun downloadFor(
        pack: PackId,
        replaceExisting: Boolean,
        owner: String?,
    ): Outcome<InstalledPack> {
        val spec = specOf(pack) ?: return busy()
        val probed = device()
        val run =
            lane.withLock {
                if (closed) return@withLock null
                if (running != null) return busy()
                val installedNow = installer.installed(spec) != null
                // A re-download of an installed pack stages apart and never resumes the first download's parts.
                val partial = if (replaceExisting && installedNow) 0L else installer.partialBytes(spec)
                val support =
                    PackSupport.supportFor(spec, probed, installedNow, partial, replaceExisting)
                if (support is Support.Blocked) {
                    val problem = support.block.problem
                    mutableState.update { it.copy(lastProblem = problem, lastProblemPack = problem?.let { spec.id }) }
                    return if (problem == null) Failure.Unavailable(FeatureId("voice")).failed()
                    else Failure.Media(problem).failed()
                }
                generation++
                mutableState.update {
                    it.copy(
                        job = PackJob(spec.id, PackJobPhase.Downloading(partial, spec.totalBytes)),
                        lastProblem = null,
                        lastProblemPack = null,
                    )
                }
                Running(spec.id, owner).also { r ->
                    r.deferred = scope.async { runInstall(spec, replaceExisting, r) }
                    running = r
                }
            } ?: return Failure.Unavailable(FeatureId("voice")).failed()
        val watch = scope.launch { watchForStall(run) }
        return try {
            run.result.await()
        } finally {
            if (run.result.isCompleted) watch.cancel()
        }
    }

    /**
     * The backstop under the installer's own timeouts: a platform socket that ignores cancellation (a connect stuck
     * on an address that never answers) would otherwise leave "0 MB of 148 MB" on screen for minutes. When nothing
     * came from the network for [stallTimeout] while downloading, the download stops with `DownloadTimedOut`.
     */
    private suspend fun watchForStall(run: Running) {
        var seen = run.activity
        while (!run.result.isCompleted) {
            delay(stallTimeout)
            if (run.result.isCompleted) return
            val phase = state.value.job?.takeIf { it.pack == run.pack }?.phase
            val now = run.activity
            if (phase is PackJobPhase.Downloading && now == seen) {
                interrupt(run, MediaProblem.DownloadTimedOut)
                return
            }
            seen = now
        }
    }

    @Suppress("TooGenericExceptionCaught") // the last line of defence: any surprise ends the job as a failure
    private suspend fun runInstall(
        spec: ModelPackSpec,
        replaceExisting: Boolean,
        run: Running,
    ): Outcome<InstalledPack> {
        val result =
            try {
                installer.install(spec, replaceExisting, onActivity = { run.activity++ }) { progress ->
                    val phase =
                        when (progress) {
                            is InstallProgress.Downloading ->
                                PackJobPhase.Downloading(progress.received, progress.total)
                            InstallProgress.Verifying -> PackJobPhase.Verifying
                        }
                    if (running === run) mutableState.update { it.copy(job = PackJob(spec.id, phase)) }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val status = restingStatus(spec)
                    lane.withLock { settle(run, spec, status, run.interruptedBy) }
                }
                if (run.interruptedBy == null) run.result.cancel(e)
                throw e
            } catch (e: Exception) {
                // An exception no layer expected must still end the job: a page left on "Downloading" forever is
                // worse than a plain failure.
                Failure.Media(MediaProblem.DownloadIncomplete, Redacted.shape(e)).failed()
            }
        lane.withLock {
            when (result) {
                is Outcome.Ok -> {
                    settle(run, spec, PackStatus.Installed, null)
                    if (spec.kind == PackKind.NaturalVoice) {
                        mutableState.update { it.copy(naturalVoice = true) }
                        prefs.setReadAloudNatural(true)
                    }
                }
                is Outcome.Failed -> {
                    val problem = (result.failure as? Failure.Media)?.problem ?: MediaProblem.DownloadIncomplete
                    settle(run, spec, restingStatus(spec), problem)
                }
            }
        }
        run.result.complete(result)
        return result
    }

    /**
     * The row once a download stops without installing: a re-download that was cancelled or failed leaves the
     * installed pack Installed (E13-S04); otherwise the kept `.part` bytes, or Missing.
     */
    private suspend fun restingStatus(spec: ModelPackSpec): PackStatus {
        if (installer.installed(spec) != null) return PackStatus.Installed
        val partial = installer.partialBytes(spec)
        return if (partial > 0) PackStatus.Partial(partial, spec.totalBytes) else PackStatus.Missing
    }

    /** Under [lane]: ends [run]'s job (unless a newer one already replaced it) and records the pack's new row. */
    private fun settle(run: Running, spec: ModelPackSpec, status: PackStatus, problem: MediaProblem?) {
        val current = running === run
        if (current) running = null
        val newerSamePack = !current && running?.pack == spec.id
        mutableState.update {
            it.copy(
                job = if (current || it.job?.pack == spec.id && running == null) null else it.job,
                packs = if (newerSamePack) it.packs else it.packs.with(spec.id, status),
                lastProblem = if (problem != null && running == null) problem else it.lastProblem,
                lastProblemPack = if (problem != null && running == null) spec.id else it.lastProblemPack,
            )
        }
    }

    /**
     * Stops the running download because of [problem] (the connection dropped, the download stalled): unlike
     * [cancelDownload], the caller gets a failure and the page says why. The `.part` files stay. The job ends at once,
     * even when the platform takes a while to let go of a stuck socket.
     */
    public suspend fun interruptDownload(problem: MediaProblem) {
        val run = lane.withLock { running } ?: return
        interrupt(run, problem)
    }

    private suspend fun interrupt(run: Running, problem: MediaProblem) {
        val spec = specOf(run.pack) ?: return
        val status = restingStatus(spec)
        val ended =
            lane.withLock {
                if (running !== run || run.result.isCompleted) return@withLock false
                generation++
                run.interruptedBy = problem
                settle(run, spec, status, problem)
                true
            }
        if (!ended) return
        run.result.complete(Failure.Media(problem).failed())
        run.deferred.cancel()
    }

    /** The person read the problem ("Not now"): it is not shown again. */
    public fun clearProblem() {
        mutableState.update { it.copy(lastProblem = null, lastProblemPack = null) }
    }

    /** Stops the running download; its `.part` files stay for the next attempt (M3). */
    public suspend fun cancelDownload() {
        val job = lane.withLock { running?.also { generation++ } } ?: return
        job.deferred.cancel()
        job.deferred.join()
        endUnstarted(job)
    }

    /** A download cancelled before its first step never ran [runInstall]: end its job here so nothing waits on it. */
    private suspend fun endUnstarted(run: Running) {
        if (run.result.isCompleted) return
        val spec = specOf(run.pack) ?: return
        val status = restingStatus(spec)
        lane.withLock { settle(run, spec, status, null) }
        run.result.cancel()
    }

    public suspend fun ownsDownload(owner: String): Boolean = lane.withLock {
        running?.owner == owner
    }

    /**
     * Setup never cancels a download admitted by another screen or by a previous process
     * invocation.
     */
    public suspend fun cancelOwned(owner: String) {
        val job =
            lane.withLock { running?.takeIf { it.owner == owner }?.also { generation++ } } ?: return
        job.deferred.cancel()
        job.deferred.join()
        endUnstarted(job)
    }

    /**
     * Deletes every file of the pack, partials included; refused while that pack downloads
     * (B-MEDIA-013).
     */
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

    /**
     * Deletes only the kept `.part` files ("Discard 120 MB"); refused while that pack downloads.
     */
    public suspend fun discardPartial(pack: PackId): Outcome<Unit> {
        val spec = specOf(pack) ?: return Unit.ok()
        lane.withLock {
            if (running?.pack == pack) return busy()
            generation++
        }
        val result = installer.discardPartial(spec)
        if (result is Outcome.Ok && mutableState.value.packs[pack] is PackStatus.Partial)
            setStatus(pack, PackStatus.Missing)
        return result
    }

    public val downloading: PackId?
        get() = running?.pack

    /**
     * Dispose: in-flight readiness and device work never publish afterwards. Running downloads are
     * cancelled.
     */
    public suspend fun close() {
        val job = lane.withLock {
            closed = true
            generation++
            running
        }
        job?.deferred?.cancel()
        job?.result?.cancel()
    }

    private suspend fun setStatus(pack: PackId, status: PackStatus) {
        lane.withLock { mutableState.update { it.copy(packs = it.packs.with(pack, status)) } }
    }

    private fun specOf(pack: PackId) = catalog.firstOrNull { it.id == pack }

    private fun busy(): Outcome<Nothing> = Failure.Media(MediaProblem.DownloadBusy).failed()

    private fun Map<PackId, PackStatus>.with(pack: PackId, status: PackStatus) =
        toMutableMap().apply { put(pack, status) }.toImmutableMap()
}
