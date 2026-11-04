package io.github.eslamasabry.oc.feature.setup.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the big line says (Flutter phone_setup_progress_screen.dart `_body` headline). [SetupUi.subject] fills it. */
public enum class SetupHeadline {
    Done,
    Failed,
    Interrupted,
    Paused,
    Checking,
    GettingStarted,
    Starting,
    Downloading,
    Unpacking,
    Installing,
}

/** The one plain failure line (Flutter `SetupProgressView.failureSentence`); technical text stays under Details. */
public sealed interface SetupFailureLine {
    /** `inAppServerOwnershipRecoveryBody`: Force stop in app settings, then reopen. */
    public data object Ownership : SetupFailureLine

    /** One of the engine's worded failures (`phoneSetupError*`). */
    public data class Error(val error: SetupError) : SetupFailureLine

    /** `setupProgressViewFailedDuring(stage)`. */
    public data class During(val stage: String) : SetupFailureLine

    /** `setupProgressViewFailedStep`. */
    public data object Step : SetupFailureLine

    /** `setupProgressViewFailedAt(title)`. */
    public data class At(val title: String) : SetupFailureLine

    /** `setupProgressViewFailedUnknown`. */
    public data object Unknown : SetupFailureLine
}

/** One step of the job as a row: its name, where it is, and its own measured line. */
public data class SetupStepUi(
    val id: String,
    val title: String,
    val state: ComponentState,
    /** The step's own stage words while it works (from the job), else null. */
    val stage: String? = null,
    /** "18" and "30 MB" (`SetupProgressRules.formatBytePair`) while it measures bytes. */
    val bytes: Pair<String, String>? = null,
    /** 0..100 while it measures a percent instead of bytes. */
    val percent: Int? = null,
    val version: String? = null,
)

/** The start page (screen A of the first setup): what Set up installs and roughly how big it is. */
public data class SetupStartUi(
    val downloadBytes: Long,
    /** "Git and SSH", "Python", "Node.js": joined by the screen with the locale's own list words. */
    val includes: ImmutableList<String>,
)

/** The live job (screen B). */
public data class SetupProgressUi(
    val state: SetupState,
    val headline: SetupHeadline,
    /** The step the headline names ("Downloading Node.js"). */
    val subject: String? = null,
    /** The working step, 1-based, of [of]; null before a step is working. */
    val step: Int? = null,
    val of: Int = 0,
    /** The working step's measured share, never backwards within a stage; null when it reports none. */
    val measured: Float? = null,
    /** "18 of 30 MB" parts of the working step. */
    val bytes: Pair<String, String>? = null,
    /** Minutes left once the engine measured a pace (never up within a job, at least 1). */
    val minutesLeft: Int? = null,
    val failure: SetupFailureLine? = null,
    val steps: ImmutableList<SetupStepUi> = persistentListOf(),
    val details: Redacted? = null,
) {
    val running: Boolean get() = state == SetupState.Running

    val canContinue: Boolean get() = (state == SetupState.Failed || state == SetupState.Interrupted || state == SetupState.Cancelled) &&
        failure != SetupFailureLine.Ownership
}

public sealed interface SetupPhase {
    /** The persisted job is being read: nothing is shown as true yet. */
    public data object Loading : SetupPhase

    public data class Start(val start: SetupStartUi) : SetupPhase

    public data class Progress(val progress: SetupProgressUi) : SetupPhase
}

/** SetupRoute.Phone's state. No copy: SetupScreen turns each value into `:ui:words`. */
public data class SetupUi(
    val phase: SetupPhase = SetupPhase.Loading,
    /** A command was sent and the engine has not answered yet. */
    val working: Boolean = false,
    /** Android's settings page did not open. */
    val settingsMissing: Boolean = false,
)

/** Intents only (ADR §9.1). */
public interface SetupActions {
    /** Set up OpenCode on this phone with the first setup's default selection. */
    public fun start()

    /** Pause: stops the job; finished steps stay and downloads resume (no confirmation, DATA-11). */
    public fun pause()

    /** Continue setup: the same steps again; the checks skip what finished. */
    public fun resume()

    /** Ownership recovery: Android's app page (Force stop). */
    public fun openAppSettings()

    public fun back()
}
