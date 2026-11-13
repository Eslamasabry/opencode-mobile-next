package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import kotlin.math.floor
import kotlinx.coroutines.delay

/**
 * The app's own update in the one status line (Flutter `AppUpdateNotice._status`). Null when the line shows nothing:
 * not supported, the Updates page is open, Not now was chosen for the offer, or nothing is happening.
 */
public sealed interface NoticeUi {
    /** "Version 2.2.0 is ready" · Update · Not now */
    public data class Ready(val version: String) : NoticeUi

    /** "Downloading version 2.2.0" · "42% done" · Cancel. [percent] null while the size is unknown. */
    public data class Downloading(val version: String, val percent: Int?) : NoticeUi

    /** "Checking the download…" */
    public data object Verifying : NoticeUi

    /** "Installing…" */
    public data object Installing : NoticeUi

    /** "Version 2.2.0 is downloaded and checked" · Install · Not now; the permission line when Android must allow it. */
    public data class ReadyToInstall(val version: String, val needsPermission: Boolean) : NoticeUi

    /**
     * B-217: "Updated to version 2.2.0" · Dismiss, on the first visit after Android installed the release this app
     * offered (Android closes the app to install it and says nothing). It leaves by itself after a short hold.
     */
    public data class Updated(val version: String) : NoticeUi
}

/** What the notice's actions reach. Update and Install both run AppUpdates.updateNow (a checked file is reused). */
public interface UpdateNoticeActions {
    public fun onUpdate()

    public fun onNotNow()

    public fun onCancel()

    public fun onInstall()
}

/** How long "Updated to version X" holds before it leaves by itself (B-217). */
public const val UPDATED_NOTICE_HOLD_MS: Long = 6_000L

/** Holds the "Updated to version X" line for [UPDATED_NOTICE_HOLD_MS], then dismisses it as Not now would. */
public suspend fun UpdateNoticeActions.dismissAfterHold() {
    delay(UPDATED_NOTICE_HOLD_MS)
    onNotNow()
}

/** The status line for [state], or null (Flutter `AppUpdateNotice._status`). Pure. */
public fun noticeUi(state: AppUpdateState): NoticeUi? {
    if (!state.noticeVisible) return updatedUi(state)
    val version = state.release?.version.orEmpty()
    return when (state.phase) {
        UpdatePhase.Available -> NoticeUi.Ready(version)
        UpdatePhase.Downloading ->
            NoticeUi.Downloading(version, state.progress?.let { floor(it.coerceIn(0f, 1f) * 100f).toInt() })
        UpdatePhase.Verifying -> NoticeUi.Verifying
        UpdatePhase.Installing -> NoticeUi.Installing
        UpdatePhase.ReadyToInstall -> NoticeUi.ReadyToInstall(version, state.needsPermission)
        UpdatePhase.None, UpdatePhase.Checking, UpdatePhase.Failed -> null
    }
}

/** "Updated to version X" once nothing else about the update needs the line (B-217). */
private fun updatedUi(state: AppUpdateState): NoticeUi? =
    state.updated
        ?.takeIf { state.supported && !state.pageOpen && !state.notNow && !state.busy }
        ?.let { NoticeUi.Updated(it.version) }

/**
 * The sheet the Settings gear opens while a newer release waits: "Version 2.2.0 is ready", the first plain sentence of
 * its notes, then Update now or Open Settings (Flutter `showKitUpdateSheet`).
 */
public data class OfferUi(val version: String, val summary: String = "")

/** The offer while a newer release is known (it stays after Not now, like the gear badge); null otherwise. Pure. */
public fun offerUi(state: AppUpdateState): OfferUi? {
    val release = state.release ?: return null
    if (!state.hasPendingRelease) return null
    return OfferUi(release.version, release.summary)
}

/** The gear's update badge ("Settings, update available"): on while a newer release waits, also after Not now. */
public fun hasUpdateBadge(state: AppUpdateState): Boolean = state.hasPendingRelease

/**
 * The prompt a start-up or resume check opens (Flutter `showInAppUpdateSheet`, #126): "Version 2.2.0 is ready", the
 * first plain sentence of its notes, then Update now or Not now. Null when it is closed, put off for the session, the
 * Updates page is open or the release is already being fetched. Pure.
 */
public fun promptUi(state: AppUpdateState): OfferUi? =
    state.release
        ?.takeIf { state.promptWanted }
        ?.let { OfferUi(it.version, it.summary) }

private val AppUpdateState.promptWanted: Boolean
    get() = supported && promptOpen && phase == UpdatePhase.Available && !(pageOpen || notNow)

/** What the prompt's choices reach. */
public interface UpdatePromptActions {
    /** Closes the prompt and starts the download (AppUpdates.updateNow). */
    public fun onPromptUpdate()

    /** Not now: this release is not offered again this session (AppUpdates.later). */
    public fun onPromptLater()

    /** Back, Close or the scrim: only closes (AppUpdates.closePrompt). */
    public fun onPromptClose()
}
