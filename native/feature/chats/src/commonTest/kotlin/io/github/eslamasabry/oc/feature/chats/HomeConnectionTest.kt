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
    fun `B-HOME-214 the person's own stop is the pill's Stopped, never Not answering, and a server that failed by itself keeps Not answering`() {
        val stopped = source("This phone", main = true).copy(onThisPhone = true, stoppedByPerson = true)
        assertEquals(
            HomeConnectionState.Stopped,
            HomeConnectionUi.of(ChatsHomeExtraState(sources = persistentListOf(stopped)))?.state,
        )
        assertEquals(
            HomeConnectionState.Stopped,
            HomeConnectionUi.of(ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Failed, "This phone", onThisPhone = true, phoneOff = true)))?.state,
        )
        // It stopped by itself: today's failure path, untouched.
        assertEquals(
            HomeConnectionState.NotAnswering,
            HomeConnectionUi.of(ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Failed, "This phone", onThisPhone = true)))?.state,
        )
        assertEquals(
            HomeConnectionState.NotAnswering,
            HomeConnectionUi.of(ChatsHomeExtraState(sources = persistentListOf(source("This phone", main = true, unreachable = true).copy(onThisPhone = true))))?.state,
        )
    }

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
