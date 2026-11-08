package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.SignInWhy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * B-190: Performance traces and the flow events (Flutter `perf_telemetry_test.dart`, `health_telemetry_test.dart` "the
 * events fire where the app does the thing"): fixed names, fixed attribute words, the `duration_ms` metric, and the
 * words each flow maps to.
 */
class HealthFlowTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private fun TestScope.live(vendor: RecordingVendor, usage: Boolean = true, crashes: Boolean = true): HealthFlow {
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(usage, crashes), NoStore, MemoryTelemetryReportStore())
        AppTelemetry.install(service)
        return HealthFlow()
    }

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    @Test
    fun `B-190-020 the trace names are the contract's, and nothing else`() {
        assertEquals(
            setOf(
                "conn_connect", "send_first_reply", "agent_start", "app_cold_start", "linux_setup_download",
                "linux_setup_unpack", "linux_setup_essentials", "linux_setup_node", "linux_setup_opencode",
                "linux_setup_paseo", "linux_setup_agents", "linux_setup_done",
            ),
            PerfSchema.names,
        )
        assertEquals("duration_ms", PerfSchema.DURATION_METRIC)
        for (name in PerfSchema.names) {
            assertTrue(Regex("^[a-z][a-z0-9_]{0,39}$").matches(name), name)
            PerfSchema.allowed(name)!!.values.flatten().forEach { assertTrue(Regex("^[a-z0-9_]+$").matches(it), it) }
        }
    }

    @Test
    fun `B-190-021 unknown trace names and unknown attribute names and values are dropped`() = runTest {
        val vendor = RecordingVendor()
        val s = TelemetryService(vendor, available = true, scope = this)
        s.start(TelemetryConsent(usage = true), NoStore, MemoryTelemetryReportStore())
        s.performanceTrace("http_get", 10)
        s.performanceTrace("linux_setup_secret-project", 10)
        s.performanceTrace(
            PerfSchema.CONN_CONNECT,
            -5,
            mapOf("server_kind" to "laptop.example.com", "result" to "ok", "url" to "https://x/y", "agent" to "codex"),
        )
        s.performanceTrace(PerfSchema.APP_COLD_START, 900, mapOf("screen" to "chat"))
        assertEquals(
            listOf(
                Triple("conn_connect", mapOf("result" to "ok"), 0L),
                Triple("app_cold_start", emptyMap<String, String>(), 900L),
            ),
            vendor.traces,
        )
    }

    @Test
    fun `B-190-022 nothing is traced without usage`() = runTest {
        val vendor = RecordingVendor()
        live(vendor, usage = false).connAttempt("phone", ok = true, elapsedMs = 300)
        assertTrue(vendor.traces.isEmpty() && vendor.events.isEmpty())
        assertEquals(listOf("conn.connect"), vendor.crumbs, "the breadcrumb is crash-side")
    }

    @Test
    fun `B-190-023 each flow produces its event and its trace with fixed attributes`() = runTest {
        val vendor = RecordingVendor()
        val flow = live(vendor)
        flow.connAttempt("computer", ok = false, elapsedMs = 12_000, failure = Failure.Network(NetworkWhy.Dns))
        flow.sendResult("ok", firstReplyMs = 2_500, serverKind = "phone")
        flow.sendResult("fail", failure = Failure.Rejected(413))
        flow.sendResult("queued")
        flow.linuxSetupStep("node", ok = false, elapsedMs = 70_000, exit = 132, compatMode = "armcap", freeGigabytes = 9.5)
        flow.agentStart("claude", ok = true, elapsedMs = 4_000)
        flow.agentStart("my-own-agent", ok = false, failKind = "needs_sign_in")
        assertEquals(
            listOf(
                Triple("conn_connect", mapOf("server_kind" to "computer", "result" to "fail"), 12_000L),
                Triple("send_first_reply", mapOf("server_kind" to "phone"), 2_500L),
                Triple("linux_setup_node", mapOf("step" to "node", "result" to "fail"), 70_000L),
                Triple("agent_start", mapOf("agent" to "claude_code", "result" to "ok"), 4_000L),
            ),
            vendor.traces,
        )
        assertEquals(
            listOf(
                "conn_attempt" to
                    mapOf("server_kind" to "computer", "result" to "fail", "fail_kind" to "dns", "ms_range" to ">10000"),
                "send_result" to mapOf("result" to "ok", "first_reply_range" to "2-10s"),
                "send_result" to mapOf("result" to "fail", "fail_kind" to "too_large", "first_reply_range" to "none"),
                "send_result" to mapOf("result" to "queued", "first_reply_range" to "none"),
                "linux_setup_step" to
                    mapOf(
                        "step" to "node",
                        "result" to "fail",
                        "ms_range" to "60000-300000",
                        "exit_kind" to "signal",
                        "signal" to "SIGILL",
                        "compat_mode" to "armcap",
                        "free_space_range" to "8-32",
                    ),
                "agent_start" to mapOf("agent" to "claude_code", "result" to "ok"),
                "agent_start" to mapOf("agent" to "other", "result" to "fail", "fail_kind" to "needs_sign_in"),
            ),
            vendor.events,
        )
        assertEquals(
            listOf(
                "conn.connect", "chat.send", "chat.send", "chat.send", "linux.step:node", "linux.fail",
                "agent.start:claude_code", "agent.start:other", "agent.fail",
            ),
            vendor.crumbs,
        )
    }

    @Test
    fun `B-190-024 exit codes become exit kinds and signals`() {
        assertEquals("0", HealthFlow.exitKindOf(null, ok = true))
        assertEquals("other", HealthFlow.exitKindOf(null, ok = false))
        assertEquals("127", HealthFlow.exitKindOf(127, ok = false))
        assertEquals("signal", HealthFlow.exitKindOf(139, ok = false))
        assertEquals("other", HealthFlow.exitKindOf(2, ok = false))
        assertEquals(listOf("SIGSEGV", "SIGBUS", "SIGKILL", "other", "none"), listOf(139, 135, 137, 143, 1).map(HealthFlow::signalOfExit))
    }

    @Test
    fun `B-190-025 failures map to the contract's fail kinds, never their text`() {
        assertEquals("timeout", failKindOf(Failure.TimedOut()))
        assertEquals("tls", failKindOf(Failure.Certificate(io.github.eslamasabry.oc.core.common.CertificateWhy.Untrusted)))
        assertEquals("auth", failKindOf(Failure.SignIn(SignInWhy.PasswordRejected)))
        assertEquals("auth", failKindOf(Failure.Rejected(401)))
        assertEquals("protocol", failKindOf(Failure.Rejected(404)))
        assertEquals("rejected", failKindOf(Failure.Rejected(404), forSend = true))
        assertEquals("server_5xx", failKindOf(Failure.Server(502)))
        assertEquals("refused", failKindOf(Failure.Network(NetworkWhy.Refused)))
        assertEquals("offline", failKindOf(Failure.Network(NetworkWhy.Offline)))
        assertEquals("other", failKindOf(null))
        val every = HealthWords.failKinds + HealthWords.sendOnlyFailKinds
        listOf(Failure.Unknown(), Failure.Device(), Failure.Busy(io.github.eslamasabry.oc.core.common.BusyKind.Starting)).forEach {
            assertTrue(failKindOf(it) in every)
        }
    }

    @Test
    fun `B-190-026 setup components map to the contract's step words (Flutter G table)`() = runTest {
        assertEquals(
            listOf("unpack", "essentials", "essentials", "node", "opencode", "agents", null, null),
            listOf("linux", "essentials", "python", "node", "opencode", "aiteam", "voice", "start").map(HealthFlow::linuxStepOf),
        )
        val vendor = RecordingVendor()
        val flow = live(vendor)
        flow.setupTiming("setup.component", mapOf("id" to "python"), 30_000, failed = false)
        flow.setupTiming("setup.component", mapOf("id" to "voice"), 30_000, failed = false)
        flow.setupTiming("setup.check", mapOf("components" to "3"), 10, failed = false)
        flow.setupTiming("setup.job", mapOf("state" to "done"), 400_000, failed = false)
        flow.setupTiming("setup.job", mapOf("state" to "failed"), 400_000, failed = true)
        assertEquals(listOf("linux_setup_essentials", "linux_setup_done"), vendor.traces.map { it.first })
    }

    @Test
    fun `B-190-027 an accepted send counts its first reply, or none when the turn ends without one`() = runTest {
        val vendor = RecordingVendor()
        live(vendor)
        var now = 1_000L
        val meter = FirstReplyMeter({ now })
        meter.sent("ses_1", onPhone = true)
        meter.accepted("ses_1", error = false)
        meter.part("ses_1", "msg_user", "text") // the person's own echo is not a reply
        meter.message("ses_1", "msg_a", assistant = true)
        now += 3_200
        meter.part("ses_1", "msg_a", "text")
        meter.delta("ses_1") // only the first one counts
        meter.ended("ses_2") // an unknown session changes nothing
        meter.sent("ses_3", onPhone = false)
        meter.ended("ses_3") // a leftover idle before the turn began
        meter.busy("ses_3")
        meter.ended("ses_3")
        meter.sent("ses_4", onPhone = false)
        meter.accepted("ses_4", error = true) // refused: counted where it was refused
        meter.delta("ses_4")
        assertEquals(
            listOf(
                "send_result" to mapOf("result" to "ok", "first_reply_range" to "2-10s"),
                "send_result" to mapOf("result" to "ok", "first_reply_range" to "none"),
            ),
            vendor.events,
        )
        assertEquals(listOf(Triple("send_first_reply", mapOf("server_kind" to "phone"), 3_200L)), vendor.traces)
    }

    @Test
    fun `B-190-028 agent words and update results`() = runTest {
        assertEquals(
            listOf("claude_code", "claude_code", "codex", "gemini", "other"),
            listOf("claude", "claude-code", "codex", "GEMINI", "acme").map(HealthFlow::agentWord),
        )
        val vendor = RecordingVendor()
        val flow = live(vendor)
        flow.updateResult("in_app", "installed")
        flow.updateResult("patch", "failed", "checksum")
        flow.bgServiceEnd("os_limit", batteryExempt = true)
        flow.connDrop("phone", "background", recovered = true)
        flow.turnStall("waiting_tool", 6 * 60_000)
        flow.queueFlush(sent = 3, failed = 0)
        assertEquals(
            listOf(
                "update_result" to mapOf("kind" to "in_app", "result" to "installed"),
                "update_result" to mapOf("kind" to "patch", "result" to "failed", "fail_kind" to "checksum"),
                "bg_service_end" to mapOf("reason" to "os_limit", "battery_exempt" to "yes"),
                "conn_drop" to mapOf("server_kind" to "phone", "reason" to "background", "recovered" to "yes"),
                "turn_stall" to mapOf("stage" to "waiting_tool", "minutes_range" to "5-15"),
                "queue_flush" to mapOf("sent" to "2-5", "failed" to "0"),
            ),
            vendor.events,
        )
        assertEquals(listOf("update.install", "update.fail", "conn.reconnect", "chat.tool_wait"), vendor.crumbs)
        assertNull(HealthFlow.linuxStepOf(null))
    }
}
