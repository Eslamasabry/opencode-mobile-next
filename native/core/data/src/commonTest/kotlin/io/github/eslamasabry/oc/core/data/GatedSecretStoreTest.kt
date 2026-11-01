package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.migration.GateState
import io.github.eslamasabry.oc.core.data.migration.MigrationNotices
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.secret.GatedSecretStore
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(MigrationOnly::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GatedSecretStoreTest {
    @Test
    fun `B-MIGR-006 every secret call waits for the migration gate (review w1-data-oc2 MAJOR 5)`() = runTest {
        val raw = MapSecretStore(mapOf("pw.1" to "fixture-pass-1"))
        val gate = StateMigrationGate(GateState.Running("M2"))
        val store = GatedSecretStore(raw, gate)
        val read = async { store.read("pw.1") }
        val write = async { store.write("pw.2", "fixture-pass-2") }
        val delete = async { store.delete("pw.1") }
        val names = async { store.names("pw.") }
        runCurrent()
        assertFalse(read.isCompleted || write.isCompleted || delete.isCompleted || names.isCompleted)
        assertEquals(setOf("pw.1"), raw.map.keys, "nothing reached the raw store before Open")

        gate.publish(GateState.Blocked(io.github.eslamasabry.oc.core.data.migration.BlockReason.StepCrashed))
        runCurrent()
        assertFalse(read.isCompleted, "never answers while Blocked")

        gate.publish(GateState.Open(firstNativeLaunch = true, notices = MigrationNotices()))
        assertEquals(Outcome.Ok("fixture-pass-1"), read.await())
        assertEquals(Outcome.Ok(Unit), write.await())
        assertEquals(Outcome.Ok(Unit), delete.await())
        names.await()
    }
}
