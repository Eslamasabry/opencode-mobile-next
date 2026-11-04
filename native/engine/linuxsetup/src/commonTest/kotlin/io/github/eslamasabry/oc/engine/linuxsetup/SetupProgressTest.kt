package io.github.eslamasabry.oc.engine.linuxsetup

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Ports of test/setup_engine_test.dart "selection" and "weights and ETA". */
class SetupProgressTest {
    private fun c(id: String, deps: List<String> = emptyList(), req: Boolean = false) =
        SetupComponent(id = id, dependsOn = deps, required = req)

    @Test
    fun `B-LINUX-200 required and selected components with dependencies come in dependency order`() {
        val registry =
            listOf(
                c("tool", deps = listOf("node")),
                c("opencode", deps = listOf("node"), req = true),
                c("node", deps = listOf("base")),
                c("base", req = true),
                c("extra"),
            )
        assertEquals(listOf("base", "node", "tool", "opencode"), expandSelection(registry, setOf("tool")).map { it.id })
        assertEquals(listOf("base", "node", "opencode"), expandSelection(registry, emptySet()).map { it.id })
    }

    @Test
    fun `B-LINUX-200 a dependency cycle is an error not a hang`() {
        assertFailsWith<IllegalStateException> {
            expandSelection(listOf(c("a", deps = listOf("b"), req = true), c("b", deps = listOf("a"))), emptySet())
        }
    }

    @Test
    fun `B-LINUX-200 the real registry lists every dependency before its dependent`() {
        for (voice in listOf(false, true)) {
            val registry = setupComponentRegistry(supportsVoice = voice)
            val seen = mutableSetOf<String>()
            for (component in registry) {
                for (dependency in component.dependsOn) assertTrue(dependency in seen, component.id)
                seen.add(component.id)
            }
            assertEquals(listOf("start"), registry.filter { it.jobStep }.map { it.id })
            val start = registry.indexOfFirst { it.id == "start" }
            assertTrue(registry.drop(start + 1).all { it.app })
        }
    }

    private fun w(id: String, seconds: Int) = SetupComponent(id = id, estimatedSeconds = seconds)

    private val components = listOf(w("a", 10), w("b", 30), w("c", 60))

    private fun close(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "$expected != $actual")

    @Test
    fun `B-LINUX-201 done counts whole, bytes and percent count real, a bare stage counts half`() {
        val done = ComponentProgress("a", ComponentState.Done)
        val pending = ComponentProgress("c", ComponentState.Pending)
        close(
            (10.0 + 15) / 100,
            overallFraction(components, listOf(done, ComponentProgress("b", ComponentState.Running, bytesDone = 15, bytesTotal = 30), pending)),
        )
        close(
            (10.0 + 6) / 100,
            overallFraction(components, listOf(done, ComponentProgress("b", ComponentState.Running, percent = 20.0), pending)),
        )
        close(
            (10.0 + 15) / 100,
            overallFraction(components, listOf(done, ComponentProgress("b", ComponentState.Running, stage = "Installing"), pending)),
        )
    }

    @Test
    fun `B-LINUX-201 a new stage never moves the bar backwards`() {
        val floors = mutableMapOf<String, Double>()
        val downloading =
            overallFraction(
                components,
                listOf(
                    ComponentProgress("a", ComponentState.Done),
                    ComponentProgress("b", ComponentState.Running, bytesDone = 27, bytesTotal = 30),
                    ComponentProgress("c", ComponentState.Pending),
                ),
                floors,
            )
        val unpacking =
            overallFraction(
                components,
                listOf(
                    ComponentProgress("a", ComponentState.Done),
                    ComponentProgress("b", ComponentState.Running, stage = "Unpacking"),
                    ComponentProgress("c", ComponentState.Pending),
                ),
                floors,
            )
        assertEquals(downloading, unpacking)
    }

    @Test
    fun `B-LINUX-201 no ETA before 10 seconds of real progress, then the remaining estimates scaled by the pace`() {
        val progress =
            listOf(
                ComponentProgress("a", ComponentState.Skipped),
                ComponentProgress("b", ComponentState.Done),
                ComponentProgress("c", ComponentState.Pending),
            )
        assertNull(estimateEta(components, progress, 0.4, 9.seconds))
        assertEquals(120, estimateEta(components, progress, 0.4, 60.seconds))
        assertNull(estimateEta(components, progress, 0.1, 60.seconds))
    }
}
