package io.github.eslamasabry.oc.feature.chats.rb.o41

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.gate.LayoutGuard
import io.github.eslamasabry.oc.ui.gate.LayoutGuard.Window
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Home as it is on a Galaxy S22: the server pill, then the filter chips beside it. The shell's bar is on and two
 * connections are saved, so the line holds "This phone", "All projects", "All connections" and "Running": the
 * fixture the first pass lacked (its Home had no pill and no second connection, so it never met the cut chip).
 */
class ChatsHomeHeaderCompactTest {
    private fun source(id: String, name: String, main: Boolean, phone: Boolean) =
        ChatsListSourceUi(ProfileId(id), name, shown = true, main = main, onThisPhone = phone, unreachable = false)

    @Test
    fun `COMPACT-030 the Home's chips beside the server pill are never cut at 360 dp`() {
        val state =
            ChatsUi(
                phase = ChatsPhase.NoMatch,
                nowMs = 1_791_028_800_000L,
                home = ChatsHomeExtraState(
                    sources = persistentListOf(source("p1", "This phone", true, true), source("p2", "Laptop", false, false)),
                ),
            )
        val scene = LayoutGuard.Scene(Frame.Screen) { KitSheetHost { ChatsScreen(state, RecordingChatsActions(), shellBar = true) } }
        val found = LayoutGuard.audit(scene, listOf(Window.Compact, Window.CompactLarge, Window.Narrow))
            .filter { it.rule == "rest-cut" || it.rule == "clipped" || it.rule == "cut-off" }
            .map { "${it.window.name} ${it.rule} ${it.subject}" }
        assertTrue(found.isEmpty(), found.toString())
    }
}
