package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class DockNicknamesTest {
    private val p = ProfileId("1735000000000001")
    private val q = ProfileId("1735000000000002")
    private val a = ConversationRef(p, SessionId("ses_a"))
    private val b = ConversationRef(p, SessionId("ses_b"))

    @Test
    fun `normalize trims, flattens, caps and drops blanks`() {
        data class Case(val label: String, val raw: String?, val expect: String?)
        val long = "x".repeat(50)
        val cases =
            listOf(
                Case("null", null, null),
                Case("blank", "  \n\t ", null),
                Case("trim", "  Bank  ", "Bank"),
                Case("inner runs become one space", "Bank \n\t API", "Bank API"),
                Case("control characters become a space", "Ba\u0000nk", "Ba nk"),
                Case("capped", long, "x".repeat(DockNicknames.MAX_LENGTH)),
                Case("cut never ends in a space", "x".repeat(DockNicknames.MAX_LENGTH - 1) + " tail", "x".repeat(DockNicknames.MAX_LENGTH - 1)),
                Case("cut never splits a surrogate pair", "x".repeat(DockNicknames.MAX_LENGTH - 1) + "😀", "x".repeat(DockNicknames.MAX_LENGTH - 1)),
                Case("arabic is kept", "  بنك  ", "بنك"),
            )
        for (c in cases) assertEquals(c.expect, DockNicknames.normalize(c.raw), c.label)
    }

    @Test
    fun `a nickname survives a restart and clears back to the default`() = runTest {
        val disk = FakeAttentionKeyStore()
        val first = DockNicknames(disk)
        assertEquals(NicknameResult.Saved("Bank"), first.set(a, "  Bank "))
        assertEquals("Bank", first.nickname(a))
        assertEquals("""{"ses_a":"Bank"}""", disk.data[DockKeys.namesKey(p)])

        val restarted = DockNicknames(disk)
        assertNull(restarted.nickname(a), "nothing is guessed before load")
        assertEquals(mapOf(a.sessionId to "Bank"), restarted.load(p))
        assertEquals("Bank", restarted.nickname(a))

        assertEquals(NicknameResult.Saved(null), restarted.set(a, "   "))
        assertNull(restarted.nickname(a))
        assertFalse(DockKeys.namesKey(p) in disk.data, "the last nickname removes the key")
    }

    @Test
    fun `profiles do not share nicknames`() = runTest {
        val disk = FakeAttentionKeyStore()
        val names = DockNicknames(disk)
        names.set(a, "Mine")
        names.set(ConversationRef(q, a.sessionId), "Theirs")
        assertEquals(mapOf(a.sessionId to "Mine"), names.load(p))
        assertEquals("""{"ses_a":"Theirs"}""", disk.data[DockKeys.namesKey(q)])
    }

    @Test
    fun `a refused write changes nothing`() = runTest {
        val disk = FakeAttentionKeyStore()
        val names = DockNicknames(disk)
        names.set(a, "Kept")
        disk.refuse = true
        assertEquals(NicknameResult.Refused, names.set(a, "Lost"))
        assertEquals("Kept", names.nickname(a))
        disk.refuse = false
        disk.throwOnWrite = true
        assertEquals(NicknameResult.Refused, names.set(b, "Lost"))
        assertNull(names.nickname(b))
    }

    @Test
    fun `unreadable or malformed saved data reads as no nicknames`() = runTest {
        val disk = FakeAttentionKeyStore()
        disk.data[DockKeys.namesKey(p)] = "not json"
        assertEquals(emptyMap(), DockNicknames(disk).load(p))
        disk.data[DockKeys.namesKey(p)] = """{"ses_a":"  Ok  ","ses_b":7,"ses_c":"   "}"""
        assertEquals(mapOf(a.sessionId to "Ok"), DockNicknames(disk).load(p))
        disk.throwOnRead = true
        assertEquals(emptyMap(), DockNicknames(disk).load(p))
    }

    @Test
    fun `the controller renames through the store, opens and marks seen, and forgets a deleted conversation`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val names = DockNicknames(disk)
        val opened = mutableListOf<ConversationRef>()
        val dock = DockController(this, names, { opened += it })
        dock.feed(DockEvent.RunStarted(a))
        dock.feed(DockEvent.RunStarted(b))
        dock.feed(DockEvent.RunEnded(a, DockRunEnd.Failed))
        assertEquals(DockState.Failed, dock.ui.value.entries.first { it.ref == a }.state)

        dock.rename(a, "Bank")
        testScheduler.advanceUntilIdle()
        assertEquals("Bank", dock.ui.value.entries.first { it.ref == a }.name)

        dock.open(a)
        assertEquals(listOf(a), opened)
        assertEquals(a, dock.ui.value.open)
        assertEquals(DockState.Idle, dock.ui.value.entries.first { it.ref == a }.state)

        dock.feed(DockEvent.Deleted(a))
        testScheduler.advanceUntilIdle()
        assertTrue(DockKeys.namesKey(p) !in disk.data, "a deleted conversation's nickname is removed")

        dock.tuck()
        assertTrue(dock.ui.value.tucked)
        dock.untuck()
        assertFalse(dock.ui.value.tucked)
    }
}
