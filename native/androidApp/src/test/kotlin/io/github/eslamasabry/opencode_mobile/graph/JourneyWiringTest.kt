package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.engine.updates.AppRelease
import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.analytics.health.ApprovalMeter
import io.github.eslamasabry.oc.engine.analytics.health.ChatJourney
import io.github.eslamasabry.oc.engine.analytics.health.HealthFlow
import io.github.eslamasabry.oc.engine.analytics.health.HealthWords
import io.github.eslamasabry.oc.engine.analytics.health.NoTelemetry
import io.github.eslamasabry.oc.engine.analytics.health.Telemetry
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import io.github.eslamasabry.oc.engine.analytics.health.SetupJobState
import io.github.eslamasabry.oc.engine.analytics.health.SetupStepState
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.StartStepKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app-side half of the journey analytics: setup errors as fail kinds, progress as funnel steps, the approval feed. */
class JourneyWiringTest {
    private val everyError: List<SetupError> =
        listOf(
            SetupError.OwnershipUnknown, SetupError.StatusUnreadable, SetupError.HostMismatch, SetupError.ToolsUncheckable,
            SetupError.PersistenceFailure, SetupError.PackageRepair, SetupError.CannotStart, SetupError.PortBusy(4096),
            SetupError.OpenCodeNoProgram, SetupError.OpenCodeWontRun, SetupError.OpenCodeNoStart, SetupError.NodeCannotRun,
            SetupError.Interrupted, SetupError.NoInternet, SetupError.NoSpace("Node"), SetupError.Offline("Node"),
            SetupError.Checksum("Node"), SetupError.Install("Node"), SetupError.StepTimedOut("Node"), SetupError.HostRefused, SetupError.Unexpected,
        ) + StartStepKind.entries.map { SetupError.StartStepFailed(it) }

    @Test
    fun `B-SETUP-012 start kinds and busy port map to their closed fail kinds`() {
        val words = StartStepKind.entries.associateWith { setupFailKindOf(SetupError.StartStepFailed(it)) }
        assertEquals(
            mapOf(
                StartStepKind.TimedOut to "timeout",
                StartStepKind.Ownership to "permission",
                StartStepKind.Exited to "exited",
                StartStepKind.Refused to "refused",
                StartStepKind.Password to "password",
                StartStepKind.Withdrawn to "withdrawn",
            ),
            words.filterValues { it != "unknown" },
        )
        assertEquals("port_busy", setupFailKindOf(SetupError.PortBusy(4096)))
    }

    @Test
    fun `B-JOURNEY-030 every setup error becomes one word of the closed fail kind list, never its text`() {
        for (error in everyError) assertTrue(setupFailKindOf(error) in HealthWords.setupFailKinds, error.toString())
        assertEquals("network", setupFailKindOf(SetupError.Offline("secret title")))
        assertEquals("storage", setupFailKindOf(SetupError.NoSpace("x")))
        assertEquals("checksum", setupFailKindOf(SetupError.Checksum("x")))
        assertEquals("killed_by_system", setupFailKindOf(SetupError.Interrupted))
        assertEquals("timeout", setupFailKindOf(SetupError.StepTimedOut("x")))
        assertEquals("apt", setupFailKindOf(SetupError.Install("x")))
        assertEquals("permission", setupFailKindOf(SetupError.HostRefused))
        assertEquals("unknown", setupFailKindOf(null))
    }

    @Test
    fun `B-JOURNEY-031 setup progress becomes funnel steps, a failed one carrying its fail kind`() {
        val progress =
            SetupProgress(
                state = SetupState.Failed,
                components =
                    listOf(
                        ComponentProgress("linux", ComponentState.Done),
                        ComponentProgress("node", ComponentState.Failed, error = SetupError.NoInternet),
                        ComponentProgress("opencode", ComponentState.Pending),
                    ),
                overall = 0.3,
                jobId = "job1",
                firstSetup = true,
            )
        assertEquals(SetupJobState.Failed, journeyJobState(progress.state))
        val steps = journeySteps(progress)
        assertEquals(listOf(SetupStepState.Done, SetupStepState.Failed, SetupStepState.Pending), steps.map { it.state })
        assertEquals(listOf(null, "network", null), steps.map { it.failKind })
    }

    private class Sink : ApprovalSink {
        val calls = mutableListOf<String>()

        override fun asked(id: String, sessionId: String, kind: String) {
            calls += "asked $id $kind"
        }

        override fun confirmed(id: String) {
            calls += "confirmed $id"
        }

        override fun answered(id: String, result: String) {
            calls += "answered $id $result"
        }

        override fun stopped(id: String) {
            calls += "stopped $id"
        }

        override fun gone(id: String) {
            calls += "gone $id"
        }
    }

