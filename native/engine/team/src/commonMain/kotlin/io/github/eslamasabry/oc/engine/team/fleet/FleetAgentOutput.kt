package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.core.common.dart.DartJson

/**
 * An agent's live output (Dart `lib/orchestration/models/agent_output.dart` + `mapTurnFrame`): the session stream
 * delivers `turn` frames, each the transcript so far or a window of it, and [merge] folds them into one text.
 */
public object FleetOutput {
    /** Dart `agentOutputLimit`: characters kept per agent; older text is dropped from the front. */
    public const val LIMIT: Int = 200_000

    /**
     * Folds [chunk] into [accumulated]: a chunk already inside the text is a repeat and changes nothing; otherwise the
     * longest suffix of the text that the chunk starts with is the overlap and only what follows it is appended. A
     * cumulative transcript appends its new tail, a pane capture its new lines, an unrelated chunk is appended whole.
     * The result is the last [limit] characters.
     */
    public fun merge(accumulated: String, chunk: String, limit: Int = LIMIT): String {
        if (chunk.isEmpty()) return accumulated
        if (accumulated.isEmpty()) return trim(chunk, limit)
        if (accumulated.contains(chunk)) return accumulated
        return trim(accumulated + chunk.substring(overlap(accumulated, chunk)), limit)
    }

    private fun overlap(text: String, chunk: String): Int {
        val first = chunk[0]
        val max = minOf(text.length, chunk.length)
        for (k in max downTo 1) {
            if (text[text.length - k] != first) continue
            if (text.endsWith(chunk.substring(0, k))) return k
        }
        return 0
    }

    private fun trim(text: String, limit: Int): String = if (text.length <= limit) text else text.substring(text.length - limit)

    /** Dart `mapTurnFrame`: a `turn` frame's turns joined by a line break; null for any other frame or an empty transcript. */
    public fun turnText(frame: FleetFrame): String? {
        if (frame.event != "turn") return null
        val turns = J.map(DartJson.decodeOrNull(frame.data))["turns"] as? List<*> ?: return null
        val text = turns.mapNotNull { J.string(J.map(it), "text")?.takeIf { t -> t.isNotEmpty() } }.joinToString("\n")
        return text.takeIf { it.isNotEmpty() }
    }
}
