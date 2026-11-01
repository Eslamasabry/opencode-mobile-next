package io.github.eslamasabry.oc.migration.readers.migration

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Journal → apply → commit marker → verify, under crashes and reruns (audit findings 4 and 5). */
class StepRunnerTest {

    private val dir: File = Files.createTempDirectory("steps").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    /** The "Flutter-readable" pref, backed by a file so it survives a simulated process death. */
    private class FilePref(private val f: File, private val refuse: () -> Boolean = { false }) : BarrierPref {
        override fun read(): Boolean? = if (f.exists()) f.readText().toBooleanStrictOrNull() else null
        override fun write(value: Boolean): Boolean {
            if (refuse()) return false
            f.writeText(value.toString())
            return true
        }
    }

    /** An idempotent step that appends a line once (it checks before writing) to a data file. */
    private class LineStep(override val id: String, private val data: File, override val breaksFlutterReadability: Boolean = false) : MigrationStep {
        var applies = 0
        override fun apply() {
            applies++
            val lines = if (data.exists()) data.readLines() else emptyList()
            if (id !in lines) data.appendText("$id\n")
        }
        override fun verify(): Boolean = data.exists() && id in data.readLines()
    }

    private val journalFile get() = File(dir, "migration.json")
    private val prefFile get() = File(dir, "flutterReadable")
    private val dataFile get() = File(dir, "data")

    private fun runner(faults: FaultPoints = FaultPoints.None) = StepRunner(JournalStore(journalFile, faults), FilePref(prefFile), faults)

    private fun runAll(faults: FaultPoints = FaultPoints.None): List<StepOutcome> {
        val r = runner(faults)
        val init = r.initBarrier()
        if (init is StepOutcome.Blocked) return listOf(init)
        return listOf(
            r.run(LineStep("M2", dataFile)),
            r.run(LineStep("M3", dataFile)),
            r.run(LineStep("V2.queue", dataFile, breaksFlutterReadability = true)),
        )
    }

    @Test
    fun `steps run once, a rerun only re-verifies`() {
        assertEquals(listOf(StepOutcome.Verified, StepOutcome.Verified, StepOutcome.Verified), runAll())
        val bytes = dataFile.readText()
        assertEquals(listOf(StepOutcome.AlreadyVerified, StepOutcome.AlreadyVerified, StepOutcome.AlreadyVerified), runAll())
        assertEquals(bytes, dataFile.readText())
        assertEquals("M2\nM3\nV2.queue\n", bytes)
        val j = JournalStore(journalFile).load()
        assertEquals(listOf(1, 1, 1), j.steps.values.map { it.attempts })
        assertEquals(false, j.flutterReadable)
        assertEquals(false, FilePref(prefFile).read())
    }

    @Test
    fun `a crash at any point converges to the same end state on restart`() {
        val points = RecordingFaults().also { runAll(it) }.seen
        dir.listFiles()!!.forEach { it.delete() }
        assertTrue(points.any { it.endsWith(":committed") } && points.any { it.contains(":barrier:") })
        for ((i, point) in points.withIndex()) {
            dir.listFiles()!!.forEach { it.delete() }
            val occurrence = points.subList(0, i + 1).count { it == point }
            assertFailsWith<SimulatedCrash> { runAll(RecordingFaults(point, occurrence)) }
            val after = runAll()
            assertTrue(after.all { it == StepOutcome.Verified || it == StepOutcome.AlreadyVerified }, "after $point: $after")
            assertEquals("M2\nM3\nV2.queue\n", dataFile.readText(), "after $point")
            assertEquals(false, FilePref(prefFile).read(), "after $point")
            assertTrue(JournalStore(journalFile).load().steps.values.all { it.state == StepState.Verified && it.attempts in 1..2 })
        }
    }

    @Test
    fun `the barrier is durable before the first write of a breaking step`() {
        runner().initBarrier()
        assertEquals(true, FilePref(prefFile).read())
        var flagDuringApply: Boolean? = null
        var journalDuringApply: Boolean? = null
        val step = object : MigrationStep {
            override val id = "V2.queue"
            override val breaksFlutterReadability = true
            override fun apply() {
                flagDuringApply = FilePref(prefFile).read()
                journalDuringApply = JournalStore(journalFile).load().flutterReadable
            }
            override fun verify() = true
        }
        assertEquals(StepOutcome.Verified, runner().run(step))
        assertEquals(false, flagDuringApply)
        assertEquals(false, journalDuringApply)
    }

