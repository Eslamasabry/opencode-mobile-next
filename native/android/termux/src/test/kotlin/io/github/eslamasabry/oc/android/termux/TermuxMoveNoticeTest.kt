package io.github.eslamasabry.oc.android.termux

import java.io.File
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

// E09-S04 (no catalog row yet; the story id names it): a person who set up Termux in the Flutter app is told once
// what changed and offered built-in Linux, and a Termux answer that arrives during the update is kept.
class TermuxMoveNoticeTest {
    private lateinit var dir: Path
    private lateinit var files: File
    private val memory = FakeMemory()

    @BeforeTest
    fun setUp() {
        dir = tempDir("termux-notice")
        files = dir.toFile()
    }

    @AfterTest
    fun tearDown() = dir.deleteRecursivelyQuietly()

    private fun notice(memory: TermuxMoveNoticeMemory = this.memory) =
        TermuxMoveNotice(
            TermuxLegacySetupFile.inApp(files),
            TermuxLateResults.inApp(files),
            memory,
            SameDispatchers(UnconfinedTestDispatcher()),
        )

    private fun flutterSetup(state: String) {
        File(files, "termux-setup-v2.json")
            .writeText("""{"jobId":"job-1","state":"$state","components":[],"host":"termux","params":{}}""")
    }

    @Test
    fun `E09-S04 termux-setup-v2 present shows the notice with Set up built-in Linux`() = runTest {
        flutterSetup("done")
        val notice = notice()
        notice.check()
        val shown = notice.state.value
        assertEquals(TermuxMoveNoticeState(setupWasRunning = false), shown)
        assertEquals(TermuxMoveNoticeWord.SetUpBuiltin, shown!!.action)
        assertEquals(TermuxMoveNoticeWord.Notice, shown.message)
    }

    @Test
    fun `E09-S04 a person who never set up Termux in the app sees nothing`() = runTest {
        val notice = notice()
        notice.check()
        assertNull(notice.state.value)
    }

    @Test
    fun `E09-S04 a damaged setup file still counts as set up`() = runTest {
        File(files, "termux-setup-v2.json").writeText("{not json")
        val notice = notice()
        notice.check()
        assertEquals(TermuxMoveNoticeState(setupWasRunning = false), notice.state.value)
    }

    @Test
    fun `E09-S04 closing the notice keeps it closed after a restart and leaves the setup file untouched`() = runTest {
        flutterSetup("done")
        val before = File(files, "termux-setup-v2.json").readText()
        val first = notice()
        first.check()
        first.close()
        assertNull(first.state.value)
        first.check()
        assertNull(first.state.value, "a check after closing must not bring it back")

        val afterRestart = notice()
        afterRestart.check()
        assertNull(afterRestart.state.value)
        assertEquals(before, File(files, "termux-setup-v2.json").readText(), "the Flutter setup file is not ours")
    }

    @Test
    fun `E09-S04 the notice stays until the person closes it`() = runTest {
        flutterSetup("done")
        notice().check()
        val nextLaunch = notice()
        nextLaunch.check()
        assertEquals(TermuxMoveNoticeState(setupWasRunning = false), nextLaunch.state.value)
        assertFalse(memory.marked)
    }

    @Test
    fun `E09-S04 a setup still running at the update says it carries on in Termux`() = runTest {
        flutterSetup("running")
        val notice = notice()
        notice.check()
        assertEquals(TermuxMoveNoticeWord.NoticeSetupRunning, notice.state.value!!.message)
    }

    @Test
    fun `E09-S04 a Termux setup answer delivered after the update reaches the notice and is cleared on close`() =
        runTest {
            flutterSetup("done")
            val body = TermuxResultBody(TermuxLateResults.inApp(files)) { 1_000L }
            assertEquals(
                TermuxLateResult(TermuxResultKind.Setup, true, 1_000L),
                body.accept(setupToken = "6f1c", executionId = -1, err = -1, exitCode = 0),
            )
            val notice = notice()
            notice.check()
            assertEquals(TermuxMoveNoticeWord.NoticeSetupRunning, notice.state.value!!.message)
            notice.close()
            assertEquals(emptyList(), TermuxLateResults.inApp(files).read())
        }

    @Test
    fun `E09-S04 a late Termux result is kept without its output and bounded`() {
        val late = TermuxLateResults.inApp(files)
        val body = TermuxResultBody(late) { 7L }
        assertEquals(
            TermuxLateResult(TermuxResultKind.Command, false, 7L),
            body.accept(setupToken = null, executionId = 12, err = -1, exitCode = 2),
        )
        assertEquals(
            TermuxLateResult(TermuxResultKind.Command, null, 7L),
            body.accept(setupToken = null, executionId = 13, err = null, exitCode = null),
        )
        assertEquals(
            TermuxLateResult(TermuxResultKind.Setup, false, 7L),
            body.accept(setupToken = "t", executionId = -1, err = 0, exitCode = 0),
        )
        assertNull(body.accept(setupToken = null, executionId = -1, err = -1, exitCode = 0), "not a Termux answer")
        repeat(20) { body.accept(setupToken = null, executionId = it, err = -1, exitCode = 0) }
        assertEquals(TermuxLateResults.LIMIT, late.read().size)
        val stored = File(files, TermuxLateResults.FILE_NAME).readText()
        assertFalse("t|" in stored || "6f1c" in stored, "no token is stored")
        assertTrue(stored.lines().all { Regex("(Setup|Command)\\|(true|false|unknown)\\|\\d+").matches(it) }, stored)
    }

    @Test
    fun `E09-S04 a damaged late-result file reads as empty lines skipped`() {
        File(files, TermuxLateResults.FILE_NAME).writeText("garbage\nSetup|true|5\nCommand|maybe|x")
        assertEquals(listOf(TermuxLateResult(TermuxResultKind.Setup, true, 5)), TermuxLateResults.inApp(files).read())
    }

    @Test
    fun `E09-S04 every notice word exists in English and Arabic`() {
        for (language in listOf("values", "values-ar")) {
            val text = File("../../androidApp/src/main/res/$language/strings_andr_notices.xml")
            assertTrue(text.isFile, text.path)
            val names =
                Regex("<string name=\"([A-Za-z0-9_]+)\"").findAll(text.readText()).map { it.groupValues[1] }.toSet()
            val missing = TermuxMoveNoticeWord.entries.map { it.resource }.filterNot { it in names }
            assertEquals(emptyList(), missing, language)
        }
    }

    private class FakeMemory : TermuxMoveNoticeMemory {
        var marked = false

        override suspend fun seen(): Boolean = marked

        override suspend fun markSeen() {
            marked = true
        }
    }
}
