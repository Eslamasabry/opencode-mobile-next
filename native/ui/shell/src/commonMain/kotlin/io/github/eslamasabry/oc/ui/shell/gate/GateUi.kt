package io.github.eslamasabry.oc.ui.shell.gate

/** What the gate draws (plan 05 §8.2, 07 §5 A01). No copy here: the screen picks the words for each value. */
public sealed interface GateUi {
    /**
     * "Opening…" while the app is not ready. [wordless] until the locale mirror exists, so no text shows in the wrong
     * language (05 §8.2); [offerRetry] after [GatePresenter.SLOW_MS].
     */
    public data class Opening(val wordless: Boolean, val offerRetry: Boolean) : GateUi

    /** "Updating your app…"; after [GatePresenter.SLOW_MS] "Still updating" and Try again ([offerRetry]). */
    public data class Updating(val offerRetry: Boolean) : GateUi

    /** A failure screen with every way out as a visible row; [confirming] is the row whose confirmation is open. */
    public data class Failed(
        val screen: GateFailureScreen,
        val rows: List<GateRow>,
        val neededBytes: Long? = null,
        val detail: String? = null,
        val confirming: GateRow? = null,
    ) : GateUi

    /**
     * The app shows. [reenter] non-empty → the one-time sheet; [deferredNotice] → the one status-line notice
     * (02 §3.7 item 4).
     */
    public data class Open(val reenter: List<GateReentry>, val deferredNotice: Boolean) : GateUi
}

/** Which failure screen (plan 05 §8.1 "What the person sees"). */
public enum class GateFailureScreen { Failed, LowSpace, SaveFailed }

/** The gate's rows, in the order they are drawn. Restore and Start fresh ask first. */
public enum class GateRow { TryAgain, OpenStorage, Restore, StartFresh, Report }
