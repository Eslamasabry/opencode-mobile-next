package io.github.eslamasabry.oc.core.domain.platform

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.NetworkState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.StateFlow

// Platform services shared by several engines or features (plan 03 §10.4, ADR §4 row 27). Implemented once per
// platform (:android:platform on Android). No platform type crosses these signatures.

/** A file the person picked; [uri] is an app-readable handle (content:// on Android), never shown or logged. */
public data class PickedFile(val uri: String, val displayName: String?, val mime: String?, val sizeBytes: Long?)

/** Where a camera photo goes: an app-owned file the caller created. */
public data class PhotoTarget(val uri: String)

public data class DocumentTarget(val uri: String, val displayName: String?)

public data class TreeTarget(val uri: String, val displayName: String?)

public data class SharedFile(val uri: String, val mime: String, val displayName: String?)

public interface ActivityResults {
    public suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>>

    /** null = the person cancelled. */
    public suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?>

    public suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?>

    public suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?>

    public suspend fun openDocumentTree(): Outcome<TreeTarget?>
}

public enum class AppPermission { Microphone, Camera, Notifications, ExactAlarms, IgnoreBatteryOptimizations, InstallPackages }

public enum class PermissionStatus { Granted, Denied, PermanentlyDenied, NotApplicable }

public interface PermissionGate {
    public suspend fun status(permission: AppPermission): PermissionStatus

    /** Never called from composition. */
    public suspend fun request(permission: AppPermission): PermissionStatus

    public fun openSettings(permission: AppPermission)
}

/** dataSync, dataSync, specialUse, specialUse, shortService. */
public enum class ForegroundKind { ReplyWatch, LiveMode, PhoneServer, Setup, WidgetAction }

public data class ForegroundTicket(val kind: ForegroundKind, val id: Long)

/** Nothing here may assume unbounded lifetime: dataSync is capped at 6 h per 24 h (AGENTS.md). */
public interface ForegroundWork {
    public val active: StateFlow<ImmutableSet<ForegroundKind>>

    /** Refused when the OS budget is spent. */
    public suspend fun begin(kind: ForegroundKind): Outcome<ForegroundTicket>

    public suspend fun end(ticket: ForegroundTicket)
}

public enum class ThermalLevel { None, Light, Moderate, Severe, Critical, Emergency, Shutdown }

public interface DeviceFacts {
    /** Kind + default network id (evict pool, reconnect at once on change). */
    public val network: StateFlow<NetworkState>

    public val thermal: StateFlow<ThermalLevel>

    public val powerSave: StateFlow<Boolean>

    /** Voice packs gate on total RAM, never memoryClass. */
    public val totalRamBytes: Long

    /** Plan 13 §11.1: from ProcessLifecycleOwner on Android. */
    public val appVisible: StateFlow<Boolean>
}

public interface ShareService {
    /** Excludes this app from the chooser. */
    public fun shareText(text: String, subject: String?): Outcome<Unit>

    public fun shareFile(file: SharedFile): Outcome<Unit>
}
