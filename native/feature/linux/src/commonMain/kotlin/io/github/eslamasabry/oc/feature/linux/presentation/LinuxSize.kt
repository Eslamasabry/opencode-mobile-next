package io.github.eslamasabry.oc.feature.linux.presentation

import kotlin.math.roundToLong

public enum class StorageByteUnit {
    B,
    KB,
    MB,
    GB,
}

public data class StorageByteAmount(val number: String, val unit: StorageByteUnit)

/**
 * Bytes in the short form the pages use ("1.4 GB", "320 MB", "12 KB"); Latin digits, shown in an
 * LTR isolate.
 */
public object LinuxSize {
    private val units = listOf("KB", "MB", "GB", "TB")

    /** Storage scans keep one decimal above a gigabyte, including totals above ten gigabytes. */
    public fun storage(bytes: Long): StorageByteAmount {
        val value = bytes.coerceAtLeast(0)
        val gb = 1024L * 1024 * 1024
        if (value >= gb) {
            val tenths = (value.toDouble() * 10 / gb).roundToLong()
            val number =
                if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
            return StorageByteAmount(number, StorageByteUnit.GB)
        }
        if (value >= 1024L * 1024)
            return StorageByteAmount("${value / (1024L * 1024)}", StorageByteUnit.MB)
        if (value >= 1024) return StorageByteAmount("${value / 1024}", StorageByteUnit.KB)
        return StorageByteAmount("$value", StorageByteUnit.B)
    }

    public fun format(bytes: Long): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val text =
            if (value < 10) {
                val tenths = (value * 10).roundToLong()
                if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
            } else {
                "${value.roundToLong()}"
            }
        return "$text ${units[unit]}"
    }
}
