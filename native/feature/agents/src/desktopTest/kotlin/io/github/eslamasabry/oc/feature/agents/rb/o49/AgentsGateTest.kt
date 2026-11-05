@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.agents.rb.o49

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.account.*
import io.github.eslamasabry.oc.feature.agents.hub.*
import io.github.eslamasabry.oc.feature.agents.ui.AgentAccountPanel
import kotlin.time.Instant
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentsGateTest {
    private class Actions : AgentsActions {
        var installed: String? = null
        var cancelled = false
        override fun cancelAccountSignIn() { cancelled = true }
        override fun back() {}
        override fun startAdding() {}
        override fun cancelAdding() {}
        override fun edited() {}
        override fun check(address: String, password: String, folder: String) {}
        override fun add(name: String, address: String, password: String, folder: String) {}
        override fun readAgents(id: ProfileId) {}
        override fun newConversation(id: ProfileId) {}
        override fun refreshPhone() {}
        override fun requestRemoval(id: String) {}
        override fun cancelRemoval() {}
        override fun confirmRemoval() {}
        override fun installPhone(id: String) { installed = id }
    }
    private val fixtureNow = Instant.parse("2026-09-05T12:00:00Z")
    private val target = AgentAccountTarget(ProfileId("codex"), "My Codex host")
    @Test fun `device sign-in names its host and keeps copy and cancel visible`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            AgentsScreen(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(
                status = AccountPanelStatus.Ready, loginStatus = AccountLoginStatus.Waiting,
                account = AgentRuntimeAccount(requiresSignIn = true), canCancel = true,
                deviceCode = AccountDeviceCode("https://auth.example/device", "DEMO-CODE"))), actions)
        } }
        onNodeWithText("\u2066auth.example\u2069").assertExists()
        onNodeWithContentDescription("Copy sign-in code").assertHasClickAction()
        onNodeWithText("Open official sign-in").assertExists()
        onNodeWithText("Cancel sign-in").performClick()
        assertEquals(true, actions.cancelled)
    }
    @Test fun `buckets with no windows say metrics are unavailable instead of drawing an empty group`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            AgentAccountPanel(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Ready,
                account = AgentRuntimeAccount(type = "chatgpt", requiresSignIn = true),
                limits = persistentListOf(AccountRateBucket("Codex", null, null)))), Actions(), now = fixtureNow)
        } }
        onNodeWithText("Rate limits unavailable").assertExists()
    }
    @Test fun `cancelling keeps a visible disabled Cancel and cannot start a second cancel`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            AgentAccountPanel(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Ready,
                loginStatus = AccountLoginStatus.Cancelling, account = AgentRuntimeAccount(requiresSignIn = true), canCancel = false)), actions, now = fixtureNow)
        } }
        onNodeWithText("Cancel sign-in").assertIsNotEnabled()
        assertEquals(false, actions.cancelled)
    }
    @Test fun signed_out() {
        FlutterGoldenGate.check("agent_account_signed_out", frame = FlutterGoldenGate.Frame.Screen) {
            AgentAccountPanel(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Ready,
                account = AgentRuntimeAccount(requiresSignIn = true), canSignIn = true, updatedAt = fixtureNow)), Actions(), now = fixtureNow)
        }
    }
    @Test fun signed_in() {
        FlutterGoldenGate.check("agent_account_signed_in", frame = FlutterGoldenGate.Frame.Screen) {
            AgentAccountPanel(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Ready,
                account = AgentRuntimeAccount(type = "chatgpt", email = "person@example.com", plan = "plus", requiresSignIn = true),
                limits = persistentListOf(AccountRateBucket("Codex", AccountRateWindow(32, 300, Instant.parse("2026-09-05T18:00:00Z").toEpochMilliseconds()), AccountRateWindow(68, 10080, null))),
                usage = AccountTokenUsage(184250, 12640), updatedAt = fixtureNow)), Actions(), now = fixtureNow)
        }
    }
    @Test fun `unavailable account methods explain their own state without claiming an unsupported server`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            AgentAccountPanel(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Unavailable)), Actions(), now = fixtureNow)
        } }
        onNodeWithText("Account panel unavailable").assertExists()
        onNodeWithText("Sign in with ChatGPT").assertDoesNotExist()
    }
    @Test fun `the install chip targets the exact phone agent`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            AgentsScreen(AgentsUi(phone = PhoneAgentsState(available = true, agents = persistentListOf(
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready),
                PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true),
            ))), actions)
        } }
        onNodeWithContentDescription("Install or update Gemini CLI").performClick()
        assertEquals("gemini", actions.installed)
    }
}
