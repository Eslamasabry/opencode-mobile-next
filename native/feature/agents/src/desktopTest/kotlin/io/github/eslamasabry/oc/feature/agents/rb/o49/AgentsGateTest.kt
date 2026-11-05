@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.agents.rb.o49

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.account.*
import io.github.eslamasabry.oc.feature.agents.hub.*
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
    private val target = AgentAccountTarget(ProfileId("codex"), "My Codex host")
    @Test fun signed_out() {
        FlutterGoldenGate.check("agent_account_signed_out", frame = FlutterGoldenGate.Frame.Screen) {
            AgentsScreen(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Ready,
                account = AgentRuntimeAccount(requiresSignIn = true), canSignIn = true)), Actions())
        }
    }
    @Test fun signed_in() {
        FlutterGoldenGate.check("agent_account_signed_in", frame = FlutterGoldenGate.Frame.Screen) {
            AgentsScreen(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Ready,
                account = AgentRuntimeAccount(type = "chatgpt", email = "person@example.com", plan = "plus", requiresSignIn = true),
                limits = persistentListOf(AccountRateBucket("Codex", AccountRateWindow(32, 300, null), AccountRateWindow(68, 10080, null))),
                usage = AccountTokenUsage(184250, 12640))), Actions())
        }
    }
    @Test fun unavailable_account() {
        FlutterGoldenGate.check("agent_account_unsupported", frame = FlutterGoldenGate.Frame.Screen) {
            AgentsScreen(AgentsUi(accountTarget = target, accountPanel = AccountPanelState(status = AccountPanelStatus.Unavailable)), Actions())
        }
    }
    @Test fun `the install chip targets the exact phone agent`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            AgentsScreen(AgentsUi(phone = PhoneAgentsState(available = true, agents = persistentListOf(
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready),
                PhoneAgentRow("gemini", "Gemini CLI", PhoneAgentStatus.NotInstalled, installable = true),
            ))), actions)
        } }
        onNodeWithText("Install").performClick()
        assertEquals("gemini", actions.installed)
    }
}
