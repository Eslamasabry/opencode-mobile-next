package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeCompatProotTest {
    @Test
    fun `only a state file that says proot-compat asks for compatibility mode`() {
        assertTrue(NodeCompatProot.required("armcap\nproot-compat\n"))
        assertTrue(NodeCompatProot.required("both\r\n proot-compat \n"))
        assertFalse(NodeCompatProot.required("armcap\n"))
        assertFalse(NodeCompatProot.required("proot-compat\n")) // a mode name comes first
        assertFalse(NodeCompatProot.required(""))
        assertFalse(NodeCompatProot.required(null as String?))
    }

    @Test
    fun `reads the file inside Ubuntu, and nothing when it is missing or odd`() {
        val rootfs = Files.createTempDirectory("oc-rootfs").toFile()
        try {
            assertFalse(NodeCompatProot.required(rootfs))
            val state = File(rootfs, NodeCompatProot.STATE_PATH).apply { parentFile!!.mkdirs() }
            state.writeText("threads\nproot-compat\n")
            assertTrue(NodeCompatProot.required(rootfs))
            state.writeText("threads\n")
            assertFalse(NodeCompatProot.required(rootfs))
            state.writeText("threads\nproot-compat\n" + "x".repeat(1000))
            assertFalse(NodeCompatProot.required(rootfs))
            state.delete()
            state.mkdirs()
            assertFalse(NodeCompatProot.required(rootfs))
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
