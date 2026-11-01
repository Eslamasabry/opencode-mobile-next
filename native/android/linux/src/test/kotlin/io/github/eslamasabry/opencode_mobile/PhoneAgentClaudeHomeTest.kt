package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class PhoneAgentClaudeHomeTest {
    @get:Rule val folder = TemporaryFolder()

    private val profile = "phone-1"
    private val signIn = """{"claudeAiOauth":{"accessToken":"terminal"}}"""

    private fun home(): File = folder.newFolder("home", "oc")
    private fun profileClaude(home: File) = File(home, ".oc-profiles/$profile/claude")
    private fun link(home: File) = File(home, ".claude").toPath()

    /** The owner's phone: signed in from the Terminal as `oc`, never in the app. */
    @Test fun terminalSignInIsSeenByTheAppAndKept() {
        val home = home()
        File(home, ".claude").mkdirs()
        File(home, ".claude/.credentials.json").writeText(signIn)
        File(home, ".claude/settings.json").writeText("{\"theme\":\"dark\"}")
        File(home, ".claude/projects/-root-projects").mkdirs()
        File(home, ".claude/projects/-root-projects/chat.jsonl").writeText("{}")

        val steps = PhoneAgentClaudeHome.prepare(home, profile)

        assertEquals(signIn, File(profileClaude(home), ".credentials.json").readText())
        assertEquals("{\"theme\":\"dark\"}", File(profileClaude(home), "settings.json").readText())
        assertTrue(File(profileClaude(home), "projects/-root-projects/chat.jsonl").isFile)
        assertTrue(Files.isSymbolicLink(link(home)))
        assertEquals(".oc-profiles/$profile/claude", Files.readSymbolicLink(link(home)).toString())
        // Copied, never moved away or deleted: the old folder stays whole.
        assertEquals(signIn, File(home, ".claude.before-app/.credentials.json").readText())
        assertTrue(File(home, ".claude.before-app/projects/-root-projects/chat.jsonl").isFile)
        assertTrue(steps.containsAll(listOf(PhoneAgentClaudeHome.Step.CREDENTIALS_ADOPTED,
            PhoneAgentClaudeHome.Step.FILES_ADOPTED, PhoneAgentClaudeHome.Step.LINKED)))
        // Only the owner may read the adopted sign-in.
        val mode = Files.getPosixFilePermissions(File(profileClaude(home), ".credentials.json").toPath())
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(mode))
    }

    /** After the link, a sign-in made in either place is one file. */
    @Test fun aLaterSignInAnywhereIsOneFile() {
        val home = home()
        assertTrue(PhoneAgentClaudeHome.prepare(home, profile).contains(PhoneAgentClaudeHome.Step.LINKED))
        File(home, ".claude/.credentials.json").writeText(signIn)
        assertEquals(signIn, File(profileClaude(home), ".credentials.json").readText())
        File(profileClaude(home), ".credentials.json").writeText("app")
        assertEquals("app", File(home, ".claude/.credentials.json").readText())
    }

    @Test fun theProfilesOwnSignInIsNeverReplaced() {
        val home = home()
        profileClaude(home).mkdirs()
        File(profileClaude(home), ".credentials.json").writeText("app")
        File(home, ".claude").mkdirs()
        File(home, ".claude/.credentials.json").writeText(signIn)

        val steps = PhoneAgentClaudeHome.prepare(home, profile)

        assertFalse(steps.contains(PhoneAgentClaudeHome.Step.CREDENTIALS_ADOPTED))
        assertEquals("app", File(profileClaude(home), ".credentials.json").readText())
        assertEquals(signIn, File(home, ".claude.before-app/.credentials.json").readText())
    }

    /** The Terminal launcher of an earlier build used /home/oc/claude; a root shell /root/.claude. */
    @Test fun earlierTerminalFoldersAreAdoptedNewestFirst() {
        val home = home()
        val root = folder.newFolder("root")
        File(home, "claude").mkdirs()
        File(home, "claude/.credentials.json").writeText("older")
        File(home, "claude/.credentials.json").setLastModified(1_000_000L)
        File(root, ".claude").mkdirs()
        File(root, ".claude/.credentials.json").writeText("newer")
        File(root, ".claude/.credentials.json").setLastModified(2_000_000L)

        PhoneAgentClaudeHome.prepare(home, profile, root)

        assertEquals("newer", File(profileClaude(home), ".credentials.json").readText())
        assertEquals("older", File(home, "claude/.credentials.json").readText())
        assertEquals("newer", File(root, ".claude/.credentials.json").readText())
    }

    @Test fun secondRunChangesNothing() {
        val home = home()
        File(home, ".claude").mkdirs()
        File(home, ".claude/.credentials.json").writeText(signIn)
        PhoneAgentClaudeHome.prepare(home, profile)
        assertEquals(emptySet<PhoneAgentClaudeHome.Step>(), PhoneAgentClaudeHome.prepare(home, profile))
        assertFalse(File(home, ".claude.before-app-2").exists())
    }

    @Test fun anotherPhoneProfilesLinkMovesButAPersonsOwnLinkStays() {
        val home = home()
        Files.createSymbolicLink(link(home), Paths.get(".oc-profiles/other/claude"))
        assertTrue(PhoneAgentClaudeHome.prepare(home, profile).contains(PhoneAgentClaudeHome.Step.RELINKED))
        assertEquals(".oc-profiles/$profile/claude", Files.readSymbolicLink(link(home)).toString())

        Files.delete(link(home))
        Files.createSymbolicLink(link(home), Paths.get("/mnt/my-claude"))
        assertTrue(PhoneAgentClaudeHome.prepare(home, profile)
            .contains(PhoneAgentClaudeHome.Step.FOREIGN_LEFT_ALONE))
        assertEquals("/mnt/my-claude", Files.readSymbolicLink(link(home)).toString())
    }

    @Test fun deletingTheProfileRemovesOnlyItsOwnLink() {
        val home = home()
        PhoneAgentClaudeHome.prepare(home, profile)
        PhoneAgentClaudeHome.forget(home, "someone-else")
        assertTrue(Files.isSymbolicLink(link(home)))
        PhoneAgentClaudeHome.forget(home, profile)
        assertFalse(Files.exists(link(home), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(profileClaude(home).isDirectory)
    }

    @Test fun anUnsafeProfileIdIsRefused() {
        val home = home()
        for (bad in listOf("../x", "", "a/b", "a b")) {
            assertTrue(runCatching { PhoneAgentClaudeHome.prepare(home, bad) }.isFailure)
        }
        assertFalse(File(home, ".claude").exists())
    }
}
