package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.agents.hub.mayAnswerSoon

/**
 * How long the page waits for a helper that was just started to answer. The Flutter app kept trying every half second
 * for 60 s (`phone_agents_host.dart`, the hello handshake) and showed nothing while the helper was still starting; on a
 * slow phone the helper takes 20 s or more. Only a refusal that means "nobody answered yet" is tried again.
 */
internal object HelperAnswerWait {
    const val BUDGET_MS = 60_000L
    const val STEP_MS = 500L

    fun stillStarting(failure: Failure): Boolean =
        failure is Failure.Network || failure is Failure.TimedOut || failure is Failure.ComputerDidNotAnswer

    /** Runs [attempt] until [done] says to stop, or [budgetMs] has passed on [now]; returns the last answer. */
    suspend fun <T> until(
        budgetMs: Long = BUDGET_MS,
        stepMs: Long = STEP_MS,
        now: () -> Long,
        pause: suspend (Long) -> Unit,
        done: (T) -> Boolean,
        attempt: suspend () -> T,
    ): T {
        val start = now()
        var answer = attempt()
        while (!done(answer) && now() - start < budgetMs) {
            pause(stepMs)
            answer = attempt()
        }
        return answer
    }
}
