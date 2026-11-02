package io.github.eslamasabry.oc.feature.voice.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.language.VoiceLanguage
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.ModelPackSpec
import io.github.eslamasabry.oc.engine.voice.state.ModelPacksState
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One speech model row (Flutter `_packChoice`). */
public data class PackRowUi(
    val id: PackId,
    val bytes: Long,
    val status: PackStatus,
    /** The phone's memory, in MB, and what the pack needs, for "needs about 2000 MB; this one has 1500 MB". */
    val needsRamMb: Long,
    val phoneRamMb: Long?,
) {
    val installed: Boolean get() = status == PackStatus.Installed
    val unsupported: Boolean get() = status is PackStatus.Unsupported
}

/** The running download or verification. */
public data class PackJobUi(val pack: PackId, val received: Long, val total: Long, val verifying: Boolean) {
    /** 0..1, or null while verifying. */
    val fraction: Float? get() = if (verifying || total <= 0) null else (received.toFloat() / total).coerceIn(0f, 1f)
}

/** The read-aloud voice: the phone's, or the downloadable natural voice. */
public data class NaturalVoiceUi(
    val bytes: Long,
    val installed: Boolean,
    val chosen: Boolean,
    val downloading: Boolean,
    /** False while the offline speech engine is not in the app: the row says so instead of offering a dead choice. */
    val available: Boolean,
)

public sealed interface VoiceConfirm {
    public data class DeletePack(val pack: PackId, val bytes: Long) : VoiceConfirm

    public data object RemoveNatural : VoiceConfirm
}

public data class VoiceSettingsUi(
    val loading: Boolean = true,
    /** False when voice could not start on this phone; the page says so plainly. */
    val available: Boolean = true,
    val engine: SpeechEngineKind = SpeechEngineKind.PhoneRecognizer,
    val hasMicrophone: Boolean = true,
    val packs: List<PackRowUi> = emptyList(),
    val selected: PackId = PackId("base"),
    val job: PackJobUi? = null,
    /** Why the last download or action failed (one plain line + Details). */
    val problem: MediaProblem? = null,
    val language: VoiceLanguage = VoiceLanguage.Auto,
    val languagePicking: Boolean = false,
    val autoSend: Boolean = false,
    val natural: NaturalVoiceUi? = null,
    val confirm: VoiceConfirm? = null,
) {
    val busy: Boolean get() = job != null
    val selectedRow: PackRowUi? get() = packs.firstOrNull { it.id == selected }
}

/**
 * Settings › Voice typing (Flutter `VoiceSettingsScreen` + `NaturalVoiceSection`, plan 10 §7.8): choose a speech model
 * (a tap only chooses; the one primary action downloads it), see the download and the SHA-256 verification with Cancel,
 * download again or delete, pick the transcription language, turn "Send when I stop talking" on or off (off by
 * default), and choose the read-aloud voice. A download keeps running when the page closes.
 */
