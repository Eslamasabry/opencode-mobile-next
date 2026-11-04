package io.github.eslamasabry.oc.feature.files.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageAccess
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileHandoff
import io.github.eslamasabry.oc.engine.files.FilePaths
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.files.PdfDocument
import io.github.eslamasabry.oc.engine.files.PdfLimits
import io.github.eslamasabry.oc.engine.files.PdfPages
import io.github.eslamasabry.oc.engine.files.ProjectFiles
import io.github.eslamasabry.oc.engine.files.WifiShare
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class FilePhase {
    Loading,
    Ready,
    Failed,
    NotConnected,
    Unavailable,
}

/** The Share on Wi-Fi sheet (07 §5 D05): stops on close, on Stop, or after 15 minutes. */
public sealed interface ShareSheetUi {
    public data object Starting : ShareSheetUi

    public data class Running(
        val url: String,
        val name: String,
        val downloads: Int,
        val remainingMs: Long?,
    ) : ShareSheetUi {
        override fun toString(): String =
            "Running(downloads=$downloads)" // the link holds the token
    }

    /** Not on Wi-Fi, a file that may hold secrets, or the server could not start. */
    public data class Refused(val failure: Failure) : ShareSheetUi

    public data object Ended : ShareSheetUi
}

public enum class FileNotice {
    OpenWithNone,
    OpenWithFailed,
    MentionFailed,
    Saved,
    SaveFailed,
}

public sealed interface PdfPageUi {
    public data object Loading : PdfPageUi

    public class Ready(public val png: ByteArray) : PdfPageUi

    public data object Failed : PdfPageUi
}

public data class FileUi(
    val path: String,
    val line: Int? = null,
    val phase: FilePhase = FilePhase.Loading,
    val body: FileBody? = null,
    val failure: Failure? = null,
    /** Original size of the opened file, for the binary body. */
    val sizeBytes: Long? = null,
    val canShare: Boolean = false,
    val canOpenWith: Boolean = false,
    /** Save the exact original bytes (B-MEDIA-001), where the platform can. */
    val canSave: Boolean = false,
    val saving: Boolean = false,
    val asking: Boolean = false,
    val opening: Boolean = false,
    val share: ShareSheetUi? = null,
    val notice: FileNotice? = null,
    /**
     * PDF: the shown page count (at most 200) once the document opened; pages fill in as they
     * render.
     */
    val pdfPageCount: Int = 0,
    val pdfFailure: Failure? = null,
    val pdfPages: ImmutableMap<Int, PdfPageUi> = persistentMapOf(),
    val storageBlock: SharedStorageBlock = SharedStorageBlock.None,
    val requestingStorage: Boolean = false,
) {
    val name: String
        get() = FilePaths.name(path)

    val folder: String
        get() = FilePaths.parent(path)
}

public interface FileActions {
    public fun requestStorage() {}

    public fun onResume() {}

    public fun back()

    public fun retry()

    /**
     * Ask about this file: `@path ` into this conversation's message, then back to the
     * conversation.
     */
    public fun ask()

    public fun openWith()

    /** Save the original bytes to a place the person picks. */
    public fun save()

    public fun shareOnWifi()

    public fun stopSharing()

    /** Closing the sheet also stops the share. */
    public fun closeShare()

    public fun dismissNotice()

    /** The viewer reached PDF page [index] (0-based): render it once. */
    public fun pdfPage(index: Int)
}

/**
 * D03 (07 §5): one file of the conversation's project. Reads once; the original bytes stay exact
 * for Open with and Share on Wi-Fi. Opened for a chat, so Ask about this file stages into that
 * chat's draft and returns to it.
 */
