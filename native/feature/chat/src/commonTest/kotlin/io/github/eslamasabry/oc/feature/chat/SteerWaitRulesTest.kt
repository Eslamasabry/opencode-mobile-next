package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.presentation.SteerWaitRules
import kotlin.test.Test
import kotlin.test.assertNull

class SteerWaitRulesTest {
    private val prompt = PromptRow("p1", "t1", "hello")

    @Test
    fun `no note when no run is going or a send would not join it`() {
        assertNull(SteerWaitRules.wait(listOf(prompt), emptyMap(), busy = false, joins = true))
        assertNull(SteerWaitRules.wait(listOf(prompt), emptyMap(), busy = true, joins = false))
    }

    @Test
    fun `no note without an earlier turn that still runs a step`() {
        assertNull(SteerWaitRules.wait(listOf(prompt), emptyMap(), busy = true, joins = true))
    }
}
