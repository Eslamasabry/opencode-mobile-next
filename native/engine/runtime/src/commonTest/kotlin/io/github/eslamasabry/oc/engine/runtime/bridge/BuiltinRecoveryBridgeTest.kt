package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/builtin_recovery_bridge_test.dart. */
class BuiltinRecoveryBridgeTest {
    private val channel = FakeBuiltinLinuxChannel()
    private val linux = BuiltinLinuxBridge(channel, supported = true)

    @Test
    fun `B-LINUX-031 missing native intent fails closed even when an old server ran`() {
        for (status in listOf(
            RuntimeStatusMap.Absent,
            RuntimeStatusMap.parse(mapOf("serverRunning" to true)),
            RuntimeStatusMap.parse(mapOf("serverRestartWanted" to "true")),
        )) {
            assertFalse(status.serverRestartWanted)
            assertNull(status.serverRecoveryGeneration)
        }
    }

    @Test
    fun `B-LINUX-031 native intent remains readable after process death`() {
        val status =
            RuntimeStatusMap.parse(
                mapOf("serverRunning" to false, "serverRestartWanted" to true, "serverRecoveryGeneration" to 42),
            )
        assertFalse(status.serverRunning)
        assertTrue(status.serverRestartWanted)
        assertEquals(42L, status.serverRecoveryGeneration)
    }

    @Test
    fun `B-LINUX-031 automatic launch and confirmation carry the same admission token`() = runTest {
        linux.restartServer("serve", expectedGeneration = 42)
        linux.confirmServerRecovery(expectedGeneration = 42)
        linux.cancelServerRecovery()
        assertEquals(listOf("restartServer", "confirmServerRecovery", "cancelServerRecovery"), channel.methods)
        assertEquals(mapOf("script" to "serve", "port" to 4097, "expectedGeneration" to 42L), channel.calls[0].arguments)
        assertEquals(mapOf("expectedGeneration" to 42L), channel.calls[1].arguments)
        assertNull(channel.calls[2].arguments)
    }

    @Test
    fun `B-LINUX-031 a launch replacement carries its own native admission token`() = runTest {
        linux.startServerForLaunch("serve", expectedGeneration = 73)
        assertEquals("startServerForLaunch", channel.calls.single().method)
        assertEquals(mapOf("script" to "serve", "port" to 4097, "expectedGeneration" to 73L), channel.calls.single().arguments)
    }

    @Test
    fun `B-LINUX-031 a launch replacement keeps the restore recipe of a normal start`() = runTest {
        linux.startServerForLaunch("serve", expectedGeneration = 73, restoreRecipe = RestoreRecipe(ProfileId("phone")))
        assertEquals(
            mapOf(
                "script" to "serve",
                "port" to 4097,
                "expectedGeneration" to 73L,
                "restoreRecipe" to mapOf("version" to 1, "profileId" to "phone", "runtime" to "openCode2"),
            ),
            channel.calls.single().arguments,
        )
    }

    @Test
    fun `B-LINUX-031 a refused launch replacement shows plain words only`() = runTest {
        channel.answerError("native_error")
        val result =
            linux.startServerForLaunch("serve", expectedGeneration = 42, restoreRecipe = RestoreRecipe(ProfileId("phone")))
        assertEquals(Failure.Runtime(RuntimeProblem.ServerStartUnavailable), (result as Outcome.Failed).failure)
    }

    @Test
    fun `B-LINUX-031 recovery errors never expose native exception details`() = runTest {
        channel.answerError("native_error", details = "private runtime path and confidential content")
        val attempts: List<suspend () -> Outcome<Unit>> =
            listOf(
                { linux.restartServer("serve", expectedGeneration = 42) },
                { linux.confirmServerRecovery(expectedGeneration = 42) },
                { linux.cancelServerRecovery() },
                { linux.ackServerRecoveryReceipt(ProfileId("phone"), "event") },
                { linux.unbindServerRecovery(ProfileId("phone"), delete = true) },
            )
        for (attempt in attempts) {
            assertEquals(Failure.Runtime(RuntimeProblem.RecoveryUnavailable, details = null), (attempt() as Outcome.Failed).failure)
        }
        assertEquals("deleteServerRecovery", channel.calls.last().method)
    }
}
