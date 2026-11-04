package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The guest names the Android groups once, idempotently (test/native/android_group_names_harness.kt). */
class AndroidGroupNamesTest {
    private val status = "Name:\tapp\nGroups:\t3003 9997 20209 50209 \nCapInh:\t0\n"

    @Test fun `B-LINUX-009 the status Groups line gives the group ids`() {
        assertEquals(listOf(3003, 9997, 20209, 50209), AndroidGroupNames.parseGroups(status))
        assertTrue(AndroidGroupNames.parseGroups("Name:\tx\n").isEmpty())
    }

    @Test fun `B-LINUX-009 missing groups are added once and a second merge changes nothing`() {
        val gids = AndroidGroupNames.parseGroups(status)
        val merged = AndroidGroupNames.merged("root:x:0:\ndaemon:x:1:", gids)
        assertEquals(
            "root:x:0:\ndaemon:x:1:\naid_3003:x:3003:\naid_9997:x:9997:\naid_20209:x:20209:\naid_50209:x:50209:\n",
            merged,
        )
        assertNull("not idempotent", AndroidGroupNames.merged(merged!!, gids))
        // A group already named by Android's own name, and a repeated id, are not duplicated.
        assertTrue(AndroidGroupNames.merged("root:x:0:\naid_inet:x:3003:\n", listOf(3003, 3003, 9997))!!.endsWith("aid_9997:x:9997:\n"))
        assertEquals("root:x:0:\naid_inet:x:3003:\naid_9997:x:9997:\n", AndroidGroupNames.merged("root:x:0:\naid_inet:x:3003:\n", listOf(3003, 3003, 9997)))
    }

    @Test fun `B-LINUX-009 setup run twice applies the groups without duplicates`() {
        val rootfs = Files.createTempDirectory("rootfs").toFile()
        try {
            File(rootfs, "etc").mkdirs()
            File(rootfs, "etc/group").writeText("root:x:0:\n")
            val statusFile = File(rootfs, "status").apply { writeText(status) }
            AndroidGroupNames.apply(rootfs, statusFile)
            AndroidGroupNames.apply(rootfs, statusFile)
            val lines = File(rootfs, "etc/group").readLines()
            assertEquals(lines.toString(), 5, lines.size)
            assertEquals(1, lines.count { it.startsWith("aid_3003:") })
            assertFalse(File(rootfs, "etc/group.oc-new").exists())
            // No guest there yet: nothing happens and nothing throws.
            AndroidGroupNames.apply(File(rootfs, "missing"), statusFile)
            assertFalse(File(rootfs, "missing").exists())
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
