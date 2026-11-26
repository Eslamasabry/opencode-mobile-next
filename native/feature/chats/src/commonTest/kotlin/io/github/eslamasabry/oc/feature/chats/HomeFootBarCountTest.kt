package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeFootBarRules
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

class HomeFootBarCountTest {
    private fun row(id: String) =
        ConversationRowUi(
            key = id,
            ref = ConversationRef(ProfileId("p"), SessionId(id)),
            title = RowTitle.Text("Row $id"),
            project = "api",
            status = RowStatus.None,
        )

    @Test
    fun `B-554 the All conversations count is never below the rows shown`() {
        val two = persistentListOf(row("s1"), row("s2"))
        assertEquals(2, HomeFootBarRules.count(ChatsUi(phase = ChatsPhase.List, recent = two, allCount = 0)))
        assertEquals(
            2,
            HomeFootBarRules.count(
                ChatsUi(
                    phase = ChatsPhase.List,
                    rightNow = persistentListOf(row("s1")),
                    recent = two,
                    allCount = 0,
                ),
            ),
        )
        assertEquals(5, HomeFootBarRules.count(ChatsUi(phase = ChatsPhase.List, recent = two, allCount = 5)))
        assertEquals(
            1,
            HomeFootBarRules.count(ChatsUi(phase = ChatsPhase.List, recent = two, allCount = 0, shownCount = 1)),
        )
        assertNull(HomeFootBarRules.count(ChatsUi(phase = ChatsPhase.Opening, recent = two, allCount = 2)))
        assertEquals(0, HomeFootBarRules.count(ChatsUi(phase = ChatsPhase.List, allCount = 0)))
    }
}
