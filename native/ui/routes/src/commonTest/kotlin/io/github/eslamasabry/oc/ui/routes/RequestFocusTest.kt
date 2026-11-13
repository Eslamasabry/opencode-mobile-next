package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Edge light plan v3.1 §4: the "show this request" signal and the navigator defaults the test fakes rely on. */
class RequestFocusTest {
    private val a = ConversationRef(ProfileId("p"), SessionId("ses_a"))
    private val b = ConversationRef(ProfileId("p"), SessionId("ses_b"))

    /** What the ~51 feature fakes look like: only the members they always had. */
    private class OldFake : AppNavigator {
        val opened = mutableListOf<ConversationRef>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            opened += ref
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class TestClock(var at: Instant = Instant.fromEpochMilliseconds(1_000)) : Clock {
        override fun now(): Instant = at
    }

    @Test
    fun `B-LAMP-N1 navigatorDefaults_fakesCompile a fake without the new members switches by opening and never offers a request`() {
        val fake = OldFake()
        fake.switchTo(a, request = "per_1")
        fake.consumeRequestFocus(1)
        assertEquals(listOf(a), fake.opened, "the default switch opens the conversation")
        assertNull(fake.requestFocus.value, "a fake never offers a request")
    }

    @Test
    fun `B-LAMP-N1 requestFocus_consumedOnce the page that showed the request does not get it again`() {
        val signal = RequestFocusSignal()
        val first = signal.emit(a, "per_1")
        assertEquals(RequestFocus(a, "per_1", first.seq), signal.state.value, "waits until the page takes it")
        signal.consume(first.seq)
        assertNull(signal.state.value, "consumed once")
        signal.consume(first.seq)
        assertNull(signal.state.value)
    }

    @Test
    fun `B-LAMP-N1 requestFocus an older seq never consumes a newer request`() {
        val signal = RequestFocusSignal()
        val first = signal.emit(a, "per_1")
        val second = signal.emit(b, "que_2")
        signal.consume(first.seq)
        assertEquals(second, signal.state.value, "the newer request still waits")
    }

    @Test
    fun `B-LAMP-N1 requestFocus a request older than ten seconds is dropped`() {
        val clock = TestClock()
        val signal = RequestFocusSignal(clock)
        val sent = signal.emit(a, "per_1")
        clock.at += 9.seconds
        signal.expireIfOld()
        assertEquals(sent, signal.state.value, "still waiting after 9 s")
        clock.at += 1.seconds
        signal.expireIfOld()
        assertNull(signal.state.value, "gone at 10 s")
    }
}
