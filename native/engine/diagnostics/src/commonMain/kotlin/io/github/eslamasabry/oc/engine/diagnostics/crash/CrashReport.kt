package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlin.concurrent.atomics.AtomicBoolean

public enum class CrashReportError {
    Unavailable,
    CaptureDisabled,
    NoCapture,
    StorageFailed,
    StalePreview,
    ShareUnavailable,
    Busy,
}

/** Identity, rather than matching text, authorizes this exact point-in-time preview. */
public class CrashReportPreview
internal constructor(
    public val text: String,
    public val recordCount: Int,
    public val byteCount: Int,
    internal val issuer: Any,
    internal val controller: CrashDiagnostics,
    internal val epoch: Int,
    internal val revision: Int,
) {
    public fun matches(state: CrashDiagnosticsState): Boolean =
        state.available &&
            state.enabled &&
            !state.storageFailed &&
            state.consentEpoch == epoch &&
            state.revision == revision
}

public data class CrashReportResult(
    val preview: CrashReportPreview? = null,
    val error: CrashReportError? = null,
)

/** Category-only, bounded evidence. Preview has no persistence or platform side effects. */
public class CrashReportBuilder(
    private val load: suspend () -> CrashDiagnostics?,
    private val header: suspend () -> String,
    private val shareText: suspend (CrashReportPreview) -> Outcome<Unit>,
) {
    private val issuer = Any()
    private val sharing = AtomicBoolean(false)

    public suspend fun preview(): CrashReportResult {
        val controller =
            controller() ?: return CrashReportResult(error = CrashReportError.Unavailable)
        val state = controller.state.value
        val error =
            when {
                !state.available -> CrashReportError.Unavailable
                state.storageFailed -> CrashReportError.StorageFailed
                !state.enabled -> CrashReportError.CaptureDisabled
                state.records.isEmpty() -> CrashReportError.NoCapture
                else -> null
            }
        if (error != null) return CrashReportResult(error = error)
        val heading =
            when (val result = attempt({ Failure.Device() }) { header() }) {
                is Outcome.Ok -> result.value
                is Outcome.Failed -> return CrashReportResult(error = CrashReportError.Unavailable)
            }
        val text = StringBuilder(heading)
        var bytes = heading.encodeToByteArray().size
        var count = 0
        for (record in state.records.take(MAX_ENTRIES)) {
            val line = "${record.at} | ${record.source.wire} | ${record.category.wire}\n"
            val size = line.encodeToByteArray().size
            if (bytes + size > MAX_BYTES) break
            text.append(line)
            bytes += size
            count++
        }
        if (count == 0) return CrashReportResult(error = CrashReportError.NoCapture)
        return CrashReportResult(
            CrashReportPreview(
                text.toString(),
                count,
                bytes,
                issuer,
                controller,
                state.consentEpoch,
                state.revision,
            )
        )
    }

    /**
     * Re-read the current controller after loading, so clearing during loading revokes the preview.
     */
    public suspend fun share(preview: CrashReportPreview): CrashReportError? {
        if (preview.issuer !== issuer) return CrashReportError.StalePreview
        if (!sharing.compareAndSet(false, true)) return CrashReportError.Busy
        try {
            val controller = controller() ?: return CrashReportError.StalePreview
            if (controller !== preview.controller) return CrashReportError.StalePreview
            val state = controller.state.value
            if (
                !state.available ||
                    !state.enabled ||
                    state.storageFailed ||
                    state.consentEpoch != preview.epoch ||
                    state.revision != preview.revision
            ) {
                return CrashReportError.StalePreview
            }
            return when (val result = attempt({ Failure.Device() }) { shareText(preview) }) {
                is Outcome.Failed -> CrashReportError.ShareUnavailable
                is Outcome.Ok ->
                    if (result.value is Outcome.Ok) null else CrashReportError.ShareUnavailable
            }
        } finally {
            sharing.store(false)
        }
    }

    private suspend fun controller(): CrashDiagnostics? =
        when (val result = attempt({ Failure.StorageRefused() }) { load() }) {
            is Outcome.Ok -> result.value
            is Outcome.Failed -> null
        }

    public companion object {
        public const val MAX_ENTRIES: Int = 20
        public const val MAX_BYTES: Int = 16 * 1024
    }
}
