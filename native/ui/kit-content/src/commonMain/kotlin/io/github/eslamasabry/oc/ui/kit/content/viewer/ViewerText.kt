package io.github.eslamasabry.oc.ui.kit.content.viewer

import kotlin.math.roundToLong

/** Text rules the viewer bodies share (plan 10 §11.2, §11.3). Pure. */
public object ViewerText {
    /** Text longer than this is cut for display; Copy and Save keep the whole original. */
    public const val MAX_CHARS: Int = 200_000

    /**
     * Strict UTF-8 (B-MEDIA-001): a NUL or any invalid sequence gives `null`, never replacement characters. The caller
     * keeps [bytes] for Save either way.
     */
    public fun decodeStrict(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** The shown part of [text]: at most [max] chars, never ending inside a surrogate pair. */
    public fun cut(text: String, max: Int = MAX_CHARS): Cut {
        if (text.length <= max) return Cut(text, truncated = false)
        val end = if (text[max - 1].isHighSurrogate()) max - 1 else max
        return Cut(text.substring(0, end), truncated = true)
    }

    public data class Cut(val text: String, val truncated: Boolean)

    /** Lines for the lazy bodies: split on `\n`, a `\r` before it dropped; a trailing terminator adds no empty line. */
    public fun lines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val parts = text.split('\n').map { it.removeSuffix("\r") }
        return if (text.endsWith('\n')) parts.dropLast(1) else parts
    }

    private val units = listOf("B", "KB", "MB", "GB", "TB")

    /**
     * A byte count in words, "2.4 MB" (Dart `_sizeInWords`): binary steps of 1,024, one decimal under 10 and none for
     * bytes. `null` when unknown or negative.
     */
    public fun sizeInWords(bytes: Long?): String? {
        if (bytes == null || bytes < 0) return null
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        val tenths = (value * 10).roundToLong()
        val number =
            when {
                unit == 0 || value >= 10 -> value.roundToLong().toString()
                tenths % 10 == 0L -> (tenths / 10).toString()
                else -> "${tenths / 10}.${tenths % 10}"
            }
        return "$number ${units[unit]}"
    }
}
