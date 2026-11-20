package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-HOME-220: one name for one thing; the line beside "Recent" never swaps between two wordings for one wait. */
class HomeStageNameTest {
    @Test
    fun `B-HOME-220 the helper starting and its agents warming up carry one name`() {
        assertEquals(HomeStage.Agents.named(), HomeStage.Helper.named())
    }

    @Test
    fun `B-HOME-220 the phone's own stages keep their names`() {
        assertEquals(HomeStage.Linux, HomeStage.Linux.named())
        assertEquals(HomeStage.OpenCode, HomeStage.OpenCode.named())
    }
}
