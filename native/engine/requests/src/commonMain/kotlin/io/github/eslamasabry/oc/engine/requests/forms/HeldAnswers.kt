package io.github.eslamasabry.oc.engine.requests.forms

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where one answer is (plan 09 §5.1 "3 s hold" and "Receipts"). No entry means the request still waits. */
public sealed interface AnswerPhase<out A> {
    public val answer: A

    /** Inside the Undo window: collapsed "Sent in a moment · Undo". */
    public data class Held<A>(override val answer: A) : AnswerPhase<A>

    /** On the wire. [slow] after 8 s: "Not confirmed yet" (STATE-10); "Sent" is never "Done". */
    public data class Sending<A>(override val answer: A, val slow: Boolean = false) : AnswerPhase<A>

    /** Not accepted: the request stays open with the reason; answering again is allowed. */
    public data class Refused<A>(override val answer: A, val failure: Failure) : AnswerPhase<A>
}

/** What the owner makes of a send's outcome. */
internal sealed interface SendResolution {
    /** The request is settled (accepted, or already settled elsewhere): the owner removes it. */
    data object Settled : SendResolution

    /** Keep the request open and say why. */
    data class Keep(val failure: Failure) : SendResolution
}

/**
 * The hold-then-send lane shared by forms and question sheets (plan 09 §5.1): an answer is held for [hold] with Undo,
 * then sent through one write slot for the whole engine, exactly once. A second answer while one is held or on the
 * wire is ignored (first decision wins, B-CHAT-074); [flush] sends held answers at once (leaving the chat,
 * `BackgroundIdle`), and the hold timer can then never send it again.
 *
 * Runs on the engine's [scope] (the connection graph's serial lane); a replacement connection has a new graph, so an
 * answer can never cross onto it (B-PROTO-048).
 */
internal class HeldAnswers<K : Any, A : Any>(
    private val scope: CoroutineScope,
    private val send: suspend (K, A) -> Outcome<Unit>,
    private val resolve: (K, Outcome<Unit>) -> SendResolution,
    private val hold: Duration = HOLD,
    private val patience: Duration = PATIENCE,
) {
    private val writeSlot = Mutex()
    private val timers = HashMap<K, Job>()
    private val _phases = MutableStateFlow<ImmutableMap<K, AnswerPhase<A>>>(persistentMapOf())
    val phases: StateFlow<ImmutableMap<K, AnswerPhase<A>>> = _phases.asStateFlow()

    /** Starts the hold. False (and nothing changes) while an earlier answer is held or on the wire. */
    fun answer(key: K, answer: A): Boolean {
        when (_phases.value[key]) {
            is AnswerPhase.Held, is AnswerPhase.Sending -> return false
            is AnswerPhase.Refused, null -> Unit
        }
        put(key, AnswerPhase.Held(answer))
        timers[key] = scope.launch {
            delay(hold)
            timers.remove(key)
            dispatch(key)
        }
        return true
    }

    /** Inside the hold only: the request waits again, as if never answered. */
    fun undo(key: K): Boolean {
        if (_phases.value[key] !is AnswerPhase.Held) return false
        timers.remove(key)?.cancel()
        drop(key)
        return true
    }

    /** Sends every held answer whose key matches now (leaving the chat sends at once). */
    suspend fun flush(which: (K) -> Boolean) {
        val held = _phases.value.filter { (k, p) -> p is AnswerPhase.Held && which(k) }.keys
        held.forEach { key ->
            timers.remove(key)?.cancel()
            dispatch(key)
        }
    }

    /** The request left (settled elsewhere, refetch found it gone): forget its hold or receipt. */
    fun forget(key: K) {
        timers.remove(key)?.cancel()
        drop(key)
    }

    /** True while an answer for [key] is held or on the wire: a refetch must not drop it. */
    fun isInFlight(key: K): Boolean = _phases.value[key].let { it is AnswerPhase.Held || it is AnswerPhase.Sending }

    private suspend fun dispatch(key: K) {
        val held = _phases.value[key] as? AnswerPhase.Held ?: return
        put(key, AnswerPhase.Sending(held.answer))
        val slowTimer = scope.launch {
            delay(patience)
            _phases.update { m ->
                val p = m[key]
                if (p is AnswerPhase.Sending) (m + (key to p.copy(slow = true))).toImmutableMap() else m
            }
        }
        val outcome = writeSlot.withLock { send(key, held.answer) }
        slowTimer.cancel()
        val stillOurs = _phases.value[key] is AnswerPhase.Sending // forget() during the write: the request already left
        when (val r = resolve(key, outcome)) {
            SendResolution.Settled -> drop(key)
            is SendResolution.Keep -> if (stillOurs) put(key, AnswerPhase.Refused(held.answer, r.failure))
        }
    }

    private fun put(key: K, phase: AnswerPhase<A>) = _phases.update { (it + (key to phase)).toImmutableMap() }

    private fun drop(key: K) = _phases.update { (it - key).toImmutableMap() }

    companion object {
        /** `DelayedAnswers` window (map CHAT-GENUI-43). */
        val HOLD: Duration = 3.seconds

        /** "Not confirmed yet" after 8 s (plan 09 §5.1 Receipts). */
        val PATIENCE: Duration = 8.seconds
    }
}
