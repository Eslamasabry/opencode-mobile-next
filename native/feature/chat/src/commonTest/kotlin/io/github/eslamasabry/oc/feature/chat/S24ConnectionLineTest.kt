package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * S24-CHAT story 1 (Flutter `connectionKitStatus`): when the link drops, the chat's line says when it
 * tries again, offers Try now, and offers Restart for this phone's server, after a confirm.
 */
class S24ConnectionLineTest {
    private class Now(var ms: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
    }

    private class LinkWorld : FakeChatWorld() {
        val retryAt = MutableStateFlow<Long?>(null)
        override val linkRetryAtMs: StateFlow<Long?> = retryAt
        var onPhone = false
        override val serverOnThisPhone: Boolean
            get() = onPhone
        var restartResult: Outcome<Unit> = Outcome.Ok(Unit)

        override fun tryLinkNow() {
            calls += "tryLinkNow"
        }

        override suspend fun restartPhoneServer(): Outcome<Unit> {
            calls += "restart"
            return restartResult
        }
    }

    private val world = LinkWorld()
    private val clock = Now(100_000)
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate, clock = clock)

    @Test
    fun `B-CHAT-S24-01 a dropped link says how many seconds until it tries again, rounded up`() {
        world.link.value = ChatLink.Offline
        world.retryAt.value = 112_400
        val line = assertNotNull(presenter.current().status)
        assertEquals(ChatStatusKind.Offline, line.kind)
        assertEquals(13, line.retryInSeconds)
        clock.ms = 112_400
        assertEquals(0, presenter.current().status?.retryInSeconds) // "trying again now…"
    }

    @Test
    fun `B-CHAT-S24-02 a reconnecting link with a schedule counts down too, and none without one`() {
        world.link.value = ChatLink.Connecting
        world.retryAt.value = 104_000
        assertEquals(4, presenter.current().status?.retryInSeconds)
        world.retryAt.value = null
        val line = assertNotNull(presenter.current().status)
        assertEquals(ChatStatusKind.Connecting, line.kind)
        assertNull(line.retryInSeconds)
    }

    @Test
    fun `B-CHAT-S24-03 Try now asks the link to try at once and resends nothing`() {
        world.link.value = ChatLink.Offline
        world.retryAt.value = 130_000
        presenter.tryLinkNow()
        assertEquals(listOf("tryLinkNow"), world.calls)
    }

    @Test
    fun `B-CHAT-S24-04 Restart is offered only for this phone's server and runs only after the confirm`() {
        world.link.value = ChatLink.Offline
        assertFalse(assertNotNull(presenter.current().status).canRestart)
        presenter.restartPhoneServer()
        assertNull(presenter.current().confirm) // not this phone's server: nothing asked
        world.onPhone = true
        assertTrue(assertNotNull(presenter.current().status).canRestart)
        presenter.restartPhoneServer()
        assertEquals(ChatConfirm.RestartPhoneServer, presenter.current().confirm)
        assertTrue("restart" !in world.calls)
        presenter.confirmRestartPhoneServer()
        assertNull(presenter.current().confirm)
        assertEquals(listOf("restart"), world.calls)
    }

    @Test
    fun `B-CHAT-S24-05 a restart that fails says so on the line`() {
        world.onPhone = true
        world.restartResult = Outcome.Failed(Failure.Unavailable(FeatureId("restartPhoneServer")))
        presenter.restartPhoneServer()
        presenter.confirmRestartPhoneServer()
        assertEquals(ChatStatusKind.ActionFailed, presenter.current().status?.kind)
    }
}
