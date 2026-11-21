package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhone
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneAgent
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/**
 * Plan holds: the Agent and model sheet offers Build/Plan for a phone agent only when that agent can be held to Plan
 * (its own read-only mode, plus the app declining its changes), and says "Build and Plan are for OpenCode." only where
 * no Build/Plan choice is offered. Never both a choice and the sentence that says there is none.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationPlanSheetTest {
    private class Actions : NewConversationActions {
        override fun back() {}

        override fun addServer() {}

        override fun chooseServer(profile: ProfileId) {}

        override fun draftChanged(text: String) {}

        override fun chooseDirectory(directory: String) {}

        override fun chooseModel(model: ModelRef?) {}

        override fun chooseAgent(agent: String?) {}

        override fun separateCopy(on: Boolean) {}

        override fun sheet(sheet: NewConversationSheet?) {}

        override fun start() {}

        override fun import(conversation: ImportableConversation) {}

        override fun retryLoad() {}
    }

    private val agents = AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }

    private fun state(agent: String) =
        NewConversationUi(
            profile = ProfileId("p1"),
            servers = listOf(NewConversationServer(ProfileId("p1"), "This phone", connected = true)),
            setup =
                NewConversationSetup(
                    directory = "/root/projects/app",
                    projects = emptyList(),
                    models = emptyList(),
                    agents = emptyList(),
                    modes = listOf("Build", "Plan"),
                    phone =
                        NewConversationPhone(
                            ProfileId("phone-agents"),
                            agents.map { NewConversationPhoneAgent(it.id.raw, it.name, it.id.raw) },
                            emptyList(),
                            selected = agent,
                        ),
                ),
            directory = "/root/projects/app",
            phoneAgent = agent,
            sheet = NewConversationSheet.Agents,
        )

    @Test
    fun `B-AGENT-PLAN-020 for every phone agent the offered modes and the sentence agree`() {
        for (agent in agents) {
            kitUiTest {
                val ui = state(agent.id.raw)
                setContent {
                    KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                        NewConversationScreen(ui, Actions())
                    }
                }
                val plan = onAllNodesWithText("Plan", substring = false).fetchSemanticsNodes().size
                val sentence = onAllNodesWithText("Build and Plan are for", substring = true).fetchSemanticsNodes().size
                if (ui.phoneCanPlan) {
                    onAllNodesWithText("Build and Plan are for", substring = true).assertCountEquals(0)
                    check(plan >= 1) { "${agent.id}: Plan is offered" }
                } else {
                    check(sentence == 1) { "${agent.id}: the sentence says why there is no Plan" }
                    check(plan == 0) { "${agent.id}: no Plan button is offered next to it" }
                }
            }
        }
    }
}
