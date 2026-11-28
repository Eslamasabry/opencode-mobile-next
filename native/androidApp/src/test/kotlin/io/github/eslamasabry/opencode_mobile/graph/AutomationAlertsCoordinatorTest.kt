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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.feature.settings.presentation.ServerAutomationsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
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

    @Test
    fun `B-620 Pause this automation pauses via server API and automation row then shows paused`() = runTest {
        val shade = FakeShade()
        val server = MutableServerAutomations(sample(id = "aut_1", status = AutomationStatus.Active, runCount = 1))
        val coordinator = AutomationAlertsCoordinator(
            shade = shade,
            source = server,
            serverName = { "Home server" },
            alertFacts = { backgroundFacts },
            redact = { it },
        )

        coordinator.setConnected(profile, true)
        coordinator.pollAutomations(profile) // baseline
        assertTrue(shade.shown.isEmpty())

        // Finding arrives via metadata.automation.
        val auto = MessageAutomation(id = "aut_1", name = "Nightly check", run = 2)
        coordinator.onAutomationMessage(profile, "ses_9", auto)
        coordinator.onSessionRunEnded(
            profile = profile,
            sessionId = "ses_9",
            failed = false,
            interrupted = false,
            replyExcerpt = "2 errors in logs",
        )
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
        val coordinator = AutomationAlertsCoordinator(
            shade = shade,
            source = server,
            serverName = { "Home server" },
            alertFacts = { backgroundFacts },
            redact = { it },
        )
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
        val coordinator = AutomationAlertsCoordinator(
            shade = shade,
            source = server,
            serverName = { "Home server" },
            alertFacts = { backgroundFacts },
            redact = { it },
        )
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
        val coordinator = AutomationAlertsCoordinator(
            shade = shade,
            source = server,
            serverName = { "Home server" },
            alertFacts = { backgroundFacts },
            redact = { it },
        )

        // Offline: poll and messages do nothing.
        coordinator.pollAutomations(profile)
        val auto = MessageAutomation(id = "aut_1", name = "Nightly check", run = 2)
        coordinator.onAutomationMessage(profile, "ses_9", auto)
        coordinator.onSessionRunEnded(profile, "ses_9", failed = true, interrupted = false, replyExcerpt = "")
        assertTrue(shade.shown.isEmpty())

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
