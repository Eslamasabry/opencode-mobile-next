package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.security.Redactor
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
import kotlin.test.assertEquals

/**
 * signin-followups: a start refused as "not ready" offers "Check <agent> again", not set-up; a chooser row whose
 * sign-in check did not answer says "Sign-in not confirmed" and asks again; a limit says the limit.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationNotConfirmedTest {
    private class Actions : NewConversationActions {
        var checks = 0
        var setUps = 0

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
        override fun checkPhoneAgents() {
            checks++
        }
        override fun setUpPhoneAgent(agent: String) {
            setUps++
        }
    }

    private val phone = ProfileId("p1")
    private val claude = NewConversationPhoneAgent("claude", "Claude Code", "claude")

    private fun ui(
        needsSetup: List<NewConversationPhoneAgent> = emptyList(),
        choices: List<NewConversationPhoneAgent> = listOf(claude),
        sheet: NewConversationSheet? = null,
        failure: Failure? = null,
        starting: Boolean = false,
    ) =
        NewConversationUi(
            profile = phone,
            servers = listOf(NewConversationServer(phone, "This phone", connected = true)),
            setup =
                NewConversationSetup(
                    "/root/projects/a",
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    phone = NewConversationPhone(ProfileId("h"), choices, needsSetup, selected = "claude"),
                ),
            directory = "/root/projects/a",
            sheet = sheet,
            phoneAgent = if (starting) "claude" else null,
            failure = failure,
            openingChat = starting,
            attempted = starting,
            text = "hello",
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: NewConversationUi, actions: Actions) {
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                NewConversationScreen(state, actions)
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun `SIGNIN-N-1 a not-confirmed agent's row says so and a tap asks again, it does not open set-up`() =
        kitUiTest(360, 900) {
            val actions = Actions()
            val row = claude.copy(signInNeeded = true, signInNotConfirmed = true)
            show(ui(needsSetup = listOf(row), choices = emptyList(), sheet = NewConversationSheet.Agents), actions)
            onNodeWithText("Sign-in not confirmed").assertExists()
            onNodeWithText("Set up").assertDoesNotExist()
            onNodeWithText("Claude Code").performClick()
            assertEquals(1, actions.checks)
            assertEquals(0, actions.setUps)
        }

    @Test
    fun `SIGNIN-N-2 an agent at its limit says the limit, not Set up`() = kitUiTest(360, 900) {
        val row = claude.copy(limitReached = true, limitResets = "Nov 20, 1:00 AM")
        show(ui(needsSetup = listOf(row), choices = emptyList(), sheet = NewConversationSheet.Agents), Actions())
        onNodeWithText("Plan limit reached · resets Nov 20, 1:00 AM").assertExists()
        onNodeWithText("Set up").assertDoesNotExist()
    }

    @Test
    fun `SIGNIN-N-3 a start refused as not ready offers to check the agent again`() = kitUiTest(360, 900) {
        val actions = Actions()
        val refused = Failure.ComputerDidNotAnswer(Redactor().details("Provider claude is not ready (unavailable)"))
        show(ui(failure = refused, starting = true), actions)
        onNodeWithText("Check Claude Code again").performClick()
        assertEquals(1, actions.checks)
        assertEquals(0, actions.setUps)
        onNodeWithText("needs setting up", substring = true).assertDoesNotExist()
    }
}
