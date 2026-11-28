package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertFacts
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AlertShade
import io.github.eslamasabry.oc.android.notifications.AlertShown
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.android.notifications.ShadeTap
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.feature.settings.presentation.ServerAutomationsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest

/** B-620: end-to-end coordinator checks for finding alerts, pause via server API, and offline behaviour. */
class AutomationAlertsCoordinatorTest {
    private class FakeShade : AlertShade {
        val shown = mutableListOf<Pair<CodingAlert, AlertRoute.Post>>()
        val dismissedKeys = mutableListOf<String>()
        val dismissedGroups = mutableListOf<String>()

        override fun show(alert: CodingAlert, route: AlertRoute.Post): AlertShown {
            shown += alert to route
            return AlertShown.Shown
        }

        override fun showOpenOnly(tap: ShadeTap): AlertShown = AlertShown.Shown

        override fun dismiss(key: String) {
            dismissedKeys += key
        }

        override fun dismissGroup(group: String) {
            dismissedGroups += group
        }

        override fun dismissIfCurrent(tap: ShadeTap): Boolean {
            dismiss(tap.key)
            return true
        }

        override fun showOpenOnlyIfCurrent(tap: ShadeTap): AlertShown = AlertShown.Shown

        override fun isShowing(key: String): Boolean = false
    }

    private class MutableServerAutomations(vararg initial: ServerAutomation) : ServerAutomationsSource {
        val items = initial.associateBy { it.id }.toMutableMap()
        val pauseRequests = mutableListOf<Pair<ProfileId, String>>()
        var failPause: Failure? = null

        override suspend fun automations(profile: ProfileId): Outcome<ImmutableList<ServerAutomation>> =
            items.values.toList().toImmutableList().ok()

        override suspend fun pauseAutomation(profile: ProfileId, id: String): Outcome<ServerAutomation> {
            pauseRequests += profile to id
            val failure = failPause
            val current = items[id]
            // A failing server changes nothing, as a missing automation changes nothing.
            return when {
                failure != null -> Outcome.Failed(failure)
                current == null -> Outcome.Failed(Failure.NotFound())
                else -> current.copy(status = AutomationStatus.Paused).also { items[id] = it }.ok()
            }
        }
    }

    private val profile = ProfileId("p1")
    private val backgroundFacts = AlertFacts(
        liveModeActive = true,
        replyWatchActive = false,
        appVisible = false,
        permissionGranted = true,
        serverAlertsOn = true,
        quietHours = false,
        onWifi = true,
        wifiOnly = false,
    )

    private fun sample(
        id: String = "aut_1",
        status: AutomationStatus = AutomationStatus.Active,
        runCount: Int = 1,
        outcome: AutomationOutcome = AutomationOutcome.Fired,
    ) = ServerAutomation(
        id = id,
        name = "Nightly check",
        status = status,
        trigger = AutomationTrigger.Cron("0 3 * * *"),
        action = AutomationAction.Prompt("Check nightly builds"),
        runCount = runCount,
        lastRunAtMs = runCount * 1_000L,
        lastOutcome = outcome,
        sessionId = "ses_9",
    )

    /** The chat of the automation's runs, oldest first; the server pages it from its newest end. */
    private val chat = mutableListOf<MessageWithParts>()
    private var pagesRead = 0

    private fun coordinator(shade: FakeShade, server: MutableServerAutomations) =
        AutomationAlertsCoordinator(
            shade = shade,
            source = server,
            serverName = { "Home server" },
            alertFacts = { backgroundFacts },
            redact = { it },
            replies = { _, session, cursor -> page(session, cursor) },
        )

    /** One page of the chat as the server reads it: the newest [PAGE] messages, then older ones by the cursor. */
    private fun page(session: String, cursor: String?): ServerPage<MessageWithParts> {
        pagesRead++
        val all = chat.filter { it.info.sessionId == session }
        val end = cursor?.toInt() ?: all.size
        val start = maxOf(0, end - PAGE)
        return ServerPage(all.subList(start, end).toImmutableList(), start.takeIf { it > 0 }?.toString())
    }

    private fun AutomationAlertsCoordinator.onMessage(profile: ProfileId, message: MessageInfo) {
        onEvent(profile, GatewayEvent.MessageUpserted(message))
    }

    private fun AutomationAlertsCoordinator.onRunStarted(profile: ProfileId, session: String) {
        onEvent(profile, GatewayEvent.RunStarted(SessionId(session)))
    }

