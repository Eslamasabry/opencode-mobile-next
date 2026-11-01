package io.github.eslamasabry.oc.android.agenthost

import java.io.File

/** Twin of `object SetupDiskSpace` in the Flutter app's `SetupRunner.kt` (verbatim; the launch guard [PhoneAgentHost] reads). */
object SetupDiskSpace {
    const val MIN_INSTALL_BYTES = 300_000_000L
    const val MIN_LAUNCH_BYTES = 128_000_000L
    private const val LOW_SPACE =
        "There is not enough free space on this phone. Free some storage and try again."

    fun requiredInstallBytes(downloadBytes: Long): Long {
        val nonnegative = downloadBytes.coerceAtLeast(0L)
        val doubled = if (nonnegative > Long.MAX_VALUE / 2L) Long.MAX_VALUE else nonnegative * 2L
        return maxOf(doubled, MIN_INSTALL_BYTES)
    }

    fun error(directory: File, requiredBytes: Long): String? {
        var existing = directory
        while (!existing.exists()) {
            existing = existing.parentFile ?: return LOW_SPACE
        }
        return if (existing.usableSpace >= requiredBytes) null else LOW_SPACE
    }
}
