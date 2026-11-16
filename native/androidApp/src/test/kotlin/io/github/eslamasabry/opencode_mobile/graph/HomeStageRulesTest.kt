package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** OWNER-1116: home names the real stage this phone is at, on the "Recent" line; never a guessed one. */
class HomeStageRulesTest {
    @Test
    fun `a cold start names each real stage in order, then nothing`() {
        // The signals in the order a cold start raises them: This phone's own start, then the agents' helper.
        val readings =
            listOf(
                (LaunchStage.Linux to listOf<LaunchStage?>(LaunchStage.Linux)) to HomeStage.Linux,
                (LaunchStage.OpenCode to listOf<LaunchStage?>(null)) to HomeStage.OpenCode,
                (null to listOf<LaunchStage?>(LaunchStage.Helper)) to HomeStage.Helper,
                (null to listOf<LaunchStage?>(LaunchStage.Agent)) to HomeStage.Agents,
            )
        for ((reading, expected) in readings)
            assertEquals(expected, HomeStageRules.of(reading.first, reading.second), "at $reading")
        assertNull(HomeStageRules.of(null, listOf(null)))
        assertNull(HomeStageRules.of(null, emptyList()))
    }

    @Test
    fun `This phone's own start is said before the helper's, and a conversation's read is not home's stage`() {
        assertEquals(HomeStage.OpenCode, HomeStageRules.of(LaunchStage.OpenCode, listOf(LaunchStage.Helper)))
        assertNull(HomeStageRules.of(LaunchStage.Conversation, listOf(LaunchStage.Conversation)))
        // Two helpers saved: the first with a stage is named.
        assertEquals(HomeStage.Agents, HomeStageRules.of(null, listOf(null, LaunchStage.Agent)))
    }
}
