package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputers
import io.github.eslamasabry.oc.feature.agents.hub.AgentsPresenter
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Owner 2026-11-11: "I had to press 4 times on codex install for it to respond and same for sign in button." From the
 * finger to the effect, with the real presenter behind the page: while the page's own read of the phone is still
 * running (the sign-in probes wait for a helper that did not start), one press on a tile's Install opens its review and
 * one press on Sign in opens the sign-in page.
 */
@OptIn(ExperimentalTestApi::class)
class AgentsTapWhileReadingTest {
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

    private object NoComputers : AgentComputers {
        override val computers = MutableStateFlow<ImmutableList<AgentComputer>>(persistentListOf())

        override suspend fun check(address: String, password: String, folder: String) = Outcome.Failed(Failure.TimedOut())

        override suspend fun add(name: String, address: String, password: String, folder: String) =
            Outcome.Failed(Failure.TimedOut())

        override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> = Outcome.Failed(Failure.TimedOut())

        override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> = Outcome.Failed(Failure.TimedOut())
    }

    /** A phone whose read never ends during the test: the state of the owner's page when the presses were lost. */
    private class StuckReadPhone(vararg rows: PhoneAgentRow) : PhoneAgents {
        override val state =
            MutableStateFlow(PhoneAgentsState(available = true, agents = persistentListOf(*rows), owner = ProfileId("owner")))
        private val never = CompletableDeferred<Unit>()

        override suspend fun refresh(): Outcome<Unit> {
            never.await()
            return Outcome.Ok(Unit)
        }
    }

    @Composable
    private fun Page(phone: PhoneAgents, signIns: MutableList<String>) {
        val scope = rememberCoroutineScope()
        val presenter =
            remember {
                AgentsPresenter(
                    scope,
                    NoComputers,
                    phone,
                    onBack = {},
                    openChat = {},
                    openSignIn = { _, agent -> signIns += agent },
                )
            }
        val state by presenter.state.collectAsState()
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { AgentsScreen(state, presenter) }
            }
        }
    }

    @Test
    fun `one press on a tile's Install opens the install review while the page reads the phone`() = runComposeUiTest {
        val phone =
            StuckReadPhone(
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready),
                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.NotInstalled, installable = true),
            )
        setContent { Page(phone, mutableListOf()) }
        onNodeWithText("Set up Codex").assertDoesNotExist()
        onNode(hasContentDescription("Install Codex") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("Set up Codex").assertExists()
    }

    @Test
    fun `one press on a tile's Sign in opens the sign-in page while the page reads the phone`() = runComposeUiTest {
        val signIns = mutableListOf<String>()
        val phone =
            StuckReadPhone(PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded, signInAvailable = true))
        setContent { Page(phone, signIns) }
        onNode(hasContentDescription("Sign in to Codex") and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(listOf("codex"), signIns)
    }
}
