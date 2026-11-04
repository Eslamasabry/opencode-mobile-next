package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.runtime.setup.SetupStepSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertTrue(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:4097"))
        assertTrue(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:4097/"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("https://127.0.0.1:4097"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://127.0.0.1:4096"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://localhost:4097"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("http://laptop.local:4097"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl("not a url"))
        assertFalse(BuiltinSetupFinisher.managesServerUrl(null))
    }
}
