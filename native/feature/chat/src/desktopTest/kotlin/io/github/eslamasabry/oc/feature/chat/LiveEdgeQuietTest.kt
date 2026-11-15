package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted

/**
 * c2-performance (2026-11-15): a finger dragging a long conversation away from its newest line recomposed about 170
 * scopes in two frames (ChatFrameCostTest): the list leaving the live edge tells the presenter (read acknowledgement),
 * and the page's state came out as a new value although nothing on screen changed. Leaving or reaching the live edge
 * must not hand the screen a different state.
 */
class LiveEdgeQuietTest {
    @Test
    fun `PERF-CHAT-3 leaving and reaching the live edge leaves the page's state as it was`() {
        val world = FakeChatWorld()
        world.transcript.value = stateOf(*(1..5).flatMap { turn(it) }.toTypedArray())
        val p = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            val before = states.value
            p.liveEdgeChanged(false)
            val away = states.value
            p.liveEdgeChanged(true)
            val back = states.value
            assertTrue(away == before, "away from the live edge the state changed: ${differences(before, away)}")
            assertTrue(back == before, "back at the live edge the state changed: ${differences(before, back)}")
        } finally {
            scope.cancel()
        }
    }

    /** The properties of two values of one class that are not equal, by name. */
    private fun differences(a: Any, b: Any): List<String> =
        a.javaClass.declaredFields.filter { f ->
            f.isAccessible = true
            f.get(a) != f.get(b)
        }.map { it.name }
}
