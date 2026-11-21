package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-AGENT-189 / B-CHAT-071, drawn: what the person reads when Claude Code's sign-in has expired and a message to it
 * was refused, in a conversation on this phone (build 10073). One block under the message names the agent and offers
 * its sign-in; the banner under the header does not say it a second time; the raw text and "/login" are not on the
 * screen until Details is opened; and a failed reach on this phone is not called "your computer".
 */
@OptIn(ExperimentalTestApi::class, RedactorApi::class)
class ExpiredSignInBubbleTest {
    private val ownerText = "Unable to validate model: OAuth refresh token is no longer valid; run /login to re-authenticate"
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

    private fun screen(
        nav: RecordingNavigator,
        failure: Failure,
        outside: Outside,
        state: PendingState = PendingState.Failed,
    ): Pair<ChatPresenter, FakeChatWorld> {
        val world = AgentWorld(ref)
        world.pending.value = persistentListOf(PendingSend(4, "Whats next", state, failure))
        return ChatPresenter(world, nav, Immediate, outside = outside) to world
    }

    @Test
    fun `B-AGENT-189 an expired Claude Code sign-in is said once, names the agent and offers Sign in again`() =
        kitUiTest {
            val nav = RecordingNavigator()
            val (p, world) = screen(nav, Failure.ComputerDidNotAnswer(Redacted(ownerText)), Outside(owner))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Whats next").assertExists()
            onNodeWithText("Claude Code needs you to sign in again").assertExists()
            onAllNodesWithText("couldn't reach", substring = true, ignoreCase = true).assertCountEquals(0)
            onAllNodesWithText("computer", substring = true, ignoreCase = true).assertCountEquals(0)
            onAllNodesWithText("/login", substring = true).assertCountEquals(0)
            onAllNodesWithTag("chat-send-error-try-again").assertCountEquals(0)
            onAllNodesWithText("Your message wasn't sent").assertCountEquals(1)
            onNodeWithTag("chat-send-sign-in-again").performClick()
            waitForIdle()
            assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "claude")), nav.opened.toList())
            assertEquals(emptyList<String>(), world.calls.filter { it.startsWith("retrySend") }, "nothing resent")
        }

    @Test
    fun `B-CHAT-071 a failed message in view is said by its block alone, not again by a banner`() =
        kitUiTest {
            val nav = RecordingNavigator()
            val (p, _) = screen(nav, Failure.Unknown(), Outside(null))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onAllNodesWithText("Your message wasn't sent").assertCountEquals(1)
            onAllNodesWithTag("chat-send-error-try-again").assertCountEquals(1)
        }

    @Test
    fun `B-AGENT-189 a failed reach on this phone names the phone's agents, never a computer`() =
        kitUiTest {
            val nav = RecordingNavigator()
            val (p, _) = screen(nav, Failure.ComputerDidNotAnswer(), Outside(owner))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("The agents on this phone did not answer", substring = true).assertExists()
            onAllNodesWithText("computer", substring = true, ignoreCase = true).assertCountEquals(0)
        }

    @Test
    fun `B-AGENT-189 away from this phone the same failure still says the computer`() =
        kitUiTest {
            val nav = RecordingNavigator()
            val (p, _) = screen(nav, Failure.ComputerDidNotAnswer(), Outside(null))
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            setContent {
                val state by states.collectAsState()
                Root { ChatScreen(state, p, composer) }
            }
            onNodeWithText("Couldn't reach your computer", substring = true).assertExists()
        }

    @Test
    fun `B-AGENT-189 a failed reply on this phone never says the computer running OpenCode`() {
        for ((reason, expected) in
            listOf(
                "getaddrinfo ENOTFOUND api.anthropic.com" to "Check the internet connection on this phone",
                "ENOSPC: no space left on device" to "This phone is out of storage space.",
            )) {
            kitUiTest {
                val world = AgentWorld(ref)
                world.transcript.value =
                    stateOf(
                        prompt("u1", "go"),
                        io.github.eslamasabry.oc.engine.transcript.state.FooterRow(
                            "footer:u1", "u1", failed = true, reason = reason, providerId = "claude",
                        ),
                    )
                val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = Outside(owner))
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
                setContent {
                    val state by states.collectAsState()
                    Root { ChatScreen(state, p, composer) }
                }
                onNodeWithText(expected, substring = true).assertExists()
                onAllNodesWithText("computer", substring = true, ignoreCase = true).assertCountEquals(0)
            }
        }
    }

    /**
     * The picture of the failed send for the owner's case, 360 dp, density 3, dark: "today" is the same failure
     * not recognised (the agent unknown), "new" is the expired sign-in read. Written to build/signin-expired-render
     * (the test's working folder) for review; asserts only that both were drawn.
     */
    @Test
    fun `B-AGENT-189 render the expired sign-in send failure at 360 dp, today and new`() {
        for ((name, runtime) in listOf("today" to null, "new" to "claude")) {
            kitUiTest(1080, 1500) {
                val world = if (runtime == null) FakeChatWorld(ref) else AgentWorld(ref)
                world.pending.value =
                    persistentListOf(
                        // "today" stands in for the unrecognised answer (the old words read "couldn't reach your computer").
                        PendingSend(
                            4,
                            "Whats next",
                            PendingState.Failed,
                            Failure.ComputerDidNotAnswer(Redacted(if (runtime == null) "ComputerDidNotAnswer" else ownerText)),
                        )
                    )
                val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = if (runtime == null) Outside(null) else Outside(owner))
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f)
                    ) {
                        KitTheme(
                            platform = platform,
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
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
                val file = java.io.File("build/signin-expired-render/$name-dark-360.png")
                file.parentFile.mkdirs()
                javax.imageio.ImageIO.write(image, "png", file)
                assertEquals(1080, image.width)
            }
        }
    }
}
