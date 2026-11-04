package io.github.eslamasabry.oc.engine.diagnostics.perf

import io.github.eslamasabry.oc.core.net.HttpCallEnd
import io.github.eslamasabry.oc.core.net.HttpCallObserver
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/** A live signal the prompt timeline cares about (protocol-neutral projection of the event stream). */
public sealed interface PromptSignal {
    public val sessionId: String

    /** A message's info arrived; only an assistant one is remembered. */
    public data class Message(override val sessionId: String, val messageId: String, val assistant: Boolean) : PromptSignal

    /** A part of [messageId] arrived; [kind] is the wire part type (`text`, `reasoning`, `tool`, `step-start`, …). */
    public data class Part(override val sessionId: String, val messageId: String, val kind: String) : PromptSignal

    /** A streamed delta. */
    public data class Delta(override val sessionId: String) : PromptSignal

    public data class Busy(override val sessionId: String) : PromptSignal

    public data class Idle(override val sessionId: String) : PromptSignal

    public data class Error(override val sessionId: String) : PromptSignal
}

/**
 * Prompt timeline (port of Dart `PromptTrace`, B-BUILD-027): sent → accepted → first token → turn done, as spans in
 * [trace]. The user's own text echo never counts as the first token; an idle left over from the previous turn does not
 * end it; a refused prompt records `prompt.accepted` as an error and ends the tracking. Tracing never breaks the send.
 */
public class PromptTimeline(private val trace: PerfTrace) {
    private class Timing(val start: Long) {
        val assistant = mutableSetOf<String>()
        var busy = false
        var firstToken = false
    }

    private val pending = AtomicReference<Map<String, Timing>>(emptyMap())

    private fun update(change: (Map<String, Timing>) -> Map<String, Timing>) {
        while (true) {
            val current = pending.load()
            if (pending.compareAndSet(current, change(current))) return
        }
    }

    /** Wraps the send: `prompt.sent` before, `prompt.accepted` after (an error when the send threw). */
    public suspend fun <T> track(sessionId: String, send: suspend () -> T): T {
        sent(sessionId)
        val result =
            try {
                send()
            } catch (e: CancellationException) {
                accepted(sessionId, error = true)
                throw e
            } catch (e: Throwable) {
                accepted(sessionId, error = true)
                throw e
            }
        accepted(sessionId, error = false)
        return result
    }

    public fun sent(sessionId: String) {
        update { current ->
            val base = if (current.size >= MAX_TRACKED && sessionId !in current) current - current.keys.first() else current
            base + (sessionId to Timing(trace.nowMicros))
        }
        trace.mark("prompt.sent")
    }

    /** The server accepted the prompt, or refused it ([error]: the tracking ends). */
    public fun accepted(sessionId: String, error: Boolean) {
        val timing = pending.load()[sessionId] ?: return
        trace.recordSince("prompt.accepted", timing.start, error = error)
        if (error) update { it - sessionId }
    }

    /** Feeds every live signal through; cheap for the ones that do not matter. */
    public fun observe(signal: PromptSignal) {
        val timing = pending.load()[signal.sessionId] ?: return
        when (signal) {
            is PromptSignal.Message -> if (signal.assistant) timing.assistant += signal.messageId
            is PromptSignal.Delta -> firstToken(timing)
            is PromptSignal.Part ->
                if (signal.messageId in timing.assistant || signal.kind in ASSISTANT_ONLY_KINDS) firstToken(timing)
            is PromptSignal.Busy -> timing.busy = true
            is PromptSignal.Idle -> done(signal.sessionId, timing, error = false)
            is PromptSignal.Error -> done(signal.sessionId, timing, error = true)
        }
    }

    private fun firstToken(timing: Timing) {
        if (timing.firstToken) return
        timing.firstToken = true
        trace.recordSince("prompt.first_token", timing.start)
    }

    private fun done(sessionId: String, timing: Timing, error: Boolean) {
        // An idle left over from the previous turn can arrive right after the send; only one after this turn visibly
        // started ends it.
        if (!error && !timing.busy && !timing.firstToken) return
        update { it - sessionId }
        trace.recordSince("prompt.turn", timing.start, error = error)
    }

    private companion object {
        const val MAX_TRACKED = 16
        val ASSISTANT_ONLY_KINDS = setOf("step-start", "reasoning", "tool")
    }
}

/**
 * Turns every observed HTTP call into a span `http <label> METHOD /path/:id` with `status` and `bytes` (port of Dart
 * `PerfTraceInterceptor`, B-BUILD-026). The span's parent is the span open around the call; the query never reaches it.
 */
public class HttpSpanRecorder(private val trace: PerfTrace, private val label: String) : HttpCallObserver {
    override suspend fun begin(method: String, path: String): HttpCallEnd {
        val handle = trace.begin("http $label $method ${TraceLabels.pathTemplate(path)}", parent = trace.current())
        return HttpCallEnd { status, bytes, failed ->
            handle.finish(error = failed, attrs = mapOf("status" to status?.toString(), "bytes" to bytes?.toString()))
        }
    }
}
