package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.AboutWait
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneHeatFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerState
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneAnswers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Flutter `_heat`: the page says the phone is warm or hot, and when the team comes back. */
class PhoneHeatAnswerTest {
    private val installed = RuntimeStatus(installed = true, phase = RuntimePhase.Ready)
    private val running = installed.copy(serverRunning = true, serverPort = 4097)
    private val now = Instant.fromEpochSeconds(1_000)

    private fun answer(facts: PhoneServerFacts) = ThisPhoneAnswers.of(PhoneServerState.of(facts), facts, now)

    @Test
    fun `a warm phone reads paused with the time it resumes`() {
        val facts =
            PhoneServerFacts(
                status = running,
                probe = ProbeAnswer.Answered,
                heat = PhoneHeatFacts(false, true, Instant.fromEpochSeconds(1_240)),
            )
        val a = answer(facts)
        assertEquals(AnswerHeadline.HeatPaused, a.headline)
        assertEquals(AnswerTone.Paused, a.tone)
        assertEquals(AnswerLine.HeatResumeIn(AboutWait.Minutes(4)), a.line)
    }

    @Test
    fun `a hot phone that stopped the server says stopped and waits for the cool-down`() {
        val facts = PhoneServerFacts(status = installed, heat = PhoneHeatFacts(true, true, null))
        val a = answer(facts)
        assertEquals(AnswerHeadline.HeatStopped, a.headline)
        assertEquals(AnswerLine.HeatResumeCool, a.line)
    }

    @Test
    fun `without self-resume the line says it stays paused and Start is the visible way back`() {
        val facts = PhoneServerFacts(status = installed, heat = PhoneHeatFacts(true, false, null))
        val a = answer(facts)
        assertEquals(AnswerLine.HeatStays, a.line)
        assertEquals(PhoneAct.Start, a.primary)
    }

    @Test
    fun `the person's own Stop is never rewritten as heat`() {
        val facts = PhoneServerFacts(status = installed, stoppedByPerson = true, heat = PhoneHeatFacts(true, true, null))
        assertEquals(AnswerHeadline.Off, answer(facts).headline)
    }
}
