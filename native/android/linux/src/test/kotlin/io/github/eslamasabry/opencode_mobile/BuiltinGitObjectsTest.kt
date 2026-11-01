package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 * Git repositories in the built-in Ubuntu and proot's link2symlink: new
 * objects are plain files, and moving the projects folder keeps old links.
 */
class BuiltinGitObjectsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun gitIsToldToRenameObjectsIntoPlace() {
        val rootfs = folder.newFolder("ubuntu")
        File(rootfs, "etc").mkdirs()

        assertTrue(BuiltinGitConfig.preferRenames(rootfs))

        val text = File(rootfs, "etc/gitconfig").readText()
        assertTrue(text.contains("[core]\n\tcreateObject = rename\n"))
        // Once is enough: a second launch leaves the file as it is.
        assertFalse(BuiltinGitConfig.preferRenames(rootfs))
        assertEquals(text, File(rootfs, "etc/gitconfig").readText())
    }

    @Test fun aPersonsOwnSystemConfigIsKeptAndTheirChoiceWins() {
        val rootfs = folder.newFolder("ubuntu")
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/gitconfig").writeText("[user]\n\tname = Someone\n")

        assertTrue(BuiltinGitConfig.preferRenames(rootfs))
        assertTrue(File(rootfs, "etc/gitconfig").readText().startsWith("[user]\n\tname = Someone\n"))

        File(rootfs, "etc/gitconfig").writeText("[core]\n  createobject = link\n")
        assertFalse(BuiltinGitConfig.preferRenames(rootfs))
        assertEquals("[core]\n  createobject = link\n", File(rootfs, "etc/gitconfig").readText())
    }

    /**
     * An older install kept projects in Ubuntu's /root/projects; the first
     * launch after the update moves them to filesDir/projects. Every Git
     * object link (absolute, made by link2symlink) must still resolve.
     */
    @Test fun movingTheProjectsFolderKeepsGitObjectLinksWorking() {
        val filesDir = folder.newFolder("files")
        val legacy = File(filesDir, "linux/ubuntu/root/projects")
        val objects = File(legacy, "app/.git/objects/ab").apply { mkdirs() }
        val stored = File(objects, ".l2s.tmp_obj_x0001.0001").apply { writeText("object bytes") }
        val middle = File(objects, ".l2s.tmp_obj_x0001")
        Files.createSymbolicLink(middle.toPath(), Paths.get(stored.absolutePath))
        val name = File(objects, "cdef0123")
        Files.createSymbolicLink(name.toPath(), Paths.get(middle.absolutePath))
        // A link that never pointed into the old folder is left alone.
        val elsewhere = File(objects, "elsewhere")
        Files.createSymbolicLink(elsewhere.toPath(), Paths.get("/somewhere/else"))
        val storage = BuiltinProjectStorage(filesDir, File(folder.root, "cache/ubuntu.tar.gz").apply {
            parentFile.mkdirs()
        }) { file -> lstat(file) }

        storage.prepare()

        val moved = File(filesDir, "projects/app/.git/objects/ab/cdef0123")
        assertTrue(Files.isSymbolicLink(moved.toPath()))
        assertEquals("object bytes", moved.readText())
        assertEquals(
            "/somewhere/else",
            Files.readSymbolicLink(File(filesDir, "projects/app/.git/objects/ab/elsewhere").toPath()).toString(),
        )
    }

    private fun lstat(file: File): BuiltinProjectStorage.Entry? {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val kind = when {
            attrs.isSymbolicLink -> BuiltinProjectStorage.Kind.LINK
            attrs.isDirectory -> BuiltinProjectStorage.Kind.DIRECTORY
            attrs.isRegularFile -> BuiltinProjectStorage.Kind.FILE
            else -> BuiltinProjectStorage.Kind.OTHER
        }
        return BuiltinProjectStorage.Entry(kind, attrs.size())
    }
}
