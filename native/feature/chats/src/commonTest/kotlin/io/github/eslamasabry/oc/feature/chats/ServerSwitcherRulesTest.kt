package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ServerSwitcherRules
import io.github.eslamasabry.oc.feature.chats.presentation.SwitcherServerState
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** F: lib/ui/widgets/server_switcher_sheet.dart:145-160,312-350 (row order and state words). */
class ServerSwitcherRulesTest {
    private fun source(id: String, main: Boolean = false, phone: Boolean = false, down: Boolean = false) =
        ChatsListSourceUi(ProfileId(id), id.uppercase(), shown = true, main = main, onThisPhone = phone, unreachable = down)

    @Test
    fun `PARITY-B-235 the server in use leads, then waiting, working, not answering, the rest, and this phone last`() {
        val extra =
            ChatsHomeExtraState(
                sources =
                    persistentListOf(
                        source("phone", phone = true),
                        source("quiet"),
                        source("down", down = true),
                        source("busy"),
                        source("main", main = true),
                        source("asks"),
                    )
            )
        val items =
            listOf(
                item("r1", status = ChatStatus.Running).copy(profileId = ProfileId("busy")),
                item("r2", status = ChatStatus.Running).copy(profileId = ProfileId("busy")),
                // A sub-agent's work is its parent's: not counted twice.
                item("r3", status = ChatStatus.Running, parent = "r1").copy(profileId = ProfileId("busy")),
                item("a1", status = ChatStatus.NeedsYou).copy(profileId = ProfileId("asks")),
                // The phone is last however busy it is (Flutter appends its row after the others).
                item("p1", status = ChatStatus.NeedsYou).copy(profileId = ProfileId("phone")),
            )
        val rows = ServerSwitcherRules.rows(extra, items)
        assertEquals(listOf("main", "asks", "busy", "down", "quiet", "phone"), rows.map { it.profile.raw })
        assertEquals(SwitcherServerState.Connected, rows[0].state)
        assertEquals(true, rows[0].current)
        assertEquals(1, rows[1].needsYou)
        assertEquals(2, rows[2].working)
        assertEquals(SwitcherServerState.NotAnswering, rows[3].state)
        assertEquals(SwitcherServerState.Saved, rows[4].state)
    }

    @Test
    fun `PARITY-B-235 the server still opening says Connecting, and Not answering once it failed`() {
        val sources = persistentListOf(source("main", main = true), source("other"))
        val opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "MAIN", profile = ProfileId("main"))
        assertEquals(
            SwitcherServerState.Connecting,
            ServerSwitcherRules.rows(ChatsHomeExtraState(sources, opening), emptyList()).first().state,
        )
        assertEquals(
            SwitcherServerState.NotAnswering,
            ServerSwitcherRules.rows(ChatsHomeExtraState(sources, opening.copy(phase = ChatsOpeningPhase.Failed)), emptyList())
                .first()
                .state,
        )
    }

    @Test
    fun `S6 one waiting command is allowable, several or no command only open, a down server asks nothing`() {
        val extra =
            ChatsHomeExtraState(
                sources = persistentListOf(source("main", main = true), source("one"), source("two"), source("down", down = true)),
            )
        val withCommand =
            item("o1", status = ChatStatus.NeedsYou).copy(
                profileId = ProfileId("one"),
                permission = io.github.eslamasabry.oc.engine.agents.feed.FeedPermission("req-1", "npm install"),
            )
        val rows =
            ServerSwitcherRules.rows(
                extra,
                listOf(
                    withCommand,
                    item("t1", status = ChatStatus.NeedsYou).copy(profileId = ProfileId("two")),
                    item("t2", status = ChatStatus.NeedsYou).copy(profileId = ProfileId("two")),
                    item("d1", status = ChatStatus.NeedsYou).copy(profileId = ProfileId("down")),
                ),
            )
        val byId = rows.associateBy { it.profile.raw }
        assertEquals(true, byId.getValue("one").ask?.allowable)
        assertEquals("npm install", byId.getValue("one").ask?.command)
        assertEquals(false, byId.getValue("two").ask?.allowable)
        assertEquals(2, byId.getValue("two").ask?.count)
        assertEquals(null, byId.getValue("down").ask)
    }

    @Test
    fun `S6 a server whose secret must be typed again carries that state`() {
        val extra =
            ChatsHomeExtraState(
                sources =
                    persistentListOf(
                        source("main", main = true),
                        source("pw").copy(secret = io.github.eslamasabry.oc.feature.chats.presentation.SwitcherSecret.Password),
                    ),
            )
        val row = ServerSwitcherRules.rows(extra, emptyList()).first { it.profile.raw == "pw" }
        assertEquals(io.github.eslamasabry.oc.feature.chats.presentation.SwitcherSecret.Password, row.secret)
    }
}
