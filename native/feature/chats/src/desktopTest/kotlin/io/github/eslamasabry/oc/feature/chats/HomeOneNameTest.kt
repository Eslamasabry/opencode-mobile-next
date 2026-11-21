package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
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
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * One name for This phone on Home (home-getting-ready, item 2): the pill, the opening card and the "isn't answering"
 * notice name This phone's own server the way the pill does, in the person's language, never by a name it was once
 * saved under (the old app's "This device (Termux)") nor by the English "This phone" in another language.
 */
@OptIn(ExperimentalTestApi::class)
class HomeOneNameTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest fun restoreLocale() = Locale.setDefault(savedLocale)

    private val phone = ProfileId("this-phone")
    private val office = ProfileId("office")

    private fun ui(sources: List<ChatsListSourceUi>, opening: ChatsOpeningUi? = null): ChatsUi {
        val base =
            ChatsState.build(ChatFeedSnapshot(persistentListOf(item("s1", at = 9_000))), ReturnBrief.None, ChatsFilters(), 10_000, true)
        return base.copy(home = base.home.copy(sources = persistentListOf(*sources.toTypedArray()), opening = opening))
    }

    private fun ComposeUiTest.show(state: ChatsUi, locale: String = "en") {
        Locale.setDefault(Locale.forLanguageTag(locale))
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
    }

    /** Every text on the page (names are wrapped in direction marks, so texts are matched by part). */
    private fun ComposeUiTest.texts(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString(" ", transform = AnnotatedString::text) }

    private fun ComposeUiTest.pill(): String =
        onAllNodesWithTag("kit-status-pill", useUnmergedTree = true).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")

    @Test
    fun `HOME-NAME-001 This phone's own server saved under an old name is This phone on the pill and the opening card`() =
        kitUiTest(360, 900) {
            val opening =
                ChatsOpeningUi(ChatsOpeningPhase.Failed, "This device (Termux)", profile = phone, onThisPhone = true)
            show(ui(listOf(ChatsListSourceUi(phone, "This device (Termux)", shown = true, main = true, onThisPhone = true, unreachable = true)), opening))
            assertTrue(pill().startsWith("This phone, "), "pill: ${pill()}")
            assertTrue(texts().any { "This phone isn’t answering" in it }, "texts: ${texts()}")
            assertEquals(emptyList(), texts().filter { "Termux" in it }, "the old saved name never shows")
        }

    @Test
    fun `HOME-NAME-002 in Arabic the notice names This phone as the pill does, never in English`() =
        kitUiTest(360, 900) {
            show(
                ui(
                    listOf(
                        ChatsListSourceUi(office, "Office PC", shown = true, main = true, onThisPhone = false, unreachable = false),
                        ChatsListSourceUi(phone, "This phone", shown = true, main = false, onThisPhone = true, unreachable = true),
                    )
                ),
                locale = "ar",
            )
            assertTrue(texts().any { "هذا الهاتف" in it }, "texts: ${texts()}")
            assertEquals(emptyList(), texts().filter { "This phone" in it }, "the saved English name never shows")
        }

    @Test
    fun `HOME-NAME-003 guard - a computer keeps the name it was saved under`() =
        kitUiTest(360, 900) {
            val opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "Office PC", profile = office, onThisPhone = false)
            show(ui(listOf(ChatsListSourceUi(office, "Office PC", shown = true, main = true, onThisPhone = false, unreachable = false)), opening))
            assertTrue(pill().startsWith("Office PC, "), "pill: ${pill()}")
        }
}
