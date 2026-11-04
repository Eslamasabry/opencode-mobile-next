package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.host.RestorePhase
import io.github.eslamasabry.oc.engine.runtime.host.RestoreReason
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Port of the `bridge` group of test/builtin_linux_test.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuiltinLinuxBridgeTest {
    private val channel = FakeBuiltinLinuxChannel()
    private val linux = BuiltinLinuxBridge(channel, supported = true)

    private fun Outcome<*>.failure(): Failure = (this as Outcome.Failed).failure

    @Test
    fun `B-LINUX-020 status reads asked at the same time share one call and a read after another request is fresh`() = runTest {
        channel.answer = { if (it.method == "status") ChannelReply.Value(mapOf("installed" to true)) else ChannelReply.Value(null) }
        val sharing = StatusReadSharing()
        val first = BuiltinLinuxBridge(channel, supported = true, statusReads = sharing)
        val second = BuiltinLinuxBridge(channel, supported = true, statusReads = sharing)
        channel.statusGate = CompletableDeferred()
        val reads = listOf(async { first.status() }, async { second.status() }, async { first.status() })
        runCurrent()
        channel.statusGate!!.complete(Unit)
        assertTrue(reads.awaitAll().all { it.getOrNull()!!.installed })
        assertEquals(listOf("status"), channel.methods)

        channel.calls.clear()
        channel.statusGate = CompletableDeferred()
        val before = async { first.status() }
        runCurrent()
        first.startServer("true")
        val after = async { first.status() }
        runCurrent()
        channel.statusGate!!.complete(Unit)
        listOf(before, after).awaitAll()
        assertEquals(listOf("status", "startServer", "status"), channel.methods)
    }

    @Test
    fun `B-LINUX-020 status reads the contract map`() = runTest {
        channel.answerValue(
            mapOf(
                "installed" to true,
                "phase" to "ready",
                "message" to null,
                "serverRunning" to true,
                "serverPort" to 4097,
                "abi" to "arm64-v8a",
                "bytesUsed" to 734003200,
            ),
        )
        val status = linux.status().getOrNull()!!
        assertEquals("status", channel.calls.single().method)
        assertTrue(status.installed)
        assertEquals(RuntimePhase.Ready, status.phase)
        assertNull(status.message)
        assertTrue(status.serverRunning)
        assertEquals(4097, status.serverPort)
        assertEquals("arm64-v8a", status.abi)
        assertEquals(734003200L, status.bytesUsed)
    }

    @Test
    fun `B-LINUX-020 an unknown phase and missing fields read as idle`() = runTest {
        channel.answerValue(mapOf("phase" to "something-new"))
        val status = linux.status().getOrNull()!!
        assertFalse(status.installed)
        assertEquals(RuntimePhase.Idle, status.phase)
        assertFalse(status.serverRunning)
        assertNull(status.bytesUsed)
        assertEquals(RestorePhase.Idle, status.restorePhase)
        assertNull(status.restoreReason)
    }

    @Test
    fun `B-LINUX-020 restoration status exposes fixed state and timeout reason`() = runTest {
        channel.answerValue(mapOf("restorePhase" to "unavailable", "restoreReason" to "systemTimeout"))
        val status = linux.status().getOrNull()!!
        assertEquals(RestorePhase.Unavailable, status.restorePhase)
        assertEquals(RestoreReason.SystemTimeout, status.restoreReason)
    }

    @Test
    fun `B-LINUX-020 unknown restoration status stays unavailable without raw copy`() {
        val status = RuntimeStatusMap.parse(mapOf("restorePhase" to "unexpected", "restoreReason" to "private native error"))
        assertEquals(RestorePhase.Unavailable, status.restorePhase)
        assertEquals(RestoreReason.OwnershipUnknown, status.restoreReason)
        assertFalse(status.toString().contains("private native error"))
    }

    @Test
    fun `B-LINUX-021 an authored start passes only canonical restoration metadata`() = runTest {
        linux.startServer("serve", restoreRecipe = RestoreRecipe(ProfileId("phone-profile")))
        assertEquals("startServer", channel.calls.single().method)
        assertEquals(
            mapOf(
                "script" to "serve",
                "port" to 4097,
                "restoreRecipe" to mapOf("version" to 1, "profileId" to "phone-profile", "runtime" to "openCode2"),
            ),
            channel.calls.single().arguments,
        )
    }

    @Test
    fun `B-LINUX-021 restoration start failures give the safe problem and its way forward`() = runTest {
        channel.answerError("native_internal_error")
        val failure = linux.startServer("serve", restoreRecipe = RestoreRecipe(ProfileId("phone-profile"))).failure()
        // :ui:words says "The phone server could not start. Open setup and try again." for this problem.
        assertEquals(Failure.Runtime(RuntimeProblem.ServerStartUnavailable), failure)
    }

    @Test
    fun `B-LINUX-021 a start failure keeps the phone's details`() = runTest {
        channel.answerError("builtin_linux", details = "IllegalStateException: Check failed.")
        val failure = linux.startServer("serve").failure()
        assertEquals("IllegalStateException: Check failed.", failure.details?.text)
        channel.answerError("server_start_unavailable", details = "IllegalStateException: Check failed.")
        val recipeFailure = linux.startServer("serve", restoreRecipe = RestoreRecipe(ProfileId("p"))).failure()
        assertEquals("IllegalStateException: Check failed.", recipeFailure.details?.text)
    }

    @Test
    fun `B-LINUX-021 a platform error becomes a fixed problem with the native code read, never its message`() = runTest {
        channel.answerError("download_failed", details = "Could not download Ubuntu: no network")
        val failure = linux.installUbuntu().failure()
        // The words come from :ui:words by problem; the phone's text stays for Details only.
        assertEquals(Failure.Runtime(RuntimeProblem.Generic, redacted("Could not download Ubuntu: no network")), failure)
        channel.answerError("not_installed")
        assertEquals(Failure.Runtime(RuntimeProblem.NotInstalled), linux.installUbuntu().failure())
        channel.answerError("work_lease_invalid")
        assertEquals(Failure.Runtime(RuntimeProblem.WorkLeaseInvalid), linux.installUbuntu().failure())
    }

    @Test
    fun `B-LINUX-021 recovery map errors expose only the safe problem`() = runTest {
        channel.answerError("raw_native", details = "Raw JSON parser detail")
        val phone = ProfileId("phone")
        val operations: List<suspend () -> Outcome<*>> =
            listOf(
                { linux.stageServerRecovery(phone, mapOf("version" to 1)) },
                { linux.bindServerRecovery(phone, enabled = true) },
                { linux.serverRecoveryBudget(phone) },
                { linux.updateServerRecoveryReceipt(phone, emptyMap()) },
                { linux.confirmManualServerStart(phone) },
                { linux.confirmServerPresence(phone) },
            )
        for (operation in operations) {
            assertEquals(Failure.Runtime(RuntimeProblem.RecoveryUnavailable, details = null), operation().failure())
        }
        channel.answerValue(null)
        assertEquals(Failure.Runtime(RuntimeProblem.RecoveryUnavailable), linux.serverRecoveryBudget(phone).failure())
    }

    @Test
    fun `B-LINUX-021 recovery receipt list errors expose only the safe problem`() = runTest {
        channel.answerError("raw_native", details = "Raw JSON parser detail")
        val phone = ProfileId("phone")
        assertEquals(Failure.Runtime(RuntimeProblem.RecoveryUnavailable), linux.serverRecoveryReceipts(phone).failure())
        channel.answerValue(listOf("malformed"))
        assertEquals(Failure.Runtime(RuntimeProblem.RecoveryUnavailable), linux.serverRecoveryReceipts(phone).failure())
        channel.answerValue(listOf(mapOf("eventId" to "e1")))
        assertEquals(listOf(mapOf("eventId" to "e1")), linux.serverRecoveryReceipts(phone).getOrNull())
    }

    @Test
    fun `B-LINUX-021 a build without the channel says so`() = runTest {
        channel.answer = { ChannelReply.Missing }
        assertEquals(Failure.Unavailable(BuiltinLinuxFeature), linux.status().failure())
        assertEquals(Failure.Unavailable(BuiltinLinuxFeature), linux.installUbuntu().failure())
    }

    @Test
    fun `B-LINUX-022 run sends the script and timeout and reads the result`() = runTest {
        channel.answerValue(mapOf("exitCode" to 3, "output" to "boom"))
        val result = linux.run("echo hi", timeout = 5.minutes).getOrNull()!!
        assertEquals("run", channel.calls.single().method)
        assertEquals(mapOf("script" to "echo hi", "timeoutSeconds" to 300L), channel.calls.single().arguments)
        assertEquals(3, result.exitCode)
        assertEquals("boom", result.output)
        assertFalse(result.ok)
    }

    @Test
    fun `B-LINUX-022 every other method speaks the contract`() = runTest {
        var toolsChanged = 0
        val bridge = BuiltinLinuxBridge(channel, supported = true, onSetupToolsChanged = { toolsChanged++ })
        channel.answer = { ChannelReply.Value(if (it.method == "serverLog") "line\n" else null) }
        bridge.installUbuntu()
        bridge.startServer("serve", port = 4097)
        bridge.stopServer()
        assertEquals("line\n", bridge.serverLog(tailBytes = 100).getOrNull())
        bridge.uninstall()
        assertEquals(1, toolsChanged)
        assertEquals(listOf("installUbuntu", "startServer", "stopServer", "serverLog", "removeRuntime"), channel.methods)
        assertEquals(mapOf("script" to "serve", "port" to 4097), channel.calls[1].arguments)
        assertEquals(mapOf("tailBytes" to 100), channel.calls[3].arguments)
        assertEquals(mapOf("alsoDeleteProjects" to false), channel.calls[4].arguments)
    }

    @Test
    fun `B-LINUX-022 removal of projects needs the typed app name and a failed removal is fixed copy`() = runTest {
        var toolsChanged = 0
        val bridge = BuiltinLinuxBridge(channel, supported = true, onSetupToolsChanged = { toolsChanged++ })
        assertEquals(
            Failure.Runtime(RuntimeProblem.ConfirmationRequired),
            bridge.remove(alsoDeleteProjects = true, confirmationName = "opencode").failure(),
        )
        assertTrue(channel.calls.isEmpty())
        bridge.remove(alsoDeleteProjects = true, confirmationName = "OpenCode")
        assertEquals(mapOf("alsoDeleteProjects" to true, "confirmationName" to "OpenCode"), channel.calls.single().arguments)
        channel.answerError("io_error", details = "/data/user/0/private/path")
        assertEquals(Failure.Runtime(RuntimeProblem.RemovalFailed), bridge.remove().failure())
        assertEquals(2, toolsChanged)
    }

    @Test
    fun `B-LINUX-022 an unsupported runner reads absent status and dispatches nothing`() = runTest {
        val desktop = BuiltinLinuxBridge(channel, supported = false)
        assertEquals(RuntimeStatusMap.Absent, desktop.status().getOrNull())
        assertEquals(Failure.Unavailable(BuiltinLinuxFeature), desktop.run("true").failure())
        assertTrue(channel.calls.isEmpty())
    }
}
