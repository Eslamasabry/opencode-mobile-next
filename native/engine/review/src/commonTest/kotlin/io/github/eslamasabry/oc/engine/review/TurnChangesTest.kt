package io.github.eslamasabry.oc.engine.review

import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnChangesTest {
    @Test
    fun `B-CHAT-068 changed files of a turn sum per-file counts the tools reported in order`() {
        val steps = listOf(
            reply("a1", 2, edit("src/a.kt", 3, 1), edit("src/b.kt", 2, 0)),
            reply("a2", 3, edit("src/a.kt", 4, 2)),
        )
        val files = TurnChanges.editedFiles(steps)
        assertEquals(listOf("src/a.kt", "src/b.kt"), files.map { it.path })
        assertEquals(7, files[0].added)
        assertEquals(3, files[0].removed)
        assertEquals("a.kt", files[0].name)
        assertEquals("src", files[0].folder)
    }

    @Test
    fun `B-CHAT-068 counts are omitted when a tool reported none, never guessed`() {
        val files = TurnChanges.editedFiles(listOf(reply("a1", 2, edit("a.kt", 3, 1), edit("a.kt"))))
        assertNull(files.single().added)
        assertNull(files.single().removed)
    }

    @Test
    fun `B-CHAT-101 changed files come only from completed executed edit tools`() {
        val steps = listOf(
            reply(
                "a1",
                2,
                edit("running.kt", 1, 0, status = ToolStatus.Running),
                edit("failed.kt", 1, 0, status = ToolStatus.Error),
                tool("bash", json("command" to "touch shell.kt")),
                tool("read", json("filePath" to "read.kt")),
                edit("done.kt", 1, 0),
            ),
        )
        assertEquals(listOf("done.kt"), TurnChanges.editedFiles(steps).map { it.path })
    }

    @Test
    fun `B-CHAT-120 undo from here lists edited paths from completed edit tools first seen once`() {
        val steps = listOf(
            reply("a1", 2, edit("b.kt"), tool("write", json("filePath" to "c.kt", "content" to "x"))),
            reply("a2", 3, edit("b.kt"), tool("apply_patch", metadata = json("files" to listOf(json("path" to "d.kt"), json("filePath" to "b.kt"))))),
        )
        assertEquals(listOf("b.kt", "c.kt", "d.kt"), TurnChanges.editedPaths(steps))
        assertEquals(listOf("b.kt"), TurnChanges.editedPaths(steps, max = 1))
    }

    @Test
    fun `B-CHAT-068 a new file's lines are counted, an overwrite's are unknown`() {
        val made = tool("write", json("filePath" to "new.kt", "content" to "a\nb\nc"), json("exists" to false))
        val over = tool("write", json("filePath" to "old.kt", "content" to "a\n"), json("exists" to true))
        val files = TurnChanges.editedFiles(listOf(reply("a1", 2, made, over)))
        assertEquals(3, files[0].added)
        assertEquals(0, files[0].removed)
        assertTrue(files[0].hasDiff)
        assertNull(files[1].added)
        assertFalse(files[1].hasDiff)
    }

    @Test
    fun `B-CHAT-058 a turn's diff spans the first before and the last after of a file`() {
        val steps = listOf(reply("a1", 2, edit("a.kt", 1, 1, before = "one\n", after = "two\n"), edit("a.kt", 1, 1, before = "two\n", after = "three\n")))
        val file = TurnChanges.editedFiles(steps).single()
        assertTrue(file.hasPair)
        val diff = TurnChanges.asFileDiff(file)
        assertEquals("one\n", diff.before)
        assertEquals("three\n", diff.after)
        assertNull(diff.patch)
    }

    @Test
    fun `B-CHAT-058 without full pairs the reported patches are kept in order`() {
        val steps = listOf(reply("a1", 2, edit("a.kt", diff = "@@ -1 +1 @@\n-a\n+b"), edit("a.kt", diff = "@@ -2 +2 @@\n-c\n+d")))
        val diff = TurnChanges.asFileDiff(TurnChanges.editedFiles(steps).single())
        assertNull(diff.before)
        assertEquals("@@ -1 +1 @@\n-a\n+b\n@@ -2 +2 @@\n-c\n+d", diff.patch)
    }

    @Test
    fun `B-CHAT-104 synthetic user rows are not prompts and turns start at the real prompt`() {
        val history = listOf(
            prompt("u1", 1),
            reply("a1", 2, edit("a.kt", 1, 0)),
            prompt("u_syn", 3, synthetic = true),
            reply("a2", 4, edit("b.kt", 1, 0)),
            prompt("u2", 5),
            reply("a3", 6, edit("c.kt", 1, 0)),
        )
        assertEquals("u1", TurnChanges.turnPrompt(history, "a2"))
        assertEquals(listOf("a.kt", "b.kt"), TurnChanges.editedFiles(TurnChanges.turnSteps(history, "a1")).map { it.path })
        assertEquals(listOf("c.kt"), TurnChanges.editedFiles(TurnChanges.turnSteps(history, "u2")).map { it.path })
        assertEquals("u1", TurnChanges.firstPrompt(history))
        assertEquals(listOf("b.kt", "c.kt"), TurnChanges.editedPaths(TurnChanges.stepsAfter(history, "u_syn")))
    }

    @Test
    fun `B-CHAT-104 an attachment-only prompt is named by its files`() {
        val p = prompt("u1", 1, text = "")
        assertEquals("", TurnChanges.promptText(p))
        assertTrue(TurnChanges.isPrompt(p))
    }
}
