package io.github.eslamasabry.oc.feature.diagnostics.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnosticsState
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.CrashReportPreview
import io.github.eslamasabry.oc.engine.diagnostics.crashreport.DiagnosticsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page's logic, without Android or Compose: the crash consent switch, the preview the person reads before
 * sharing (only the builder-issued preview authorizes a share, B-SEC-051), Android's exit history read fresh each
 * time (B-SEC-059), and Clear. Every failure becomes one fixed [KeptNotice].
 */
public class KeptPresenter(private val host: KeptDiagnosticsHost, private val scope: CoroutineScope) : KeptActions {
    private val local = MutableStateFlow(KeptUi())
    private var issued: CrashReportPreview? = null
    private var onBack: () -> Unit = {}

    public val state: StateFlow<KeptUi> = local.asStateFlow()

    public fun start(back: () -> Unit) {
        onBack = back
        scope.launch {
            combine(host.crash, host.errorsKept) { crash, errors -> crash to errors }.collect { (crash, errors) ->
                local.update { it.withCrash(crash).copy(errorsKept = errors) }
            }
        }
        loadExits()
    }

    private fun KeptUi.withCrash(crash: CrashDiagnosticsState?): KeptUi {
        val usable = crash != null && crash.available && !crash.storageFailed
        return copy(
            crashAvailable = usable,
            crashOn = usable && crash.enabled,
            crashRows = if (usable) crash.records.map { KeptCrashRow(host.timeLabel(it.at), it.category.wire) } else emptyList(),
        )
    }

    private fun loadExits() {
        local.update { it.copy(exits = KeptExits.Loading) }
        scope.launch {
            val history = host.exitHistory()
            val exits =
                when {
                    history.error != null -> KeptExits.Failed
                    !history.supported -> KeptExits.Unsupported
                    else -> KeptExits.Ready(history.entries.map { KeptExitRow(it.kind, host.timeLabel(it.at)) }, history.helperExits.size)
                }
            local.update { it.copy(exits = exits) }
        }
    }

    override fun onBack() {
        onBack.invoke()
    }

    override fun onCrashCapture(on: Boolean) {
        scope.launch {
            val ok = host.setCrashCapture(on)
            // Turning it off deletes saved reports: an open preview no longer matches the evidence.
            if (ok && !on) dropPreview()
            if (!ok) local.update { it.copy(notice = KeptNotice.CrashUpdateFailed) }
        }
    }

    override fun onPreviewCrashReport() {
        scope.launch {
            val result = host.previewCrashReport()
            val preview = result.preview
            if (preview == null) {
                issued = null
                local.update { it.copy(preview = null, notice = notice(result.error)) }
            } else {
                issued = preview
                local.update {
                    it.copy(preview = KeptPreview(preview.text, preview.recordCount, preview.byteCount), notice = null)
                }
            }
        }
    }

    override fun onShareCrashReport() {
        val preview = issued ?: return
        if (local.value.sharing) return
        local.update { it.copy(sharing = true) }
        scope.launch {
            val result = host.shareCrashReport(preview)
            local.update { ui ->
                when {
                    result.opened -> ui.copy(sharing = false, notice = null)
                    // A stale preview is dropped: the person must preview the new evidence first.
                    result.error == DiagnosticsError.StalePreview ->
                        ui.copy(sharing = false, preview = null, notice = KeptNotice.Stale).also { issued = null }
                    else -> ui.copy(sharing = false, notice = notice(result.error))
                }
            }
        }
    }

    override fun onClosePreview() = dropPreview()

    private fun dropPreview() {
        issued = null
        local.update { it.copy(preview = null) }
    }

    override fun onDeleteCrashReports() {
        local.update { it.copy(confirmDelete = true) }
    }

    override fun onConfirmDelete() {
        local.update { it.copy(confirmDelete = false) }
        scope.launch {
            if (host.deleteCrashReports()) dropPreview() else local.update { it.copy(notice = KeptNotice.CrashUpdateFailed) }
        }
    }

    override fun onClearErrors() {
        local.update { it.copy(confirmClear = true) }
    }

    override fun onConfirmClear() {
        local.update { it.copy(confirmClear = false) }
        scope.launch {
            when (host.clearErrors()) {
                is Outcome.Ok -> dropPreview()
                is Outcome.Failed -> local.update { it.copy(notice = KeptNotice.ClearFailed) }
            }
        }
    }

    override fun onCancelConfirm() {
        local.update { it.copy(confirmDelete = false, confirmClear = false) }
    }

    override fun onRetryExits() = loadExits()

    override fun onDismissNotice() {
        local.update { it.copy(notice = null) }
    }

    private fun notice(error: DiagnosticsError?): KeptNotice =
        when (error) {
            DiagnosticsError.CaptureDisabled -> KeptNotice.CaptureOff
            DiagnosticsError.NoCapture -> KeptNotice.NothingToShare
            DiagnosticsError.StalePreview -> KeptNotice.Stale
            DiagnosticsError.ShareUnavailable, DiagnosticsError.Busy -> KeptNotice.ShareFailed
            DiagnosticsError.StorageFailed -> KeptNotice.CrashUnavailable
            else -> KeptNotice.DetailsUnavailable
        }
}
