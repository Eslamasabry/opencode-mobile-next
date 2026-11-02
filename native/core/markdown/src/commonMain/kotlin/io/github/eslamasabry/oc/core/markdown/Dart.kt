package io.github.eslamasabry.oc.core.markdown

/** Regex pieces that keep the ported Dart patterns' meaning on every Kotlin target. */
internal object Dart {
    /** Dart's `.` (not dotAll): anything but a line terminator. Java's `.` also refuses U+0085. */
    const val DOT = """[^\n\r  ]"""

    /** Dart's `$` (not multiLine): the very end. Java's `$` also matches before a final line terminator. */
    const val END = """(?![\s\S])"""
}
