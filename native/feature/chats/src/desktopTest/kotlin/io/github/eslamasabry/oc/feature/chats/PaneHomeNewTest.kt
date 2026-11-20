package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitCapsAtStart
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.LocalOpenBeside
import io.github.eslamasabry.oc.ui.routes.LocalRestingBeside
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Big screens, slice 3, the pages beside Home's list (tablet emulator pass of d1998a681d): New conversation resting in
 * the 920 dp pane beside a 360 dp list on a 1280 dp window, and Home in that list pane. The shell provides the same
 * locals (`LocalKitCapsAtStart`, `LocalRestingBeside`, `LocalOpenBeside`); a phone provides none of them.
 */
@OptIn(ExperimentalTestApi::class)
class PaneHomeNewTest {
    private var locale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(locale)

    private object NewActions : NewConversationActions {
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

    private val p1 = ProfileId("p1")
    private val sonnet = NewConversationModel(ModelRef("anthropic", "sonnet"), "Claude Sonnet 4.5", providerName = "Anthropic")
    private val newUi =
        NewConversationUi(
            servers = listOf(NewConversationServer(p1, "This phone", connected = true)),
            profile = p1,
            setup =
                NewConversationSetup(
                    "/root/projects/shop",
                    listOf(NewConversationProject("/root/projects/shop", "shop", false)),
                    listOf(sonnet),
                    emptyList(),
                    model = sonnet.model,
                ),
            directory = "/root/projects/shop",
            model = sonnet.model,
        )

    private var gutter = 0

    private fun ComposeUiTest.show(wDp: Int, beside: Boolean, resting: Boolean, open: Any? = null, content: @Composable () -> Unit) {
        setContent {
            ChatEnvironment {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en") {
                    gutter = LocalKitTokens.current.gutter
                    Box(Modifier.size(wDp.dp, 800.dp)) {
                        KitPaneWindow(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(
                                LocalKitCapsAtStart provides beside,
                                LocalRestingBeside provides resting,
                                LocalOpenBeside provides open as androidx.navigation3.runtime.NavKey?,
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.count(matcher: androidx.compose.ui.test.SemanticsMatcher): Int =
        onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `B-NAV-220 beside the list the start box fills its pane between the gutters`() {
        runComposeUiTest {
            show(920, beside = true, resting = true) { NewConversationScreen(newUi, NewActions) }
            val box = onNodeWithTag("chats-new-field").getBoundsInRoot()
            assertTrue(abs(box.left.value - gutter) <= 1f, "the box starts at the pane's gutter (${box.left})")
            assertTrue(abs(920f - gutter - box.right.value) <= 1f, "the box ends at the pane's other gutter (${box.right})")
        }
        // A phone (or one page on any window): the box keeps the conversation's cap, as before.
        runComposeUiTest {
            show(1280, beside = false, resting = false) { NewConversationScreen(newUi, NewActions) }
            val box = onNodeWithTag("chats-new-field").getBoundsInRoot()
            assertTrue(box.right.value - box.left.value <= 700f + 1f, "one page: the box is held to 700 dp ($box)")
        }
    }

    @Test
    fun `B-NAV-221 beside the list the title starts where the box starts`() = runComposeUiTest {
        show(920, beside = true, resting = true) { NewConversationScreen(newUi, NewActions) }
        val title = onNodeWithText("New conversation").getBoundsInRoot()
        val box = onNodeWithTag("chats-new-field").getBoundsInRoot()
        assertTrue(abs(title.left.value - box.left.value) <= 1f, "title at ${title.left}, box at ${box.left}")
    }

    @Test
    fun `B-NAV-223 the resting New conversation has no Close, one opened over a conversation keeps it`() {
        runComposeUiTest {
            show(920, beside = true, resting = true) { NewConversationScreen(newUi, NewActions) }
            assertEquals(0, count(hasContentDescription("Close")), "nothing to close to beside the list")
        }
        runComposeUiTest {
            show(412, beside = false, resting = false) { NewConversationScreen(newUi, NewActions) }
            assertEquals(1, count(hasContentDescription("Close")), "opened as a page, it closes")
        }
    }

    @Test
    fun `B-NAV-224 with New conversation beside the list the list shows no second New conversation`() {
        val home = ChatsUi(phase = ChatsPhase.List)
        runComposeUiTest {
            show(360, beside = false, resting = false, open = ChatsRoute.New(profile = null)) { ChatsScreen(home, RecordingChatsActions()) }
            assertEquals(0, count(hasContentDescription("New conversation")), "the + is not shown beside New conversation")
        }
        runComposeUiTest {
            show(360, beside = false, resting = false, open = ChatRoute(ConversationRef(p1, SessionId("s1")))) {
                ChatsScreen(home, RecordingChatsActions())
            }
            assertEquals(1, count(hasContentDescription("New conversation")), "beside a conversation the + stays")
        }
        runComposeUiTest {
            show(360, beside = false, resting = false) { ChatsScreen(home, RecordingChatsActions()) }
            assertEquals(1, count(hasContentDescription("New conversation")), "a phone keeps its +")
        }
    }

    @Test
    fun `B-NAV-225 the empty list beside a page is one quiet line and the way to a computer`() {
        val empty = ChatsUi(phase = ChatsPhase.Empty)
        runComposeUiTest {
            show(360, beside = false, resting = false, open = ChatsRoute.New(profile = null)) { ChatsScreen(empty, RecordingChatsActions()) }
            assertEquals(1, count(hasText("No conversations yet")))
            assertEquals(0, count(hasText("agent gets to work", substring = true)), "no calm body beside the page")
            assertEquals(0, count(hasText("New conversation")), "New conversation is the page beside it")
            assertEquals(1, count(hasText("Connect a computer")))
        }
        runComposeUiTest {
            show(360, beside = false, resting = false) { ChatsScreen(empty, RecordingChatsActions()) }
            assertEquals(1, count(hasText("agent gets to work", substring = true)), "a phone keeps today's empty page")
            assertEquals(1, count(hasText("New conversation")))
        }
    }
}