public class VoiceSettingsPresenter(
    private val hub: VoiceHub,
    private val scope: CoroutineScope,
    private val leave: () -> Unit,
) {
    private val mutable = MutableStateFlow(VoiceSettingsUi())
    public val state: StateFlow<VoiceSettingsUi> = mutable.asStateFlow()

    private var parts: VoiceParts? = null
    private var device: DeviceProbeResult? = null
    private val ownProblem = MutableStateFlow<MediaProblem?>(null)

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        when (val loaded = hub.parts()) {
            is Outcome.Failed -> mutable.update { it.copy(loading = false, available = false) }
            is Outcome.Ok -> {
                val p = loaded.value
                parts = p
                scope.launch {
                    device = p.models.device()
                    p.models.refresh()
                    render(p.models.state.value)
                }
                p.models.state.collect { render(it) }
            }
        }
    }

    private fun render(s: ModelPacksState) {
        val p = parts ?: return
        val known = device as? DeviceProbeResult.Known
        val rows =
            p.models.speechPacks.map { spec ->
                PackRowUi(
                    id = spec.id,
                    bytes = spec.totalBytes,
                    status = s.packs[spec.id] ?: PackStatus.Missing,
                    needsRamMb = spec.minTotalRamMb,
                    phoneRamMb = known?.totalRamMb,
                )
            }
        val job =
            s.job?.let { j ->
                when (val phase = j.phase) {
                    is PackJobPhase.Downloading -> PackJobUi(j.pack, phase.received, phase.total, verifying = false)
                    PackJobPhase.Verifying -> PackJobUi(j.pack, 0, 0, verifying = true)
                }
            }
        val natural: ModelPackSpec = p.models.naturalPack
        mutable.update {
            it.copy(
                loading = false,
                available = true,
                engine = p.engine,
                hasMicrophone = known?.hasMicrophone ?: true,
                packs = rows,
                selected = s.selected,
                job = job,
                problem = ownProblem.value,
                language = s.language,
                autoSend = s.autoSend,
                natural =
                    NaturalVoiceUi(
                        bytes = natural.totalBytes,
                        installed = s.packs[natural.id] == PackStatus.Installed,
                        chosen = s.naturalVoice && p.naturalVoiceAvailable,
                        downloading = job?.pack == natural.id,
                        available = p.naturalVoiceAvailable,
                    ),
            )
        }
    }

    public fun back(): Unit = leave()

    /** A tap only chooses; the primary action downloads (Flutter: a list that acts on tap would mark a missing pack). */
    public fun choose(pack: PackId) {
        act { it.select(pack) }
    }

    /** The one primary action: download and verify the chosen pack. */
    public fun downloadSelected() {
        act { it.download(mutable.value.selected) }
    }

    public fun downloadAgain() {
        act { it.download(mutable.value.selected, replaceExisting = true) }
    }

    public fun cancelDownload() {
        val p = parts ?: return
        scope.launch { p.models.cancelDownload() }
    }

    public fun askDelete() {
        val row = mutable.value.selectedRow ?: return
        if (!row.installed || mutable.value.busy) return
        mutable.update { it.copy(confirm = VoiceConfirm.DeletePack(row.id, row.bytes)) }
    }

    public fun askRemoveNatural() {
        if (mutable.value.natural?.installed != true) return
        mutable.update { it.copy(confirm = VoiceConfirm.RemoveNatural) }
    }

    public fun dismissConfirm() {
        mutable.update { it.copy(confirm = null) }
    }

    public fun confirm() {
        val pending = mutable.value.confirm ?: return
        mutable.update { it.copy(confirm = null) }
        when (pending) {
            is VoiceConfirm.DeletePack -> act { it.remove(pending.pack) }
            VoiceConfirm.RemoveNatural -> {
                val p = parts ?: return
                act { it.remove(p.models.naturalPack.id) }
            }
        }
    }

    public fun discardPartial(pack: PackId) {
        act { it.discardPartial(pack) }
    }

    public fun openLanguages() {
        if (mutable.value.busy) return
        mutable.update { it.copy(languagePicking = true) }
    }

    public fun closeLanguages() {
        mutable.update { it.copy(languagePicking = false) }
    }

    public fun chooseLanguage(language: VoiceLanguage) {
        mutable.update { it.copy(languagePicking = false) }
        act { it.setLanguage(language) }
    }

    public fun setAutoSend(on: Boolean) {
        act { it.setAutoSend(on) }
    }

    /** false = the phone's voice; true = the natural voice (downloads it first when missing). */
    public fun setNaturalVoice(on: Boolean) {
        val natural = mutable.value.natural ?: return
        if (on && !natural.available) return
        act { it.setNaturalVoice(on) }
    }

    public fun dismissProblem() {
        ownProblem.value = null
        mutable.update { it.copy(problem = null) }
    }

    private fun act(block: suspend (VoiceModels) -> Outcome<Unit>) {
        val p = parts ?: return
        ownProblem.value = null
        mutable.update { it.copy(problem = null) }
        scope.launch {
            when (val result = block(p.models)) {
                is Outcome.Ok -> Unit
                is Outcome.Failed -> {
                    val problem =
                        when (val f = result.failure) {
                            is Failure.Media -> f.problem
                            is Failure.StorageRefused -> MediaProblem.DownloadNoSpace
                            else -> MediaProblem.DownloadIncomplete
                        }
                    // Another download already running is not an error the person caused: the progress says it.
                    if (problem != MediaProblem.DownloadBusy) {
                        ownProblem.value = problem
                        mutable.update { it.copy(problem = problem) }
                    }
                }
            }
        }
    }
}
