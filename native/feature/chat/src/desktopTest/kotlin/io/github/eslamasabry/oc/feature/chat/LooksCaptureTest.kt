package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    private class Outside : ChatOutside {
        override fun copy(text: String) = true
        override fun share(text: String) = true
        override fun checkLink(url: String): LinkCheck = LinkCheck.Ask("example.com")
        override fun openLink(url: String) = true
    }

    private object Actions : BasicComposerActions {
        override fun draftChanged(text: String) {}
        override suspend fun send(text: String) = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
    }

    private fun m(p: String, pn: String, id: String, label: String, v: List<String> = emptyList()) =
        ComposerModel(p, id, label, true, variants = v, providerName = pn, reasoning = true, tools = true, contextLimit = 200_000)

    private val sonnet = m("anthropic", "Anthropic", "sonnet-5", "Claude Sonnet 5", listOf("low", "high"))
    private val models = ComposerModels(
        choices = listOf(
            sonnet,
            m("anthropic", "Anthropic", "opus-5", "Claude Opus 5"),
            m("openai", "OpenAI", "gpt-6", "GPT-6 Sol"),
            m("openai", "OpenAI", "gpt-6-mini", "GPT-6 Mini"),
            m("google", "Google", "gemini-4", "Gemini 4 Pro"),
        ),
        selected = sonnet,
        recent = listOf(m("openai", "OpenAI", "gpt-6", "GPT-6 Sol")),
    )

    private fun code(start: Int, text: String) =
        BlockRow("b:a1/p:$start", "u1", RowKind.Code, "p", start, text, false, false, Origin.Loaded)

    private fun scene(name: String, running: Boolean, open: Boolean) {
        val world = FakeChatWorld()
        world.transcript.value = stateOf(
            prompt("u1", "Fix the login redirect loop. After signing in the app sends me straight back to /login."),
            block("u1", "a1", "p", 0, "I found it. `LoginRedirect.remember` stores **/login** itself as the next route, so after sign-in the app redirects to the login page again. I am guarding that case and adding a test."),
            WorkLineRow("work:u1", "u1", calls = 3, failed = 0, state = if (running) WorkState.Running else WorkState.Done),
            code(200, "```kotlin\nfun remember(route: String) {\n    if (route.startsWith(\"/login\")) return\n    target.value = route\n}\n```"),
            block("u1", "a2", "p2", 0, "Running the auth tests now.", live = running),
            run = RunFacts(busy = running),
        )
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = Outside())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        val host = object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(BasicComposerUi(available = true, sending = false, steers = running, targetKey = "p/s", models = models), Actions, chrome, modifier)
            }
        }
        Looks.shoot(
            name,
            advanceMs = 1_500,
            setup = { if (open) { onNodeWithContentDescription("Model").performClick(); waitForIdle(); mainClock.advanceTimeBy(800) } },
        ) {
            val state by states.collectAsState()
            KitStandaloneRedaction { KitSheetHost { ChatScreen(state, p, host) } }
        }
    }

    @Test fun chatRunning() = scene("native-03-chat-running", running = true, open = false)

    @Test fun modelPicker() = scene("native-04-model-picker", running = false, open = true)
}
