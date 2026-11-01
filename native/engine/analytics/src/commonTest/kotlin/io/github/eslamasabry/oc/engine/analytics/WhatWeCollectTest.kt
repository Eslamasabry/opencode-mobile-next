package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatWeCollectTest {
    @Test
    fun `B-ANLY-011 every registered event is listed once, under its tier, with every value it can take`() {
        val view = WhatWeCollectSource.build(ConsentState(), NoOpSink)
        val listed = view.basicEvents + view.detailedEvents
        assertEquals(AnalyticsSchema.events.map { it.name }, listed.map { it.name })
        assertTrue(view.basicEvents.all { it.tier == AnalyticsTier.Basic })
        assertTrue(view.detailedEvents.all { it.tier == AnalyticsTier.Detailed })
        listed.forEach { event ->
            val spec = AnalyticsSchema.spec(event.name)!!
            assertEquals(spec.props.map { it.key to it.allowed }, event.props.map { it.key to it.values.toList() })
        }
    }

    @Test
    fun `B-ANLY-011 with no destination it says not sent in this version and hides the switch`() {
        val view = WhatWeCollectSource.build(ConsentState(), NoOpSink)
        assertEquals(SendingState.NotSentInThisVersion, view.sending)
        assertFalse(view.detailedSwitchShown)
        assertNull(view.destinationHost)
    }

    @Test
    fun `B-ANLY-011 it reflects consent and the bound sink, and lists what is never collected`() {
        val off = WhatWeCollectSource.build(ConsentState(DetailedConsent.Declined), RecordingRemoteSink("collector.example"))
        assertEquals(SendingState.Sending, off.sending)
        assertEquals("collector.example", off.destinationHost)
        assertTrue(off.detailedSwitchShown)
        assertTrue(off.basicEvents.all { it.active })
        assertTrue(off.detailedEvents.none { it.active })
        val on = WhatWeCollectSource.build(ConsentState(DetailedConsent.Granted), JsonDebugSink())
        assertEquals(SendingState.LocalDebugOnly, on.sending)
        assertTrue(on.detailedEvents.all { it.active })
        assertEquals(NeverCollected.entries.toList(), off.never.toList())
        assertTrue(NeverCollected.Credentials in off.never && NeverCollected.ServerAddresses in off.never)
        assertEquals(30, off.installIdRotationDays)
        assertEquals(60, off.sessionIdleMinutes)
    }
}
