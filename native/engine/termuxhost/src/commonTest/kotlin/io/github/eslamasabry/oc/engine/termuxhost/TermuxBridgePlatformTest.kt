package io.github.eslamasabry.oc.engine.termuxhost

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** test/termux_bridge_platform_test.dart and test/termux_running_server_test.dart:209. */
class TermuxBridgePlatformTest {
    private val channel = FakeTermuxChannel()
    private val wait = RecordingWait()

    private fun bridge(supported: Boolean) = TermuxBridge(supported, channel, Redactor(), wait)

    private fun killed(message: String) =
        ChannelReply.Value(mapOf("stdout" to "", "stderr" to "", "exitCode" to 2, "err" to 2, "errorMessage" to message))

    @Test
    fun `B-LINUX-228 without a bridge capabilities report unavailable instead of throwing`() = runTest {
        val caps = bridge(supported = false).capabilities()
        assertFalse(caps.platformSupported)
        assertFalse(caps.installed)
        assertFalse(caps.serviceAvailable)
        assertFalse(caps.protocolSupported)
        assertFalse(caps.permissionGranted)
        assertNull(caps.version)
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-228 without a bridge the boolean entry points answer false`() = runTest {
        val b = bridge(supported = false)
        assertFalse(b.requestPermission())
        assertFalse(b.openTermux())
        assertFalse(b.openAppSettings())
        assertFalse(b.supported)
    }

    @Test
    fun `B-LINUX-228 without a bridge run and every derived command fail as the handled unsupported error`() = runTest {
        val b = bridge(supported = false)
        assertEquals(TermuxBridge.UNSUPPORTED_PLATFORM, b.run("echo hi").errorOrNull()?.code)
        for (answer in listOf(b.verifyBridge(), b.ensureWakeLock(), b.status(), b.setupSnapshot(), b.diagnostics())) {
            assertEquals(TermuxBridge.UNSUPPORTED_PLATFORM, answer.errorOrNull()?.code)
        }
        assertTrue(channel.calls.isEmpty())
    }

    @Test
    fun `B-LINUX-228 on Android the channel is used for capabilities`() = runTest {
        channel.handler = { method, _ ->
            assertEquals("getCapabilities", method)
            ChannelReply.Value(
                mapOf(
                    "installed" to true, "version" to "0.118.0", "serviceAvailable" to true,
                    "protocolSupported" to true, "permissionGranted" to true,
                ),
            )
        }
        val b = bridge(supported = true)
        assertTrue(b.supported)
        val caps = b.capabilities()
        assertTrue(caps.platformSupported)
        assertTrue(caps.installed)
        assertEquals("0.118.0", caps.version)
        assertTrue(caps.permissionGranted)
    }

    @Test
    fun `B-LINUX-228 a missing handler is a bare device and a handled bridge error, not a crash`() = runTest {
        val b = bridge(supported = true)
        assertFalse(b.capabilities().installed)
        assertFalse(b.openTermux())
        assertIs<TermuxAnswer.Failed>(b.run("echo hi"))
    }

    @Test
    fun `B-LINUX-228 an execution killed by the Termux service shutdown is retried once`() = runTest {
        var calls = 0
        channel.handler = { method, _ ->
            assertEquals("runInTermux", method)
            calls++
            if (calls == 1) {
                killed(
                    "Error Code: `2`\nError Message:\n```\nSending SIGKILL to process on user request or because " +
                        "android is killing the execution service\n```",
                )
            } else {
                ChannelReply.Value(
                    mapOf("stdout" to "aiteam-started:41\n", "stderr" to "", "exitCode" to 0, "err" to -1, "errorMessage" to ""),
                )
            }
        }
        val result = bridge(supported = true).run("echo hi")
        assertEquals(2, calls)
        assertEquals("aiteam-started:41\n", result.valueOrNull()?.stdout)
        assertEquals(listOf(1_500L), wait.pauses)
    }

    @Test
    fun `B-LINUX-228 a second service-shutdown kill is reported, not retried again`() = runTest {
        var calls = 0
        channel.handler = { _, _ ->
            calls++
            killed("Sending SIGKILL to process on user request or because android is killing the execution service")
        }
        val error = bridge(supported = true).run("echo hi").errorOrNull()
        assertTrue(error!!.message.contains("killing the execution service"))
        assertEquals(2, calls)
    }

    @Test
    fun `B-LINUX-228 a platform error still surfaces its own code and message`() = runTest {
        channel.handler = { _, _ -> ChannelReply.PlatformError("permission_denied", "RUN_COMMAND permission is not granted.") }
        val error = bridge(supported = true).run("echo hi").errorOrNull()
        assertEquals("permission_denied", error?.code)
        assertEquals("RUN_COMMAND permission is not granted.", error?.message)
    }

    @Test
    fun `B-LINUX-228 web, iOS and desktop never touch the native bridge or the probe`() = runTest {
        val probes = mutableListOf<String>()
        val b = bridge(supported = false)
        val detector = TermuxRunningServerDetector(b, TermuxAccess(b)) { url, _, _ -> probes += url; null }
        assertEquals(TermuxRunningServerState.Unsupported, detector.detect().state)
        assertTrue(channel.calls.isEmpty())
        assertTrue(probes.isEmpty())
    }
}
