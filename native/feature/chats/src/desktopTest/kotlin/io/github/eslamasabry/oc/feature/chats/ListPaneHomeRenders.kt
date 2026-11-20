package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.FeedRunning
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumn
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPaneColumn
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.LocalOpenBeside
import io.github.eslamasabry.oc.ui.routes.LocalRestingBeside
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Pictures only (asserts nothing), big screens slice 3: the REAL Home and New conversation at the sizes the shell gives
 * them beside each other on a 1280 x 800 window (Home in the 360 dp list pane with the open conversation's row chosen;
 * New conversation in the 920 dp pages pane, its column at the pane's start), and Home at the full window as today.
 * Each pane is its own `KitPaneWindow`, which renders as a window of its size (slice 2). Dark, density 2, into
 * `build/bigscreen/list-pane-real/`; a script outside the repository joins the panes with the kit hairline.
 */
@OptIn(ExperimentalTestApi::class)
class ListPaneHomeRenders {
    private fun feedItem(
        id: String,
        title: String,
        minutesAgo: Long,
        preview: String = "",
        dir: String = "/root/projects/oc_app",
        agent: String = "opencode",
    ): ChatFeedItem =
        item(
            id,
            dir,
            at = NOW - minutesAgo * 60_000L,
            title = title,
        ).copy(preview = preview, agentId = agent)

    private fun homeState(): io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi {
        val items =
            listOf(
                feedItem(
                    "explain",
                    "Explain this project",
                    0,
                    "Reading lib/ui/kit to map how the parts fit together",
                    agent = "claude",
                )
                    .copy(status = ChatStatus.Running, running = FeedRunning(NOW - 4 * 60_000L)),
                feedItem(
                    "ask",
                    "Run the tests",
                    5,
                    "Wants to run npm test",
                ).copy(status = ChatStatus.NeedsYou, permission = FeedPermission("req1", "npm test")),
                feedItem(
                    "checkout",
                    "Fix flaky checkout test",
                    12,
                    "Made the test wait on the settled state",
                    dir = "/root/projects/shopfront",
                    agent = "claude",
                ),
                feedItem(
                    "long",
                    "Rework the reconnect loop so a dropped stream refetches the session list and the open co" +
                        "nversation instead of replaying the missed events from the last cursor",
                    20,
                    "Refetching now",
                ),
                feedItem("hi", "Hi", 60 * 26, "Hi! What would you like to work on today?"),
                feedItem(
                    "who",
                    "You know who you are and what you do",
                    60 * 27,
                    "I am OpenCode, a coding agent working in your folder",
                    dir = "/root/projects/site",
                ),
                feedItem("ready", "Start the release notes", 60 * 30, "Ready when you are.", agent = "codex"),
                feedItem("build", "What should we build?", 60 * 50, "Hi there, what should we build?"),
                feedItem(
                    "help",
                    "Fix the login bug",
                    60 * 52,
                    "The token is read before the profile loads",
                    dir = "/root/projects/site",
                ),
                feedItem("o1", "Older one", 60 * 80, "This one is past the five"),
                feedItem("o2", "Older two", 60 * 90, "So is this one", dir = "/root/projects/docs"),
            )
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()),
                ReturnBrief.None,
                ChatsFilters(),
                startOfTodayMs = NOW - 12 * 3_600_000L,
                settingsAvailable = true,
                nowMs = NOW,
            )
        return base.copy(
            home =
                base.home.copy(
                    sources =
                        persistentListOf(
                            ChatsListSourceUi(
                                profile = ProfileId("this-phone"),
                                name = "This phone",
                                shown = true,
                                main = true,
                                onThisPhone = true,
                                unreachable = false,
                            ),
                        ),
                ),
        )
    }

    private object NewActions : NewConversationActions {
        override fun back() = Unit
        override fun addServer() = Unit
        override fun chooseServer(profile: ProfileId) = Unit
        override fun draftChanged(text: String) = Unit
        override fun chooseDirectory(directory: String) = Unit
        override fun chooseModel(model: ModelRef?) = Unit
        override fun chooseAgent(agent: String?) = Unit
        override fun separateCopy(on: Boolean) = Unit
        override fun sheet(sheet: NewConversationSheet?) = Unit
        override fun start() = Unit
        override fun import(conversation: ImportableConversation) = Unit
        override fun retryLoad() = Unit
    }

    private val p1 = ProfileId("p1")
    private val sonnet = NewConversationModel(
        ModelRef("anthropic", "sonnet"),
        "Claude Sonnet 4.5",
        providerName = "Anthropic",
        variants = listOf("low", "medium", "high"),
    )
    private val newUi =
        NewConversationUi(
            servers = listOf(NewConversationServer(p1, "This phone", connected = true)),
            profile = p1,
            setup =
                NewConversationSetup(
                    "/root/projects/shop",
                    listOf(
                        NewConversationProject("/root/projects/shop", "shop", false),
                        NewConversationProject("/root/projects/oc_app", "oc_app", false),
                        NewConversationProject("/root/projects/site", "site", false),
                    ),
                    listOf(
                        sonnet,
                        NewConversationModel(
                            ModelRef("anthropic", "opus"),
                            "Claude Opus 4.1",
                            providerName = "Anthropic",
                        ),
                    ),
                    emptyList(),
                    model = sonnet.model,
                ),
            directory = "/root/projects/shop",
            model = sonnet.model,
            text = "",
        )

    private fun shoot(name: String, wDp: Int, hDp: Int, content: @Composable () -> Unit) {
        val out = File("build/bigscreen/list-pane-real").also { it.mkdirs() }
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("en"))
        try {
            val platform = DesktopKitPlatform()
            runDesktopComposeUiTest(wDp * 2, hDp * 2) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(2f, 1f),
                        LocalKitPlatform provides platform,
                    ) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = platform,
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(
                                Modifier.fillMaxSize().background(LocalKitColors.current.ground),
                            ) { ChatEnvironment(content) }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** The list pane: Home in 360 dp with the conversation open beside it chosen ("Fix flaky checkout test"). */
    @Test
    fun homeListPane() =
        shoot("home-pane-360x800", 360, 800) {
            KitPaneWindow(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalOpenBeside provides ChatRoute(ConversationRef(P1, SessionId("checkout"))),
                ) {
                    ChatsScreen(homeState(), RecordingChatsActions())
                }
            }
        }

    /** Decision 3: the list pane beside a conversation in a 1024 x 1366 portrait tablet (360 dp of 1024). */
    @Test
    fun homeListPanePortrait() =
        shoot("home-pane-360x1366", 360, 1366) {
            KitPaneWindow(Modifier.fillMaxSize()) {
                val open = ChatRoute(ConversationRef(P1, SessionId("checkout")))
                CompositionLocalProvider(LocalOpenBeside provides open) { ChatsScreen(homeState(), RecordingChatsActions()) }
            }
        }

    /** The list pane with New conversation resting beside it: no row chosen, no second +. */
    @Test
    fun homeListPaneNothingOpen() =
        shoot("home-pane-rest-360x800", 360, 800) {
            KitPaneWindow(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalOpenBeside provides ChatsRoute.New(profile = null),
                ) { ChatsScreen(homeState(), RecordingChatsActions()) }
            }
        }

    /** No conversations yet, New conversation resting beside the list (the tablet emulator's first screen). */
    @Test
    fun emptyListPane() =
        shoot("home-empty-pane-360x800", 360, 800) {
            KitPaneWindow(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalOpenBeside provides ChatsRoute.New(profile = null)) {
                    ChatsScreen(ChatsUi(phase = ChatsPhase.Empty), RecordingChatsActions())
                }
            }
        }

    /** The pages pane at rest: New conversation in 920 dp, at the pane's start, no Close. */
    @Test
    fun newConversationPane() =
        shoot("new-pane-920x800", 920, 800) {
            KitPaneWindow(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalKitPaneColumn provides KitPaneColumn.PAGE_MAX_DP.dp, LocalRestingBeside provides true) {
                    NewConversationScreen(newUi.copy(text = ""), NewActions)
                }
            }
        }

    /** The phone, unchanged (before and after must not differ by a pixel): Home, empty Home, New conversation. */
    @Test
    fun phone() {
        shoot("phone-home-360x780", 360, 780) { ChatsScreen(homeState(), RecordingChatsActions()) }
        shoot("phone-empty-360x780", 360, 780) { ChatsScreen(
            ChatsUi(phase = ChatsPhase.Empty),
            RecordingChatsActions(),
        ) }
        shoot("phone-new-360x780", 360, 780) { NewConversationScreen(newUi, NewActions) }
    }

    /** Before: today's empty Home over the whole 1280 x 800 window. */
    @Test
    fun emptyBefore() = shoot("before-home-empty-1280x800", 1280, 800) { ChatsScreen(
        ChatsUi(phase = ChatsPhase.Empty),
        RecordingChatsActions(),
    ) }

    /** Before: today's Home over the whole 1280 x 800 window. */
    @Test
    fun homeBefore() = shoot("before-home-1280x800", 1280, 800) { ChatsScreen(homeState(), RecordingChatsActions()) }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
