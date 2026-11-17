package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.opencode_mobile.SharedFolderReach
import io.github.eslamasabry.opencode_mobile.SharedStorageView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * W3 (walker F-043, Galaxy S22): a folder chosen in the phone's storage, the phone's agents restarted once so it is
 * bound, and minutes later the page said again "The phone's agents started before you chose this folder". What the
 * agents' helper binds at a start is the list the app rebuilds from the saved folders at every preference or profile
 * change; whatever starts the helper again later (the agent-update look, an own-agent reload, a connect) binds that
 * list as it is at that moment.
 */
class PhoneSharedRootsTest {
    private val folder = "/storage/emulated/0/Documents/docsdemo"
    private val phone = "phone"

    @Suppress("LongParameterList") // a test builder: every input has a default, a test names the ones it changes
    private fun roots(
        saved: String,
        serverPort: Int,
        owner: String? = phone,
        granted: Map<String, List<String>> = mapOf(phone to listOf(folder)),
        addresses: Map<String, String> = mapOf(phone to saved),
        opened: List<String> = emptyList(),
        excluding: String? = null,
    ): List<String> = PhoneSharedRoots.of(owner, addresses, granted, opened, serverPort, excluding)

    private fun reach(binds: List<String>): SharedFolderReach =
        SharedFolderReach.decide(
            appAccess = true,
            appSeesFolder = true,
            path = folder,
            running = SharedStorageView(broad = false, roots = binds),
            probeExit = 0,
        )

    @Test
    fun `W3-STORAGE after one restart that bound the folder, the agents' next start binds it too while the server moves port`() {
        // Chosen, then "Restart the phone's agents": the helper starts with the folder and the page says it works there.
        val restarted = roots(saved = "http://127.0.0.1:4100", serverPort = 4100)
        assertEquals(SharedFolderReach.Ready, reach(restarted), "the restart binds the chosen folder")
        // The phone's server comes back on another of its own ports; the saved address follows it only when the app
        // next looks at the server (B-239). Any preference written meanwhile rebuilds the list.
        val rebuilt = roots(saved = "http://127.0.0.1:4100", serverPort = 4101)
        // Something starts the helper again with that list (the agent-update look runs every half minute).
        assertEquals(
            SharedFolderReach.Ready,
            reach(rebuilt),
            "the notice does not come back: the folder stays bound for the agents' next start",
        )
        assertEquals(listOf(folder), roots(saved = "http://localhost:4101/", serverPort = 4100))
    }

    @Test
    fun `W3-STORAGE only this phone's own server keeps its folders across its ports, never a remote or deleted one`() {
        assertEquals(emptyList(), roots(saved = "http://192.168.1.20:4100", serverPort = 4100), "a computer on the same port")
        assertEquals(emptyList(), roots(saved = "http://127.0.0.1:8080", serverPort = 4100), "not one of the phone server's ports")
        assertEquals(
            emptyList(),
            roots(saved = "http://127.0.0.1:4100", serverPort = 4101, owner = null),
            "not this phone's server: only the current port counts, as before",
        )
        assertEquals(emptyList(), roots(saved = "http://127.0.0.1:4100", serverPort = 4100, excluding = phone), "deleted")
        // Another saved local profile (a Flutter build's grant) still counts only on the server's current port.
        val flutter = "/storage/emulated/0/Old"
        val both =
            roots(
                saved = "http://127.0.0.1:4100",
                serverPort = 4101,
                granted = mapOf(phone to listOf(folder), "old" to listOf(flutter)),
                addresses = mapOf(phone to "http://127.0.0.1:4100", "old" to "http://127.0.0.1:4100"),
            )
        assertEquals(listOf(folder), both)
        // Folders opened on this phone's server follow the same rule, in the one spelling Linux binds.
        assertEquals(
            listOf(folder, "/storage/emulated/0/Notes"),
            roots(saved = "http://127.0.0.1:4100", serverPort = 4102, opened = listOf("/sdcard/Notes", "/root/projects/x")),
        )
    }

    @Test
    fun `W3-STORAGE a folder in the phone's storage made a moment ago is there for the page, before Linux binds it`() = runTest {
        var askedLinux = 0
        val linuxNotYetBound: suspend () -> Boolean? = {
            askedLinux++
            false
        }
        assertEquals(true, PhoneProjectCreation.folderPresence(folder, { it == folder }, linuxNotYetBound))
        assertEquals(0, askedLinux, "Linux is not asked about the phone's own storage")
        assertEquals(
            null,
            PhoneProjectCreation.folderPresence("/sdcard/Gone", { false }, linuxNotYetBound),
            "a folder the phone does not find is left to the phone-storage check, never called gone here",
        )
        assertEquals(false, PhoneProjectCreation.folderPresence("/root/projects/x", { true }, linuxNotYetBound))
        assertEquals(1, askedLinux, "the app's own projects are asked of Linux as before")
    }
}
