package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaseoResumeMemoryTest {
    @Test
    fun aRefusedReopenIsRemembered() {
        val memory = PaseoResumeMemory()
        memory.note("a", PaseoFailureKind.Unavailable, live = false)
        assertTrue(memory.failedBefore("a", live = false))
        assertFalse(memory.failedBefore("b", live = false))
    }

    @Test
    fun aDroppedConnectionIsNotARefusal() {
        val memory = PaseoResumeMemory()
        memory.note("a", PaseoFailureKind.Disconnected, live = false)
        memory.note("a", PaseoFailureKind.DeliveryUnknown, live = false)
        assertFalse(memory.failedBefore("a", live = false))
    }

    @Test
    fun aLiveConversationIsNeverCalledFailed() {
        val memory = PaseoResumeMemory()
        memory.note("a", PaseoFailureKind.Unavailable, live = true)
        assertFalse(memory.failedBefore("a", live = false))
        memory.note("a", PaseoFailureKind.Unavailable, live = false)
        assertFalse(memory.failedBefore("a", live = true))
    }

    @Test
    fun forgetAndClearForget() {
        val memory = PaseoResumeMemory()
        memory.note("a", PaseoFailureKind.NotFound, live = false)
        memory.forget("a")
        assertFalse(memory.failedBefore("a", live = false))
        memory.note("a", PaseoFailureKind.NotFound, live = false)
        memory.clear()
        assertFalse(memory.failedBefore("a", live = false))
    }
}
