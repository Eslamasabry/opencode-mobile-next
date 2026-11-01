package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadPolicyTest {
    private fun decide(
        conditions: DeviceConditions,
        sink: SinkKind = SinkKind.Remote,
        gateOpen: Boolean = true,
        queued: Int = 3,
        backoff: Backoff = Backoff(),
        now: Long = 0,
    ) = UploadPolicy.decide(conditions, sink, gateOpen, queued, backoff, now)

    private val wifiFg = Online
    private val wifiBg = Online.copy(foreground = false)
    private val mobileFg = Online.copy(network = NetworkKind.Metered)
    private val mobileBg = mobileFg.copy(foreground = false)

    @Test
    fun `B-ANLY-008 never on mobile data while the app is in the background`() {
        assertEquals(UploadDecision.Hold(HoldReason.MobileDataInBackground), decide(mobileBg))
        assertEquals(UploadDecision.Go, decide(mobileFg))
        assertEquals(UploadDecision.Go, decide(wifiBg))
    }

    @Test
    fun `B-ANLY-008 never without a validated network, and never on Data Saver in the background`() {
        assertEquals(UploadDecision.Hold(HoldReason.NoNetwork), decide(DeviceConditions.Unknown))
        assertEquals(UploadDecision.Hold(HoldReason.NetworkNotValidated), decide(wifiFg.copy(validated = false)))
        assertEquals(UploadDecision.Hold(HoldReason.DataSaverInBackground), decide(wifiBg.copy(dataSaver = true)))
        assertEquals(UploadDecision.Go, decide(wifiFg.copy(dataSaver = true)))
    }

    @Test
    fun `B-ANLY-008 with no destination, before the gate, or with nothing queued nothing is sent`() {
        assertEquals(UploadDecision.Hold(HoldReason.NoDestination), decide(wifiFg, sink = SinkKind.None))
        assertEquals(UploadDecision.Hold(HoldReason.GateClosed), decide(wifiFg, gateOpen = false))
        assertEquals(UploadDecision.Hold(HoldReason.NothingQueued), decide(wifiFg, queued = 0))
    }

    @Test
    fun `B-ANLY-008 a local debug sink ignores the network rules`() {
        assertEquals(UploadDecision.Go, decide(mobileBg, sink = SinkKind.LocalDebug))
        assertEquals(UploadDecision.Go, decide(DeviceConditions.Unknown, sink = SinkKind.LocalDebug))
    }

    @Test
    fun `B-ANLY-007 backoff starts at one minute, doubles, and stops at six hours`() {
        assertEquals(60_000L, Backoff.delayFor(1))
        assertEquals(120_000L, Backoff.delayFor(2))
        assertEquals(240_000L, Backoff.delayFor(3))
        assertEquals(6 * HOUR_MS, Backoff.delayFor(10))
        assertEquals(6 * HOUR_MS, Backoff.delayFor(1_000))
        val once = Backoff().failed(nowMs = 1_000)
        assertEquals(UploadDecision.Hold(HoldReason.BackingOff), decide(wifiFg, backoff = once, now = 60_999))
        assertEquals(UploadDecision.Go, decide(wifiFg, backoff = once, now = 61_000))
    }
}
