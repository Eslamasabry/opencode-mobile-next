package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.diagnostics.AgentHelperStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotice
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Device QA basics-3 B1: "fx stopped in the background / Resume fx" at app open, although nothing had stopped. */
class HomeAgentNoticeRulesTest {
    // fx installed and proved; the helper simply has not been started in this app process yet.
    private val fx = PhoneAgentRow("fx", "fx", PhoneAgentStatus.Stopped)
    private val notCertified: (String) -> Boolean = { false }

    @Test
    fun `after an app start the helper that never ran is not called stopped in the background`() {
        val opened = PhoneAgentsState(available = true, agents = persistentListOf(fx))
        assertTrue(HomeAgentNoticeRules.notices(opened, notCertified).lines.isEmpty())
    }

    @Test
    fun `a helper seen to stop on its own still names the agent and offers its Resume`() {
        val stopped = HomeAgentNotice("fx", "fx", HomeAgentNoticeKind.Stopped)
        val unexpected =
            PhoneAgentsState(available = true, agents = persistentListOf(fx), helperStoppedUnexpectedly = true)
        assertEquals(listOf(stopped), HomeAgentNoticeRules.notices(unexpected, notCertified).lines)
        // The Android variant is a refinement of a stop, as AppPhoneAgents builds it (HelperStopNews): both flags set.
        val byAndroid = unexpected.copy(helperMayHaveBeenStoppedByAndroid = true)
        assertEquals(listOf(stopped), HomeAgentNoticeRules.notices(byAndroid, notCertified).lines)
        // Alone (an exit code 137 on a stop the person asked for) it is not a stop.
        val killCodeOnly =
            PhoneAgentsState(available = true, agents = persistentListOf(fx), helperMayHaveBeenStoppedByAndroid = true)
        assertTrue(HomeAgentNoticeRules.notices(killCodeOnly, notCertified).lines.isEmpty())
    }

    @Test
    fun `sign-in and plan lines keep their rule`() {
        val rows =
            persistentListOf(
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded),
                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.Limit),
                PhoneAgentRow("goose", "Goose", PhoneAgentStatus.SignInNeeded),
            )
        val state = PhoneAgentsState(available = true, agents = rows)
        val lines = HomeAgentNoticeRules.notices(state) { it != "goose" }.lines
        assertEquals(
            listOf(
                HomeAgentNotice("claude", "Claude Code", HomeAgentNoticeKind.SignedOut),
                HomeAgentNotice("codex", "Codex", HomeAgentNoticeKind.LimitReached),
            ),
            lines,
        )
        assertTrue(HomeAgentNoticeRules.notices(state.copy(available = false)) { true }.lines.isEmpty())
    }

    // Gate (w4-agent-install2): a sign-in check that could not run (timed out, no answer) is not news that the agent
    // is signed out, and an installed agent whose sign-in the app cannot read never ran, so it is not "stopped" either.
    @Test
    fun `no signed-out or stopped line for a check that could not run or an agent the app cannot check`() {
        val rows =
            persistentListOf(
                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded, signInCheckFailed = true),
                PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.Unavailable, signInUnchecked = true),
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded),
            )
        val state = PhoneAgentsState(available = true, agents = rows, helperStoppedUnexpectedly = true)
        assertEquals(
            listOf(HomeAgentNotice("claude", "Claude Code", HomeAgentNoticeKind.SignedOut)),
            HomeAgentNoticeRules.notices(state) { true }.lines,
        )
    }

    // Gate: the same lines read from the helper's own exit record, the way AppPhoneAgents builds the state.
    private val exitAt = 1_791_633_600_000L
    private val since = exitAt - 3_600_000 // boot or the app's update, an hour before the exit

    private fun record(
        stopRequested: Boolean = false,
        code: Int = 137,
        launchAt: Long? = exitAt - 600_000,
        running: Boolean = false,
    ): AgentHelperStatus =
        AgentHelperStatus.fromRecord(
            JsonObject(
                buildMap<String, JsonElement> {
                    put("running", JsonPrimitive(running))
                    put("lastExitCode", JsonPrimitive(code))
                    put("lastExitAtMs", JsonPrimitive(exitAt))
                    put("lastStopRequested", JsonPrimitive(stopRequested))
                    put("exitReason", JsonPrimitive(if (code == 137) "memory_or_phantom_kill" else "exited"))
                    launchAt?.let { put("lastLaunchAtMs", JsonPrimitive(it)) }
                }
            )
        )

    private fun lines(helper: AgentHelperStatus?, since: Long = this.since) =
        HomeAgentNoticeRules.notices(
                PhoneAgentsState(
                    available = true,
                    agents = persistentListOf(fx),
                    helperStoppedUnexpectedly = HelperStopNews.stopped(helper, since),
                    helperMayHaveBeenStoppedByAndroid = HelperStopNews.byAndroid(helper, since),
                ),
                notCertified,
            )
            .lines

    private val stoppedLine = listOf(HomeAgentNotice("fx", "fx", HomeAgentNoticeKind.Stopped))

    @Test
    fun `a fresh start with no exit record says nothing`() {
        assertTrue(lines(null).isEmpty())
        assertTrue(lines(AgentHelperStatus.fromRecord(JsonObject(mapOf("running" to JsonPrimitive(false))))).isEmpty())
    }

    @Test
    fun `a helper whose latest run ended on its own while the app lived is said, Android kill or not`() {
        assertEquals(stoppedLine, lines(record()))
        assertEquals(stoppedLine, lines(record(code = 1)))
        // A record written before launch times were kept still counts.
        assertEquals(stoppedLine, lines(record(launchAt = null)))
    }

    @Test
    fun `a helper the person stopped is not said, even when it had to be killed`() {
        assertTrue(lines(record(stopRequested = true)).isEmpty())
        assertTrue(lines(record(stopRequested = true, code = 143)).isEmpty())
    }

    @Test
    fun `an exit from before the phone's boot or the app's update is not said`() {
        assertTrue(lines(record(), since = exitAt + 1).isEmpty())
    }

    @Test
    fun `after Resume started the helper again the old exit is not said again`() {
        // Running now.
        assertTrue(lines(record(launchAt = exitAt + 5_000, running = true)).isEmpty())
        // That run then ended with the app (force-stop, update, the app killed): no exit is written, the old one stays.
        assertTrue(lines(record(launchAt = exitAt + 5_000)).isEmpty())
    }
}