    private fun run(number: Int) = MessageAutomation(id = "aut_1", name = "Nightly check", run = number)

    /** One step of a long run: an assistant message that only calls a tool, with nothing to read. */
    private fun step(id: String) {
        val info = MessageInfo(id = id, sessionId = "ses_9", role = Role.Assistant)
        val tool = Part("prt_$id", id, sessionId = "ses_9", type = PartType.Tool, rawType = "tool")
        chat += MessageWithParts(info, persistentListOf(tool))
    }

    /** A notice the server wrote into the chat as a user message; returns it as the stream shows it. */
    private fun notice(id: String): MessageInfo {
        val info = MessageInfo(id = id, sessionId = "ses_9", role = Role.User)
        val part = Part("prt_$id", id, sessionId = "ses_9", type = PartType.Notice, rawType = "v2:notice")
        chat += MessageWithParts(info, persistentListOf(part))
        return info
    }

    /** The automation's prompt of run [number], as the stream shows it and as the chat keeps it. */
    private fun prompt(number: Int): MessageInfo =
        MessageInfo(id = "inb_$number", sessionId = "ses_9", role = Role.User, automation = run(number))
            .also { chat += MessageWithParts(it, persistentListOf(text(it.id, "Check nightly builds"))) }

    private fun reply(id: String, words: String) {
        val info = MessageInfo(id = id, sessionId = "ses_9", role = Role.Assistant)
        chat += MessageWithParts(info, persistentListOf(text(id, words)))
    }

    private fun text(message: String, words: String) =
        Part("prt_$message", message, sessionId = "ses_9", type = PartType.Text, rawType = "text", text = words)

    private val failure = RunOutcome.Failed(kind = null, tag = null, details = null)
    private val instructions = NoticeKind.InstructionsUpdated

    private companion object {
        /** How many messages the fake server gives per page. */
        const val PAGE = 24
    }

    @Test
    fun `B-620 a failed automation run in the chat feed tells once on the automation's own alert`() = runTest {
        val shade = FakeShade()
        val server = MutableServerAutomations(sample(id = "aut_1", runCount = 1))
        val coordinator = coordinator(shade, server)
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        // From its prompt on the run is the automation's: the chat's stopped and finished alerts stand down.
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"))
        coordinator.onRunEnded(profile, "ses_9", failure)
        assertEquals(AlertKind.AutomationFailed, shade.shown.single().first.kind)
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "still the automation's after it ended")

