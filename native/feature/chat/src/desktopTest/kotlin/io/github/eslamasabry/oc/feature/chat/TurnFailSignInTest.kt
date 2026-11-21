package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-AGENT-189 / B-CHAT-072: the "Didn't finish" block of a turn in a Claude Code conversation on this phone whose
 * sign-in ran out (build 10077). One sentence (the sign-in one, not "send a message to carry on" beside it), one
 * button, "Sign in again", which opens that agent's sign-in (the destination Settings > Agents > Claude Code reaches),
 * and Details, which opens. Pressed here with the real presenter, so a dead button fails.
 */
@OptIn(ExperimentalTestApi::class, RedactorApi::class)
class TurnFailSignInTest {
    private val ownerText =
        "Unable to validate model: OAuth refresh token is no longer valid; " +
            "run /login to re-authenticate"
    private val owner = ProfileId("phone")
    private val ref = ConversationRef(ProfileId("phone-agents"), SessionId("draft-1"))

    private val platform =
        object : KitPlatform by DesktopKitPlatform() {
            override val isDebug: Boolean = true
        }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private class Outside(private val owner: ProfileId?) : ChatOutside {
        override fun phoneAgentOwner(ref: ConversationRef): ProfileId? = owner

        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

        override fun openLink(url: String): Boolean = false
    }

    private class AgentWorld(ref: ConversationRef) : FakeChatWorld(ref) {
        override fun runtimeId(): String? = "claude"
    }

    private fun block(nav: RecordingNavigator): ChatPresenter {
        val world = AgentWorld(ref)
        world.transcript.value =
            stateOf(
                prompt("u1", "Reply with OK"),
                FooterRow("footer:u1", "u1", failed = true, reason = ownerText, providerId = "claude"),
            )
        return ChatPresenter(world, nav, Immediate, outside = Outside(owner))
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

    @Test
    fun `B-AGENT-189 a turn that failed on a lapsed sign-in says the sign-in sentence alone, not also carry on`() =
        kitUiTest {
            val p = block(RecordingNavigator())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Claude Code needs you to sign in again").assertExists()
            onAllNodesWithText("Send a message to ask it to carry on", substring = true).assertCountEquals(0)
            onAllNodesWithText("/login", substring = true).assertCountEquals(0)
        }

    @Test
    fun `B-AGENT-189 the turn failure block has one button, Sign in again, and no providers or model detour`() =
        kitUiTest {
            val p = block(RecordingNavigator())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onAllNodesWithText("Sign in again").assertCountEquals(1)
            onAllNodesWithText("Sign in").assertCountEquals(0)
            onAllNodesWithText("Open providers").assertCountEquals(0)
            onAllNodesWithText("Choose another model").assertCountEquals(0)
            onAllNodesWithText("Open settings").assertCountEquals(0)
        }

    @Test
    fun `B-AGENT-189 pressing Sign in again on the turn failure opens that agent's sign-in`() =
        kitUiTest {
            val nav = RecordingNavigator()
            val p = block(nav)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Sign in again").performClick()
            waitForIdle()
            assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "claude")), nav.opened.toList())
        }

    @Test
    fun `B-AGENT-189 Details on the turn failure opens and holds the agent's own text`() =
        kitUiTest {
            val p = block(RecordingNavigator())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Details").performClick()
            waitForIdle()
            onNodeWithText("refresh token is no longer valid", substring = true).assertExists()
        }

    /**
     * The picture of the block for the owner's case, 360 dp wide at density 3, dark, text 1.0 and 1.3. Written to
     * build/turn-fail-render (this test's working folder) for review; asserts only that each was drawn.
     */
    @Test
    fun `B-AGENT-189 render the turn failure block at 360 dp, text 1_0 and 1_3`() {
        for (scale in listOf(1.0f, 1.3f)) {
            kitUiTest(1080, 1900) {
                val p = block(RecordingNavigator())
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f, scale)
                    ) {
                        KitTheme(
                            platform = platform,
                            appearance =
                                KitAppearance(
                                    mode = io.github.eslamasabry.oc.ui.design.effects.AppearanceMode.Dark,
                                    motion = MotionLevel.Off,
                                ),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            KitStandaloneRedaction {
                                val state by states.collectAsState()
                                ChatScreen(state, p, composer)
                            }
                        }
                    }
                }
                waitForIdle()
                val image = onRoot().captureToImage().toAwtImage()
                val file = java.io.File("build/turn-fail-render/block-dark-360-text-$scale.png")
                file.parentFile.mkdirs()
                javax.imageio.ImageIO.write(image, "png", file)
                assertEquals(1080, image.width)
            }
        }
    }
}
