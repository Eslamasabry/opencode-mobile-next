package io.github.eslamasabry.oc.feature.background.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/** Why Android stopped the background connection (Flutter `BackgroundPauseReason`). */
public enum class PauseReason {
    TimeLimit,
    BatteryRestricted,
    UserStopped,
    DeviceRestarted,
    AppUpdated,
    Interrupted,
}

/** A durable pause the person has not dismissed yet (Flutter `BackgroundPauseNoticeState`). */
public data class PauseFacts(
    val reason: PauseReason,
    /** The stop time in the phone's own clock words ("3:10 PM"), formatted by the host. */
    val clock: String,
    /** The short day ("Sep 25") when the pause is not from today; null = today. */
    val day: String? = null,
    /**
     * A Resume for this very pause was accepted but Android did not confirm the start (B-ANDR-016
     * resumeFailed).
     */
    val resumeFailed: Boolean = false,
    /**
     * Android allows this exact pause to be resumed. Otherwise the visible action opens Keep
     * running.
     */
    val canResume: Boolean = true,
)

/**
 * What the background connection is doing right now, as the app knows it (Flutter
 * `BackgroundLiveController`).
 */
public data class BackgroundLiveFacts(
    /** The platform can run the live connection at all (PlatformCapabilities.backgroundService). */
    val supported: Boolean = false,
    /** The person's choice (the switch). */
    val enabled: Boolean = false,
    /** Android confirmed the foreground service is running. */
    val active: Boolean = false,
    /** A start, stop or resume is in flight: the switch does nothing until it settles. */
    val busy: Boolean = false,
    /** Android's foreground-service time limit turned live mode off (B-ANDR-014). */
    val stoppedByAndroid: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val pause: PauseFacts? = null,
    val resuming: Boolean = false,
)

/**
 * Why the switch did not end where the person put it (shown in plain words, never Android's text).
 */
public enum class SwitchFailure {
    NotEnabled,
    NotDisabled,
    NotificationsDenied,
    Unavailable,
}

/**
 * The live connection, bound by :androidApp over :android:background. Each command names its one
 * target.
 */
public interface BackgroundLive {
    public val facts: StateFlow<BackgroundLiveFacts>

    /** The switch. Answers the state Android ended in, or the typed reason it refused. */
    public suspend fun setEnabled(on: Boolean): Outcome<Boolean>

    /** Resume the paused connection; true only once Android confirms the foreground start. */
    public suspend fun resume(): Outcome<Boolean>

    /** Hide this pause's notice; a later, new pause shows again (B-ANDR-018). */
    public suspend fun dismissPause()

    /** Opens Android's "unrestricted battery" request. */
    public suspend fun allowUnrestrictedBattery()

    /** Reads Android's state again (the person may have changed it outside the app). */
    public fun refresh() {}
}

/**
 * The status row: the disagreement between the choice and the service shows here (Flutter
 * `_BackgroundStatusRow`).
 */
public enum class LiveStatusLine {
    StoppedByAndroid,
    Running,
    Starting,
    Off,
}

public data class BackgroundUi(
    val supported: Boolean,
    val switchOn: Boolean,
    val switchEnabled: Boolean,
    val batteryRow: Boolean,
    val batteryUnrestricted: Boolean,
    val status: LiveStatusLine,
    /** Restart is offered on the status row only after Android stopped it. */
    val restart: Boolean,
    val timeoutNotice: Boolean,
    val pause: PauseFacts?,
    val resuming: Boolean,
    val failure: SwitchFailure?,
)

/** Pure mapping, so every rule is a plain test (no Compose). */
public fun backgroundUi(facts: BackgroundLiveFacts, failure: SwitchFailure? = null): BackgroundUi {
    val stopped = facts.stoppedByAndroid
    val running = facts.enabled && facts.active
    val starting = facts.enabled && !facts.active
    return BackgroundUi(
        supported = facts.supported,
        switchOn = facts.enabled,
        switchEnabled = !facts.busy,
        batteryRow = facts.enabled,
        batteryUnrestricted = facts.batteryUnrestricted,
        status =
            when {
                stopped -> LiveStatusLine.StoppedByAndroid
                running -> LiveStatusLine.Running
                starting -> LiveStatusLine.Starting
                else -> LiveStatusLine.Off
            },
        restart = stopped && !facts.busy,
        timeoutNotice = stopped,
        // The pause notice yields while a resume is being confirmed: the line then only says it is
        // resuming.
        pause = facts.pause,
        resuming = facts.resuming,
        failure = failure,
    )
}
