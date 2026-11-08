package io.github.eslamasabry.oc.feature.diagnostics.presentation

/**
 * One kept error, already redacted by the diagnostics ring: [source] and [message] are technical and live under the
 * row's Details; the row's title is chosen from [source] by the screen.
 */
public data class KeptErrorRow(
    val id: Long,
    val time: String,
    val source: String,
    val message: String,
    val stack: String,
    val occurrences: Int = 1,
)

/** One timing step: [typical], [slow] and [longest] arrive formatted. Never a message. */
public data class KeptPerfRow(
    val name: String,
    val count: Int,
    val errors: Int,
    val typical: String,
    val slow: String,
    val longest: String,
)
