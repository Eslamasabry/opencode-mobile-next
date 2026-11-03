package io.github.eslamasabry.oc.feature.about.managespace

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Android's "Clear storage" lands here first (Flutter `ManageSpaceScreen`, B-ANDR-035): what clearing deletes, what
 * stays, the cache-only choice and the confirmed full clear. The native preview has no built-in Linux projects to
 * export, so the export rows are absent and the intro says there is nothing to export.
 */
public data class ManageSpaceFacts(
    /** Bytes in the app's cache folders; null when they could not be measured. */
    val cacheBytes: Long?,
    val savedServers: Int,
    /** The built-in "On this phone" server exists and would be deleted. */
    val phoneServer: Boolean = false,
)

/** The platform side of the page: measuring, clearing the cache, and Android's clear-all. */
public interface ManageSpacePort {
    public suspend fun measure(): Outcome<ManageSpaceFacts>

    /** Deletes the cache folders; the freed byte count. */
    public suspend fun clearCache(): Outcome<Long>

    /** `ActivityManager.clearApplicationUserData()`; true when Android accepted (it then ends the process). */
    public suspend fun clearAllData(): Boolean
}

/** The one line shown after a cache clear. */
public sealed interface CacheNotice {
    public data class Cleared(val freedBytes: Long) : CacheNotice

    public data object Failed : CacheNotice
}

public data class ManageSpaceUi(
    /** Null while measuring. */
    val facts: ManageSpaceFacts? = null,
    val busy: Boolean = false,
    val confirmingDelete: Boolean = false,
    val notice: CacheNotice? = null,
)

public interface ManageSpaceActions {
    public fun onClose()

    public fun onClearCache()

    public fun onDeleteAll()

    public fun onConfirmDelete()

    public fun onCancelDelete()
}

/** Flutter `formatPhoneStorage`: bytes under 1 KiB as "N B", else one decimal in KB/MB/GB/TB (binary steps). */
public fun formatPhoneStorage(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    val tenths = kotlin.math.round(value * 10).toLong()
    return "${tenths / 10}.${tenths % 10} ${units[unit]}"
}
