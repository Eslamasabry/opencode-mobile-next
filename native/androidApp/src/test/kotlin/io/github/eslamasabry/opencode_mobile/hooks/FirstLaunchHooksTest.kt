package io.github.eslamasabry.opencode_mobile.hooks

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * E00-S07 (plan 05 §7.9): the first-launch hooks run once after the Flutter → native update, resume after a kill, and
 * never run on a fresh install. The journal fixtures are the runner's real `files/native/migration.json` shapes
 * (plan 05 §7.2).
 */
class FirstLaunchHooksTest {
    private val dir: File = Files.createTempDirectory("hooks").toFile()
    private val journal = File(dir, "native/migration.json")
    private val markerFile = File(dir, "native/first-launch.json")
    private var now = 1_730_457_605_000L

    /** After a 2.1.4 install: Flutter data adopted, day-1 migration complete. */
    private val adoptedJournal =
        """{"schema":1,"source":{"kind":"flutter","prefsBytes":812345,"prefsKeys":412,"secretEntries":7,""" +
            """"foundAt":1730457600000},"backup":{"state":"done"},"steps":{"M1":{"state":"done","attempts":1}},""" +
            """"migrations":{"native.migration.v1":{"complete":true,"at":1730457601000}},""" +
            """"gate":{"firstOpenAt":1730457601000},"followUp":[],"blocked":null}"""

    private class ProcessDeath : Error("killed")

    private class Step(override val id: String, var answer: StepResult = StepResult.Done, var dies: Boolean = false) :
        HookStep {
        var runs = 0

        override suspend fun run(): StepResult {
            runs++
            if (dies) throw ProcessDeath()
            return answer
        }
    }

    private fun hooks(vararg steps: Step) =
        FirstLaunchHooks(JournalSource(journal), FileHookMarker(markerFile), steps.toList(), { now })

    private fun journal(text: String) {
        journal.parentFile.mkdirs()
        journal.writeText(text)
    }

    @Test
    fun `E00-S07 a fresh install runs no hook and writes no marker`() = runTest {
        val step = Step("H1.locale")
        assertNull(hooks(step).run(), "no journal")
        journal(adoptedJournal.replace("\"kind\":\"flutter\"", "\"kind\":\"none\""))
        assertNull(hooks(step).run(), "source none")
        journal(adoptedJournal.replace("\"complete\":true", "\"complete\":false"))
        assertNull(hooks(step).run(), "migration not complete")
        journal("{not json")
        assertNull(hooks(step).run(), "unreadable journal")
        assertEquals(0, step.runs)
        assertTrue(!markerFile.exists())
    }

    @Test
    fun `E00-S07 the hooks run once after the update`() = runTest {
        journal(adoptedJournal)
        val a = Step("H1.locale")
        val b = Step("H2.schedules")
        val first = hooks(a, b).run()!!
        assertEquals(listOf("H1.locale", "H2.schedules"), first.ran)
        assertEquals(HookState.Done, first.state)
        val again = hooks(a, b).run()!!
        assertEquals(emptyList(), again.ran)
        assertEquals(1, a.runs)
        assertEquals(1, b.runs)
        val saved = markerFile.readText()
        assertTrue("\"hooks.firstLaunch.v1\"" in saved && "\"state\":\"done\"" in saved, saved)
    }

    @Test
    fun `E00-S07 a launch killed half-way resumes at the first unfinished step`() = runTest {
        journal(adoptedJournal)
        val a = Step("H1.locale")
        val b = Step("H2.widgets", dies = true)
        val c = Step("H2.tiles")
        assertFailsWith<ProcessDeath> { hooks(a, b, c).run() }
        val killed = FileHookMarker(markerFile).read()
        assertEquals(HookState.Running, killed.state)
        assertEquals(1, killed.attempts, "the attempt is counted before any step runs")
        assertEquals(setOf("H1.locale"), killed.done)

        b.dies = false
        val resumed = hooks(a, b, c).run()!!
        assertEquals(listOf("H2.widgets", "H2.tiles"), resumed.ran)
        assertEquals(1, a.runs, "a finished step is not run again")
        assertEquals(HookState.Done, resumed.state)
    }

    @Test
    fun `E00-S07 a step that cannot finish yet is tried on the next launch, at most three launches`() = runTest {
        journal(adoptedJournal)
        val a = Step("H1.locale")
        val server = Step("H2.phoneServer", answer = StepResult.Later)
        assertEquals(HookState.Running, hooks(a, server).run()!!.state)
        assertEquals(HookState.Running, hooks(a, server).run()!!.state)
        val third = hooks(a, server).run()!!
        assertEquals(HookState.Done, third.state)
        assertEquals(listOf("H2.phoneServer"), third.later)
        assertEquals(setOf("H2.phoneServer"), FileHookMarker(markerFile).read().gaveUp)
        assertEquals(3, server.runs)
        assertEquals(1, a.runs)
        hooks(a, server).run()
        assertEquals(3, server.runs, "closed after the third launch")
    }

    @Test
    fun `E00-S07 a step that throws is retried like Later and never stops the others`() = runTest {
        journal(adoptedJournal)
        val broken =
            object : HookStep {
                override val id = "H2.shortcuts"

                override suspend fun run(): StepResult = error("launcher refused")
            }
        val after = Step("H2.tiles")
        val report =
            FirstLaunchHooks(JournalSource(journal), FileHookMarker(markerFile), listOf(broken, after), { now }).run()!!
        assertEquals(listOf("H2.tiles"), report.ran)
        assertEquals(listOf("H2.shortcuts"), report.later)
    }
}
