package io.github.eslamasabry.oc.engine.termuxhost

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** test/termux_clarity_test.dart "one typed cause" and "the permission request" (the widget parts are UI, see report). */
class TermuxClarityTest {
    /** A phone with Termux and the app's OpenCode in it, as Android and Termux answer `oc/termux`. */
    private class Phone {
        var installed = true
        var serviceAvailable = true
        var granted = false
        var otherAppsAllowed = true
        var awake = true
        var accessAnswer = "granted"
        val status = "phase=ready\nport=4096\nruntime=opencode1\nversion=1.18.32\npid=12\n"
        val methods = mutableListOf<String>()

        fun handle(method: String, arguments: Map<String, Any?>?): ChannelReply {
            methods += method
            return when (method) {
                "getCapabilities" ->
                    ChannelReply.Value(
                        mapOf(
                            "installed" to installed, "version" to "0.118", "serviceAvailable" to serviceAvailable,
                            "protocolSupported" to true, "permissionGranted" to granted,
                        ),
                    )
                "requestRunCommandAccess" -> {
                    if (accessAnswer == "granted") granted = true
                    ChannelReply.Value(accessAnswer)
                }
                "openAppSettings", "openTermux" -> ChannelReply.Value(true)
                "runInTermux" ->
                    when {
                        !granted -> ChannelReply.PlatformError("permission_denied", "x")
                        !awake -> ChannelReply.PlatformError("command_timeout", "x")
                        !otherAppsAllowed ->
                            ChannelReply.Value(
                                mapOf(
                                    "stdout" to "", "stderr" to "", "exitCode" to -1, "err" to 2,
                                    "errorMessage" to
                                        "OpenCode requires `allow-external-apps` property to be set to `true` in " +
                                        "`~/.termux/termux.properties` file.",
                                ),
                            )
                        (arguments?.get("script") as String).contains("server.password") ->
                            ChannelReply.Value(mapOf("stdout" to "the-password-setup-wrote", "exitCode" to 0))
                        else -> ChannelReply.Value(mapOf("stdout" to status, "stderr" to "", "exitCode" to 0))
                    }
                else -> ChannelReply.Value(null)
            }
        }
    }

    private val phone = Phone()
    private val bridge = TermuxBridge(true, FakeTermuxChannel { m, a -> phone.handle(m, a) }, Redactor(), RecordingWait())
    private val access = TermuxAccess(bridge)
    private var health: TermuxHealth? = TermuxHealth(ok = false, needsPassword = true)
    private val detector = TermuxRunningServerDetector(bridge, access) { _, _, _ -> health }

    private fun caps(installed: Boolean = true, service: Boolean = true, granted: Boolean = true) =
        TermuxCapabilities(installed, "0.118", service, protocolSupported = true, permissionGranted = granted)

    @Test
    fun `B-LINUX-229 capabilities name the cause before any command`() {
        assertEquals(TermuxProblem.NotInstalled, TermuxProblem.ofCapabilities(caps(installed = false), false))
        assertEquals(TermuxProblem.Outdated, TermuxProblem.ofCapabilities(caps(service = false), false))
        assertEquals(TermuxProblem.AccessNeeded, TermuxProblem.ofCapabilities(caps(granted = false), false))
        assertEquals(TermuxProblem.AccessBlocked, TermuxProblem.ofCapabilities(caps(granted = false), true))
        assertNull(TermuxProblem.ofCapabilities(caps(), false))
        assertNull(TermuxProblem.ofCapabilities(TermuxCapabilities.Unavailable, false))
    }

    @Test
    fun `B-LINUX-229 bridge failures map to their cause`() {
        fun of(code: String, message: String = "") = TermuxProblem.ofError(TermuxBridgeError(message, code))
        assertEquals(TermuxProblem.AccessNeeded, of("permission_denied"))
        assertEquals(TermuxProblem.Outdated, of("service_unavailable"))
        assertEquals(TermuxProblem.Asleep, of("command_timeout"))
        assertEquals(TermuxProblem.Asleep, of("missing_result"))
        assertEquals(TermuxProblem.OtherAppsOff, of("command_failed", "needs `allow-external-apps` set to `true`"))
        assertEquals(TermuxProblem.Unknown, of("command_failed", "exit 1"))
    }

    @Test
    fun `B-LINUX-229 the observation carries the cause, before any command runs`() = runTest {
        // Storage reset: Android took the permission back; OpenCode still answers on the phone.
        var seen = detector.detect()
        assertEquals(TermuxRunningServerState.Denied, seen.state)
        assertEquals(TermuxProblem.AccessNeeded, seen.problem)
        assertTrue(seen.heardOnPhone)
        assertEquals(listOf("getCapabilities"), phone.methods)

        phone.granted = true
        phone.otherAppsAllowed = false
        seen = detector.detect()
        assertEquals(TermuxProblem.OtherAppsOff, seen.problem)

        phone.otherAppsAllowed = true
        phone.awake = false
        seen = detector.detect()
        assertEquals(TermuxProblem.Asleep, seen.problem)

        phone.awake = true
        health = null
        seen = detector.detect()
        assertEquals(TermuxProblem.NotAnswering, seen.problem)
        assertEquals(TermuxRuntime.OpenCode1, seen.runtime)

        phone.installed = false
        seen = detector.detect()
        assertEquals(TermuxRunningServerState.Absent, seen.state)
        assertEquals(TermuxProblem.NotInstalled, seen.problem)
    }

    @Test
    fun `B-LINUX-229 every cause has plain words and one named fix, retry only where it can help`() {
        for (locale in TermuxWordsLocale.entries) {
            for (problem in TermuxProblem.entries) {
                val words = TermuxProblemCopy.words(problem, runtime = "OpenCode 1", locale = locale)
                assertTrue(words.line.isNotEmpty(), "$problem")
                assertFalse(words.line.contains("Exception"), "$problem")
                val retry = words.action == TermuxProblemCopy.RETRY || words.action == "إعادة المحاولة"
                assertEquals(problem.retryHelps, retry, "$problem")
            }
        }
        assertEquals("Allow access to Termux", TermuxProblemCopy.words(TermuxProblem.AccessNeeded).action)
        assertEquals("Allow other apps in Termux", TermuxProblemCopy.words(TermuxProblem.OtherAppsOff).action)
        assertEquals("Open Termux", TermuxProblemCopy.words(TermuxProblem.Asleep).action)
        assertEquals(
            "Restart OpenCode 1 in Termux",
            TermuxProblemCopy.words(TermuxProblem.NotAnswering, runtime = "OpenCode 1").action,
        )
        assertEquals("Get Termux", TermuxProblemCopy.words(TermuxProblem.NotInstalled).action)
        assertTrue(TermuxProblemCopy.words(TermuxProblem.AccessNeeded, heard = true).line.startsWith("OpenCode is running"))
    }

    @Test
    fun `B-LINUX-229 the permission request asks Android directly and remembers a permanent no`() = runTest {
        assertEquals(TermuxAccessAnswer.Granted, access.request())
        assertEquals(listOf("requestRunCommandAccess"), phone.methods)
        phone.accessAnswer = "permanentlyDenied"
        phone.granted = false
        assertEquals(TermuxAccessAnswer.PermanentlyDenied, access.request())
        assertTrue(access.blocked)
        // The remembered no changes the cause the capabilities show.
        val seen = detector.detect()
        assertEquals(TermuxProblem.AccessBlocked, seen.problem)
    }
}
