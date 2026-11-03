@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Add tools on a phone that already finished a setup (test/builtin/add_tools_run_test.dart). Fakes only. */
class SetupJobEngineTest {
    /** The native runner and the check run in memory: setup.json is [job], encoded the way the runner writes it. */
    private class FakeRunner : SetupRunnerPort {
        var job: MutableMap<String, Any?>? = null
        val started = mutableListOf<String>()
        val steps = mutableListOf<Pair<String, Boolean>>()
        var holdChecks: CompletableDeferred<Unit>? = null
        var startBreaks = false

        override suspend fun check(job: List<SetupPart>): Outcome<Map<String, SetupCheck>> {
            holdChecks?.await()
            return emptyMap<String, SetupCheck>().ok()
        }

        override suspend fun startSetup(jobId: String, components: List<SetupSpec>, params: JobParams): Outcome<Unit> {
            if (startBreaks) error("the runner exploded")
            started += jobId
            job = mutableMapOf(
                "jobId" to jobId,
                "state" to "running",
                "current" to null,
                "order" to components.map { it.id },
                "components" to components.associate { spec ->
                    spec.id to mutableMapOf<String, Any?>(
                        "state" to if (spec.skipped) "skipped" else "pending",
                        "data" to mapOf("openCodeChanged" to spec.openCodeChanged.toString()),
                    )
                }.toMutableMap(),
                "params" to params,
            )
            return Unit.ok()
        }

        override suspend fun setupStatus(): Outcome<String?> = job?.let { DartJson.encode(it) }.ok()

        override suspend fun completeSetupStep(jobId: String, id: String, ok: Boolean, error: String?, version: String?): Outcome<Unit> {
            steps += id to ok
            @Suppress("UNCHECKED_CAST")
            val row = (job!!["components"] as MutableMap<String, MutableMap<String, Any?>>)[id]!!
            row["state"] = if (ok) "done" else "failed"
            job!!["state"] = if (ok) "done" else "failed"
            return Unit.ok()
        }

        /** Moves the running job to [id]. */
        fun advance(id: String) {
            job!!["current"] = id
            @Suppress("UNCHECKED_CAST")
            (job!!["components"] as MutableMap<String, MutableMap<String, Any?>>)[id]!!["state"] = "running"
        }
    }

    private val registry = listOf(
        SetupPart("linux", required = true),
        SetupPart("opencode", dependsOn = listOf("linux"), required = true),
        SetupPart("python", dependsOn = listOf("linux")),
        SetupPart("voice"),
        SetupPart("node", dependsOn = listOf("linux")),
        SetupPart(SetupIds.START, required = true, jobStep = true),
    )

