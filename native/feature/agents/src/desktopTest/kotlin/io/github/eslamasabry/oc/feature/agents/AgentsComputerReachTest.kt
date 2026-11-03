package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentsActions
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** BUILD-PROTOCOLS: each computer row reaches its page (update its agents, import Claude Code conversations). */
@OptIn(ExperimentalTestApi::class)
class AgentsComputerReachTest {
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

        override fun back() {}

        override fun startAdding() {}

        override fun cancelAdding() {}

        override fun edited() {}

        override fun check(address: String, password: String, folder: String) {}

        override fun add(name: String, address: String, password: String, folder: String) {}

        override fun readAgents(id: ProfileId) {}

        override fun newConversation(id: ProfileId) {}

        override fun openComputer(id: ProfileId) { calls += "computer:${id.raw}" }
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
    fun `B-AGENT-167 a computer row has a visible way to its update and import page`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Screen(AgentsUi(computers = persistentListOf(desk)), actions) }
        onNodeWithText("Update and import").performClick()
        assertEquals(listOf("computer:p1"), actions.calls)
    }

    @Test
    fun `B-AGENT-174 the way to the import page reads in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val actions = Recorder()
        setContent { Screen(AgentsUi(computers = persistentListOf(desk)), actions, "ar") }
        onNodeWithText("التحديث والاستيراد").performClick()
        assertEquals(listOf("computer:p1"), actions.calls)
    }
}
