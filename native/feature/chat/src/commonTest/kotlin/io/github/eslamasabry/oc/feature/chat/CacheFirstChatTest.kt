package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cache-first (owner rule, 2026-11-09): a conversation opened again draws its real transcript from the rows it last
 * showed; there is no loading or offline variant once rows are known. The status line is the only place that says
 * the server is still connecting or away.
 */
class CacheFirstChatTest {
    private val world = FakeChatWorld()
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
    private val kept = stateOf(*(turn(1) + turn(2)).toTypedArray())

    @Test
    fun `CF-12 known rows draw the live layout while the history read is on its way`() {
        world.transcript.value = kept
        world.load.value = ChatLoad.Live
        world.link.value = ChatLink.Live
        val live = presenter.current()

        world.load.value = ChatLoad.Loading
        world.link.value = ChatLink.Connecting
        val first = presenter.current()

        assertEquals(ChatPhase.Live, first.phase, "no skeleton when rows are known")
        assertEquals(live.rows.map { it.key }, first.rows.map { it.key }, "the same rows, in the same order")
        assertEquals(live.title, first.title)
        assertEquals(ChatStatusKind.Connecting, first.status?.kind, "the status line says it is still connecting")
    }

    @Test
    fun `CF-13 a conversation opened while its server is away shows its kept rows, not an error page`() {
        // Rows from the last visit (the world holds them) and no server: the load fails at once.
        world.transcript.value = kept
        world.link.value = ChatLink.Offline
        world.load.value = ChatLoad.Failed(Failure.Network(NetworkWhy.Offline))

        val ui = presenter.current()
        assertEquals(ChatPhase.Live, ui.phase)
        assertEquals(kept.rows.map { it.key }, ui.rows.map { it.key })
        assertEquals(ChatStatusKind.Offline, ui.status?.kind)
    }

    @Test
    fun `CF-14 with nothing known the first-ever load still shows the loading body`() {
        world.transcript.value = stateOf()
        world.load.value = ChatLoad.Loading
        assertEquals(ChatPhase.Loading, presenter.current().phase)
    }
}
