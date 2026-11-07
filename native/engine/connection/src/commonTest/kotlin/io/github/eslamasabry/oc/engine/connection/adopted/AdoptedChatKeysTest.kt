package io.github.eslamasabry.oc.engine.connection.adopted

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.adopted.AdoptedChatKeys.Companion.parseChatTail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E00-S03: the key names and the chat tail format, as the Flutter 2.1.4 writers spell them. */
class AdoptedChatKeysTest {
    private val p = ProfileId("1735000000000001")

    private fun tail(
        rows: String = """[{"id":"m1","role":"user","text":"hi"}]""",
        at: String = "1762500000000",
        v: String = "1",
    ) = """{"v":$v,"scope":"/w","session":"ses_1","at":$at,"rows":$rows}"""

    @Test
    fun `B-MIGR-005 adopted key names match the Flutter writers`() {
        assertEquals("oc.lastProject.${p.raw}", AdoptedChatKeys.lastProjectKey(p))
        assertEquals("oc.chatTail.${p.raw}", AdoptedChatKeys.chatTailKey(p))
        assertEquals("oc.chatNoticesSeen.${p.raw}", AdoptedChatKeys.chatNoticesSeenKey(p))
        assertEquals("oc.withdrawnPrompts.${p.raw}", AdoptedChatKeys.withdrawnPromptsKey(p))
        assertEquals(
            "oc.providerRuntimeRefresh.v1.${p.raw}.%3Cdefault%3E%0A%3Cdefault%3E",
            AdoptedChatKeys.providerRuntimeRefreshKey(p),
        )
        assertEquals(
            "oc.providerRuntimeUnloadable.${p.raw}.%2Fhome%2Fdev%2Fmy%20app%0Awrk_1",
            AdoptedChatKeys.providerRuntimeUnloadableKey(p, directory = "/home/dev/my app", workspace = "wrk_1"),
        )
        // Every family carries the id as its last or an interior segment, so the profile sweep finds it.
        for (shape in AdoptedChatKeys.SHAPES) assertTrue(shape.prefix.startsWith("oc.") && shape.prefix.endsWith("."))
        assertEquals(6, AdoptedChatKeys.SHAPES.map { it.prefix }.toSet().size)
    }

    @Test
    fun `B-MIGR-005 a chat tail Dart wrote reads back`() {
        val read = assertNotNull(parseChatTail(tail()))
        assertEquals("/w", read.scope)
        assertEquals("ses_1", read.session)
        assertEquals(1762500000000, read.fetchedAtMs)
        assertEquals(listOf(ChatTailRow("m1", "user", "hi")), read.rows)
        assertNotNull(parseChatTail(tail(rows = "[]")), "an empty tail is still a tail")
        val twelve = (1..12).joinToString(",", "[", "]") { """{"id":"m$it","role":"assistant","text":"t"}""" }
        assertEquals(12, parseChatTail(tail(rows = twelve))?.rows?.size)
    }

    @Test
    fun `B-MIGR-005 a chat tail Dart would not read is ignored`() {
        val long = "x".repeat(AdoptedChatKeys.MAX_TAIL_CHARS)
        val thirteen = (1..13).joinToString(",", "[", "]") { """{"id":"m$it","role":"user","text":"t"}""" }
        val garbled = listOf(
            "" to "empty",
            "{" to "truncated JSON",
            "[]" to "not an object",
            """{"v":1,"fixture":"ChatTail"}""" to "the synthetic-v1 placeholder",
            tail(v = "2") to "another version",
            tail(v = "1.0") to "a version that is not an int",
            tail(at = "1.5") to "a time that is not an int",
            tail(at = "\"now\"") to "a time that is a string",
            tail(rows = thirteen) to "more than twelve rows",
            tail(rows = """[{"id":"m1","role":"user"}]""") to "a row without text",
            tail(rows = """["m1"]""") to "a row that is not an object",
            tail(rows = "{}") to "rows that are not a list",
            tail(rows = """[{"id":"m1","role":"user","text":"$long"}]""") to "too long",
        )
        for ((raw, why) in garbled) assertNull(parseChatTail(raw), why)
    }

    @Test
    fun `B-MIGR-005 no adopted text is printed by toString`() {
        val row = ChatTailRow("m1", "user", "secret prompt text")
        val read = ChatTail("/w", "ses_1", 1, listOf(row))
        val printed = listOf(row.toString(), read.toString(), AdoptedStartState("/home/dev/private", read).toString())
        val leaks = listOf("secret prompt", "/home/dev/private", "ses_1")
        assertFalse(printed.any { line -> leaks.any { it in line } }, printed.toString())
    }
}
