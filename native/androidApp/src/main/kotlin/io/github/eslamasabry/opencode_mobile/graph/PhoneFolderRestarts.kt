package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderSide
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * W3 (device run on the S22, build 10050): a folder made in the phone's storage is missing on both Linux sides that
 * were already running, the phone's OpenCode server and the agents' helper. "Restart the phone's agents" restarted
 * only the helper, so choosing OpenCode afterwards asked for a second restart for the same folder.
 *
 * One [restart] now restarts the side that will work in the folder ([chosen]) and the other side too when it also runs
 * without it, one after the other, as one operation. Work running on a side is never interrupted: a reply being written
 * on the phone's OpenCode, or an agent's turn in the helper, makes that side wait ([waiting]) and restart by itself as
 * soon as it is idle. The chosen agents' side that is busy keeps refusing as before (its failure says to try again
 * when the agent is done).
 */
internal class PhoneFolderRestarts(
    private val scope: CoroutineScope,
    /** Whether the side's process now running was started without [path] (false when it does not run). */
    private val misses: suspend (side: PhoneFolderSide, path: String) -> Boolean,
    /** Whether a reply is being written on the phone's OpenCode now. */
    private val serverBusy: suspend () -> Boolean,
    private val restartServer: suspend () -> Outcome<Unit>,
    /** Restarts the agents' helper; [Failure.Busy] while an agent works. */
    private val restartAgents: suspend () -> Outcome<Unit>,
    private val every: Duration,
    private val tries: Int = DEFAULT_TRIES,
) {
    private val mutableWaiting = MutableStateFlow<Set<PhoneFolderSide>>(emptySet())

    /** The sides whose restart waits for their work to end. */
    val waiting: StateFlow<Set<PhoneFolderSide>> = mutableWaiting.asStateFlow()

    /** [path]: the folder chosen; null when not known (then only [chosen] restarts). */
    suspend fun restart(chosen: PhoneFolderSide, path: String?): Outcome<Unit> {
        val other = if (chosen == PhoneFolderSide.Server) PhoneFolderSide.Agents else PhoneFolderSide.Server
        val otherMisses = path != null && misses(other, path)
        // A reply running on OpenCode is known now, not after the agents' restart (about half a minute): the page
        // must be able to say that OpenCode waits while the agents restart, not when that is already over.
        if (otherMisses && other == PhoneFolderSide.Server && serverBusy()) later(other)
        val first = restartSide(chosen, chosenSide = true)
        if (first is Outcome.Failed) return first
        if (otherMisses && other !in mutableWaiting.value) restartSide(other, chosenSide = false)
        return Outcome.Ok(Unit)
    }

    private suspend fun restartSide(side: PhoneFolderSide, chosenSide: Boolean): Outcome<Unit> =
        when (side) {
            PhoneFolderSide.Server ->
                if (serverBusy()) {
                    later(side)
                    Outcome.Ok(Unit)
                } else restartServer()
            PhoneFolderSide.Agents ->
                when (val restarted = restartAgents()) {
                    is Outcome.Ok -> restarted
                    is Outcome.Failed ->
                        if (!chosenSide && restarted.failure is Failure.Busy) {
                            later(side)
                            Outcome.Ok(Unit)
                        } else restarted
                }
        }

    /** One waiting restart per side: a start then binds every folder chosen meanwhile. */
    private fun later(side: PhoneFolderSide) {
        var added = false
        mutableWaiting.update { now -> if (side in now) now else now + side.also { added = true } }
        if (!added) return
        scope.launch {
            try {
                repeat(tries) {
                    delay(every)
                    val done =
                        when (side) {
                            PhoneFolderSide.Server -> !serverBusy() && restartServer() is Outcome.Ok
                            PhoneFolderSide.Agents ->
                                (restartAgents() as? Outcome.Failed)?.failure !is Failure.Busy
                        }
                    if (done) return@launch
                }
            } finally {
                mutableWaiting.update { it - side }
            }
        }
    }

    private companion object {
        /** About an hour at the production pace; after that the side is left as it is and the notice asks again. */
        const val DEFAULT_TRIES = 720
    }
}