    @Test
    fun `a crash between the journal and the pref write is repaired before anything else`() {
        runner().initBarrier()
        // Journal says lowered, pref still says readable (process died between the two writes).
        JournalStore(journalFile).update { it.flutterReadable = false }
        assertEquals(true, FilePref(prefFile).read())
        assertEquals(StepOutcome.Verified, runner().initBarrier())
        assertEquals(false, FilePref(prefFile).read())
    }

    @Test
    fun `a refused barrier write blocks the breaking step before it writes anything`() {
        var refuse = false
        val r = StepRunner(JournalStore(journalFile), FilePref(prefFile) { refuse }, FaultPoints.None)
        r.initBarrier()
        refuse = true
        val step = LineStep("V2.queue", dataFile, breaksFlutterReadability = true)
        assertEquals(StepOutcome.Blocked("BarrierNotDurable"), r.run(step))
        assertEquals(0, step.applies)
    }

    @Test
    fun `three crashes in one step block instead of looping, Try again resets only that step`() {
        runner().initBarrier()
        val crashing = object : MigrationStep {
            override val id = "M2"
            override fun apply() = throw SimulatedCrash("inside M2")
            override fun verify() = false
        }
        repeat(3) { assertFailsWith<SimulatedCrash> { runner().run(crashing) } }
        assertEquals(StepOutcome.Blocked("StepCrashed"), runner().run(crashing))
        assertEquals(StepOutcome.Blocked("StepCrashed"), runner().run(LineStep("M3", dataFile)), "blocked means blocked for every step")
        runner().retry()
        assertNull(JournalStore(journalFile).load().blocked)
        assertEquals(StepOutcome.Verified, runner().run(LineStep("M2", dataFile)))
    }

    @Test
    fun `a committed step whose verify fails blocks, a verified step that regressed blocks`() {
        runner().initBarrier()
        val step = LineStep("M3", dataFile)
        assertEquals(StepOutcome.Verified, runner().run(step))
        dataFile.writeText("")
        assertEquals(StepOutcome.Blocked("VerifyRegressed"), runner().run(step))
    }

    @Test
    fun `R1 rollback guard table`() {
        assertEquals(RollbackGuard.Decision.Allowed, RollbackGuard.evaluate(true, nativeJournalExists = true))
        assertEquals(RollbackGuard.Decision.Blocked("Incompatible"), RollbackGuard.evaluate(false, nativeJournalExists = true))
        assertEquals(RollbackGuard.Decision.Blocked("Uncertain"), RollbackGuard.evaluate(null, nativeJournalExists = true))
        assertEquals(RollbackGuard.Decision.Allowed, RollbackGuard.evaluate(null, nativeJournalExists = false))
    }

    @Test
    fun `rollback is never allowed while a breaking step may be half done`() {
        // Simulate death at every point of a run; after each, evaluate what the R1 build would decide.
        val points = RecordingFaults().also { runAll(it) }.seen
        for ((i, point) in points.withIndex()) {
            dir.listFiles()!!.forEach { it.delete() }
            val occurrence = points.subList(0, i + 1).count { it == point }
            assertFailsWith<SimulatedCrash> { runAll(RecordingFaults(point, occurrence)) }
            val breakingStarted = dataFile.exists() && "V2.queue" in dataFile.readLines() ||
                JournalStore(journalFile).load().step("V2.queue").state != StepState.Pending
            val decision = RollbackGuard.evaluate(FilePref(prefFile).read(), journalFile.exists())
            if (breakingStarted) assertTrue(decision is RollbackGuard.Decision.Blocked, "rollback allowed after crash at $point")
        }
    }

    @Test
    fun `journal keeps unknown fields and survives a round trip`() {
        journalFile.writeText("""{"schema":1,"generation":"prefs-v1","futureField":{"x":[1,2]},"steps":{"M1":{"state":"verified","attempts":1}}}""")
        val j = JournalStore(journalFile).load()
        assertEquals(StepState.Verified, j.step("M1").state)
        JournalStore(journalFile).save(j)
        assertTrue(journalFile.readText().contains("\"futureField\":{\"x\":[1,2]}"))
    }
}
