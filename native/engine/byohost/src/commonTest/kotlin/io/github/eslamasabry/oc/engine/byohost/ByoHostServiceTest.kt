package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedRunner : FakeRunner() {
    var disposals = 0

    override suspend fun dispose() {
        disposals++
    }
}

class ByoHostServiceTest {
    private val disabled = ByoHostFailureCode.Disabled.asFailure()

    @Test
    fun `B-AGENT-049 the gate is default off and a missing artifact never reports available`() = runTest {
        val service = ByoHostService(MemoryStore(), SharedRunner(), CountingRandom(), bundle = testBundle)
        assertFalse(service.available())
        assertEquals(disabled, assertIs<Outcome.Failed>(service.newMachine()).failure)
        assertEquals(disabled, assertIs<Outcome.Failed>(service.machines()).failure)
        val noPackage = ByoHostService(MemoryStore(), SharedRunner(), CountingRandom(), enabled = true)
        assertFalse(noPackage.available())
        val shipped = ByoHostReleasePins.bundleForAppVersion("2.1.0+57")
        val pinned = ByoHostService(MemoryStore(), SharedRunner(), CountingRandom(), shipped, enabled = true)
        assertFalse(pinned.available())
        val ready = ByoHostService(MemoryStore(), SharedRunner(), CountingRandom(), testBundle, enabled = true)
        assertTrue(ready.available())
        service.dispose()
        noPackage.dispose()
        ready.dispose()
        assertFalse(ready.available())
    }

    @Test
    fun `B-AGENT-049 machines keep independent controllers and releasing one keeps the shared runner`() = runTest {
        val runner = SharedRunner()
        val service = ByoHostService(MemoryStore(), runner, CountingRandom(), enabled = true)
        val first = service.newMachine().value()
        val second = service.newMachine().value()
        assertNotEquals(first.profileId, second.profileId)
        assertTrue(Regex("host-[0-9a-f]{32}").matches(first.profileId))
        assertSame(second, service.machine(second.profileId).value())
        service.release(first.profileId).value()
        assertEquals(0, runner.disposals)
        assertSame(second, service.machine(second.profileId).value())
        service.dispose()
        assertEquals(1, runner.disposals)
        assertEquals(disabled, assertIs<Outcome.Failed>(service.machine(second.profileId)).failure)
    }
}
