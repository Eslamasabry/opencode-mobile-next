package io.github.eslamasabry.oc.feature.voice.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadAloud
import io.github.eslamasabry.oc.engine.voice.readaloud.ReadRequest
import io.github.eslamasabry.oc.engine.voice.state.ReadAloudState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the reading strip says after or beside a reading. */
public sealed interface ReadNote {
    public data class Failed(val problem: MediaProblem) : ReadNote

    /** The reply is only code and tool output: nothing to read (`readAloudNoProse`). */
    public data object NoProse : ReadNote

    /** The natural voice cannot read this language yet, so the phone's voice reads it (once per language per run). */
    public data object NaturalFallback : ReadNote
}

public data class ReplyReaderUi(
    val available: Boolean = false,
    /** The message being read (its row key), or null. */
    val reading: String? = null,
    /** The once-per-phone question before the first reading (B-MEDIA-009: no engine is touched before it). */
    val asking: Boolean = false,
    val note: ReadNote? = null,
)

/**
 * "Read aloud" on a reply (Flutter `read_aloud.dart`, plan 10 §8): asks once per phone, then reads the reply's prose
 * (code fences, tables and link targets are skipped) with the chosen voice; Stop ends it, leaving the app ends it, a
 * new reading replaces it. Failures are a plain line, never exception text.
 */
public class ReplyReaderPresenter(
    private val hub: VoiceHub,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(ReplyReaderUi())
    public val state: StateFlow<ReplyReaderUi> = mutable.asStateFlow()

    private var reader: Reader? = null
    private var pending: Pending? = null

    private class Pending(val rowKey: String, val text: String)

    init {
        scope.launch {
            when (val loaded = hub.parts()) {
                is Outcome.Failed -> mutable.update { it.copy(available = false) }
                is Outcome.Ok -> {
                    val r = loaded.value.reader
                    reader = r
                    mutable.update { it.copy(available = true) }
                    r.state.collect { s -> onReader(s) }
                }
            }
        }
    }

    private fun onReader(s: ReadAloudState) {
        when (s) {
            ReadAloudState.Idle -> mutable.update { it.copy(reading = null) }
            is ReadAloudState.Speaking ->
                mutable.update {
                    it.copy(
                        reading = s.utteranceId,
                        note = if (s.fallbackLanguage != null) ReadNote.NaturalFallback else it.note,
                    )
                }
            is ReadAloudState.Failed -> mutable.update { it.copy(reading = null, note = ReadNote.Failed(s.problem)) }
        }
    }

    /** The message menu's "Read aloud" on a reply. */
    public fun read(rowKey: String, markdown: String) {
        val r = reader
        if (r == null) {
            mutable.update { it.copy(note = ReadNote.Failed(MediaProblem.ReadAloudUnsupported)) }
            return
        }
        val prose = SpeechProse.of(markdown)
        if (prose.isBlank()) {
            mutable.update { it.copy(note = ReadNote.NoProse) }
            return
        }
        if (prose.length > ReadAloud.MAX_CHARS) {
            mutable.update { it.copy(note = ReadNote.Failed(MediaProblem.ReadAloudTooLong)) }
            return
        }
        if (!r.consentGiven()) {
            pending = Pending(rowKey, prose)
            mutable.update { it.copy(asking = true, note = null) }
            return
        }
        start(r, rowKey, prose)
    }

    /** "Read aloud" in the consent sheet: remembered on this phone, then the waiting reply is read. */
    public fun consent() {
        val r = reader ?: return
        val waiting = pending
        pending = null
        mutable.update { it.copy(asking = false) }
        scope.launch {
            when (val given = r.giveConsent()) {
                is Outcome.Failed -> mutable.update { it.copy(note = ReadNote.Failed(problemOf(given.failure))) }
                is Outcome.Ok -> if (waiting != null) start(r, waiting.rowKey, waiting.text)
            }
        }
    }

    /** "Not now": nothing is read and nothing is remembered. */
    public fun declineConsent() {
        pending = null
        mutable.update { it.copy(asking = false) }
    }

    public fun stop(): Unit = stopIn(scope)

    /** Stops a reading from a scope that outlives this presenter (the screen is going away). */
    public fun stopIn(cleanup: CoroutineScope) {
        val r = reader ?: return
        if (mutable.value.reading == null) return
        cleanup.launch { r.stop() }
    }

    public fun dismissNote() {
        mutable.update { it.copy(note = null) }
    }

    /** Leaving the app stops a reading and blocks new ones until it is back (B-MEDIA-009). */
    public fun hostChanged(resumed: Boolean) {
        reader?.onHostChanged(resumed)
    }

    private fun start(r: Reader, rowKey: String, prose: String) {
        mutable.update { it.copy(note = null) }
        scope.launch {
            when (val read = r.read(ReadRequest(utteranceId = rowKey, text = prose))) {
                is Outcome.Ok -> Unit
                is Outcome.Failed -> mutable.update { it.copy(reading = null, note = ReadNote.Failed(problemOf(read.failure))) }
            }
        }
    }

    private fun problemOf(failure: Failure): MediaProblem =
        when (failure) {
            is Failure.Media -> failure.problem
            is Failure.Unavailable -> MediaProblem.ReadAloudUnsupported
            else -> MediaProblem.SpeechEngineUnavailable
        }
}

/**
 * The prose of a reply for speech (B-CHAT-060's rule until `:core:markdown` has `MarkdownProse.forSpeech`): fenced code
 * and tables are dropped, link targets and image addresses are left out, inline code keeps its words, and the marks of
 * headings, quotes, lists and emphasis are not read.
 */
public object SpeechProse {
    private val link = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val autolink = Regex("""<(https?://[^>]+)>""")
    private val bareUrl = Regex("""https?://\S+""")
    private val inlineCode = Regex("""`([^`]*)`""")
    private val emphasis = Regex("""(\*\*|__|\*|_|~~)(?=\S)(.+?)(?<=\S)\1""")
    private val lineMark = Regex("""^\s{0,3}(#{1,6}\s+|>\s?|[-*+]\s+|\d+[.)]\s+)""")
    private val tableRule = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")

    public fun of(markdown: String): String {
        val out = StringBuilder()
        var inFence = false
        var lastMarked = false
        for (raw in markdown.lines()) {
            val trimmed = raw.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (inFence) continue
            if (trimmed.startsWith("|") || tableRule.matches(trimmed)) continue
            val marked = lineMark.containsMatchIn(raw)
            var line = raw.replace(lineMark, "")
            line = line.replace(link) { it.groupValues[1] }
            line = line.replace(autolink, "")
            line = line.replace(bareUrl, "")
            line = line.replace(inlineCode) { it.groupValues[1] }
            line = line.replace(emphasis) { it.groupValues[2] }
            line = line.trim()
            if (line.isEmpty()) {
                if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
                continue
            }
            // A heading, list item or quote is its own sentence group; plain lines of one paragraph join.
            if (out.isNotEmpty() && !out.endsWith("\n")) out.append(if (marked || lastMarked) '\n' else ' ')
            out.append(line)
            lastMarked = marked
        }
        return out.toString().trim()
    }
}
