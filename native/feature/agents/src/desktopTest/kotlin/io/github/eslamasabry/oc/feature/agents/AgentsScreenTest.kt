package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsIssue
import io.github.eslamasabry.oc.feature.agents.hub.AgentsProblem
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.hub.ComputerCheck
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real Agents screen, found by visible text. */
@OptIn(ExperimentalTestApi::class)
class AgentsScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class Recorder : AgentsActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun startAdding() { calls += "add-open" }

        override fun cancelAdding() { calls += "add-cancel" }

        override fun edited() {}

        override fun check(address: String, password: String, folder: String) { calls += "check" }

        override fun add(name: String, address: String, password: String, folder: String) { calls += "save" }

        override fun readAgents(id: ProfileId) { calls += "agents:${id.raw}" }

        override fun newConversation(id: ProfileId) { calls += "new:${id.raw}" }
    }

    @Composable
    private fun Screen(state: AgentsUi, actions: AgentsActions, locale: String = "en") {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { AgentsScreen(state, actions) }
            }
        }
    }

    private val desk = AgentComputer(ProfileId("p1"), "Desk", "ws://127.0.0.1:6767", "/home/me/app", connected = true)

    @Test
    fun `B-AGENT-111 without a phone agent host the phone part says where agents run, never an empty page`() =
        runComposeUiTest {
            setContent { Screen(AgentsUi(), Recorder()) }
            onNodeWithText("Agents run on the built-in server").assertExists()
            onNodeWithText("Add a computer").assertExists()
        }

    @Test
    fun `B-NAV-030 a computer row offers a visible start and agents check`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Screen(
                AgentsUi(computers = persistentListOf(desk), ready = persistentMapOf(desk.id to persistentListOf("Claude Code"))),
                actions,
            )
        }
        onNodeWithText("Desk").assertExists()
        onNodeWithText("Ready on that computer: Claude Code.", substring = true).assertExists()
        onNodeWithText("Start conversation").performClick()
        onNodeWithText("Check agents").performClick()
        assertEquals(listOf("new:p1", "agents:p1"), actions.calls)
    }

    @Test
    fun `B-PROTO-070 the add form checks before saving and names the ready agents`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Screen(AgentsUi(adding = true, check = ComputerCheck.Ready("0.8.0", persistentListOf("Claude Code", "Pi"))), actions)
        }
        onNodeWithText("Ready on that computer: Claude Code, Pi.").assertExists()
        // The form is taller than the test window: scroll each button into view, as a person would.
        onNodeWithText("Check the connection").performScrollTo().performClick()
        onNodeWithText("Save and connect").performScrollTo().performClick()
        assertEquals(listOf("check", "save"), actions.calls)
    }

    @Test
    fun `B-AGENT-160 a refused password reads plainly and the daemon's words stay under Details`() = runComposeUiTest {
        val daemon = "hello.rejected · password_mismatch"
        setContent {
            Screen(
                AgentsUi(issue = AgentsProblem(AgentsIssue.CheckFailed, Failure.SignIn(SignInWhy.PasswordRejected, details(daemon)))),
                Recorder(),
            )
        }
        onNodeWithText("That computer didn't accept the password.").assertExists()
        onNodeWithText(daemon).assertDoesNotExist()
        onNodeWithTag("kit-details-toggle").performClick()
        onNodeWithText(daemon).assertExists()
    }

    @Test
    fun `B-L10N agents screen reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Screen(AgentsUi(), Recorder(), "ar") }
        onNodeWithText("إضافة حاسوب").assertExists()
        onNodeWithText("على حواسيبك").assertExists()
    }
}

@OptIn(RedactorApi::class) private fun details(text: String): Redacted = Redacted(text)
