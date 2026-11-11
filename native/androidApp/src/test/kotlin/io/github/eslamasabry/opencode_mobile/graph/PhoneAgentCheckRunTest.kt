package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.agents.check.PhoneCheckFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PhoneAgentCheckRunTest {
    private class Steps(
        var clear: Boolean = true,
        var install: InstallEnd = InstallEnd.Done,
        var runs: Boolean = true,
        var helper: Outcome<String?> = Outcome.Ok("1.2.3"),
        var save: Boolean = true,
    ) : PhoneCheckSteps {
        val calls = mutableListOf<String>()

        override suspend fun clearProof() = clear.also { calls += "clear" }

        override suspend fun install() = install.also { calls += "install" }

        override suspend fun pinnedRuns() = runs.also { calls += "runs" }

        override suspend fun helperAnswer() = helper.also { calls += "helper" }

        override suspend fun saveProof() = save.also { calls += "save" }
    }

    private fun run(steps: Steps, tooOld: Boolean = false) =
        PhoneAgentCheckRun(steps, "1.2.3", helperTooOld = { tooOld }).run()

    @Test
    fun `a full pass runs every step in order and only then stores the proof`() = runTest {
        val steps = Steps()
        assertNull(run(steps))
        assertEquals(listOf("clear", "install", "runs", "helper", "save"), steps.calls)
    }

    @Test
    fun `a failed install stores no proof and names the install`() = runTest {
        val steps = Steps(install = InstallEnd.Failed)
        assertEquals(PhoneCheckFailure.Install, run(steps))
        assertTrue("save" !in steps.calls && "helper" !in steps.calls)
    }

    @Test
    fun `busy, space, version, daemon and storage each say their own reason`() = runTest {
        assertEquals(PhoneCheckFailure.Busy, run(Steps(install = InstallEnd.Busy)))
        assertEquals(PhoneCheckFailure.Storage, run(Steps(install = InstallEnd.Space)))
        assertEquals(PhoneCheckFailure.Storage, run(Steps(clear = false)))
        assertEquals(PhoneCheckFailure.Version, run(Steps(runs = false)))
        assertEquals(
            PhoneCheckFailure.Daemon,
            run(Steps(helper = Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable)))),
        )
        assertEquals(PhoneCheckFailure.Storage, run(Steps(save = false)))
    }

    @Test
    fun `a helper that is too old, or that reports another Paseo version, asks for the helper update`() = runTest {
        val refused = Outcome.Failed(Failure.Runtime(RuntimeProblem.HelperUpdateRequired))
        assertEquals(PhoneCheckFailure.HelperVersion, run(Steps(helper = refused), tooOld = true))
        val other = Steps(helper = Outcome.Ok("0.0.1"))
        assertEquals(PhoneCheckFailure.HelperVersion, run(other))
        assertTrue("save" !in other.calls, "a helper at the wrong version proves nothing")
        assertEquals(PhoneCheckFailure.HelperVersion, run(Steps(helper = Outcome.Ok(null))))
    }
}
