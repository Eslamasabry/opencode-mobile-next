package io.github.eslamasabry.oc.engine.transcript

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The connection-scoped transcript engine (plan 09 §3). Each open conversation is a [TranscriptHandle]: gateway events
 * become newest-first rows on one serial lane per handle, history is read and merged behind version fences, the
 * display passes run per turn, and the state is published at most once per 50 ms with the first change at once.
 *
 * Handles are ref-counted: [open] joins a warm handle, [TranscriptHandle.close] lets go. A handle nobody holds stays
 * warm for 60 s, and at most 3 unheld handles stay warm (LRU) [new: bounds memory, §14.4]. [open] and close are
 * called from the main thread (the ViewModels), so the bookkeeping needs no lock.
 */
@SingleIn(ConnectionScope::class)
@Inject
public class TranscriptEngine(
    private val gateway: ServerGateway,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val scope: ConnectionCoroutineScope,
) {
    private class Slot(val handle: TranscriptHandle) {
        var holders = 0
        var closer: Job? = null
    }

    private val slots = LinkedHashMap<ConversationRef, Slot>()

    /** P1 seam kept: the S4 accessor proof reads a session through the engine. */
    public suspend fun session(id: SessionId): Outcome<Session> = gateway.session(id)

    /** Opens (or joins) the transcript of [ref]. Events of other conversations never publish on it. */
    public fun open(ref: ConversationRef): TranscriptHandle {
        val slot =
            slots.remove(ref)?.also { it.closer?.cancel() }
                ?: Slot(
                    TranscriptHandle(
                        sessionId = ref.sessionId.raw,
                        lane = dispatchers.serial("transcript:${ref.sessionId.raw}"),
                        parent = scope,
                        nowMs = { clock.now().toEpochMilliseconds() },
                        history = { session, cursor, limit -> gateway.messagePage(SessionId(session), cursor, limit) },
                        events = gateway.events.all.map { it.event },
                        release = { release(ref) },
                    ),
                )
        slots[ref] = slot // most recently used last
        slot.holders++
        slot.closer = null
        return slot.handle
    }

    /** Handles currently warm (held or not). Test and diagnostics read. */
    public val warm: Int get() = slots.size

    private fun release(ref: ConversationRef) {
        val slot = slots[ref] ?: return
        if (slot.holders > 0) slot.holders--
        if (slot.holders > 0) return
        // §14.4: a handle with no holder closes after 60 s.
        slot.closer =
            CoroutineScope(scope.coroutineContext + dispatchers.main).launch {
                delay(UNHELD_CLOSE_MS)
                if (slots[ref] === slot && slot.holders == 0) {
                    slots.remove(ref)
                    slot.handle.dispose()
                }
            }
        // §14.4: at most 3 unheld handles stay warm; the least recently used goes first.
        val unheld = slots.entries.filter { it.value.holders == 0 }
        for (i in 0 until unheld.size - MAX_WARM_UNHELD) {
            val (key, s) = unheld[i]
            s.closer?.cancel()
            slots.remove(key)
            s.handle.dispose()
        }
    }

    internal companion object {
        const val UNHELD_CLOSE_MS = 60_000L
        const val MAX_WARM_UNHELD = 3
    }
}

@ContributesTo(ConnectionScope::class)
public interface TranscriptAccessors {
    public val transcriptEngine: TranscriptEngine
}
