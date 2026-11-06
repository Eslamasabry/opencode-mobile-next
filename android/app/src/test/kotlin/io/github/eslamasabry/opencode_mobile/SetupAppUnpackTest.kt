package io.github.eslamasabry.opencode_mobile

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SetupAppUnpackTest {
    private lateinit var rootfs: File

    @Before fun setUp() { rootfs = Files.createTempDirectory("oc-unpack").toFile() }
    @After fun tearDown() { rootfs.deleteRecursively() }

    private class Builder {
        val out = ByteArrayOutputStream()

        fun entry(name: String, type: Char, mode: Int, body: ByteArray = ByteArray(0), link: String = ""): Builder {
            val header = ByteArray(512)
            fun put(text: String, offset: Int) = text.toByteArray().copyInto(header, offset)
            put(name, 0)
            put("%07o".format(mode), 100)
            put("%011o".format(body.size), 124)
            header[156] = type.code.toByte()
            put(link, 157)
            put("ustar", 257)
            " ".toByteArray().copyInto(header, 148)
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            val sum = header.sumOf { it.toInt() and 0xFF }
            put("%06o".format(sum), 148)
            header[154] = 0
            header[155] = ' '.code.toByte()
            out.write(header)
            out.write(body)
            out.write(ByteArray((512 - body.size % 512) % 512))
            return this
        }

        fun file(name: String, text: String, mode: Int = 0x1A4) = entry(name, '0', mode, text.toByteArray())
        fun dir(name: String) = entry(name, '5', 0x1ED)
        fun symlink(name: String, target: String) = entry(name, '2', 0x1FF, link = target)

        fun gz(): ByteArray {
            out.write(ByteArray(1024))
            val packed = ByteArrayOutputStream()
            GZIPOutputStream(packed).use { it.write(out.toByteArray()) }
            return packed.toByteArray()
        }
    }

    private fun archive(build: Builder.() -> Unit): File =
        File(rootfs, "var/cache/a.tar.gz").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(Builder().apply(build).gz())
        }

    private fun unpack(file: File, strip: Int = 0, member: String? = null) =
        SetupAppUnpack(rootfs).unpack(file, File(rootfs, "opt/node.new"), strip, member) { false }

    @Test fun unpacksFilesDirectoriesSymlinksAndModesWithStrip() {
        val file = archive {
            dir("node-v1/")
            dir("node-v1/bin/")
            file("node-v1/bin/node", "#!bin", mode = 0x1ED)
            file("node-v1/README", "hi", mode = 0x1A4)
            symlink("node-v1/bin/npm", "../lib/npm-cli.js")
        }
        unpack(file, strip = 1)
        val base = File(rootfs, "opt/node.new")
        assertEquals("#!bin", File(base, "bin/node").readText())
        assertTrue(File(base, "bin/node").canExecute())
        assertFalse(File(base, "README").canExecute())
        assertTrue(Files.isSymbolicLink(File(base, "bin/npm").toPath()))
        assertEquals("../lib/npm-cli.js", Files.readSymbolicLink(File(base, "bin/npm").toPath()).toString())
        assertFalse(File(base, "node-v1").exists())
    }

    @Test fun extractsOnlyTheNamedMember() {
        val file = archive { file("package/bin/opencode", "prog", 0x1ED); file("package/other", "x") }
        unpack(file, member = "package/bin/opencode")
        val base = File(rootfs, "opt/node.new")
        assertEquals("prog", File(base, "package/bin/opencode").readText())
        assertFalse(File(base, "package/other").exists())
    }

    @Test fun missingMemberIsAFailure() {
        val file = archive { file("a", "x") }
        try { unpack(file, member = "nope"); fail() } catch (_: SetupAppUnpack.UnpackFailure) { }
    }

    @Test fun refusesParentAndAbsolutePaths() {
        for (bad in listOf("../evil", "a/../../evil", "/etc/evil")) {
            val file = archive { file(bad, "x") }
            try { unpack(file); fail("accepted $bad") } catch (_: SetupAppUnpack.UnpackFailure) { }
        }
        assertFalse(File(rootfs, "opt/evil").exists())
        assertFalse(File(rootfs, "evil").exists())
    }

    @Test fun refusesWritingThroughALinkThatLeavesTheFolder() {
        val outside = Files.createTempDirectory("oc-outside").toFile()
        try {
            val file = archive { symlink("link", outside.path); file("link/pwned", "x") }
            try { unpack(file); fail() } catch (_: SetupAppUnpack.UnpackFailure) { }
            assertFalse(File(outside, "pwned").exists())
        } finally { outside.deleteRecursively() }
    }

    @Test fun answersThroughMarkerFilesAndStaysInsideTheRootfs() {
        val file = archive { dir("d/"); file("d/f", "ok") }
        val request = SetupProtocol.Event.Unpack("/var/cache/a.tar.gz", "/opt/node.new", 1, null)
        SetupAppUnpack(rootfs).run(request) { false }
        assertEquals("ok", File(file.path + ".oc-unpack-done").readText())
        assertTrue(File(file.path + ".oc-unpack-ack").exists())
        assertEquals("ok", File(rootfs, "opt/node.new/f").readText())
        // A request outside the rootfs gets no answer and no file.
        SetupAppUnpack(rootfs).run(SetupProtocol.Event.Unpack("/var/cache/a.tar.gz", "/../x", 0, null)) { false }
    }

    @Test fun damagedArchiveFailsPlainly() {
        val file = File(rootfs, "var/cache/bad.tar.gz").apply { parentFile!!.mkdirs(); writeText("not gzip") }
        SetupAppUnpack(rootfs).run(SetupProtocol.Event.Unpack("/var/cache/bad.tar.gz", "/opt/x", 0, null)) { false }
        assertTrue(File(file.path + ".oc-unpack-done").readText().startsWith("fail "))
    }

    @Test fun protocolParsesUnpackLines() {
        val parsed = SetupProtocol.parse("::oc unpack /a.tar.gz /d 1 -")
        assertEquals(SetupProtocol.Event.Unpack("/a.tar.gz", "/d", 1, null), parsed)
        assertEquals(
            SetupProtocol.Event.Unpack("/a", "/d", 0, "package/bin/x"),
            SetupProtocol.parse("::oc unpack /a /d 0 package/bin/x"),
        )
        assertEquals(null, SetupProtocol.parse("::oc unpack /a /d x -"))
        assertEquals(null, SetupProtocol.parse("::oc unpack /a /d"))
    }

    @Test fun segfaultRetriesOnceInCompatibilityMode() {
        assertTrue(SetupStepRetry.compatibility(139, alreadyCompatible = false, cancelled = false))
        assertFalse(SetupStepRetry.compatibility(139, alreadyCompatible = true, cancelled = false))
        assertFalse(SetupStepRetry.compatibility(139, alreadyCompatible = false, cancelled = true))
        assertFalse(SetupStepRetry.compatibility(1, alreadyCompatible = false, cancelled = false))
        assertFalse(SetupStepRetry.compatibility(null, alreadyCompatible = false, cancelled = false))
    }

    @Test fun aReportedSegfaultInAFailedStepCountsAsOne() {
        assertTrue(SetupStepRetry.reportsSegfault("sh: 3: Segmentation fault"))
        assertEquals(139, SetupStepRetry.crashExit(1, true))
        assertEquals(0, SetupStepRetry.crashExit(0, true))
        assertEquals(1, SetupStepRetry.crashExit(1, false))
    }
}
