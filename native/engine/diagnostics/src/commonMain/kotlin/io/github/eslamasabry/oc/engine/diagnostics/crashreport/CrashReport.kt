package io.github.eslamasabry.oc.engine.diagnostics.crashreport

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnostics
import kotlin.concurrent.atomics.AtomicBoolean

/** Fixed device-diagnostics outcomes (port of Dart `DiagnosticsError`); never carries provider or native text. */
public enum class DiagnosticsError {
    Unavailable,
    InvalidLimit,
    StorageFailed,
    CaptureDisabled,
    NoCapture,
    StalePreview,
    ShareUnavailable,
    ResumeFailed,
    Busy,
}

/**
 * The immutable text the person previews and may explicitly share (B-SEC-047). Only the preview object the builder
 * issued can authorize a share: one made with this public constructor, even with identical text, is stale (B-SEC-051).
 */
public class CrashReportPreview(public val text: String, public val byteCount: Int, public val recordCount: Int) {
    internal var issued: IssuedEvidence? = null
        private set

    internal fun issue(evidence: IssuedEvidence): CrashReportPreview = also { issued = evidence }

    override fun toString(): String = "CrashReportPreview(records=$recordCount, bytes=$byteCount)"
}

/** Who issued a preview and which evidence it showed. */
internal class IssuedEvidence(
    val builder: CrashReportBuilder,
    val controller: CrashDiagnostics,
    val consentEpoch: Int,
    val revision: Int,
)

public data class CrashReportResult(val preview: CrashReportPreview? = null, val error: DiagnosticsError? = null)

/** [opened] means the system chooser opened, never that delivery completed. */
public data class CrashShareResult(val opened: Boolean, val error: DiagnosticsError? = null)

/** The platform's share sheet (Android: `ShareService.shareText`). True when the chooser opened. May throw. */
public fun interface CrashShareSheet {
    public suspend fun shareText(text: String): Boolean
}

/**
 * Port of `CrashReportBuilder` (B-SEC-046..054): builds only from the validated categories, sources and times of
 * [CrashDiagnostics]. Previewing changes neither consent nor storage and never shares; sharing hands exactly the
 * issued text to [shareSheet]. [shareSheet] null means this platform has no share channel.
 */
public class CrashReportBuilder(
    private val loadController: suspend () -> CrashDiagnostics?,
    private val shareSheet: CrashShareSheet?,
) {
    private val sharing = AtomicBoolean(false)

    public val shareSupported: Boolean get() = shareSheet != null

    public suspend fun preview(): CrashReportResult {
        val controller =
            when (val loaded = attempt(::unavailable) { loadController() }) {
                is Outcome.Ok -> loaded.value ?: return CrashReportResult(error = DiagnosticsError.Unavailable)
                is Outcome.Failed -> return CrashReportResult(error = DiagnosticsError.Unavailable)
            }
        val snapshot = controller.state.value
        if (!snapshot.available) return CrashReportResult(error = DiagnosticsError.Unavailable)
        if (snapshot.storageFailed) return CrashReportResult(error = DiagnosticsError.StorageFailed)
        if (!snapshot.enabled) return CrashReportResult(error = DiagnosticsError.CaptureDisabled)
        if (snapshot.records.isEmpty()) return CrashReportResult(error = DiagnosticsError.NoCapture)
        val text = StringBuilder(HEADER)
        var bytes = HEADER.encodeToByteArray().size
        var count = 0
        for (record in snapshot.records.take(MAX_ENTRIES)) {
            val line = "${record.at} | ${record.source.wire} | ${record.category.wire}\n"
            val lineBytes = line.encodeToByteArray().size
            if (bytes + lineBytes > MAX_BYTES) break
            text.append(line)
            bytes += lineBytes
            count++
        }
        if (count == 0) return CrashReportResult(error = DiagnosticsError.NoCapture)
        val preview =
            CrashReportPreview(text.toString(), bytes, count)
                .issue(IssuedEvidence(this, controller, snapshot.consentEpoch, snapshot.revision))
        return CrashReportResult(preview = preview)
    }

    public suspend fun share(preview: CrashReportPreview): CrashShareResult {
        val evidence = preview.issued
        if (evidence == null || evidence.builder !== this) return STALE
        if (!sharing.compareAndSet(false, true)) return CrashShareResult(opened = false, error = DiagnosticsError.Busy)
        try {
            val controller =
                when (val loaded = attempt(::unavailable) { loadController() }) {
                    is Outcome.Ok -> loaded.value
                    is Outcome.Failed -> return STALE
                }
            if (controller !== evidence.controller) return STALE
            val snapshot = controller.state.value
            if (!snapshot.available || !snapshot.enabled || snapshot.storageFailed ||
                snapshot.consentEpoch != evidence.consentEpoch || snapshot.revision != evidence.revision
            ) {
                return STALE
            }
            val sheet = shareSheet ?: return CrashShareResult(opened = false, error = DiagnosticsError.ShareUnavailable)
            val opened = (attempt(::unavailable) { sheet.shareText(preview.text) } as? Outcome.Ok)?.value == true
            return CrashShareResult(opened = opened, error = if (opened) null else DiagnosticsError.ShareUnavailable)
        } finally {
            sharing.store(false)
        }
    }

    // The thrown error's text is never kept: the failure carries no details.
    private fun unavailable(@Suppress("UNUSED_PARAMETER") error: Throwable): Failure = Failure.Device()

    public companion object {
        public const val MAX_BYTES: Int = 16 * 1024
        public const val MAX_ENTRIES: Int = 20
        public const val HEADER: String =
            "OpenCode Mobile crash report\n" +
                "Captured error categories and times only.\n" +
                "No messages, stacks or conversations are included.\n\n"

        private val STALE = CrashShareResult(opened = false, error = DiagnosticsError.StalePreview)
    }
}
