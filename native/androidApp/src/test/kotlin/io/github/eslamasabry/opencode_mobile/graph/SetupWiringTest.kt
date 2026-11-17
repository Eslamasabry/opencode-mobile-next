package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.setup.SetupStepSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** BUILD-RUNTIME-B: the pure parts of SetupWiring (the native job's spec, the phone server's address rule). */
class SetupWiringTest {
    @Test
    fun `B-LINUX-192 a job step reaches SetupRunner with its weight, script, labels and space data unchanged`() {
        val spec =
            SetupStepSpec(
                id = "node",
                weight = 20,
                data = mapOf("requiredFreeBytes" to "300000000"),
                script = "# oc setup prelude\nset -eu\n",
                labels = mapOf("retry" to "Interrupted. Trying again"),
                agentUser = false,
            )
        val runner = NativeSetupHost.spec(spec)
        assertEquals("node", runner.id)
        assertEquals(20.0, runner.weight)
        assertEquals("# oc setup prelude\nset -eu\n", runner.script)
        assertEquals(mapOf("requiredFreeBytes" to "300000000"), runner.data)
        assertEquals(mapOf("retry" to "Interrupted. Trying again"), runner.labels)
        assertFalse(runner.native || runner.step || runner.skipped)
        val start = NativeSetupHost.spec(SetupStepSpec("start", 15, emptyMap(), step = true, stage = "Starting OpenCode"))
        assertTrue(start.step)
        assertEquals("Starting OpenCode", start.stage)
    }

    @Test
    fun `B-LINUX-205 only the phone server's own address is taken as the setup's profile`() {
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:4097"))
        assertTrue(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:${PhoneServerScripts.PORT}"))
        assertTrue(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:${PhoneServerScripts.PORT}/"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("https://127.0.0.1:${PhoneServerScripts.PORT}"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:4096"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://localhost:${PhoneServerScripts.PORT}"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://laptop.local:${PhoneServerScripts.PORT}"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("not a url"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl(null))
    }

    @Test
    fun `B-LINUX-206 an old server that cannot be stopped fails the restart instead of reporting the old version as new`() =
        runTest {
            val stopped = BuiltinSetupFinisher.replaceRunningServer(UnconfinedTestDispatcher(testScheduler)) {
                throw IllegalStateException("still serving")
            }
            assertTrue(stopped is Outcome.Failed, "a stop that throws must not read as success: $stopped")
        }

    @Test
    fun `B-LINUX-206 a stopped old server lets the restart go on`() = runTest {
        var calls = 0
        val stopped = BuiltinSetupFinisher.replaceRunningServer(UnconfinedTestDispatcher(testScheduler)) { calls++ }
        assertTrue(stopped is Outcome.Ok)
        assertEquals(1, calls)
    }
}
