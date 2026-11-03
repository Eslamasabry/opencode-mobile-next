package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Issue #111 FIX-4: new projects are made by the app, not by `git init` in
 * the sandbox (whose seccomp filter denies the chmod git needs). The result
 * must be what the Ubuntu's git 2.43 made before.
 */
class BuiltinNewProjectTest {
    @get:Rule val folder = TemporaryFolder()

    private val defaults = BuiltinNewProject.Defaults(
        name = "OpenCode Mobile", email = "aiteam@opencode-mobile.local", branch = "master")
    private val time = 1793677896L

    private fun projects() = folder.newFolder("files", "projects")
    private fun staging() = File(folder.root, "files/oc-new-project-staging")

    private fun git(dir: File, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args).directory(dir).redirectErrorStream(true).apply {
            environment().apply {
                clear()
                put("PATH", System.getenv("PATH") ?: "/usr/bin:/bin")
                put("HOME", folder.root.absolutePath)
                put("GIT_CONFIG_NOSYSTEM", "1")
                put("GIT_AUTHOR_DATE", "@$time +0000")
                put("GIT_COMMITTER_DATE", "@$time +0000")
                put("LANG", "C")
            }
        }.start()
        val out = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS))
        assertEquals("git ${args.joinToString(" ")}: $out", 0, process.exitValue())
        return out
    }

    private fun gitAvailable(): Boolean = try {
        ProcessBuilder("git", "--version").start().waitFor() == 0
    } catch (_: Exception) { false }

    private fun listing(git: File): List<String> = git.walkTopDown()
        .filter { it != git }
        .map { it.relativeTo(git).path + if (it.isDirectory) "/" else "" }
        .filterNot { it.startsWith("hooks/") && it != "hooks/" }
        .sorted().toList()

    @Test fun makesTheSameRepositoryAsGitInitAndAnEmptyFirstCommit() {
        assumeTrue("git is not installed", gitAvailable())
        val projects = projects()

        val outcome = BuiltinNewProject.create(projects, "my-app", staging(), defaults, time)
        assertEquals(BuiltinNewProject.Outcome.CREATED, outcome)

        // What the old script did, with the same identity and clock.
        val reference = folder.newFolder("reference")
        git(reference, "init", "-q", "my-app")
        val referenceRepo = File(reference, "my-app")
        git(referenceRepo, "-c", "user.name=${defaults.name}", "-c", "user.email=${defaults.email}",
            "commit", "-q", "--allow-empty", "-m", "First commit")

        val ours = File(projects, "my-app/.git")
        val theirs = File(referenceRepo, ".git")
        assertEquals(listing(theirs), listing(ours))
        for (file in listOf("config", "HEAD", "description", "info/exclude", "refs/heads/master",
            "logs/HEAD", "logs/refs/heads/master", "COMMIT_EDITMSG", "index")) {
            assertArrayEquals(file, File(theirs, file).readBytes(), File(ours, file).readBytes())
        }
        val dir = File(projects, "my-app")
        assertEquals(git(referenceRepo, "rev-parse", "HEAD"), git(dir, "rev-parse", "HEAD"))
        git(dir, "fsck", "--strict", "--no-dangling")
        assertEquals("", git(dir, "status", "--porcelain"))
        assertEquals("master\n", git(dir, "branch", "--show-current"))
        assertEquals("OpenCode Mobile <aiteam@opencode-mobile.local> First commit\n",
            git(dir, "log", "--format=%an <%ae> %s"))
        // A team branches from it and commits on top, as the engine does.
        git(dir, "-c", "user.name=t", "-c", "user.email=t@l", "commit", "-q", "--allow-empty", "-m", "next")
        assertTrue(staging().list().isNullOrEmpty())
    }

    @Test fun theDefaultBranchAndIdentityComeFromTheUbuntusGitConfig() {
        val rootfs = folder.newFolder("ubuntu")
        File(rootfs, "etc").mkdirs()
        File(rootfs, "root").mkdirs()
        File(rootfs, "etc/gitconfig").writeText(
            "[core]\n\tcreateObject = rename\n[user]\n\tname = System\n[init]\n\tdefaultBranch = trunk\n")
        File(rootfs, "root/.gitconfig").writeText(
            "[user]\n\tname = \"OpenCode Mobile\"\n\temail = aiteam@opencode-mobile.local\n" +
                "[init]\n\tdefaultBranch = main\n")
        val read = BuiltinNewProject.readDefaults(rootfs)
        assertEquals(BuiltinNewProject.Defaults("OpenCode Mobile", "aiteam@opencode-mobile.local", "main"), read)

        val projects = projects()
        BuiltinNewProject.create(projects, "p", staging(), read, time)
        assertEquals("ref: refs/heads/main\n", File(projects, "p/.git/HEAD").readText())
        assertTrue(File(projects, "p/.git/refs/heads/main").isFile)
    }

    @Test fun withoutAnyGitConfigTheScriptsFallbacksApply() {
        val rootfs = folder.newFolder("ubuntu")
        assertEquals(BuiltinNewProject.Defaults("AI Team", "aiteam@localhost", "master"),
            BuiltinNewProject.readDefaults(rootfs))
    }

    @Test fun unsafeConfigValuesAndLinkedConfigFilesAreIgnored() {
        val rootfs = folder.newFolder("ubuntu")
        File(rootfs, "root").mkdirs()
        File(rootfs, "root/.gitconfig").writeText(
            "[user]\n\tname = a <b>\n\temail = x\u0001y\n[init]\n\tdefaultBranch = ../../escape\n")
        assertEquals(BuiltinNewProject.Defaults(), BuiltinNewProject.readDefaults(rootfs))

        val secret = folder.newFile("secret").apply { writeText("[user]\n\tname = Leaked\n") }
        val linked = folder.newFolder("ubuntu2")
        File(linked, "root").mkdirs()
        Files.createSymbolicLink(File(linked, "root/.gitconfig").toPath(), secret.toPath())
        assertEquals("AI Team", BuiltinNewProject.readDefaults(linked).name)
        val linkedRoot = folder.newFolder("ubuntu3")
        Files.createSymbolicLink(File(linkedRoot, "root").toPath(), File(folder.root, "ubuntu2/root").toPath())
        assertEquals("AI Team", BuiltinNewProject.readDefaults(linkedRoot).name)
    }

    @Test fun anExistingFolderIsLeftAlone() {
        val projects = projects()
        val existing = File(projects, "old").apply { mkdirs() }
        File(existing, "keep.txt").writeText("mine")

        assertEquals(BuiltinNewProject.Outcome.EXISTS,
            BuiltinNewProject.create(projects, "old", staging(), defaults, time))
        assertEquals(listOf("keep.txt"), existing.list()!!.toList())
    }

    @Test fun nothingIsWrittenThroughALinkInTheProjectsFolder() {
        val projects = projects()
        val outside = folder.newFolder("app-private")
        Files.createSymbolicLink(File(projects, "to-dir").toPath(), outside.toPath())
        assertEquals(BuiltinNewProject.Outcome.EXISTS,
            BuiltinNewProject.create(projects, "to-dir", staging(), defaults, time))
        assertEquals(0, outside.list()!!.size)

        Files.createSymbolicLink(File(projects, "dangling").toPath(), File(outside, "nothing").toPath())
        try {
            BuiltinNewProject.create(projects, "dangling", staging(), defaults, time)
            fail("A link that is not a folder must not be replaced")
        } catch (_: IllegalStateException) {
        }
        assertFalse(File(outside, "nothing").exists())
        assertTrue(Files.isSymbolicLink(File(projects, "dangling").toPath()))
    }

    @Test fun anUnfinishedBuildFromAnEarlierRunIsCleared() {
        val projects = projects()
        val leftover = File(staging(), "p123/.git").apply { mkdirs() }
        BuiltinNewProject.create(projects, "fresh", staging(), defaults, time)
        assertFalse(leftover.exists())
        assertTrue(staging().list().isNullOrEmpty())
        assertTrue(File(projects, "fresh/.git/HEAD").isFile)
    }

    @Test fun onlyDirectChildrenOfTheProjectsFolderAreMade() {
        assertEquals("a b", BuiltinNewProject.managedName("/root/projects/a b"))
        for (path in listOf("/root/projects/", "/root/projects/.", "/root/projects/..",
            "/root/projects/a/b", "/root/projectsx", "/sdcard/a", "/root/projects/a\nb")) {
            assertNull(path, BuiltinNewProject.managedName(path))
        }
    }

    @Test fun theEmptyIndexMatchesGits() {
        // `git commit --allow-empty` on a new repository, git 2.43 (65 bytes).
        val hex = BuiltinNewProject.emptyIndex().joinToString("") { "%02x".format(it) }
        assertEquals("4449524300000002000000005452454500000019003020300a4b825dc642cb6e" +
            "b9a060e54bf8d69288fbee490472d53f787d86a932a25a8537cee236d81846a8f1", hex)
    }
}
