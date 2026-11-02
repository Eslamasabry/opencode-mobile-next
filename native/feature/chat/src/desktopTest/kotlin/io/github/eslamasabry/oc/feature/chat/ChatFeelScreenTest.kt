package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** P12-CHAT-FEEL screen tests, by the words a person sees (plan 09 §6.6, §9.1–9.2, R17). */
@OptIn(ExperimentalTestApi::class)
class ChatFeelScreenTest {
    private val platform =
        object : KitPlatform by DesktopKitPlatform() {
            override val isDebug: Boolean = true
        }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                val insert = chrome.pendingInsert()
                KitText(insert?.text ?: "Composer slot", modifier)
            }
        }

    private class Outside : ChatOutside {
        val calls = mutableListOf<String>()

        override fun copy(text: String): Boolean = true.also { calls += "copy $text" }

        override fun share(text: String): Boolean = true.also { calls += "share $text" }

        override fun checkLink(url: String): LinkCheck = LinkCheck.Ask("example.com").also { calls += "check $url" }

        override fun openLink(url: String): Boolean = true.also { calls += "open $url" }
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(
            platform = platform,
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitStandaloneRedaction(content)
        }
    }

    private fun code(start: Int, text: String) =
        BlockRow("b:a1/p:$start", "u1", RowKind.Code, "p", start, text, false, false, Origin.Loaded)

    private fun setUp(world: FakeChatWorld, outside: Outside): ChatPresenter {
        world.transcript.value =
            stateOf(
                prompt("u1", "Show me the docs"),
                block("u1", "a1", "p", 0, "Read **the guide** at [the docs](https://example.com/docs)."),
                code(60, "```kotlin\nval answer = 42\n```"),
                footer("u1"),
            )
        return ChatPresenter(world, RecordingNavigator(), Immediate, outside = outside)
    }

    @Test
    fun `B-CHAT-064 a reply draws its Markdown, not its marks, and a link asks before it opens`() =
        runComposeUiTest {
            val outside = Outside()
            val p = setUp(FakeChatWorld(), outside)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Read the guide at the docs.").assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("**", substring = true).fetchSemanticsNodes().size, "no raw marks")
            p.openLink("https://example.com/docs") // the link span's tap lands here (LinkAnnotation listener)
            waitForIdle()
            onNodeWithText("Open link?").assertIsDisplayed()
            onNodeWithText("Open").performClick()
            waitForIdle()
            assertEquals(listOf("check https://example.com/docs", "open https://example.com/docs"), outside.calls)
        }

    @Test
    fun `B-CHAT-056 a code block shows its code without fences and Copy copies the source verbatim`() =
        runComposeUiTest {
            val outside = Outside()
            val p = setUp(FakeChatWorld(), outside)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("val answer = 42", substring = true).assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("```", substring = true).fetchSemanticsNodes().size)
            onNodeWithText("Copy").performClick()
            assertEquals(listOf("copy val answer = 42"), outside.calls)
        }

    @Test
    fun `B-KIT-051 a long-press on a prompt opens its actions, and Edit and resend fills the composer`() =
        runComposeUiTest {
            val outside = Outside()
            val p = setUp(FakeChatWorld(), outside)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Show me the docs").performTouchInput { longClick() }
            waitForIdle()
            for (label in listOf("Copy", "Share", "Select text", "Edit and resend", "Undo from here")) {
                assertTrue(onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty(), "menu shows $label")
            }
            onNodeWithText("Edit and resend").performClick()
            waitForIdle()
            assertTrue(onAllNodesWithText("Show me the docs").fetchSemanticsNodes().size == 2, "the bubble, and the words in the composer")
        }

    @Test
    fun `09-6_6 Select text shows the whole message in a sheet, closed by Done`() =
        runComposeUiTest {
            val outside = Outside()
            val p = setUp(FakeChatWorld(), outside)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            p.openMessageMenu("prompt:u1")
            waitForIdle()
            onNodeWithText("Select text").performClick()
            waitForIdle()
            onNodeWithTag("chat-select-text").assertIsDisplayed()
            onNodeWithText("Done").performClick()
            waitForIdle()
            assertEquals(null, p.current().select)
        }
}
