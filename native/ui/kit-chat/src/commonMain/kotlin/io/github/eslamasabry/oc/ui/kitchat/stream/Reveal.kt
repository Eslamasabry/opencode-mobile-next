package io.github.eslamasabry.oc.ui.kitchat.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest

/**
 * The chat's motion level (plan 09 §8.6). P3-local: the real `MotionLevel` and `resolveMotion` come
 * from `:ui:design` in slice 1 (06 §4.8); kit-chat will read that one instead.
 */
public enum class KitMotionLevel {
    Full,
    Calm,
    Off,
}

/** Plan 09 §8.2 / plan 08 §5.1 pace constants. */
public object RevealPace {
    /** Characters per second when there is no backlog (the demo value the owner felt). */
    public const val BASE_CPS: Float = 45f

    /**
     * A backlog drains in 250 ms: the reveal never lags the network by more (M-009, `MotionTokens.REVEAL_MAX_LAG_MS`).
     * A hard deadline, not a rate: the words that arrived last are all laid out 250 ms after they arrived, so the
     * reply's footer (which enters over STANDARD when the turn ends) and the text finish together.
     */
    public const val DRAIN_S: Float = 0.25f

    /** [DRAIN_S] in frame nanos. */
    public const val DRAIN_NS: Long = 250_000_000L

    /** Each fresh word fades over 250 ms (KitMotion.standard). */
    public const val FADE_NS: Long = 250_000_000L

    /** A stalled frame never reveals a burst. */
    public const val MAX_DT_S: Float = 0.05f
}

/** A range of characters still fading in, born at a frame time. */
public data class FreshRun(val start: Int, val end: Int, val bornNanos: Long)

/**
 * The reveal of one streamed block (plan 09 §8.2). [shown] changes only when a whole word lands, so
 * the text re-lays out at the word rate; [frameNanos] changes every frame but is read only in the
 * draw phase.
 */
@Stable
public class RevealState
internal constructor(initialShown: Int, private val predecessor: RevealState?) {
    /** Characters laid out (a layout read). */
    public var shown: Int by mutableIntStateOf(initialShown)
        internal set

    /** The current text length of the block, updated by the block after each composition. */
    public var targetLength: Int by mutableIntStateOf(initialShown)
        internal set

    internal val runs = mutableStateListOf<FreshRun>()

    /** Draw-phase clock. */
    internal var frameNanos: Long by mutableLongStateOf(0L)

    /** Frames the clock ran (tests read it to prove the clock parks when idle). */
    public var frames: Int = 0
        private set

    /** When every character seen so far must be laid out (frame nanos); 0 = no deadline yet. */
    private var deadline: Long = 0L

    /** The text length the deadline was set for. */
    private var seenLength: Int = initialShown

    /** Word landings: the only times the block re-lays out. */
    public var landings: Int = 0
        private set

    public val active: Boolean
        get() = shown < targetLength || runs.isNotEmpty()

    /**
     * Where the stream caret stands: after the words a reader can see, never ahead of words still fading in. A run
     * past half of its fade counts as seen. Read in the draw phase only (it reads the frame clock).
     */
    internal fun caretAt(laidOut: Int): Int {
        val now = frameNanos
        for (r in runs) {
            if (now - r.bornNanos < RevealPace.FADE_NS / 2) return r.start.coerceIn(0, laidOut)
        }
        return laidOut
    }

    /** Words never appear out of order: a block waits for the block before it in the same part. */
    internal val predecessorDone: Boolean
        get() = predecessor?.let { it.shown >= it.targetLength } ?: true

    internal fun advance(
        now: Long,
        last: Long,
        carryIn: Float,
        text: CharSequence,
        motion: KitMotionLevel,
    ): Float {
        frames++
        if (motion == KitMotionLevel.Off) {
            if (shown != text.length) shown = text.length
            runs.clear()
            return 0f
        }
        val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(RevealPace.MAX_DT_S)
        // M-009: new words (or a block that just got its turn after the one before it) restart the 250 ms deadline.
        if (text.length > seenLength || last == 0L || deadline == 0L) deadline = now + RevealPace.DRAIN_NS
        seenLength = text.length
        val backlog = (text.length - shown).coerceAtLeast(0)
        val left = (deadline - now) / 1e9f
        // Never slower than the base pace, and linear to the deadline: what is left lands by the deadline, never later
        // (the old rate, backlog / 250 ms re-taken every frame, only decayed toward it and trailed for over a second).
        val step =
            if (left <= 0f) backlog.toFloat()
            else maxOf(RevealPace.BASE_CPS * dt, backlog * (dt / left).coerceAtMost(1f))
        var carry = carryIn + step
        val want = shown + carry.toInt()
        carry -= carry.toInt()
        val snapped = text.wordEndAtOrAfter(want).coerceAtMost(text.length)
        if (snapped > shown) {
            runs += FreshRun(shown, snapped, now)
            shown = snapped
            landings++
        }
        frameNanos = now
        runs.removeAll { now - it.bornNanos > RevealPace.FADE_NS }
        return carry
    }
}

/** The end of the word at or after [index] (whole words only; a word ends at whitespace). */
public fun CharSequence.wordEndAtOrAfter(index: Int): Int {
    if (index <= 0) return 0
    var i = index.coerceAtMost(length)
    while (i < length && !this[i].isWhitespace()) i++
    return i
}

/**
 * The frame clock of one block (plan 09 §8.2, ported from the demo's RevealLoop without its
 * per-frame re-parse and per-frame spans). Parks when there is nothing to reveal or fade: no frames
 * are scheduled while idle.
 */
internal suspend fun RevealState.drive(text: () -> CharSequence, motion: () -> KitMotionLevel) {
    snapshotFlow { active && predecessorDone }
        .collectLatest { busy ->
            if (!busy) return@collectLatest
            var last = 0L
            var carry = 0f
            while (true) {
                val more = withFrameNanos { now ->
                    carry = advance(now, last, carry, text(), motion())
                    last = now
                    active
                }
                if (!more) break
            }
        }
}

/**
 * One reveal state per streamed block, remembered once per transcript (outside the lazy items, so
 * recycling never loses it). Rows that arrive settled, or that were loaded from history, never
 * animate.
 */
@Stable
public class RevealRegistry internal constructor() {
    private val states = HashMap<String, RevealState>()
    private val lastInGroup = HashMap<String, RevealState>()

    /**
     * The state for block [key] of part [group]. A block first seen while [live] starts hidden and
     * reveals; a block first seen settled draws at full length at once.
     */
    public fun stateFor(key: String, group: String, live: Boolean, length: Int): RevealState =
        states.getOrPut(key) {
            RevealState(if (live) 0 else length, lastInGroup[group]).also {
                lastInGroup[group] = it
            }
        }

    /** Blocks still revealing or fading. */
    public val activeCount: Int
        get() = states.values.count { it.active }

    public val totalFrames: Int
        get() = states.values.sumOf { it.frames }

    public val totalLandings: Int
        get() = states.values.sumOf { it.landings }

    /**
     * Text layouts of the transcript's reply blocks, and the characters they laid out (STREAM-FIX
     * gate, PB-17).
     */
    public var layouts: Int = 0
        private set

    public var layoutChars: Long = 0
        private set

    internal fun laidOut(chars: Int) {
        layouts++
        layoutChars += chars
    }
}

@Composable public fun rememberRevealRegistry(): RevealRegistry = remember { RevealRegistry() }
