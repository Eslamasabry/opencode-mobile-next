package io.github.eslamasabry.oc.feature.chat.ui

/** Test-only counters for transcript row and step recompositions during idle clock ticks. */
internal object TranscriptProbe {
    var on: Boolean = false
    val rows: HashMap<String, Int> = HashMap()
    val steps: HashMap<String, Int> = HashMap()
}