        // The idle that follows a failure, and the read's word of the same run, tell nothing more.
        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Idle)
        server.items["aut_1"] = failed("aut_1", run = 2)
        coordinator.pollAutomations(profile)
        assertEquals(1, shade.shown.size)
        assertEquals(1, shade.shown.single().first.automationCoveredRuns)

        // The chat's next run is the person's own: its alerts speak for it again.
        coordinator.onRunStarted(profile, "ses_9")
        assertFalse(coordinator.isAutomationSession(profile, "ses_9"))
    }

    @Test
    fun `B-620 a readable finding tells the first line of its own run's reply`() = runTest {
        val shade = FakeShade()
        val coordinator = coordinator(shade, MutableServerAutomations(sample(id = "aut_1", runCount = 1)))
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        prompt(1)
        reply("msg_1", "Disk is almost full")
        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        reply("msg_2", "2 errors in logs\nSee the build page")
        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)

        val posted = shade.shown.single().first
        assertEquals(AlertKind.AutomationMessage, posted.kind)
        assertEquals("2 errors in logs", posted.detail)
        assertEquals("ses_9", posted.sessionId)
    }

    @Test
    fun `B-620 a quiet run after an older readable reply makes no notification`() = runTest {
        val shade = FakeShade()
        val coordinator = coordinator(shade, MutableServerAutomations(sample(id = "aut_1", runCount = 1)))
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        // Run 1 found something while nobody was told; run 2 says nothing readable.
        prompt(1)
        reply("msg_1", "Disk is almost full")
        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        reply("msg_2", "   ")
        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)
        assertTrue(shade.shown.isEmpty(), "run 1's words are not run 2's finding")
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "a quiet run has no finished alert either")

        // A run whose prompt the chat does not hold anywhere has no reply to tell: nothing older speaks for it.
        val late = MessageInfo(id = "inb_3", sessionId = "ses_9", role = Role.User, automation = run(3))
        coordinator.onMessage(profile, late)
        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)
        assertTrue(shade.shown.isEmpty())
    }

    @Test
    fun `B-620 a run longer than one page of its chat still tells its own reply's first line`() = runTest {
        val shade = FakeShade()
        val coordinator = coordinator(shade, MutableServerAutomations(sample(id = "aut_1", runCount = 1)))
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        // Run 1 found something earlier. Run 2 takes thirty steps before it says what it found, so its prompt
        // is not among the newest messages of the chat when it ends.
        prompt(1)
        reply("msg_1", "Disk is almost full")
        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        repeat(30) { step("stp_$it") }
        // A notice the server writes into the chat mid-run is nobody's prompt: the run and its reply go on.
        coordinator.onEvent(profile, GatewayEvent.Notice(SessionId("ses_9"), MessageId("msg_note"), instructions))
        coordinator.onMessage(profile, notice("msg_note"))
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "a notice does not take the chat back")
        reply("msg_2", "2 errors in logs\nSee the build page")
        assertTrue(chat.size > PAGE + 2, "the prompt lies beyond the first page")

        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)
        val posted = shade.shown.single().first
        assertEquals(AlertKind.AutomationMessage, posted.kind)
        assertEquals("2 errors in logs", posted.detail)
        assertEquals(2, pagesRead, "read back only until the run's own prompt")
    }

    @Test
    fun `B-620 a run whose prompt lies beyond the bound of pages tells nothing`() = runTest {
        val shade = FakeShade()
        val coordinator = coordinator(shade, MutableServerAutomations(sample(id = "aut_1", runCount = 1)))
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        repeat(10 * PAGE) { step("stp_$it") }
        reply("msg_2", "2 errors in logs")

        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)
        assertEquals(10, pagesRead, "the read back stops at its bound")
        assertTrue(shade.shown.isEmpty(), "a reply that cannot be tied to the run's prompt is not told")
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "and the chat's finished alert stays down")
    }

    @Test
    fun `B-620 a failed run that spans a reconnect tells once on the automation's own alert`() = runTest {
        val shade = FakeShade()
        val coordinator = coordinator(shade, MutableServerAutomations(sample(id = "aut_1", runCount = 1)))
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        coordinator.setConnected(profile, false)
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "offline the run is still the automation's")
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // the read after a reconnect is a baseline again
        assertTrue(shade.shown.isEmpty())

        coordinator.onRunEnded(profile, "ses_9", failure)
        assertEquals(AlertKind.AutomationFailed, shade.shown.single().first.kind)
        assertEquals(1, shade.shown.single().first.automationCoveredRuns)
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "the chat's stopped alert stands down")
    }

    @Test
    fun `B-620 a quiet run that spans a reconnect stays quiet until a person's own prompt takes the chat`() = runTest {
        val shade = FakeShade()
        val coordinator = coordinator(shade, MutableServerAutomations(sample(id = "aut_1", runCount = 1)))
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        prompt(1)
        reply("msg_1", "Disk is almost full")
        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunStarted(profile, "ses_9")
        reply("msg_2", "   ")
        coordinator.setConnected(profile, false)
        coordinator.setConnected(profile, true)
        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)
        assertTrue(shade.shown.isEmpty(), "a quiet run tells nothing, on either path")
        assertTrue(coordinator.isAutomationSession(profile, "ses_9"), "the chat's finished alert stands down")

        // A prompt that no automation sent is the person's own: the chat's alerts speak for what follows.
        coordinator.onMessage(profile, MessageInfo(id = "inb_person", sessionId = "ses_9", role = Role.User))
        assertFalse(coordinator.isAutomationSession(profile, "ses_9"))
    }

    @Test
    fun `B-620 Pause this automation pauses via server API and automation row then shows paused`() = runTest {
        val shade = FakeShade()
        val server = MutableServerAutomations(sample(id = "aut_1", status = AutomationStatus.Active, runCount = 1))
        val coordinator = coordinator(shade, server)

        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline
        assertTrue(shade.shown.isEmpty())

        // Finding arrives via metadata.automation.
        coordinator.onMessage(profile, prompt(2))
        reply("msg_2", "2 errors in logs")
        coordinator.onRunEnded(profile, "ses_9", RunOutcome.Succeeded)
        val posted = shade.shown.single().first
        assertEquals(AlertKind.AutomationMessage, posted.kind)
        assertEquals("aut_1", posted.requestId)
        assertTrue(posted.actionToken.isNotBlank())

        // Tap Pause this automation from the notification -> server API pauses it, row now shows Paused.
        val pauseResult = coordinator.pauseFromShade(profile, "aut_1", posted.actionToken)
        assertIs<Outcome.Ok<Unit>>(pauseResult)
        assertEquals(listOf(profile to "aut_1"), server.pauseRequests)
        val listed = assertIs<Outcome.Ok<ImmutableList<ServerAutomation>>>(server.automations(profile))
        assertEquals(AutomationStatus.Paused, listed.value.single().status)
        assertTrue(posted.automationGroup in shade.dismissedGroups)

        // Token is one-shot: replay is refused.
        val replay = assertIs<Outcome.Failed>(coordinator.pauseFromShade(profile, "aut_1", posted.actionToken))
        assertEquals(Failure.Refused(RefusalReason.StaleScope), replay.failure)
    }

    private fun failed(id: String, run: Int) = sample(id = id, runCount = run, outcome = AutomationOutcome.Failed)

    private fun stale(outcome: Outcome<Unit>) {
        assertEquals(Failure.Refused(RefusalReason.StaleScope), assertIs<Outcome.Failed>(outcome).failure)
    }

    @Test
    fun `B-620 a quiet automation's Pause stays good while a noisy one replaces its own rows`() = runTest {
        val shade = FakeShade()
        val server = MutableServerAutomations(sample(id = "aut_1", runCount = 0), sample(id = "aut_2", runCount = 0))
        val coordinator = coordinator(shade, server)
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline

        server.items["aut_2"] = failed("aut_2", run = 1)
        coordinator.pollAutomations(profile)
        val quiet = shade.shown.single().first

        // The noisy automation replaces its three rows 300 times; the quiet one's row is still in the shade.
        for (run in 1..300) {
            server.items["aut_1"] = failed("aut_1", run)
            coordinator.pollAutomations(profile)
        }
        assertEquals(301, shade.shown.size)

        assertIs<Outcome.Ok<Unit>>(coordinator.pauseFromShade(profile, "aut_2", quiet.actionToken))
        assertEquals(listOf(profile to "aut_2"), server.pauseRequests)
    }

    @Test
    fun `B-620 a replaced row's Pause and a dismissed row's Pause are retired`() = runTest {
        val shade = FakeShade()
        val server = MutableServerAutomations(sample(id = "aut_1", runCount = 0))
        val coordinator = coordinator(shade, server)
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline
        for (run in 1..4) {
            server.items["aut_1"] = failed("aut_1", run)
            coordinator.pollAutomations(profile)
        }
        val rows = shade.shown.map { it.first }
        assertEquals(rows[0].key, rows[3].key, "the fourth alert replaces the first row")

        // The first row is gone from the shade: its token pauses nothing.
        stale(coordinator.pauseFromShade(profile, "aut_1", rows[0].actionToken))
        assertTrue(server.pauseRequests.isEmpty())

        // The row that replaced it is on screen: its Pause works, and clears the automation's group.
        assertIs<Outcome.Ok<Unit>>(coordinator.pauseFromShade(profile, "aut_1", rows[3].actionToken))
        assertEquals(listOf(profile to "aut_1"), server.pauseRequests)

        // The group's other rows went with it: their tokens are retired too.
        stale(coordinator.pauseFromShade(profile, "aut_1", rows[2].actionToken))
        assertEquals(1, server.pauseRequests.size)
    }

    @Test
    fun `B-620 offline connection posts nothing and failed pause does not pretend paused`() = runTest {
        val shade = FakeShade()
        val server = MutableServerAutomations(sample(id = "aut_1", status = AutomationStatus.Active, runCount = 1))
        val coordinator = coordinator(shade, server)

        // Offline: poll and messages do nothing.
        coordinator.pollAutomations(profile)
        coordinator.onMessage(profile, prompt(2))
        coordinator.onRunEnded(profile, "ses_9", failure)
        assertTrue(shade.shown.isEmpty())
        assertFalse(coordinator.isAutomationSession(profile, "ses_9"), "offline, nothing is the automation's")

        // Online failure, then server goes offline before user taps Pause.
        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile)
        server.items["aut_1"] = sample(id = "aut_1", runCount = 3, outcome = AutomationOutcome.Failed)
        coordinator.pollAutomations(profile)
        val posted = shade.shown.single().first

        server.failPause = Failure.Network()
        val failedPause = assertIs<Outcome.Failed>(coordinator.pauseFromShade(profile, "aut_1", posted.actionToken))
        assertIs<Failure.Network>(failedPause.failure)
        assertEquals(AutomationStatus.Active, server.items["aut_1"]!!.status)
        assertTrue(shade.dismissedGroups.isEmpty())
    }
}
