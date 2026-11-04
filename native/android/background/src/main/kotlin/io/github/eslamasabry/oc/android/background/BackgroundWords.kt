package io.github.eslamasabry.oc.android.background

import androidx.annotation.StringRes

/** The one sentence for each value this module reports (`strings_background.xml` in `values` and `values-ar`). */
public object BackgroundWords {
    /** Takes the time phrase as `%1$s` when [PauseMessage.saysTime]. */
    @StringRes
    public fun message(message: PauseMessage): Int =
        when (message) {
            PauseMessage.TimeLimit -> R.string.background_pause_time_limit
            PauseMessage.Restricted -> R.string.background_pause_restricted
            PauseMessage.UserStopped -> R.string.background_pause_user_stopped
            PauseMessage.AppUpdated -> R.string.background_pause_app_updated
            PauseMessage.DeviceRestarted -> R.string.background_pause_device_restarted
            PauseMessage.Interrupted -> R.string.background_pause_interrupted
            PauseMessage.Resuming -> R.string.background_pause_resuming
            PauseMessage.ResumeFailed -> R.string.background_pause_resume_failed
        }

    /** The visible label. */
    @StringRes
    public fun action(action: PauseAction): Int =
        when (action) {
            PauseAction.Resume -> R.string.background_pause_resume_short
            PauseAction.ResumeAgain -> R.string.background_pause_resume
            PauseAction.KeepRunning -> R.string.background_pause_open_keep_running
        }

    /** What a screen reader hears: the inline Resume says its whole target. */
    @StringRes
    public fun actionSemantics(action: PauseAction): Int =
        when (action) {
            PauseAction.Resume, PauseAction.ResumeAgain -> R.string.background_pause_resume
            PauseAction.KeepRunning -> R.string.background_pause_open_keep_running
        }

    /** Announced once when a Resume is confirmed (the line folds away). */
    @StringRes
    public val resumed: Int = R.string.background_pause_resumed

    @StringRes
    public fun error(error: LiveModeError): Int =
        when (error) {
            LiveModeError.NotificationsRequired -> R.string.background_error_notifications_required
            LiveModeError.CouldNotChange -> R.string.background_error_could_not_change
            LiveModeError.AndroidAppOnly -> R.string.background_error_android_app_only
        }
}
