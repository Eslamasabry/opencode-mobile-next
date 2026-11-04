package io.github.eslamasabry.oc.engine.runtime.components

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Port of test/setup_component_update_test.dart: the ported [ComponentUpdates.PRELUDE] over real files in /bin/sh. */
class ComponentUpdateScriptTest {
    private lateinit var temp: File
    private lateinit var target: String

    @BeforeTest
    fun setUp() {
        temp = Files.createTempDirectory("component-journal-").toFile()
        target = "${temp.path}/active"
    }

    @AfterTest
    fun tearDown() {
        temp.deleteRecursively()
    }

    private fun run(script: String) = runShell("/bin/sh", "set -eu\n${ComponentUpdates.PRELUDE}\n$script")

    private fun file(path: String) = File(path)

    // ---- B-LINUX-190

    @Test
    fun `B-LINUX-190 a fresh process restores the uncommitted activation and replay is safe`() {
        file(target).writeText("good")
        file("$target.new").writeText("bad")
        val activated = run("oc_update_activate \"$target\" \"$target.new\"")
        assertEquals(0, activated.exitCode, activated.stderr)
        assertEquals("bad", file(target).readText())
        repeat(2) {
            val recovered = run("oc_update_recover \"$target\"")
            assertEquals(0, recovered.exitCode, recovered.stderr)
            assertEquals("good", file(target).readText())
            assertFalse(file("$target.oc-pending").exists())
        }
    }

    @Test
    fun `B-LINUX-190 commit retains only the immediately preceding good generation`() {
        file(target).writeText("first")
        for (version in listOf("second", "third")) {
            file("$target.new").writeText(version)
            val committed =
                run(
                    """
                    oc_update_activate "$target" "$target.new"
                    oc_update_commit "$target"
                    """.trimIndent(),
                )
            assertEquals(0, committed.exitCode, committed.stderr)
        }
        assertEquals("third", file(target).readText())
        assertEquals("second", file("$target.oc-good").readText())
        assertFalse(file("$target.oc-pending").exists())
    }

    @Test
    fun `B-LINUX-190 first-install recovery removes the incomplete activation`() {
        file("$target.new").writeText("bad")
        assertEquals(0, run("oc_update_activate \"$target\" \"$target.new\"").exitCode)
        assertEquals("new\n", file("$target.oc-pending").readText(), "the journal holds only the activation state")
        assertEquals(0, run("oc_update_recover \"$target\"").exitCode)
        assertFalse(file(target).exists())
    }

    // ---- B-LINUX-191

    @Test
    fun `B-LINUX-191 recovery restores launch links without following their targets`() {
        val good = File(temp, "good").apply { writeText("good") }
        val other = File(temp, "other").apply { writeText("other") }
        Files.createSymbolicLink(file(target).toPath(), good.toPath())
        Files.createSymbolicLink(file("$target.new").toPath(), other.toPath())
        assertEquals(0, run("oc_update_activate \"$target\" \"$target.new\"").exitCode)
        assertEquals(0, run("oc_update_recover \"$target\"").exitCode)
        assertTrue(Files.isSymbolicLink(file(target).toPath()))
        assertEquals(good.path, Files.readSymbolicLink(file(target).toPath()).toString())
        assertEquals("other", other.readText())
    }

    @Test
    fun `B-LINUX-191 a corrupt receipt fails closed and exposes only fixed guidance`() {
        file(target).writeText("active")
        file("$target.oc-good").writeText("good")
        file("$target.oc-pending").writeText("invalid-private-content")
        val result = run("oc_update_recover \"$target\"")
        assertNotEquals(0, result.exitCode)
        assertTrue(result.stderr.contains("A component update could not be restored. Run setup again."))
        assertTrue(result.stderr.contains(ComponentUpdates.RECOVERY_FAILURE_LINE))
        assertFalse(result.stderr.contains("invalid-private-content"))
        assertEquals("active", file(target).readText())
        assertEquals("good", file("$target.oc-good").readText())
        assertTrue(file("$target.oc-pending").exists())
    }

    @Test
    fun `B-LINUX-191 a receipt that is a link fails closed`() {
        file(target).writeText("active")
        val elsewhere = File(temp, "elsewhere").apply { writeText("existing\n") }
        Files.createSymbolicLink(file("$target.oc-pending").toPath(), elsewhere.toPath())
        assertNotEquals(0, run("oc_update_recover \"$target\"").exitCode)
        assertEquals("active", file(target).readText())
        assertTrue(Files.exists(file("$target.oc-pending").toPath(), LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun `B-LINUX-191 interruption before the old move or after restore is replayable`() {
        file(target).writeText("good")
        file("$target.oc-pending").writeText("existing\n")
        assertEquals(0, run("oc_update_recover \"$target\"").exitCode)
        assertEquals("good", file(target).readText())
        file("$target.oc-pending").writeText("existing\n")
        file(target).delete()
        assertNotEquals(0, run("oc_update_recover \"$target\"").exitCode)
        assertTrue(file("$target.oc-pending").exists())
    }
}
