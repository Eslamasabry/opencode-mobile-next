@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneModel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThisPhoneSizeShownTest {
    private class RecordingServer(facts: PhoneServerFacts) : PhoneServerSource by FakeServer(facts) {
        val shown = mutableListOf<Boolean>()

        override fun sizeShown(visible: Boolean) {
            shown += visible
        }
    }

    @Test
    fun `B-LINUX-287 This phone tells the source when the size is on screen`() =
        runTest {
            val running = RuntimeStatus(installed = true, phase = RuntimePhase.Ready, serverRunning = true)
            val server = RecordingServer(PhoneServerFacts(status = running, probe = ProbeAnswer.Answered))
            val model =
                ThisPhoneModel(server, FakeSettings(), RecordingNav(), backgroundScope, testClock(), foregroundPacer())
            model.shown(true)
            runCurrent()
            assertEquals(listOf(true), server.shown)
            model.shown(false)
            runCurrent()
            assertEquals(listOf(true, false), server.shown)
        }
}