    /** A clock that never moves: job ids must still differ. */
    private val frozen = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_790_467_200_000)
    }

    private fun engine(runner: FakeRunner, finisher: SetupFinishStep? = SetupFinishStep { Unit.ok() }) =
        SetupJobEngine(runner, { registry }, frozen, finisher)

    @Test
    fun `B-LINUX-014 Add tools starts even when the previous job ended any way and is read while its checks run`() = runTest {
        for (ended in listOf("done", "failed", "cancelled", "interrupted")) {
            for ((id, before) in listOf("voice" to "python", "python" to "voice", "node" to "voice")) {
                val runner = FakeRunner()
                val engine = engine(runner)
                engine.run(setOf(before), SetupJobParams.firstSetup)
                runner.job!!["state"] = ended
                val oldJob = runner.job!!["jobId"]

                // The person adds a tool; its checks are slow, and meanwhile the app re-reads the runner's record.
                runner.holdChecks = CompletableDeferred()
                val adding = launch { engine.run(setOf(id), SetupJobParams.adding(listOf(id))) }
                runCurrent()
                engine.restore()
                assertEquals(listOf(id), engine.progress.value.adding, "the old record never replaces the starting job ($ended)")
                runner.holdChecks!!.complete(Unit)
                adding.join()

                assertNotEquals(oldJob, runner.started.last())
                val progress = engine.progress.value
                assertEquals(runner.started.last(), progress.jobId)
                assertEquals(listOf(id), progress.adding)
                assertEquals(SetupState.Running, progress.state)
                assertTrue(progress.components.any { it.id == id }, "$id after $before ($ended)")
            }
        }
    }

    @Test
    fun `B-LINUX-014 a start that breaks is a failed job with its text kept, not a throw`() = runTest {
        val runner = FakeRunner().apply { startBreaks = true }
        val engine = engine(runner)
        engine.run(setOf("voice"), SetupJobParams.adding(listOf("voice")))
        val progress = engine.progress.value
        assertEquals(SetupState.Failed, progress.state)
        val failure = progress.failure
        assertTrue(failure is Failure.Unexpected && failure.details?.text?.contains("IllegalStateException") == true, "$failure")
        assertTrue(failure.details?.text?.contains("exploded") == false, "the message never reaches Details")
        assertEquals(listOf("voice"), progress.adding)
        assertTrue(progress.components.any { it.id == "voice" })
        assertTrue(progress.canContinue)
    }

    @Test
    fun `B-LINUX-014 Continue after the start step broke starts it again and ends done, with the adding list kept`() = runTest {
        val runner = FakeRunner()
        var calls = 0
        val engine = engine(runner, SetupFinishStep {
            calls++
            if (calls == 1) error("Using ref when a widget is gone")
            Unit.ok()
        })
        engine.run(setOf("voice"), SetupJobParams.adding(listOf("voice")))
        runner.advance(SetupIds.START)
        engine.poll()
        assertEquals(SetupState.Failed, engine.progress.value.state)
        assertTrue(engine.progress.value.canContinue)
        assertEquals(listOf(SetupIds.START to false), runner.steps)

        // "Continue setup": the same components again, no params of its own.
        val ids = engine.progress.value.components.map { it.id }.toSet()
        engine.run(ids)
        runner.advance(SetupIds.START)
        engine.poll()
        engine.poll() // A poll after the end never finishes twice.
        assertEquals(2, calls)
        assertEquals(SetupState.Done, engine.progress.value.state)
        assertEquals(listOf("voice"), engine.progress.value.adding)
    }

    @Test
    fun `B-LINUX-014 a job ending tells the pages that list tools, once, without a watcher`() = runTest {
        val runner = FakeRunner()
        val engine = engine(runner)
        val before = engine.toolsChanged.revision.value
        engine.run(setOf("voice"), SetupJobParams.adding(listOf("voice")))
        assertEquals(before, engine.toolsChanged.revision.value, "still running")
        runner.job!!["state"] = "failed"
        engine.restore()
        assertEquals(before + 1, engine.toolsChanged.revision.value)
        engine.restore()
        assertEquals(before + 1, engine.toolsChanged.revision.value, "the same end")
    }

    @Test
    fun `B-LINUX-014 an unreadable saved job or a failed check fails the start instead of starting fresh`() = runTest {
        val runner = object : SetupRunnerPort by FakeRunner() {
            override suspend fun setupStatus(): Outcome<String?> = "{unreadable setup".ok()
        }
        val engine = SetupJobEngine(runner, { registry }, frozen, null)
        engine.run(setOf("voice"))
        assertEquals(SetupState.Failed, engine.progress.value.state)

        val checking = object : SetupRunnerPort by FakeRunner() {
            override suspend fun check(job: List<SetupPart>): Outcome<Map<String, SetupCheck>> =
                Failure.Runtime(RuntimeProblem.OwnershipUnknown).failed()
        }
        val second = SetupJobEngine(checking, { registry }, frozen, null)
        second.run(setOf("voice"), SetupJobParams.adding(listOf("voice")))
        assertEquals(SetupState.Failed, second.progress.value.state)
        assertEquals(Failure.Runtime(RuntimeProblem.OwnershipUnknown), second.progress.value.failure)
        assertTrue(second.progress.value.components.all { it.state == ComponentState.Pending })
    }

    @Test
    fun `B-LINUX-014 the job is the selection with its dependencies, in order, ending with the start step`() {
        assertEquals(
            listOf("linux", "opencode", "python", "start"),
            expandSelection(registry, setOf("python")).map { it.id },
        )
    }
}
