package io.github.eslamasabry.opencode_mobile.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Owner decision 2026-11-07: a Flutter Termux setup gets one plain notice and nothing else. */
class TermuxRetiredNoticeTest {
    private class Rig(
        var setupFile: Boolean = false,
        var addresses: List<String> = emptyList(),
        var seen: Boolean = false,
    ) {
        var marks = 0
        val notice =
            TermuxRetiredNotice(
                legacySetupFile = { setupFile },
                savedAddresses = { addresses },
                memory =
                    object : TermuxRetiredNotice.Memory {
                        override suspend fun seen(): Boolean = seen

                        override suspend fun markSeen() {
                            marks++
                            seen = true
                        }
                    },
            )
    }

    @Test
    fun `B-MIGR-016 a migrated profile at the Flutter Termux address shows the notice`() = runTest {
        val rig =
            Rig(addresses = listOf("https://server.example.test:4097", "http://127.0.0.1:4096"))
        rig.notice.check()
        assertTrue(rig.notice.shown.value)
    }

    @Test
    fun `B-MIGR-016 the Flutter Termux setup file alone shows the notice`() = runTest {
        val rig = Rig(setupFile = true)
        rig.notice.check()
        assertTrue(rig.notice.shown.value)
    }

    @Test
    fun `B-MIGR-016 no Termux sign shows nothing, the built-in server is not Termux`() = runTest {
        val rig =
            Rig(
                addresses =
                    listOf(
                        "http://127.0.0.1:4097",
                        "https://server.example.test:4096",
                        "http://localhost:4096",
                    )
            )
        rig.notice.check()
        assertFalse(rig.notice.shown.value)
    }

    @Test
    fun `B-MIGR-016 profiles arriving after the first check still lead to the notice`() = runTest {
        val rig = Rig()
        rig.notice.check()
        assertFalse(rig.notice.shown.value)
        rig.addresses = listOf("http://127.0.0.1:4096")
        rig.notice.check()
        assertTrue(rig.notice.shown.value)
    }

    @Test
    fun `B-MIGR-016 closing it remembers, and it never comes back`() = runTest {
        val rig = Rig(setupFile = true, addresses = listOf("http://127.0.0.1:4096"))
        rig.notice.check()
        rig.notice.close()
        assertFalse(rig.notice.shown.value)
        assertEquals(1, rig.marks)
        rig.notice.check()
        assertFalse(rig.notice.shown.value)

        val restarted = Rig(setupFile = true, addresses = listOf("http://127.0.0.1:4096"), seen = true)
        restarted.notice.check()
        assertFalse(restarted.notice.shown.value)
    }

    @Test
    fun `B-MIGR-016 the notice says the owner's words, the same as the Flutter app`() {
        val xml = java.io.File("src/main/res/values/strings_andr_notices.xml").readText()
        fun text(name: String) =
            Regex("<string name=\"$name\">(.*?)</string>").find(xml)!!.groupValues[1].replace("\\u0027", "'")
        assertEquals(
            "Termux is no longer supported. Set up built-in Linux on this phone, or connect to your computer. " +
                "Your projects in Termux are still there; copy them to the phone's shared storage or push them " +
                "with Git to bring them over.",
            text("andr_termuxRetiredNotice"),
        )
        assertEquals("Set up built-in Linux", text("andr_termuxRetiredSetUpBuiltin"))
        assertEquals("Connect a computer", text("andr_termuxRetiredConnectComputer"))
    }

    @Test
    fun `B-MIGR-016 the address rule is Flutter's managesServerUrl`() {
        assertTrue(TermuxRetiredNotice.isTermuxAddress("http://127.0.0.1:4096"))
        assertTrue(TermuxRetiredNotice.isTermuxAddress("http://127.0.0.1:4096/"))
        assertFalse(TermuxRetiredNotice.isTermuxAddress("https://127.0.0.1:4096"))
        assertFalse(TermuxRetiredNotice.isTermuxAddress("http://127.0.0.1:4097"))
        assertFalse(TermuxRetiredNotice.isTermuxAddress("http://127.0.0.1"))
        assertFalse(TermuxRetiredNotice.isTermuxAddress("not a url"))
        assertFalse(TermuxRetiredNotice.isTermuxAddress(null))
    }
}
