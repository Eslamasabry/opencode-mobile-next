package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/first_reply_notify_offer_test.dart (B-ANDR-023, -024) on the real LiveModeController.
@OptIn(ExperimentalCoroutinesApi::class)
class FirstReplyNotifyOfferTest {
    private class Ask(var value: String?) : NotifyAskStore {
        override fun pending(): Boolean = value == NotifyAskStore.PENDING

        override suspend fun answer(): Boolean {
            value = NotifyAskStore.ANSWERED
            return true
        }
    }

    private class Host(override val profileId: String? = null) : NotifyOfferHost {
        var notifications = true
        var requestsTurnedOn = 0
        override val isolated: Boolean = false

        override fun supportsNotifications(): Boolean = notifications

        override suspend fun enableRequestNotifications() {
            requestsTurnedOn++
        }
    }

    private class Consent(var owed: Boolean = true) : NeedsYouConsent {
        val answers = mutableListOf<Boolean>()

        override suspend fun claim(profileId: String): Outcome<Boolean> = owed.ok()

        override suspend fun answer(profileId: String, allow: Boolean): Outcome<Unit> {
            answers += allow
            return Unit.ok()
        }
    }

    /** Flutter `_Native`: the pause reads clean; every other method answers off. Records non-pause calls. */
    private val port =
        FakeLivePort().apply { all { ok(ServiceStatus(enabled = false, active = false)) } }

    private fun nativeCalls() = port.calls.filter { it != "getBackgroundPause" }

    private class World(val offer: FirstReplyNotifyOffer, val ask: Ask, val host: Host)

    private fun TestScope.world(
        ask: String? = NotifyAskStore.PENDING,
        optIn: Boolean? = null,
        host: Host = Host(),
        consent: Consent = Consent(),
    ): World {
        val asked = Ask(ask)
        val controller = liveMode(backgroundScope, port, FakeOptIn(optIn))
        return World(FirstReplyNotifyOffer(host, controller, asked, consent, backgroundScope), asked, host)
    }

    /** Flutter `_shows`: ask once, let the claim or quiet answer run, ask again. */
    private fun TestScope.shows(offer: FirstReplyNotifyOffer, replyCompleted: Boolean = true): Boolean {
        offer.showFor(replyCompleted)
        runCurrent()
        return offer.showFor(replyCompleted)
    }

    @Test
    fun `B-ANDR-023 the notification offer is asked once a reply has completed, never before`() = runTest {
        val w = world()
        assertFalse(shows(w.offer, replyCompleted = false))
        assertTrue(shows(w.offer))
        assertTrue(w.offer.pending())
        // Asking asks Android for nothing.
        assertEquals(emptyList(), nativeCalls())
    }

    @Test
    fun `B-ANDR-023 after an answer the offer is never made again`() = runTest {
        val w = world()
        assertTrue(shows(w.offer))
        w.offer.decline()
        assertFalse(w.offer.showFor(replyCompleted = true))
        assertFalse(w.offer.pending())
        assertEquals(emptyList(), nativeCalls())
    }

    @Test
    fun `B-ANDR-024 the offer is not made to a person who never came through first run, was never asked, already answered or has not finished`() =
        runTest {
            // Only `oc.firstRun.notifyAsk == pending` owes the question: no value (never came through first run,
            // returning and never asked, first run still armed) or `answered` never asks.
            for (ask in listOf(null, NotifyAskStore.ANSWERED, "armed")) {
                val w = world(ask = ask)
                assertFalse(shows(w.offer), ask.toString())
                assertEquals(emptyList(), nativeCalls())
            }
        }

    @Test
    fun `B-ANDR-024 the offer is not made when the background connection is already on, and that answers it`() = runTest {
        val w = world(optIn = true)
        assertFalse(shows(w.offer))
        runCurrent()
        assertEquals(emptyList(), nativeCalls())
        assertFalse(w.ask.pending())
    }

    @Test
    fun `B-ANDR-024 the offer is not made where the device cannot notify and stays owed`() = runTest {
        val host = Host().apply { notifications = false }
        val w = world(host = host)
        assertFalse(shows(w.offer))
        assertTrue(w.ask.pending())
    }

    @Test
    fun `B-ANDR-024 a refusal at Android's prompt counts as an answer with a reason`() = runTest {
        val w = world()
        assertTrue(w.offer.showFor(replyCompleted = true))
        w.offer.accept()
        assertEquals(1, w.host.requestsTurnedOn)
        assertEquals(NotifyOfferFailure.NotEnabled, w.offer.state.value.failure)
        assertFalse(w.offer.showFor(replyCompleted = true))
        assertFalse(w.ask.pending())
        w.offer.dismissFailure()
        assertNull(w.offer.state.value.failure)
    }

    @Test
    fun `B-ANDR-024 a server that was already asked is not asked again and settles the question`() = runTest {
        val consent = Consent(owed = false)
        val w = world(host = Host(profileId = "p1"), consent = consent)
        assertFalse(shows(w.offer))
        runCurrent()
        assertFalse(w.ask.pending())

        val owed = Consent(owed = true)
        val again = world(host = Host(profileId = "p1"), consent = owed)
        assertTrue(shows(again.offer), "claimed for the server first, then shown")
        again.offer.decline()
        assertEquals(listOf(false), owed.answers)
    }
}