    @Test
    fun `B-JOURNEY-032 the approval feed tells asked once, the confirmed answer's word once, and gone when the request leaves`() {
        val sink = Sink()
        val feed = ApprovalFeed(sink)
        feed.update(listOf(PendingFacts("r1", "s1", "run", null)))
        feed.update(listOf(PendingFacts("r1", "s1", "run", null)))
        feed.update(listOf(PendingFacts("r1", "s1", "run", "denied"), PendingFacts("r2", "s1", "edit", null)))
        feed.update(listOf(PendingFacts("r2", "s1", "edit", null)))
        feed.update(emptyList())
        assertEquals(
            listOf("asked r1 run", "confirmed r1", "asked r2 edit", "answered r1 denied", "gone r1", "gone r2"),
            sink.calls,
        )
    }

    // ---- stage 4: an approval answer counts only when the server confirmed it ----

    private class Recorder : Telemetry by NoTelemetry {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        override fun track(event: ProductEvent, props: Map<String, Any?>) {
            events += event.wire to props
        }

        fun results() = events.filter { it.first == "approval" }.map { it.second["result"] }
    }

    private class ApprovalRig(automatic: Set<String> = emptySet()) {
        val telemetry = Recorder()
        var now = 0L
        val journey = ChatJourney({ now }, HealthFlow { telemetry })
        val meter = ApprovalMeter({ now }, HealthFlow { telemetry }) { journey }
        val feed = ApprovalFeed(meter.asSink()) { it.id in automatic }

        fun show(vararg requests: PendingRequest) = feed.update(pendingFacts(requests.toList()))
    }

    private fun ask(id: String, phase: AnswerPhase = AnswerPhase.Waiting) =
        PendingRequest.Permission(
            PermissionRequest(
                id = RequestId(id), sessionId = SessionId("s1"), permission = "bash", patterns = persistentListOf(),
                metadata = JsonObject(emptyMap()), always = persistentListOf(), tool = null, canAlwaysAllow = true,
                message = null,
            ),
            phase,
        )

    private val once = RequestAnswer.Permission(PermissionReply.Once)

    @Test
    fun `B-JOURNEY-060 a held answer the person undid sends nothing, and the request leaving later is not an answer`() {
        val r = ApprovalRig()
        r.journey.sent("s1", onPhone = true)
        r.journey.busy("s1")
        r.show(ask("a1"))
        r.show(ask("a1", AnswerPhase.Held(once, untilMs = 3_000, token = 1)))
        r.show(ask("a1")) // undone inside the 3 s window
        assertEquals(emptyList(), r.telemetry.results(), "held and undone is no answer")
        r.journey.stopped("s1")
        r.show()
        assertEquals(listOf("stopped"), r.telemetry.results(), "it left because the run was stopped, not expired")
    }

    @Test
    fun `B-JOURNEY-061 a held answer counts once, when the server confirms it, with the wait to the confirmation`() {
        val r = ApprovalRig()
        r.show(ask("a1"))
        r.now += 4_000
        r.show(ask("a1", AnswerPhase.Held(once, untilMs = 7_000, token = 1)))
        assertEquals(emptyList(), r.telemetry.results(), "still undoable: nothing yet")
        r.now += 8_000
        r.show(ask("a1", AnswerPhase.Sent(once, sinceMs = 0)))
        r.now += 600_000
        r.show(ask("a1", AnswerPhase.Sent(once, sinceMs = 0)))
        r.show()
        assertEquals(listOf("allowed"), r.telemetry.results())
        assertEquals("10-30s", r.telemetry.events.single { it.first == "approval" }.second["ms_range"])
    }

    @Test
    fun `B-JOURNEY-062 a saved Auto answer is auto, never the person's allowed`() {
        val r = ApprovalRig(automatic = setOf("a1"))
        r.show(ask("a1"))
        r.show(ask("a1", AnswerPhase.Sent(once, sinceMs = 0)))
        r.show()
        assertEquals(listOf("auto"), r.telemetry.results())
        assertTrue("auto" in HealthWords.approvalResults && productValueIsSafe("auto"))
    }

    @Test
    fun `B-JOURNEY-063 Stop retires the request as stopped, an unanswered one in a running turn expires`() {
        val r = ApprovalRig()
        r.journey.sent("s1", onPhone = true)
        r.journey.busy("s1")
        r.show(ask("a1"))
        r.show(ask("a1", AnswerPhase.NoLongerNeeded))
        r.show()
        assertEquals(listOf("stopped"), r.telemetry.results())
        r.show(ask("a2"))
        r.show() // left with no answer while the run went on
        assertEquals(listOf("stopped", "expired"), r.telemetry.results())
        assertTrue("stopped" in HealthWords.approvalResults && productValueIsSafe("stopped"))
    }

    // ---- stage 5: declared events emitted at the boundary that knows ----

    private val release = AppRelease("v3.1.0+10060", "3.1.0", 10060, "", "", emptyList(), "{}")

