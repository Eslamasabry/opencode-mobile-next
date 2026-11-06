package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

/** The server pill's words on the Conversations home. */
class HomeConnectionTest {
    private fun source(name: String, main: Boolean, unreachable: Boolean = false) =
        ChatsListSourceUi(ProfileId(name), name, shown = true, main = main, onThisPhone = false, unreachable = unreachable)

    @Test
    fun `B-NAV-037 the pill names the main server, and says when it is opening or not answering`() {
        assertNull(HomeConnectionUi.of(ChatsHomeExtraState()))
        val sources = persistentListOf(source("Laptop", main = false), source("Office PC", main = true))
        assertEquals(
            HomeConnectionUi("Office PC", HomeConnectionState.Connected),
            HomeConnectionUi.of(ChatsHomeExtraState(sources = sources)),
        )
        assertEquals(
            HomeConnectionState.NotAnswering,
            HomeConnectionUi.of(ChatsHomeExtraState(sources = persistentListOf(source("Office PC", main = true, unreachable = true))))?.state,
        )
        assertEquals(
            HomeConnectionUi("Laptop", HomeConnectionState.Connecting),
            HomeConnectionUi.of(ChatsHomeExtraState(sources = sources, opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "Laptop"))),
        )
        assertEquals(
            HomeConnectionState.NotAnswering,
            HomeConnectionUi.of(ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Failed, "Laptop")))?.state,
        )
    }
}
