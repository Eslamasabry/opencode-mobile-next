package io.github.eslamasabry.oc.ui.kitchat.transcript

import io.github.eslamasabry.oc.ui.kitchat.markdown.P3Fixtures
import kotlin.random.Random

/**
 * One reply block as the transcript engine publishes it (the fields `ChatTranscript` hands to
 * [KitReplyBlock]).
 */
data class FeedRow(
    val key: String,
    val text: String,
    val kind: KitBlockKind,
    val live: Boolean,
    val continued: Boolean,
    val continues: Boolean,
)

/**
 * A test stand-in for the engine's block rows (`:engine:transcript` `StreamingBlocks`; kit modules
 * may not depend on engines): top-level blocks split at blank lines, headings, list items and
 * fences, and, when [chunkLines] is set, every block cut into chunks of at most that many lines,
 * the way the STREAM-FIX engine publishes them. With [chunkLines] = [Int.MAX_VALUE] it is the P3
 * engine's shape: a 400-line fence is one block. Settled rows keep their instances across
 * publishes, like the engine's.
 */
class StreamFeed(private val chunkLines: Int) {
    private val cache = HashMap<String, FeedRow>()

    /** Newest-first rows for the reply text received so far; [ended] settles the tail. */
    fun rows(text: String, ended: Boolean = false): List<FeedRow> {
        val out = ArrayList<FeedRow>()
        var blockStart = -1
        var kind = KitBlockKind.Prose
        var lines = 0
        var inFence = false
        var continued = false
        fun emit(end: Int, continues: Boolean, live: Boolean) {
            if (blockStart < 0 || blockStart >= end) return
            var t = text.substring(blockStart, end)
            t = if (continues) t.removeSuffix("\n") else t.trimEnd('\n')
            if (t.isBlank() && !inFence) return
            val row = FeedRow("b:m/t:$blockStart", t, kind, live, continued, continues)
            val old = cache[row.key]
            out += if (old == row) old else row.also { cache[it.key] = it }
        }
        var ls = 0
        while (ls < text.length) {
            val nl = text.indexOf('\n', ls)
            if (nl < 0) break
            val line = text.substring(ls, nl)
            val fence = line.trimStart().startsWith("```")
            when {
                inFence -> {
                    if (lines == chunkLines) {
                        emit(ls, continues = true, live = false)
                        blockStart = ls
                        continued = true
                        lines = 0
                    }
                    lines++
                    if (fence) {
                        emit(nl + 1, continues = false, live = false)
                        inFence = false
                        blockStart = -1
                        continued = false
                    }
                }
                fence ||
                    line.isBlank() ||
                    line.startsWith("#") ||
                    line.startsWith("- ") ||
                    line.matches(NUMBERED_LIST_ITEM) -> {
                    emit(ls, continues = false, live = false)
                    continued = false
                    blockStart = if (line.isBlank()) -1 else ls
                    lines = if (line.isBlank()) 0 else 1
                    inFence = fence
                    kind =
                        when {
                            fence -> KitBlockKind.Code
                            line.startsWith("#") -> KitBlockKind.Heading
                            line.isBlank() -> KitBlockKind.Prose
                            else -> KitBlockKind.ListItem
                        }
                    if (line.startsWith("#")) {
                        emit(nl + 1, continues = false, live = false)
                        blockStart = -1
                    }
                }
                else -> {
                    if (blockStart < 0) {
                        blockStart = ls
                        kind = KitBlockKind.Prose
                        lines = 0
                    }
                    if (lines == chunkLines) {
                        emit(ls, continues = true, live = false)
                        blockStart = ls
                        continued = true
                        lines = 0
                    }
                    lines++
                }
            }
            ls = nl + 1
        }
        if (blockStart < 0 && ls < text.length && text.substring(ls).isNotBlank()) {
            blockStart = ls
            kind = if (text.startsWith("#", ls)) KitBlockKind.Heading else KitBlockKind.Prose
        }
        emit(text.length, continues = false, live = !ended)
        return out.asReversed()
    }

    companion object {
        private val NUMBERED_LIST_ITEM = Regex("\\d+\\. .*")

        /**
         * The `perf:stream-6000` deltas with their arrival times (ms), the same draws as
         * `Stream6000.events`.
         */
        fun paced(reply: String = P3Fixtures.reply(), seed: Int = 6000): List<Pair<Long, String>> {
            val rnd = Random(seed + 1)
            val out = ArrayList<Pair<Long, String>>()
            var t = 0L
            var i = 0
            while (i < reply.length) {
                val n = rnd.nextInt(1, 13).coerceAtMost(reply.length - i)
                t += (1000.0 / rnd.nextInt(40, 91)).toLong()
                out += t to reply.substring(i, i + n)
                i += n
            }
            return out
        }
    }
}
