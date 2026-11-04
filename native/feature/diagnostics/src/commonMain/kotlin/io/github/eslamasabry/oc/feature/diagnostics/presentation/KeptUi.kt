package io.github.eslamasabry.oc.feature.diagnostics.presentation

import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind

/** One saved crash report row: when, and only its fixed category (never a message). */
public data class KeptCrashRow(
    val time: String,
    val category: String,
    val source: io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource =
        io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource.Platform,
)

/** One Android exit row: its kind in words (the screen maps [kind]) and when. */
public data class KeptExitRow(val kind: ExitKind, val time: String)

public sealed interface KeptExits {
    public data object Loading : KeptExits

    public data object Unsupported : KeptExits

    public data object Failed : KeptExits

    /**
     * Newest first; [helperDeaths] are the built-in Linux helper's own deaths, kept apart from the
     * app's exits.
     */
    public data class Ready(val rows: List<KeptExitRow>, val helperDeaths: Int) : KeptExits
}

/** A fixed plain outcome line (B-SEC-053): never report text or a private error. */
public enum class KeptNotice {
    CrashUnavailable,
    CrashUpdateFailed,
    CaptureOff,
    NothingToShare,
    Stale,
    ShareFailed,
    DetailsUnavailable,
    ClearFailed,
}

/** The open preview: exactly the text Share hands the share sheet. */
public data class KeptPreview(val text: String, val records: Int, val bytes: Int)

public data class KeptUi(
    /** The switch can be used (store open and healthy). */
    val crashAvailable: Boolean = false,
    val crashOn: Boolean = false,
    val crashRows: List<KeptCrashRow> = emptyList(),
    val exits: KeptExits = KeptExits.Loading,
    val errorsKept: Int = 0,
    val preview: KeptPreview? = null,
    val notice: KeptNotice? = null,
    /**
     * A share is running: Share stays off so a second tap cannot open the sheet twice (B-SEC-054).
     */
    val sharing: Boolean = false,
    val confirmDelete: Boolean = false,
    val confirmClear: Boolean = false,
)

/** What the page can do. Each name says its one target. */
public interface KeptActions {
    public fun onBack()

    public fun onCrashCapture(on: Boolean)

    public fun onPreviewCrashReport()

    public fun onShareCrashReport()

    public fun onClosePreview()

    public fun onDeleteCrashReports()

    public fun onConfirmDelete()

    public fun onClearErrors()

    public fun onConfirmClear()

    public fun onCancelConfirm()

    public fun onRetryExits()

    public fun onDismissNotice()
}
