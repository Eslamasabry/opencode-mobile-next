package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Which sentence the state line under the version says. The UI turns it into words with [UpdatesUi]'s arguments. */
public enum class UpdatesLine {
    /** "Not checked yet" */
    NeverChecked,

    /** "Checking is off. Use Check now when you want to look." */
    CheckingOff,

    /** "Checking…" */
    Checking,

    /** "Up to date · checked just now" */
    CheckedJustNow,

    /** "Up to date · checked N min ago" ([UpdatesUi.agoCount]) */
    CheckedMinutesAgo,

    /** "Up to date · checked Nh ago" */
    CheckedHoursAgo,

    /** "Up to date · checked Nd ago" */
    CheckedDaysAgo,

    /** "2.2.0 is ready · 39 MB" ([UpdatesUi.releaseVersion], [UpdatesUi.sizeMb]) */
    Available,

    /** "Version 2.2.0 is ready" (size not known yet) */
    AvailableNoSize,

    /** "2.2.0 couldn't be installed" */
    NotInstalled,

    /** "Downloading version 2.2.0" */
    Downloading,

    /** "Checking the download…" */
    Verifying,

    /** "Installing…" */
    Installing,

    /** "Version 2.2.0 is downloaded and checked" */
    ReadyToInstall,
}

/** The one main action of the version row (Flutter `_UpdatesBody` `action`). */
public enum class UpdatesAction { Update, TryAgain, OpenRelease, Cancel, Install }

/**
 * Everything the Updates page shows, as data (no words). Port of the Flutter `_UpdatesBody` decisions.
 *
 * [version] null means the installed version is not known yet (the title then names the app).
 */
public data class UpdatesUi(
    val supported: Boolean = true,
    val version: String? = null,
    /** `2.1.0+57`, shown only under Details. */
    val build: String? = null,
    val line: UpdatesLine = UpdatesLine.NeverChecked,
    /** The newer release's version, the argument of the release lines. */
    val releaseVersion: String = "",
    /** Download size in whole MB, rounded up. */
    val sizeMb: Long? = null,
    /** The count for the "checked … ago" lines. */
    val agoCount: Int = 0,
    val action: UpdatesAction? = null,
    /** The progress view shows (downloading). */
    val showProgress: Boolean = false,
    /** 0..1, or null while the size is unknown (the bar waits). */
    val progress: Float? = null,
    /** Whole percent done, floor of [progress]. */
    val percent: Int? = null,
    /** Why the update stopped, in plain words on screen. */
    val failure: UpdateFailure? = null,
    /** Generated technical text for the Details fold; only when failed. Never shown outside Details. */
    val detail: String? = null,
    /** "To install it, allow this app to install updates, then come back." */
    val needsPermission: Boolean = false,
    val checksOn: Boolean = true,
    val checkNowEnabled: Boolean = true,
    /** Release notes (Markdown source) for What's new; null when there are none. */
    val notes: String? = null,
)

private const val MEGABYTE = 1_048_576.0

/** The page's state from the engine's [state] at [now] (Flutter `_UpdatesBody.build`). Pure. */
public fun updatesUi(state: AppUpdateState, now: Instant): UpdatesUi {
    val release = state.release
    val app = state.installed
    val failed = state.phase == UpdatePhase.Failed
    val line: UpdatesLine
    var action: UpdatesAction? = null
    var ago = 0
    when (state.phase) {
        UpdatePhase.Checking -> line = UpdatesLine.Checking
        UpdatePhase.Available, UpdatePhase.Failed -> {
            line =
                when {
                    release == null -> UpdatesLine.NeverChecked
                    failed -> UpdatesLine.NotInstalled
                    state.downloadSize == null -> UpdatesLine.AvailableNoSize
                    else -> UpdatesLine.Available
                }
            if (release != null) {
                action =
                    when {
                        !failed -> UpdatesAction.Update
                        state.retryHelps -> UpdatesAction.TryAgain
                        release.pageUrl.isNotEmpty() -> UpdatesAction.OpenRelease
                        else -> null
                    }
            }
        }
        UpdatePhase.Downloading -> {
            line = UpdatesLine.Downloading
            action = UpdatesAction.Cancel
        }
        UpdatePhase.Verifying -> line = UpdatesLine.Verifying
        UpdatePhase.Installing -> line = UpdatesLine.Installing
        UpdatePhase.ReadyToInstall -> {
            line = UpdatesLine.ReadyToInstall
            action = UpdatesAction.Install
        }
        UpdatePhase.None -> {
            val at = state.checkedAt
            if (at == null) {
                line = if (state.enabled) UpdatesLine.NeverChecked else UpdatesLine.CheckingOff
            } else {
                val since = now - at
                when {
                    since < 1.minutes -> line = UpdatesLine.CheckedJustNow
                    since < 1.hours -> {
                        line = UpdatesLine.CheckedMinutesAgo
                        ago = since.inWholeMinutes.toInt()
                    }
                    since < 1.days -> {
                        line = UpdatesLine.CheckedHoursAgo
                        ago = since.inWholeHours.toInt()
                    }
                    else -> {
                        line = UpdatesLine.CheckedDaysAgo
                        ago = since.inWholeDays.toInt()
                    }
                }
            }
        }
    }
    if (!state.supported) action = null
    val downloading = state.phase == UpdatePhase.Downloading
    val progress = state.progress?.coerceIn(0f, 1f)
    return UpdatesUi(
        supported = state.supported,
        version = app?.version,
        build = app?.let { "${it.version}+${it.build}" },
        line = line,
        releaseVersion = release?.version.orEmpty(),
        sizeMb = state.downloadSize?.let { ceil(it / MEGABYTE).toLong() },
        agoCount = ago,
        action = action,
        showProgress = downloading,
        progress = if (downloading) progress else null,
        percent = if (downloading) progress?.let { floor(it * 100f).toInt() } else null,
        failure = if (failed) state.failure ?: UpdateFailure.Network else null,
        detail = if (failed) state.detail?.takeIf { it.isNotBlank() } else null,
        needsPermission = state.phase == UpdatePhase.ReadyToInstall && state.needsPermission,
        checksOn = state.enabled,
        checkNowEnabled = state.supported && !state.busy,
        notes = release?.notes?.takeIf { it.isNotBlank() },
    )
}
