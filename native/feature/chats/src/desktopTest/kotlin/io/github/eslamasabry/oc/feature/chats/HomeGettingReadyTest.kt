package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.AppStart
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Home rows while the phone's own servers get ready (owner, build 10062, right after installing: "Where is waiting
 * indicator?"). While the app starts the agents' helper, agent rows show the working mark alone in the time's
 * place (never the word "Connecting"; a screen reader hears "Getting ready…"); once ready, the times are back.
 */
@OptIn(ExperimentalTestApi::class)
class HomeGettingReadyTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest fun restoreLocale() = Locale.setDefault(savedLocale)

    private val phone = ProfileId("this-phone")
    private val agents = ProfileId("this-phone-agents")
    private val office = ProfileId("office")

    private fun row(id: String, title: String, minutesAgo: Long, agent: String): ChatFeedItem {
        val onAgents = agent != "opencode"
        return item(id, at = NOW - minutesAgo * 60_000L, title = title)
            .copy(
                agentId = agent,
                profileId = if (onAgents) agents else phone,
                sourceId = if (onAgents) "paseo:${agents.raw}" else "opencode:${phone.raw}",
            )
    }

    private val items =
        listOf(
            row("agy1", "Tidy the docs demo README", 12, "antigravity"),
            row("cc1", "Refactor the sync worker", 40, "claude"),
            row("oc1", "Explain this project", 55, "opencode"),
        )

    private fun phoneSource(starting: AppStart? = null, unreachable: Boolean = false, main: Boolean = true) =
        ChatsListSourceUi(phone, "This phone", shown = true, main = main, onThisPhone = true, unreachable = unreachable, starting = starting)

    private fun agentsSource(starting: AppStart? = null, unreachable: Boolean = false) =
        ChatsListSourceUi(
            agents,
            "Agents on this phone",
            shown = true,
            main = false,
            onThisPhone = true,
            unreachable = unreachable,
            agents = true,
            starting = starting,
        )

    private fun ui(vararg sources: ChatsListSourceUi): ChatsUi {
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()),
                ReturnBrief.None,
                ChatsFilters(),
                startOfTodayMs = NOW - 12 * 3_600_000L,
                settingsAvailable = true,
                nowMs = NOW,
            )
        return base.copy(home = base.home.copy(sources = persistentListOf(*sources)))
    }

    /** The app has just been installed: the phone's OpenCode answers, the agents' helper is still starting. */
    private val starting get() = ui(phoneSource(), agentsSource(starting = AppStart.Agents))

    private val ready get() = ui(phoneSource(), agentsSource())

    private fun ComposeUiTest.home(initial: ChatsUi, locale: String = "en"): (ChatsUi) -> Unit {
        Locale.setDefault(Locale.forLanguageTag(locale))
        var state by mutableStateOf(initial)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ChatEnvironment {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = Style.Solid),
                        platform = DesktopKitPlatform(),
                        localeTag = locale,
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { ChatsScreen(state, RecordingChatsActions()) }
                    }
                }
            }
        }
        waitForIdle()
        return { next ->
            state = next
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
    }

    private fun ComposeUiTest.spoken(tag: String): List<String> =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        }

    private fun ComposeUiTest.rowsOf(title: String): List<String> = spoken("kit-recent-row").filter { title in it }

    /** Every text on the page (the notice's name is wrapped in direction marks, so texts are matched by part). */
    private fun ComposeUiTest.texts(): List<String> =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString(" ", transform = AnnotatedString::text) }

    @Test
    fun `HOME-READY-001 replay install - no row rings while the agents start, every row keeps its time`() =
        runDesktopComposeUiTest(360, 900) {
            val show = home(starting)
            // OW2: the wait is named ONCE, in Home's status line; no row rings or says anything of its own.
            for (title in listOf("Tidy the docs demo README", "Refactor the sync worker")) {
                val row = rowsOf(title).single()
                assertTrue("Getting ready" !in row && "min ago" in row, "row: $row")
            }
            // This phone's own OpenCode is up: its row keeps its time.
            val oc = rowsOf("Explain this project").single()
            assertTrue("55 min ago" in oc && "Getting ready" !in oc, "row: $oc")
            assertTrue(texts().none { it == "Connecting" }, "texts: ${texts()}")

            show(ready)
            assertTrue("12 min ago" in rowsOf("Tidy the docs demo README").single())
            assertTrue("40 min ago" in rowsOf("Refactor the sync worker").single())
            assertTrue(spoken("kit-recent-row").none { "Getting ready" in it || "Connecting" in it }, "rows: ${spoken("kit-recent-row")}")
        }

    @Test
    fun `HOME-READY-002 guard - This phone's OpenCode opening or starting keeps its rows' times, no mark`() =
        runDesktopComposeUiTest(360, 900) {
            // This phone is the main server and still opening: the pill carries that wait; its rows keep their times.
            val opening =
                starting.let {
                    it.copy(
                        home =
                            it.home.copy(
                                sources = persistentListOf(phoneSource(), agentsSource()),
                                opening = ChatsOpeningUi(ChatsOpeningPhase.Starting, "This phone", profile = phone, onThisPhone = true),
                            ),
                    )
                }
            val show = home(opening)
            val oc = rowsOf("Explain this project").single()
            assertTrue("55 min ago" in oc && "Getting ready" !in oc, "row: $oc")
            // This phone's OpenCode as a second source that the app is starting: the same.
            show(ui(ChatsListSourceUi(office, "Office PC", shown = true, main = true, onThisPhone = false, unreachable = false), phoneSource(starting = AppStart.Phone, main = false)))
            val second = rowsOf("Explain this project").single()
            assertTrue("55 min ago" in second && "Getting ready" !in second, "row: $second")
        }

    @Test
    fun `HOME-READY-003 guard - an agent update puts no word or ring on the rows, and a failed source shows times`() =
        runDesktopComposeUiTest(360, 900) {
            val show = home(ui(phoneSource(), agentsSource(starting = AppStart.AgentsUpdating)))
            assertTrue("12 min ago" in rowsOf("Tidy the docs demo README").single())
            assertTrue(spoken("kit-recent-row").none { "Getting ready" in it || "Updating" in it }, "rows: ${spoken("kit-recent-row")}")
            show(ui(phoneSource(), agentsSource(unreachable = true)))
            assertTrue(spoken("kit-recent-row").none { "Getting ready" in it }, "rows: ${spoken("kit-recent-row")}")
            assertTrue("12 min ago" in rowsOf("Tidy the docs demo README").single())
        }

    @Test
    fun `B-HOME-227 a source that did not start is said once in a notice with Try again, never on the rows`() =
        runDesktopComposeUiTest(360, 900) {
            val said = "The agents on this phone didn't start"
            // Waiting is not failing: while the helper starts the notice is not shown.
            val show = home(ui(phoneSource(), agentsSource(starting = AppStart.Agents)))
            assertTrue(texts().none { said in it }, "texts: ${texts()}")
            // Refused for good: Home says so once, with its way forward, and no row repeats it.
            show(ui(phoneSource(), agentsSource(unreachable = true)))
            assertEquals(1, texts().count { said in it }, "texts: ${texts()}")
            assertTrue(texts().any { it == "Try again" }, "texts: ${texts()}")
            assertTrue(spoken("kit-recent-row").none { "Getting ready" in it || "Not answering" in it }, "rows: ${spoken("kit-recent-row")}")
        }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