    @Test
    fun `B-JOURNEY-064 update_offered once when the notice appears, declined once on Not now, failed once with its kind`() {
        val t = Recorder()
        val meter = UpdateMeter { HealthFlow { t } }
        meter.observe(AppUpdateState(phase = UpdatePhase.Checking))
        meter.observe(AppUpdateState(phase = UpdatePhase.Available, release = release))
        meter.observe(AppUpdateState(phase = UpdatePhase.Available, release = release, promptOpen = true))
        assertEquals(listOf("update_offered"), t.events.map { it.first }, "one offer per release, nothing about it sent")
        assertEquals(emptyMap(), t.events.single().second)
        meter.observe(AppUpdateState(phase = UpdatePhase.Available, release = release, notNow = true))
        meter.observe(AppUpdateState(phase = UpdatePhase.Available, release = release, notNow = true))
        meter.observe(AppUpdateState(phase = UpdatePhase.Downloading, release = release))
        meter.observe(AppUpdateState(phase = UpdatePhase.Failed, release = release, failure = UpdateFailure.Checksum))
        meter.observe(AppUpdateState(phase = UpdatePhase.Failed, release = release, failure = UpdateFailure.Checksum))
        assertEquals(
            listOf(
                "update_offered" to emptyMap<String, Any?>(),
                "update_result" to mapOf<String, Any?>("kind" to "in_app", "result" to "declined"),
                "update_result" to mapOf<String, Any?>("kind" to "in_app", "result" to "failed", "fail_kind" to "checksum"),
            ),
            t.events,
        )
        // A release the page shows while the notice steps aside was not offered by the notice.
        val quiet = Recorder()
        UpdateMeter { HealthFlow { quiet } }.observe(AppUpdateState(phase = UpdatePhase.Available, release = release, pageOpen = true))
        assertTrue(quiet.events.isEmpty())
    }

    @Test
    fun `B-JOURNEY-065 bg_service_end once per service run - os_limit on Android's timeout, other when a held service goes`() {
        val t = Recorder()
        val ends = BackgroundServiceEnd { HealthFlow { t } }
        ends.timedOut(batteryExempt = true)
        ends.destroyed(held = true, batteryExempt = true) // the same run ending: already counted
        ends.destroyed(held = false, batteryExempt = false) // a widget action alone: no background run
        ends.destroyed(held = true, batteryExempt = false)
        assertEquals(
            listOf(
                mapOf<String, Any?>("reason" to "os_limit", "battery_exempt" to "yes"),
                mapOf<String, Any?>("reason" to "other", "battery_exempt" to "no"),
            ),
            t.events.filter { it.first == "bg_service_end" }.map { it.second },
        )
    }

    @Test
    fun `B-JOURNEY-066 connect_computer_chosen is the new-server form opening, never editing a saved one`() {
        assertTrue(choseToConnectComputer(ServersRoute.Edit()))
        assertTrue(choseToConnectComputer(ServersRoute.Edit(newConversation = true)))
        assertTrue(!choseToConnectComputer(ServersRoute.Edit(ProfileId("1735000000000001"))))
        assertTrue(!choseToConnectComputer(ServersRoute.Home))
    }

    @Test
    fun `B-JOURNEY-033 a failed turn's own words become one closed word and are dropped`() {
        assertEquals("rate_limit", turnErrorWord(null, null, "Provider is busy, try again later"))
        assertEquals("provider_auth", turnErrorWord("Unknown", null, "Please sign in to continue"))
        assertEquals("context_overflow", turnErrorWord("ContextOverflow", null, null))
        assertEquals("server", turnErrorWord(null, null, null))
        val allowed = HealthWords.turnErrorKinds
        for (reason in listOf("sk-secret-123 at /home/me/app", "quota exceeded", "model gpt-x not found", null))
            assertTrue(turnErrorWord(null, null, reason) in allowed)
    }

    // ---- decision 20: where an approval was answered ----

    @Test
    fun `B-JOURNEY-111 an answer sent from home or a notification says so, a failed send takes the word back`() {
        val telemetry = Recorder()
        val meter = ApprovalMeter({ 0L }, HealthFlow { telemetry })
        io.github.eslamasabry.oc.engine.analytics.health.AppJourneys.install(ChatJourney({ 0L }, HealthFlow { telemetry }), meter)
        try {
            meter.asked("a1", "s1", "run")
            kotlinx.coroutines.runBlocking {
                withApprovalSurface("a1", "notification") { io.github.eslamasabry.oc.core.common.Outcome.Ok(Unit) }
            }
            meter.answered("a1", "allowed")
            assertEquals("notification", telemetry.events.last { it.first == "approval" }.second["surface"])
            meter.asked("a2", "s1", "edit")
            kotlinx.coroutines.runBlocking {
                withApprovalSurface("a2", "home") {
                    io.github.eslamasabry.oc.core.common.Outcome.Failed(
                        io.github.eslamasabry.oc.core.common.Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope)
                    )
                }
            }
            meter.answered("a2", "denied")
            assertEquals("chat", telemetry.events.last { it.first == "approval" }.second["surface"], "the send did not go from home")
            meter.asked("a3", "s1", "run")
            noteWidgetAnswer("a3", allowed = false)
            assertEquals("widget", telemetry.events.last { it.first == "approval" }.second["surface"])
            assertEquals("denied", telemetry.events.last { it.first == "approval" }.second["result"])
        } finally {
            io.github.eslamasabry.oc.engine.analytics.health.AppJourneys.reset()
        }
    }
}
