package io.github.eslamasabry.oc.engine.linuxtools

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The parsing half of test/termux_folders_test.dart plus termux_scripts_test.dart:1117. */
class TermuxFoldersModelTest {
    @Test
    fun `B-LINUX-231 parsing ignores noise and refuses unsafe names`() {
        val out =
            "proot warning: can't sanitize binding \"/proc/self/fd/1\"\n" +
                "oc-dir\tg\tbefore-ok\n" +
                "oc-folders-ok\n" +
                "oc-dir\tg\tapp\n" +
                "oc-dir\t-\tnotes\n" +
                "oc-dir\tx\tbad-flag\n" +
                "oc-dir\t-\t.hidden\n" +
                "oc-dir\t-\t..\n" +
                "oc-dir\t-\ta/b\n" +
                "oc-dir\t-\tbell\u0007\n" +
                "oc-dir\t-\ttoo\tmany\n" +
                "garbage line\n"
        val entries = TermuxFolders.parseListing("/root/projects", out).valueOrNull()!!
        assertEquals(listOf("/root/projects/app" to true, "/root/projects/notes" to false), entries.map { it.path to it.isGit })
        assertEquals(
            listOf("opt"),
            TermuxFolders.parseListing("/", "oc-folders-ok\noc-dir\t-\tproc\noc-dir\t-\topt\n").valueOrNull()!!.map { it.name },
        )
    }

    @Test
    fun `B-LINUX-231 no answer, a timeout inside Termux, and a missing projects folder`() {
        assertEquals(FolderListProblem.Failed, TermuxFolders.parseListing("/root/x", "proot: something\n").problemOrNull())
        assertEquals(FolderListProblem.TimedOut, TermuxFolders.parseListing("/root/x", "oc-folders-timeout\n").problemOrNull())
        assertEquals(emptyList(), TermuxFolders.parseListing("/root/projects", "oc-folders-missing\n").valueOrNull())
    }

    @Test
    fun `B-LINUX-231 the outer script embeds only base64, never the path`() {
        val script = TermuxFolders.listScript("/root/projects/a b'c")
        assertFalse(script.contains("a b'c"))
        assertTrue(script.contains(Base64.encode("/root/projects/a b'c".encodeToByteArray())))
        assertFalse(TermuxFolders.LIST_INNER_SCRIPT.contains("'"))
        assertFalse(TermuxFolders.CREATE_INNER_SCRIPT.contains("'"))
        assertTrue(script.contains("timeout -k 2s 15s proot-distro login opencode-ubuntu"))
    }

    @Test
    fun `B-LINUX-231 a path with a newline or dot-dot is refused before anything runs`() = runTest {
        var ran = 0
        val termux =
            TermuxFolders({ _, _ ->
                ran++
                TermuxCommandResult("oc-folders-ok\n").ok()
            })
        for (path in listOf("/root/new\nline", "/root/../etc", "relative")) {
            assertEquals(FolderListProblem.Invalid, termux.list(path).problemOrNull(), path)
            assertEquals(FolderListProblem.Invalid, termux.create(path).problemOrNull(), path)
        }
        // The home folder and the root are never made into a workspace either.
        assertEquals(FolderListProblem.Invalid, termux.create("/root").problemOrNull())
        assertEquals(0, ran)
    }

    @Test
    fun `B-LINUX-231 a bridge timeout or failure is a folder problem, never a crash`() = runTest {
        val timedOut = TermuxFolders({ _, _ -> Failure.TimedOut().failed() })
        assertEquals(FolderListProblem.TimedOut, timedOut.list("/root/projects").problemOrNull())
        val broken = TermuxFolders({ _, _ -> Failure.Termux().failed() })
        assertEquals(FolderListProblem.Failed, broken.list("/root/projects").problemOrNull())
        assertEquals(FolderListProblem.Failed, broken.create("/root/projects/new").problemOrNull())
    }

    @Test
    fun `B-LINUX-231 project folders are created only as one safe name under projects`() = runTest {
        val script = termuxCreateProjectFolderScript("my-app")
        assertTrue(script.contains("proot-distro login opencode-ubuntu -- sh -c"))
        assertTrue(script.contains("dir=\"/root/projects/\$name\""))
        assertTrue(script.contains("-- 'my-app'"))
        assertTrue(script.contains("*/*|.*) echo \"invalid-folder-name\""))
        assertEquals("invalid_folder_name", projectFolderRefusal("../etc")!!.code)
        var ran = 0
        val runner =
            TermuxScriptRunner { _, _ ->
                ran++
                TermuxCommandResult("noise\n/root/projects/my-app\n").ok()
            }
        assertIs<Outcome.Failed>(createProjectFolder(runner, "../etc"))
        assertEquals(0, ran)
        assertEquals("/root/projects/my-app", (createProjectFolder(runner, " my-app ") as Outcome.Ok).value)
        assertIs<Outcome.Failed>(createProjectFolder(runner, "other"), "a different echoed path is not a made folder")
    }
}
