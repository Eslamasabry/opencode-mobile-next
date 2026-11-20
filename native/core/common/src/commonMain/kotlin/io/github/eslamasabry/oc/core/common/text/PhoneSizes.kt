package io.github.eslamasabry.oc.core.common.text

/**
 * "410 MB", "1.2 GB": a size in round figures, isolated left to right (port of `thisPhoneSize`). One unit: the
 * decimal megabyte (1,000,000 bytes), the one Android's own Settings and Files show, so a size said here matches the
 * phone and the byte figures the app keeps (130 MB is 130,000,000 bytes; a 300 MB floor reads 300).
 */
public object PhoneSizes {
    private const val LRI = '⁦'
    private const val PDI = '⁩'
    private const val KILOBYTE = 1000.0
    private const val MEGABYTE = 1_000_000.0
    private const val NBSP = ' '

    public fun text(bytes: Long): String {
        val mb = bytes / MEGABYTE
        val body =
            // Flutter formatPhoneStorage (phone_server_card.dart): a small file reads "12 B" or "3.4 KB",
            // never "0 MB" (a measured size that looked unmeasured on Export projects).
            if (bytes < KILOBYTE) "${Numbers.format(bytes.coerceAtLeast(0))}${NBSP}B"
            else if (bytes < MEGABYTE) "${Numbers.decimal(bytes / KILOBYTE, 1)}${NBSP}KB"
            else if (mb < 995) {
                val rounded =
                    if (mb < 100) kotlin.math.round(mb).toLong()
                    else kotlin.math.round(mb / 10).toLong() * 10
                "${Numbers.format(rounded)}${NBSP}MB"
            } else {
                val tenths = kotlin.math.round(mb / KILOBYTE * 10).toLong()
                "${Numbers.decimal(tenths / 10.0, 1)}${NBSP}GB"
            }
        return "$LRI$body$PDI"
    }

    /**
     * Memory in use, the one way every page of This phone writes it: whole MB below 1 GB ("997 MB"), then GB with one
     * decimal ("1.1 GB"). Never rounded to tens, so one reading reads the same everywhere it is shown.
     */
    public fun memory(bytes: Long): String {
        val mb = bytes.coerceAtLeast(0) / (1024.0 * 1024.0)
        val whole = kotlin.math.round(mb).toLong()
        val body =
            if (whole < 1000) "${Numbers.format(whole)}${NBSP}MB"
            else "${Numbers.decimal(kotlin.math.round(mb / 1024 * 10) / 10.0, 1)}${NBSP}GB"
        return "$LRI$body$PDI"
    }
}