@AssistedInject
public class FileViewModel(
    @Assisted private val route: FilesRoute.File,
    private val connections: FilesConnections,
    private val mentions: ComposerMentions,
    private val share: WifiShare,
    private val handoffs: Set<FileHandoff>,
    private val pdfPages: Set<PdfPages>,
    private val nav: AppNavigator,
    private val appScope: AppCoroutineScope,
    private val savers: Set<FileSaver>,
    private val storageAccess: Set<SharedStorageAccess> = emptySet(),
) : ViewModel(), FileActions {
    private val local =
        MutableStateFlow(
            FileUi(
                FilePaths.normalize(route.path),
                route.line,
                canShare = share.supported,
                canOpenWith = handoffs.isNotEmpty(),
                canSave = savers.isNotEmpty(),
            )
        )
    public val state: StateFlow<FileUi> = local.asStateFlow()
    private var data: FileData? = null
    private var pdf: PdfDocument? = null
    private var loads = 0
    private var shareWanted = false

    /** The share this viewer started; another viewer's share is never adopted or stopped here. */
    private var shareId: Long? = null

    init {
        check(storageAccess.size <= 1) { "multiple shared-storage hosts" }
        load()
        viewModelScope.launch {
            share.state.collect { running ->
                if (!shareWanted) return@collect
                local.update { ui ->
                    when {
                        running != null && running.id == shareId ->
                            ui.copy(
                                share =
                                    ShareSheetUi.Running(
                                        running.url,
                                        running.name,
                                        running.downloads,
                                        (ui.share as? ShareSheetUi.Running)?.remainingMs,
                                    )
                            )
                        ui.share is ShareSheetUi.Running -> ui.copy(share = ShareSheetUi.Ended)
                        else -> ui
                    }
                }
            }
        }
        viewModelScope.launch {
            share.remainingMs.collect { left ->
                local.update { ui ->
                    val running = ui.share as? ShareSheetUi.Running ?: return@update ui
                    ui.copy(share = running.copy(remainingMs = left))
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = load()

    private fun load() {
        val mine = ++loads
        val identity = connections.sourceIdentity(route.from)
        val gateway = connections.gateway(route.from)
        when {
            gateway == null ->
                local.update { it.copy(phase = FilePhase.NotConnected, failure = null) }
            !connections.canBrowse(route.from) ->
                local.update { it.copy(phase = FilePhase.Unavailable, failure = null) }
            else -> {
                local.update {
                    it.copy(
                        phase = FilePhase.Loading,
                        failure = null,
                        requestingStorage = false,
                        asking = false,
                    )
                }
                viewModelScope.launch {
                    if (!checkStorage(identity) { mine == loads }) return@launch
                    if (mine != loads || connections.sourceIdentity(route.from) !== identity)
                        return@launch
                    val opened = ProjectFiles.open(gateway, route.path, route.line)
                    if (mine != loads || connections.sourceIdentity(route.from) !== identity)
                        return@launch
                    when (opened) {
                        is Outcome.Failed ->
                            local.update {
                                it.copy(phase = FilePhase.Failed, failure = opened.failure)
                            }
                        is Outcome.Ok -> {
                            data = opened.value.data
                            local.update {
                                it.copy(
                                    phase = FilePhase.Ready,
                                    body = opened.value.body,
                                    sizeBytes = opened.value.data.bytes.size.toLong(),
                                    failure = null,
                                )
                            }
                            (opened.value.body as? FileBody.Pdf)?.let { openPdf(it.bytes) }
                        }
                    }
                }
            }
        }
    }

    private var projectDirectory: String? = null

    private suspend fun checkStorage(identity: Any?, wanted: () -> Boolean = { true }): Boolean {
        val access = storageAccess.singleOrNull() ?: return true
        val directory = connections.directory(route.from)
        if (!wanted()) return false
        if (!sourceCurrent(identity)) return false
        val checked = access.check(route.from.profileId, directory, identity)
        if (!wanted()) return false
        if (!sourceCurrent(identity)) return false
        projectDirectory = directory
        return when (checked) {
            is Outcome.Failed -> {
                local.update {
                    it.copy(
                        phase = FilePhase.Failed,
                        storageBlock = SharedStorageBlock.None,
                        failure = checked.failure,
                        asking = false,
                    )
                }
                false
            }
            is Outcome.Ok -> {
                local.update { it.copy(storageBlock = checked.value) }
                if (checked.value == SharedStorageBlock.None) true
                else {
                    data = null
                    closeShare()
                    pdf?.close()
                    pdf = null
                    local.update {
                        it.copy(
                            phase = FilePhase.Failed,
                            body = null,
                            failure = null,
                            asking = false,
                            pdfPages = persistentMapOf(),
                            pdfPageCount = 0,
                        )
                    }
                    false
                }
            }
        }
    }

    private fun sourceCurrent(identity: Any?): Boolean {
        if (connections.sourceIdentity(route.from) === identity) return true
        data = null
        closeShare()
        local.update {
            it.copy(
                phase = FilePhase.NotConnected,
                storageBlock = SharedStorageBlock.None,
                requestingStorage = false,
                failure = null,
                asking = false,
                body = null,
            )
        }
        return false
    }

    override fun onResume() {
        // Flutter asks the storage gate only when the page opens; a resume re-checks only a
        // page that is still showing the shared-storage notice (back from the permission page).
        val l = local.value
        if (
            l.storageBlock != SharedStorageBlock.None &&
                !l.requestingStorage &&
                l.phase != FilePhase.Loading
        )
            load()
    }

    override fun requestStorage() {
        val access = storageAccess.singleOrNull() ?: return
        val block = local.value.storageBlock
        val identity = connections.sourceIdentity(route.from)
        val run = loads
        if (block == SharedStorageBlock.None || local.value.requestingStorage) return
        local.update { it.copy(requestingStorage = true) }
        viewModelScope.launch {
            val asked = access.request(route.from.profileId, projectDirectory, block, identity)
            if (run != loads) return@launch
            if (!sourceCurrent(identity)) return@launch
            local.update {
                it.copy(requestingStorage = false, failure = (asked as? Outcome.Failed)?.failure)
            }
            if (asked is Outcome.Ok) retry()
        }
    }

    private suspend fun openPdf(bytes: ByteArray) {
        val pages = pdfPages.singleOrNull()
        val refused =
            when {
                pages == null -> Failure.Media(MediaProblem.PdfNeedsAndroid10)
                bytes.size > PdfLimits.MAX_BYTES -> Failure.Media(MediaProblem.PdfTooLarge)
                else -> null
            }
        if (refused != null || pages == null) {
            local.update { it.copy(pdfFailure = refused) }
            return
        }
        when (val opened = pages.open(bytes)) {
            is Outcome.Failed -> local.update { it.copy(pdfFailure = opened.failure) }
            is Outcome.Ok -> {
                pdf = opened.value
                local.update {
                    it.copy(pdfPageCount = opened.value.pageCount.coerceIn(0, PdfLimits.MAX_PAGES))
                }
            }
        }
    }

    override fun pdfPage(index: Int) {
        val doc = pdf ?: return
        val ui = local.value
        if (index !in 0 until ui.pdfPageCount) return
        val current = ui.pdfPages[index]
        if (current is PdfPageUi.Loading || current is PdfPageUi.Ready) return
        local.update {
            it.copy(pdfPages = it.pdfPages.toPersistentMap().putting(index, PdfPageUi.Loading))
        }
        viewModelScope.launch {
            val page =
                when (val rendered = doc.renderPage(index, PDF_WIDTH_PX)) {
                    is Outcome.Ok -> PdfPageUi.Ready(rendered.value)
                    is Outcome.Failed -> PdfPageUi.Failed
                }
            local.update {
                it.copy(
                    pdfPages = keepNear(it.pdfPages.toPersistentMap().putting(index, page), index)
                )
            }
        }
    }

    /**
     * Rendered pages stay only near the page just shown (Flutter frees off-screen pages): at most
     * [PDF_WINDOW] pages each side and [PDF_BUDGET_BYTES] of PNGs, farthest first. A dropped page
     * renders again when it is reached.
     */
    private fun keepNear(pages: Map<Int, PdfPageUi>, center: Int): ImmutableMap<Int, PdfPageUi> {
        val near = pages.filter { (i, page) ->
            page !is PdfPageUi.Ready || kotlin.math.abs(i - center) <= PDF_WINDOW
        }
        var bytes = near.values.sumOf { (it as? PdfPageUi.Ready)?.png?.size?.toLong() ?: 0L }
        if (bytes <= PDF_BUDGET_BYTES) return near.toPersistentMap()
        val kept = near.toMutableMap()
        for (i in near.keys.sortedByDescending { kotlin.math.abs(it - center) }) {
            if (bytes <= PDF_BUDGET_BYTES || i == center) break
            val page = kept[i] as? PdfPageUi.Ready ?: continue
            kept.remove(i)
            bytes -= page.png.size
        }
        return kept.toPersistentMap()
    }

    override fun ask() {
        if (
            local.value.phase != FilePhase.Ready ||
                local.value.storageBlock != SharedStorageBlock.None
        )
            return
        if (local.value.asking) return
        val identity = connections.sourceIdentity(route.from)
        val run = loads
        local.update { it.copy(asking = true, notice = null) }
        viewModelScope.launch {
            if (!checkStorage(identity) { run == loads }) return@launch
            if (connections.sourceIdentity(route.from) !== identity) return@launch
            val directory = projectDirectory ?: connections.directory(route.from)
            val result = mentions.mention(route.from, local.value.path, directory)
            if (connections.sourceIdentity(route.from) !== identity) return@launch
            local.update { it.copy(asking = false) }
            when (result) {
                is Outcome.Failed -> local.update { it.copy(notice = FileNotice.MentionFailed) }
                is Outcome.Ok -> returnToConversation()
            }
        }
    }

    /**
     * Pops every Files page this conversation opened; the chat is under them (07 §5 D03
     * `popTo(ChatRoute)`).
     */
    private fun returnToConversation() {
        var guard = 0
        while (nav.top.value is FilesRoute && guard++ < MAX_POPS) nav.back()
    }

    override fun openWith() {
        if (
            local.value.phase != FilePhase.Ready ||
                local.value.storageBlock != SharedStorageBlock.None
        )
            return
        val file = data ?: return
        val handoff = handoffs.firstOrNull() ?: return
        if (local.value.opening) return
        local.update { it.copy(opening = true, notice = null) }
        viewModelScope.launch {
            val result = handoff.openWith(file)
            local.update {
                it.copy(
                    opening = false,
                    notice =
                        when (result) {
                            is Outcome.Ok -> null
                            is Outcome.Failed ->
                                if (result.failure == Failure.Media(MediaProblem.NoAppToOpen))
                                    FileNotice.OpenWithNone
                                else FileNotice.OpenWithFailed
                        },
                )
            }
        }
    }

    override fun save() {
        if (
            local.value.phase != FilePhase.Ready ||
                local.value.storageBlock != SharedStorageBlock.None
        )
            return
        val file = data ?: return
        val saver = savers.firstOrNull() ?: return
        if (local.value.saving) return
        local.update { it.copy(saving = true, notice = null) }
        viewModelScope.launch {
            val result = saver.save(file)
            local.update {
                it.copy(
                    saving = false,
                    notice =
                        when (result) {
                            is Outcome.Ok -> if (result.value) FileNotice.Saved else null
                            is Outcome.Failed -> FileNotice.SaveFailed
                        },
                )
            }
        }
    }

    override fun shareOnWifi() {
        if (
            local.value.phase != FilePhase.Ready ||
                local.value.storageBlock != SharedStorageBlock.None
        )
            return
        val file = data ?: return
        if (local.value.share == ShareSheetUi.Starting) return
        shareWanted = true
        local.update { it.copy(share = ShareSheetUi.Starting) }
        viewModelScope.launch {
            when (val started = share.start(file)) {
                is Outcome.Failed ->
                    local.update { it.copy(share = ShareSheetUi.Refused(started.failure)) }
                is Outcome.Ok -> {
                    shareId = started.value.id
                    // The sheet was closed while the server was starting: end the share it no
                    // longer shows.
                    if (!shareWanted) {
                        share.stop(started.value.id)
                        return@launch
                    }
                    local.update {
                        it.copy(
                            share =
                                ShareSheetUi.Running(
                                    started.value.url,
                                    started.value.name,
                                    started.value.downloads,
                                    null,
                                )
                        )
                    }
                }
            }
        }
    }

    override fun stopSharing() {
        viewModelScope.launch {
            shareId?.let { share.stop(it) }
            local.update { it.copy(share = ShareSheetUi.Ended) }
        }
    }

    override fun closeShare() {
        val wasSharing = shareWanted
        shareWanted = false
        local.update { it.copy(share = null) }
        val id = shareId
        if (wasSharing && id != null) viewModelScope.launch { share.stop(id) }
    }

    override fun dismissNotice(): Unit = local.update { it.copy(notice = null) }

    override fun onCleared() {
        // Leaving the file ends its share (the sheet cannot outlive the viewer) and frees the PDF
        // renderer.
        val id = shareId
        if (shareWanted && id != null) share.stopLater(id)
        val doc = pdf
        pdf = null
        if (doc != null) appScope.launch { doc.close() }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FilesRoute.File): FileViewModel
    }

    private companion object {
        const val PDF_WIDTH_PX = 1440
        const val PDF_WINDOW = 3
        const val PDF_BUDGET_BYTES = 24L * 1024 * 1024
        const val MAX_POPS = 64
    }
}
