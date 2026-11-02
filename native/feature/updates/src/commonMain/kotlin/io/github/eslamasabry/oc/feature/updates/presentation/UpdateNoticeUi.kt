package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import kotlin.math.floor

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
}

/** What the notice's actions reach. Update and Install both run AppUpdates.updateNow (a checked file is reused). */
public interface UpdateNoticeActions {
    public fun onUpdate()

    public fun onNotNow()

    public fun onCancel()

    public fun onInstall()
}

/** The status line for [state], or null (Flutter `AppUpdateNotice._status`). Pure. */
public fun noticeUi(state: AppUpdateState): NoticeUi? {
    if (!state.noticeVisible) return null
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
