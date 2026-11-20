package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtras
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationsFeed
import io.github.eslamasabry.oc.feature.chats.presentation.ReturnBriefFeed
import io.github.eslamasabry.oc.feature.chats.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Cold start (Galaxy S22, build 10053): a tap on Home's + 2.2 s and 2.6 s after launch did nothing, on a page that
 * looked ready (the saved rows stand in while the server opens). The real home page and its real view model, the test
 * clock: the server answers [READY_MS] after the first Home frame, and + is tapped once at each frame from that first
 * frame until 3 s. Every tap opens New conversation exactly once (+ is never drawn disabled on the home).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class HomeColdStartTapTest {
    private val savedLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.forLanguageTag("en"))
    }

    @AfterTest
    fun reset() {
        Dispatchers.resetMain()
        Locale.setDefault(savedLocale)
    }

    private class Nav : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(ChatsRoute.Home)
        val opened = mutableListOf<AppRoute>()

        override fun open(route: AppRoute) {
            opened += route
            top.value = route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Extras : ChatsHomeExtras {
        override val snapshot =
            MutableStateFlow(
                ChatsHomeExtraState(
                    sources = persistentListOf(ChatsListSourceUi(P1, "This phone", shown = true, main = true, onThisPhone = true, unreachable = false)),
                    // The saved rows stand in while the server opens: the page looks ready.
                    opening =
                        ChatsOpeningUi(
                            ChatsOpeningPhase.Connecting,
                            "This phone",
                            rows = ROWS,
                            profile = P1,
                        ),
                )
            )

        override suspend fun showSource(profile: ProfileId, shown: Boolean): Outcome<Unit> = no()

        override suspend fun retrySource(profile: ProfileId): Outcome<Unit> = no()

        override suspend fun retryOpening(): Outcome<Unit> = no()

        override suspend fun setWatched(profile: ProfileId, enabled: Boolean): Outcome<Unit> = no()

        override suspend fun refreshWatched(): Outcome<Unit> = no()

        override suspend fun openWatched(key: String): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        private fun no(): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    private class Feed : ConversationsFeed {
        override val snapshot = MutableStateFlow(ChatFeedSnapshot())

        override suspend fun refresh(): Outcome<Unit> = Unit.ok()
    }

    private class Briefs : ReturnBriefFeed {
        override val brief: StateFlow<ReturnBrief> = MutableStateFlow(ReturnBrief.None)

        override suspend fun dismiss(shown: ReturnBrief): Outcome<Unit> = Unit.ok()
    }

    private object Door : SettingsDoor {
        override val available: Boolean = true

        override fun open() {}
    }

    /** One cold start: its own home state, navigator and view model. */
    private class Start {
        val extras = Extras()
        val feed = Feed()
        val nav = Nav()
        val vm =
            ChatsViewModel(
                feed,
                Briefs(),
                Door,
                { Instant.fromEpochMilliseconds(0) },
                object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(10_000)
                },
                nav,
                home = extras,
            )

        /** The server answers: its live rows replace the saved ones (same rows), and the home stops opening. */
        fun answer() {
            feed.snapshot.value = ChatFeedSnapshot(ROWS)
            extras.snapshot.value = extras.snapshot.value.copy(opening = null)
        }

        val newOpened: Int get() = nav.opened.count { it is ChatsRoute.New }
    }

    @Composable
    private fun Page(start: Start) {
        val top by start.nav.top.collectAsState()
        if (top is ChatsRoute.New) BasicText("New conversation page")
        else {
            val ui by start.vm.state.collectAsState()
            ChatsScreen(ui, start.vm)
        }
    }

    /** Home's + in the foot bar (the waiting line's words also name New conversation). */
    private val plus =
        SemanticsMatcher("the + of the foot bar") { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "New conversation" } == true
        } and hasAnyAncestor(hasTestTag("kit-screen-bottom"))

    /** [check] gets the tap's time, the start, and whether + was there to tap at that frame. */
    private fun ComposeUiTest.startEach(taps: List<Long>, check: (tapMs: Long, start: Start, plusShown: Boolean) -> Unit) {
        var current by mutableStateOf<Start?>(null)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ChatEnvironment {
                    // Motion off: the dropped tap was the view model's, not a moving part, and 188 cold starts must stay quick.
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = Style.Solid),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { current?.let { s -> key(s) { Page(s) } } }
                    }
                }
            }
        }
        mainClock.autoAdvance = false
        for (tapMs in taps) {
            val start = Start()
            current = start
            waitForIdle()
            // 0 ms is the first frame that draws Home's + (the page and its foot bar may take a few frames to come in).
            var waited = 0
            while (onAllNodes(plus).fetchSemanticsNodes().isEmpty()) {
                check(++waited < FIRST_FRAME_LIMIT) { "Home's + never appeared" }
                mainClock.advanceTimeByFrame()
            }
            var at = 0L
            var tapped = false
            var plusShown = false
            var answered = false
            while (at < END_MS) {
                if (!answered && at >= READY_MS) {
                    start.answer()
                    answered = true
                }
                if (!tapped && at >= tapMs) {
                    plusShown = onAllNodes(plus).fetchSemanticsNodes().size == 1
                    if (plusShown) onNode(plus).performClick()
                    tapped = true
                }
                mainClock.advanceTimeByFrame()
                at += FRAME_MS
            }
            check(tapMs, start, plusShown)
        }
    }

    /** Taps from [fromMs] until [untilMs], one per frame, each in its own cold start. */
    private fun tapsFrom(fromMs: Long, untilMs: Long) =
        kitUiTest(360, 780, options = KitUiOptions(timeout = 10.minutes)) {
            val missed = mutableListOf<Long>()
            val twice = mutableListOf<Long>()
            val noPlus = mutableListOf<Long>()
            startEach((fromMs until untilMs step FRAME_MS).toList()) { tapMs, start, plusShown ->
                when {
                    !plusShown -> noPlus += tapMs
                    start.newOpened == 1 -> Unit
                    start.newOpened == 0 -> missed += tapMs
                    else -> twice += tapMs
                }
            }
            assertTrue(
                missed.isEmpty() && twice.isEmpty() && noPlus.isEmpty(),
                "ms after the first Home frame - taps on + that opened nothing (${missed.size}): $missed; " +
                    "opened more than once: $twice; frames with no + to tap: $noPlus",
            )
        }

    @Test
    fun `COLD-TAP-010 a tap on plus at any frame of the first second opens New conversation exactly once`() = tapsFrom(0, 1_008)

    @Test
    fun `COLD-TAP-012 a tap on plus at any frame from 1 s to 2 s opens New conversation exactly once`() = tapsFrom(1_008, 2_000)

    @Test
    fun `COLD-TAP-013 a tap on plus at any frame from 2 s to 3 s, across the server's answer, opens New conversation exactly once`() =
        tapsFrom(2_000, TAPS_UNTIL_MS)

    @Test
    fun `COLD-TAP-011 a tap kept while the server opens says New conversation is on its way until it opens`() =
        kitUiTest(360, 780) {
            val start = Start()
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = Style.Solid),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { Page(start) }
                        }
                    }
                }
            }
            waitForIdle()
            onNode(plus).performClick()
            waitForIdle()
            assertTrue(
                onAllNodes(androidx.compose.ui.test.hasText("New conversation opens as soon as This phone answers.", substring = true))
                    .fetchSemanticsNodes()
                    .isNotEmpty(),
                "the home says the tap was taken",
            )
            assertEquals(0, start.newOpened)
            start.answer()
            waitForIdle()
            assertEquals(1, start.newOpened)
            assertTrue(onAllNodes(androidx.compose.ui.test.hasText("New conversation page")).fetchSemanticsNodes().isNotEmpty())
        }

    private companion object {
        val ROWS = persistentListOf(item("s1", at = 9_000), item("s2", at = 8_000), item("s3", at = 7_000))
        const val FRAME_MS = 16L
        const val READY_MS = 2_400L
        const val TAPS_UNTIL_MS = 3_000L
        const val END_MS = 3_200L
        const val FIRST_FRAME_LIMIT = 120
    }
}
