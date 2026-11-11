package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S8: the conversation's hardware keys, table by table: which key means what, and when it is on offer. */
class ChatKeysTest {
    private fun of(key: Key, ctrl: Boolean = false, meta: Boolean = false, alt: Boolean = false, shift: Boolean = false) =
        ChatKeys.of(key, ctrl, meta, alt, shift)

    @Test
    fun `keys map to their action`() {
        val table =
            listOf(
                Triple(Key.K, "ctrl", ChatKey.Launcher),
                Triple(Key.F, "ctrl", ChatKey.Find),
                Triple(Key.Grave, "ctrl", ChatKey.Terminal),
                Triple(Key.B, "ctrl", ChatKey.Background),
                Triple(Key.K, "meta", ChatKey.Launcher),
                Triple(Key.F2, "", ChatKey.ModelNext),
                Triple(Key.F2, "shift", ChatKey.ModelPrevious),
                Triple(Key.F3, "", ChatKey.FindNext),
                Triple(Key.F3, "shift", ChatKey.FindPrevious),
            )
        table.forEach { (key, mod, want) ->
            assertEquals(want, of(key, ctrl = mod == "ctrl", meta = mod == "meta", shift = mod == "shift"), "$key $mod")
        }
    }

    @Test
    fun `a plain letter is typing, never a shortcut`() {
        listOf(Key.K, Key.F, Key.B, Key.Grave, Key.N).forEach { assertNull(of(it), "$it") }
        assertNull(of(Key.K, ctrl = true, alt = true))
        assertNull(of(Key.K, ctrl = true, shift = true))
        assertNull(of(Key.F2, ctrl = true))
    }

    @Test
    fun `availability per screen state`() {
        val live = ChatKeyScene(terminal = true, canBackground = true, findOpen = true)
        ChatKey.entries.forEach { assertTrue(ChatKeys.available(it, live), "$it live") }
        // A sheet or menu on top gets every key; the page stays quiet.
        ChatKey.entries.forEach { assertFalse(ChatKeys.available(it, live.copy(overlayOpen = true)), "$it overlay") }
        // The offline demo offers no server actions.
        ChatKey.entries.forEach { assertFalse(ChatKeys.available(it, live.copy(demo = true)), "$it demo") }
        // F3 only walks matches while find is open.
        assertFalse(ChatKeys.available(ChatKey.FindNext, live.copy(findOpen = false)))
        assertFalse(ChatKeys.available(ChatKey.FindPrevious, live.copy(findOpen = false)))
        // Capabilities gate their targets.
        assertFalse(ChatKeys.available(ChatKey.Terminal, live.copy(terminal = false)))
        assertFalse(ChatKeys.available(ChatKey.Background, live.copy(canBackground = false)))
        assertTrue(ChatKeys.available(ChatKey.Launcher, ChatKeyScene()))
    }

    @Test
    fun `the launcher lights, moves, runs and filters`() {
        var ran = ""
        fun entry(key: String, title: String, slash: String) =
            ChatCommandEntry(key, slash, title, null, "g", null, onRun = { ran = key })
        val all = listOf(entry("new", "New conversation", "new"), entry("files", "Project files", "files"), entry("find", "Find", "find"))
        assertEquals(listOf("files"), all.filter { it.matches("fil") }.map { it.key })
        assertEquals(listOf("files"), all.filter { it.matches("/files") }.map { it.key })
        assertEquals(1, LauncherKeys.move(0, 1, 3))
        assertEquals(2, LauncherKeys.move(2, 1, 3))
        assertEquals(0, LauncherKeys.move(0, -1, 3))
        assertEquals(0, LauncherKeys.move(0, 1, 0))
        LauncherKeys.run(all, 1)!!.onRun()
        assertEquals("files", ran)
        assertNull(LauncherKeys.run(emptyList(), 0))
    }
}
