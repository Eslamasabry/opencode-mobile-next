package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.DeflaterOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The built-in Linux half of the Git link repair, kept when its Termux half was removed (2026-11-07). */
class BuiltinGitObjectRepairTest {
    private val projects = createTempDirectory("projects").toFile()
    private val objects = File(projects, "app/.git/objects").apply { mkdirs() }

    /** A loose object for [text]: its name is the SHA-1 of the header and text, its bytes the deflated form. */
    private fun looseObject(text: String): Pair<String, ByteArray> {
        val raw = "blob ${text.length}\u0000$text".toByteArray()
        val id = MessageDigest.getInstance("SHA-1").digest(raw).joinToString("") { "%02x".format(it) }
        val deflated = java.io.ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(raw) } }
        return id to deflated.toByteArray()
    }

    private fun repair() = BuiltinGitObjectRepair(projects, { true }) { true }

    @Test
    fun `B-SEC-091 a proot object link is restored from the verified copy in the project`() = runTest {
        val (id, bytes) = looseObject("hello")
        val dir = File(objects, id.take(2)).apply { mkdirs() }
        File(dir, ".l2s.${id.drop(2)}0001").writeBytes(bytes)
        val link = File(dir, id.drop(2))
        Files.createSymbolicLink(link.toPath(), File(".l2s.${id.drop(2)}0001").toPath())

        val report = repair().repair("/root/projects/app")

        assertEquals(GitLinkRepairResult.Repaired, report.result)
        assertEquals(1, report.restored)
        assertFalse(Files.isSymbolicLink(link.toPath()))
        assertTrue(bytes.contentEquals(link.readBytes()))
    }

    @Test
    fun `B-SEC-091 a link into Termux's old store is named missing and left untouched`() = runTest {
        val (id, _) = looseObject("gone")
        val dir = File(objects, id.take(2)).apply { mkdirs() }
        val link = File(dir, id.drop(2))
        val outside =
            "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/.l2s/.l2s.${id.drop(2)}0001"
        Files.createSymbolicLink(link.toPath(), File(outside).toPath())

        val report = repair().repair("/root/projects/app")

        assertEquals(GitLinkRepairResult.MissingObjects, report.result)
        assertEquals(listOf("objects/${id.take(2)}/${id.drop(2)}"), report.missing)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals(outside, Files.readSymbolicLink(link.toPath()).toString())
    }

    @Test
    fun `B-SEC-091 a path outside the projects folder is not a repository`() = runTest {
        assertEquals(GitLinkRepairResult.NotRepository, repair().repair("/root/projects/../etc").result)
        assertEquals(GitLinkRepairResult.NotRepository, repair().repair("/home/app").result)
    }
}
